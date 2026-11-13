package dev.infuse.customs.arc;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.sword.Arc;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the server's arcs. Admins start Arc I and move on through Arc V; the current arc decides
 * which weapons can be forged (its own and every earlier arc's) and which PvP twist is in play.
 * While no arc is running, nothing is locked and there are no twists. Saved in arcs.yml.
 */
public final class ArcService implements Listener {

    /** Materials too rare to hand out as twist rewards. */
    private static final Set<Material> TOO_RARE = EnumSet.of(Material.NETHER_STAR, Material.BEACON, Material.TOTEM_OF_UNDYING,
            Material.CONDUIT, Material.DRAGON_HEAD, Material.HEART_OF_THE_SEA, Material.END_CRYSTAL, Material.WITHER_SKELETON_SKULL,
            Material.DRAGON_BREATH, Material.RECOVERY_COMPASS, Material.SCULK_CATALYST, Material.SCULK_SHRIEKER, Material.NETHERITE_INGOT,
            Material.NETHERITE_SWORD, Material.NETHERITE_SPEAR, Material.NETHERITE_AXE, Material.MACE, Material.TRIDENT, Material.ELYTRA,
            Material.ENCHANTED_GOLDEN_APPLE, Material.ECHO_SHARD, Material.NETHERITE_SCRAP, Material.BEEHIVE);
    private static final long BOUNTY_MS = 5 * 60_000L;

    private final CustomsPlugin plugin;
    private final File file;
    private final Orders orders;
    private final NamespacedKey heartsKey;

    private @Nullable Arc current;
    private long startedAt;
    private @Nullable BossBar bar;
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** Spoils of War: players we made glow. */
    private final Set<UUID> glowing = new HashSet<>();
    /** Bounty Hunt: the current bounty and when they win. */
    private @Nullable UUID bounty;
    private long bountyUntil;
    /** Eternal Hearts: hearts each player has gained (or lost) this arc. */
    private final Map<UUID, Integer> hearts = new HashMap<>();
    /** Collapse: each world's border before it started closing. */
    private final Map<String, double[]> bordersBefore = new HashMap<>();

    public ArcService(CustomsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arcs.yml");
        this.orders = new Orders(plugin, this);
        this.heartsKey = new NamespacedKey(plugin, "eternal_hearts");
    }

    public Orders orders() {
        return orders;
    }

    // ---------------------------------------------------------------- state

    public @Nullable Arc current() {
        return current;
    }

    public boolean running() {
        return current != null;
    }

    /** Whether a weapon can be forged right now: always when no arc runs, else up to the current arc. */
    public boolean unlocked(SwordType type) {
        return current == null || type.arc().ordinal() <= current.ordinal();
    }

    /** Why a weapon can't be forged yet, or null if it can. */
    public @Nullable String lockReason(SwordType type) {
        return unlocked(type) ? null : "unlocks when " + type.arc().label() + " begins";
    }

    private boolean twist(Arc arc) {
        return current == arc && plugin.getConfig().getBoolean("arcs.twists." + arc.title().toLowerCase(java.util.Locale.ROOT), true);
    }

    /** Ability cooldowns aren't changed by any arc at the moment. */
    public double cooldownMultiplier() {
        return 1.0;
    }

    /** Players on the same side (the same order in Arc II): abilities and hits pass them by. */
    public boolean allies(Player a, Player b) {
        return orders.allies(a, b);
    }

    /** Loads the saved arc and puts its twist back in play (after a restart or reload). */
    public void load() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        current = Arc.parse(data.getString("current"));
        startedAt = data.getLong("started", 0);
        orders.read(data.getConfigurationSection("orders"));
        hearts.clear();
        ConfigurationSection h = data.getConfigurationSection("hearts");
        if (h != null) {
            for (String key : h.getKeys(false)) {
                try {
                    hearts.put(UUID.fromString(key), h.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    // a bad entry
                }
            }
        }
        bordersBefore.clear();
        ConfigurationSection b = data.getConfigurationSection("borders-before");
        if (b != null) {
            for (String world : b.getKeys(false)) {
                bordersBefore.put(world, new double[]{b.getDouble(world + ".x"), b.getDouble(world + ".z"), b.getDouble(world + ".size")});
            }
        }
        if (current != null) {
            applyTwist();
        }
    }

    void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.options().setHeader(List.of("The server's current arc. Managed by the plugin: use /customs arc."));
        data.set("current", current == null ? null : current.name());
        data.set("started", startedAt);
        orders.write(data.createSection("orders"));
        for (Map.Entry<UUID, Integer> e : hearts.entrySet()) {
            data.set("hearts." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, double[]> e : bordersBefore.entrySet()) {
            data.set("borders-before." + e.getKey() + ".x", e.getValue()[0]);
            data.set("borders-before." + e.getKey() + ".z", e.getValue()[1]);
            data.set("borders-before." + e.getKey() + ".size", e.getValue()[2]);
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arcs.yml: " + e.getMessage());
        }
    }

    /** Starts an arc (closing the one before) with a ceremony for everyone online. */
    public void begin(Arc arc) {
        // a war just ended: let its winner have the screen for a few seconds before the next arc
        boolean crowned = current == Arc.II && orders.active();
        closeArc(true);
        current = arc;
        startedAt = System.currentTimeMillis();
        save();
        applyTwist();
        if (crowned) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (current == arc) {
                    ceremony(arc);
                }
            }, 80L);
        } else {
            ceremony(arc);
        }
        plugin.altars().refreshAll();
    }

    /** Ends the arcs: everything unlocks and the twists stop. */
    public void end() {
        closeArc(true);
        current = null;
        save();
        plugin.altars().refreshAll();
        Bukkit.broadcast(Text.plain("The arcs have ended. Every weapon may now be forged.", NamedTextColor.GOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
        }
    }

    /** Takes the current twist out of play for good (results announced, per-arc progress cleared). */
    private void closeArc(boolean finished) {
        if (finished && current == Arc.II) {
            orders.stop(true);
            orders.reset();
        }
        removeTwist();
        if (finished) {
            hearts.clear();
            clearBounty(false);
            restoreBorders();
        }
    }

    /** Server stopping or reloading: pause the twist (it comes back on load). */
    public void shutdown() {
        removeTwist();
    }

    // ---------------------------------------------------------------- ceremony and boss bar

    private void ceremony(Arc arc) {
        Title title = Title.title(
                Text.plain(arc.label().toUpperCase(java.util.Locale.ROOT), arc.color()).decorate(TextDecoration.BOLD),
                Text.caps(arc.title(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(600), Duration.ofSeconds(4), Duration.ofMillis(1200)));
        Component header = Text.plain("━━━━━━━━  ", arc.color())
                .append(Text.plain(arc.label() + " · " + arc.title(), arc.color()).decorate(TextDecoration.BOLD))
                .append(Text.plain("  ━━━━━━━━", arc.color()));
        Component twistLine = Text.plain("Twist: ", NamedTextColor.GRAY)
                .append(Text.plain(arc.twist(), arc.color()).decorate(TextDecoration.BOLD));
        Component forgeable = Text.plain("Now forgeable: ", NamedTextColor.GRAY);
        boolean first = true;
        for (SwordType type : arc.members()) {
            if (!first) {
                forgeable = forgeable.append(Text.plain(", ", NamedTextColor.DARK_GRAY));
            }
            forgeable = forgeable.append(Text.sword(type).hoverEvent(plugin.items().create(type).asHoverEvent()));
            first = false;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.sendMessage(Component.empty());
            p.sendMessage(header);
            p.sendMessage(twistLine);
            p.sendMessage(Text.plain(arc.twistText(), NamedTextColor.WHITE));
            p.sendMessage(forgeable);
            p.sendMessage(Component.empty());
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 0.8f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.9f);
            p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.2f);
            Location at = p.getLocation();
            plugin.fx().beam(at.clone().add(0, 30, 0), at, arc.color().value(), 1.4f, 30);
            plugin.fx().flash(at.clone().add(0, 1.2, 0), arc.color().value(), 3f);
            plugin.fx().castCircle(at, arc.color().value(), 2.4f);
        }
    }

    private Component barName() {
        if (current == null) {
            return Component.empty();
        }
        Component name = Text.plain(current.label() + " · " + current.title(), current.color()).decorate(TextDecoration.BOLD)
                .append(Text.plain("   " + current.twist(), NamedTextColor.WHITE));
        if (current == Arc.II && orders.active()) {
            name = name.append(Text.plain("     ", NamedTextColor.WHITE)).append(orders.scoreLine());
        }
        return name;
    }

    private void showBar() {
        hideBar();
        if (current == null || !plugin.getConfig().getBoolean("arcs.boss-bar", true)) {
            return;
        }
        BossBar.Color color = switch (current) {
            case I -> BossBar.Color.YELLOW;
            case II -> BossBar.Color.GREEN;
            case III -> BossBar.Color.PURPLE;
            case IV -> BossBar.Color.RED;
            case V -> BossBar.Color.WHITE;
        };
        bar = BossBar.bossBar(barName(), (current.ordinal() + 1) / (float) Arc.values().length, color, BossBar.Overlay.NOTCHED_10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
        }
    }

    /** Updates the bar's text (Arc II's scores). */
    void refreshBar() {
        if (bar != null) {
            bar.name(barName());
        }
    }

    private void hideBar() {
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (bar != null) {
            player.showBossBar(bar);
        }
        applyHearts(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (glowing.remove(player.getUniqueId())) {
            player.setGlowing(false);
        }
        if (player.getUniqueId().equals(bounty)) {
            Bukkit.broadcast(Text.plain("☠ The bounty, " + player.getName() + ", fled the server.", Arc.I.color()));
            clearBounty(false);
        }
    }

    // ---------------------------------------------------------------- twists

    private void applyTwist() {
        showBar();
        if (current == null) {
            return;
        }
        if (twist(Arc.I)) {
            long minutes = Math.max(1, plugin.getConfig().getLong("arcs.bounty-interval-minutes", 10));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::pickBounty, 20L * 60, 20L * 60 * minutes));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::trackBounty, 10L, 10L));
        }
        if (twist(Arc.II)) {
            orders.start();
            refreshBar();
        }
        if (twist(Arc.III)) {
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::dominionGlow, 20L, 20L));
        }
        if (twist(Arc.IV)) {
            collapse();
            long minutes = Math.max(1, plugin.getConfig().getLong("arcs.meteor-interval-minutes", 4));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::meteorStorm, 20L * 60 * minutes, 20L * 60 * minutes));
        }
        if (twist(Arc.V)) {
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::holdNight, 1L, 20L));
            for (Player p : Bukkit.getOnlinePlayers()) {
                applyHearts(p);
            }
        }
    }

    private void removeTwist() {
        hideBar();
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        orders.stop(false);
        for (UUID id : glowing) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setGlowing(false);
            }
        }
        glowing.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.removeModifier(heartsKey);
            }
        }
        if (bounty != null) {
            Player p = Bukkit.getPlayer(bounty);
            if (p != null) {
                p.setGlowing(false);
            }
        }
    }

    /** Every player kill during an arc: the killer's rewards, whatever the twist. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        boolean pvp = killer != null && killer != victim;
        if (twist(Arc.I)) {
            if (pvp) {
                bloodrush(killer);
            }
            if (victim.getUniqueId().equals(bounty)) {
                if (pvp) {
                    Bukkit.broadcast(Text.plain("☠ " + killer.getName(), NamedTextColor.WHITE)
                            .append(Text.plain(" claimed the bounty on " + victim.getName() + "!", Arc.I.color())));
                    reward(killer, 4);
                } else {
                    Bukkit.broadcast(Text.plain("☠ The bounty, " + victim.getName() + ", died. No one claims it.", Arc.I.color()));
                }
                clearBounty(false);
            }
        }
        if (twist(Arc.III) && pvp) {
            spoils(event, victim, killer);
        }
        if (twist(Arc.V)) {
            stealHeart(victim, pvp ? killer : null);
        }
    }

    /** A handful of forging materials, for bounties and victories. */
    void reward(Player player, int count) {
        for (int i = 0; i < count; i++) {
            ItemStack material = randomMaterial();
            if (material != null) {
                for (ItemStack overflow : player.getInventory().addItem(material).values()) {
                    player.getWorld().dropItem(player.getLocation(), overflow);
                }
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        Fx.particle(player.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 30, 0.5, 0.3);
    }

    /** A random ingredient from the recipes of the weapons that can be forged right now. */
    public @Nullable ItemStack randomMaterial() {
        List<Material> pool = new ArrayList<>();
        for (SwordType type : SwordType.values()) {
            if (!unlocked(type)) {
                continue;
            }
            for (Material m : plugin.settings().sword(type).recipe().ingredients().values()) {
                if (!TOO_RARE.contains(m) && m.isItem()) {
                    pool.add(m);
                }
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        Material m = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return ItemStack.of(m, m.getMaxStackSize() > 1 ? ThreadLocalRandom.current().nextInt(1, 3) : 1);
    }

    // ---- Arc I · Bounty Hunt ----

    /** Every kill heals the killer fully and makes them stronger for a moment. */
    private void bloodrush(Player killer) {
        AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            killer.setHealth(max.getValue());
        }
        killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0));
        killer.playSound(killer.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.4f);
        Fx.particle(killer.getLocation().add(0, 1, 0), Particle.DUST, 20, 0.4, 0, Fx.dust(0xB0102A, 1.4f));
        killer.sendActionBar(Text.caps("bloodrush: healed and empowered", Arc.I.color()));
    }

    private void pickBounty() {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if ((p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) && !p.getUniqueId().equals(bounty)) {
                candidates.add(p);
            }
        }
        if (bounty != null || candidates.size() < 2) {
            return;
        }
        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        bounty = target.getUniqueId();
        bountyUntil = System.currentTimeMillis() + BOUNTY_MS;
        target.setGlowing(true);
        target.showTitle(Title.title(Text.plain("YOU ARE THE BOUNTY", Arc.I.color()).decorate(TextDecoration.BOLD),
                Text.caps("survive five minutes", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        Bukkit.broadcast(Text.plain("☠ " + target.getName() + " is the bounty! ", Arc.I.color()).decorate(TextDecoration.BOLD)
                .append(Text.plain("Kill them in the next five minutes for a reward.", NamedTextColor.WHITE)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.2f);
        }
    }

    /** Points everyone at the bounty, and pays out if they survive. */
    private void trackBounty() {
        if (bounty == null) {
            return;
        }
        Player target = Bukkit.getPlayer(bounty);
        if (target == null) {
            clearBounty(false);
            return;
        }
        long left = bountyUntil - System.currentTimeMillis();
        if (left <= 0) {
            Bukkit.broadcast(Text.plain("☠ " + target.getName() + " survived the bounty and claims the reward!", Arc.I.color()));
            reward(target, 4);
            clearBounty(false);
            return;
        }
        target.setGlowing(true);
        target.sendActionBar(Text.caps("you are the bounty · " + (left / 60000) + ":" + String.format("%02d", (left / 1000) % 60),
                Arc.I.color()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == target) {
                continue;
            }
            if (p.getWorld() != target.getWorld()) {
                p.sendActionBar(Text.plain("☠ Bounty: " + target.getName() + " (another world)", Arc.I.color()));
                continue;
            }
            Vector to = target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            double distance = to.length();
            double targetYaw = Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
            double rel = ((targetYaw - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
            String[] arrows = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
            String arrow = arrows[(int) Math.round(((rel + 360) % 360) / 45.0) % 8];
            p.sendActionBar(Text.plain("☠ Bounty: ", Arc.I.color()).append(Text.plain(target.getName() + "  ", NamedTextColor.WHITE))
                    .append(Text.plain(arrow + " " + (int) distance + "m", Arc.I.color())));
        }
    }

    private void clearBounty(boolean silent) {
        if (bounty != null) {
            Player p = Bukkit.getPlayer(bounty);
            if (p != null) {
                p.setGlowing(false);
            }
        }
        bounty = null;
    }

    // ---- Arc III · Spoils of War ----

    private void dominionGlow() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean armed = false;
            for (ItemStack stack : p.getInventory().getContents()) {
                if (plugin.items().isSword(stack)) {
                    armed = true;
                    break;
                }
            }
            if (armed && p.getGameMode() != GameMode.SPECTATOR) {
                if (glowing.add(p.getUniqueId()) || !p.isGlowing()) {
                    p.setGlowing(true);
                }
            } else if (glowing.remove(p.getUniqueId())) {
                p.setGlowing(false);
            }
        }
    }

    private void spoils(PlayerDeathEvent event, Player victim, Player killer) {
        List<ItemStack> taken = new ArrayList<>();
        event.getDrops().removeIf(stack -> {
            if (plugin.items().isSword(stack)) {
                taken.add(stack);
                return true;
            }
            return false;
        });
        if (event.getKeepInventory()) {
            ItemStack[] contents = victim.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (plugin.items().isSword(contents[i])) {
                    taken.add(contents[i]);
                    victim.getInventory().setItem(i, null);
                }
            }
            event.getItemsToKeep().removeIf(stack -> plugin.items().isSword(stack));
        }
        if (taken.isEmpty()) {
            return;
        }
        for (ItemStack overflow : killer.getInventory().addItem(taken.toArray(new ItemStack[0])).values()) {
            killer.getWorld().dropItem(killer.getLocation(), overflow);
        }
        Component line = Text.plain(killer.getName(), NamedTextColor.WHITE).append(Text.plain(" claimed ", NamedTextColor.GRAY));
        for (int i = 0; i < taken.size(); i++) {
            SwordType type = plugin.items().typeOf(taken.get(i));
            if (type != null) {
                line = line.append(i > 0 ? Text.plain(", ", NamedTextColor.DARK_GRAY) : Component.empty()).append(Text.sword(type));
            }
        }
        Bukkit.broadcast(line.append(Text.plain(" from " + victim.getName() + ".", NamedTextColor.GRAY)));
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        Fx.particle(killer.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 30, 0.5, 0.3);
    }

    // ---- Arc IV · Collapse ----

    /** The border closes in on spawn over the arc (picking up where it left off after a restart). */
    private void collapse() {
        double start = Math.max(16, plugin.getConfig().getDouble("arcs.collapse.start-size", 3000));
        double end = Math.max(16, plugin.getConfig().getDouble("arcs.collapse.end-size", 300));
        double hours = Math.max(0.05, plugin.getConfig().getDouble("arcs.collapse.hours", 4));
        long total = (long) (hours * 3600);
        long elapsed = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
        double t = Math.min(1, elapsed / (double) total);
        double now = start + (end - start) * t;
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            WorldBorder border = world.getWorldBorder();
            if (!bordersBefore.containsKey(world.getName())) {
                bordersBefore.put(world.getName(), new double[]{border.getCenter().getX(), border.getCenter().getZ(), border.getSize()});
            }
            Location spawn = world.getSpawnLocation();
            border.setCenter(spawn.getX(), spawn.getZ());
            border.setSize(now);
            if (t < 1) {
                border.changeSize(end, Math.max(1, total - elapsed) * 20L);
            }
            border.setDamageAmount(1.0);
            border.setWarningDistance(25);
        }
        save();
    }

    private void restoreBorders() {
        for (Map.Entry<String, double[]> e : bordersBefore.entrySet()) {
            World world = Bukkit.getWorld(e.getKey());
            if (world != null) {
                WorldBorder border = world.getWorldBorder();
                border.setCenter(e.getValue()[0], e.getValue()[1]);
                border.setSize(e.getValue()[2]);
            }
        }
        bordersBefore.clear();
    }


    private void meteorStorm() {
        boolean any = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() != World.Environment.NORMAL || p.getGameMode() == GameMode.SPECTATOR
                    || p.getGameMode() == GameMode.CREATIVE && !plugin.getConfig().getBoolean("arcs.meteors-hit-creative", false)) {
                continue;
            }
            any = true;
            p.sendActionBar(Text.caps("☄ meteor storm incoming", Arc.IV.color()));
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 0.6f);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble(Math.PI * 2);
                double d = r.nextDouble(5, 16);
                Location target = Combat.ground(p.getLocation().add(Math.cos(a) * d, 4, Math.sin(a) * d));
                long delay = 30L + i * 25L + r.nextInt(15);
                Bukkit.getScheduler().runTaskLater(plugin, () -> meteor(target), delay);
            }
        }
        if (any) {
            plugin.getLogger().fine("Meteor storm.");
        }
    }

    /** One meteor: a warning ring on the ground, the rock falling in, and the impact. */
    public void meteor(Location ground) {
        int warning = 30;
        plugin.fx().castCircle(ground, Arc.IV.color().value(), 3.2f);
        Fx.sound(ground, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.5f);
        Location sky = ground.clone().add(-6, 26, -4);
        sky.setYaw(0);
        sky.setPitch(0);
        Displays displays = plugin.displays();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BlockDisplay rock = displays.block(sky, Material.MAGMA_BLOCK.createBlockData(), new Vector3f(1.6f), new Quaternionf(), 20);
            rock.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            rock.setTeleportDuration(1);
            Vector path = ground.toVector().add(new Vector(0, 0.8, 0)).subtract(sky.toVector());
            for (int t = 1; t <= 10; t++) {
                int tick = t;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location at = sky.clone().add(path.clone().multiply(tick / 10.0));
                    if (rock.isValid()) {
                        rock.teleport(at);
                        Displays.animateBlock(rock, new Vector3f(1.6f), new Quaternionf().rotateXYZ(tick * 0.4f, tick * 0.3f, 0), new Vector3f(), 1);
                    }
                    Fx.particle(at, Particle.FLAME, 10, 0.4, 0.05);
                    Fx.particle(at, Particle.LARGE_SMOKE, 4, 0.3, 0.02);
                    if (tick == 10) {
                        displays.remove(rock);
                        impact(ground);
                    }
                }, t);
            }
            Fx.sound(ground, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.4f);
        }, warning - 10);
    }

    private void impact(Location ground) {
        Location at = ground.clone().add(0, 0.6, 0);
        plugin.fx().burst(at, 0xFF7A1A, 3.4f);
        plugin.fx().flash(at, 0xFFC56B, 3.6f);
        plugin.fx().crack(ground, 0xE5482C, 4f, 60);
        plugin.fx().shockwave(ground, 0xFF7A1A, 5f, 8);
        plugin.displays().eruptGround(ground, 0.8f, 1.0f);
        Fx.particle(at, Particle.EXPLOSION_EMITTER, 1, 0, 0);
        Fx.particle(at, Particle.LAVA, 20, 1, 0.2);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        Fx.sound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.5f);
        for (LivingEntity e : at.getWorld().getNearbyLivingEntities(at, 3.6)) {
            if (e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            e.damage(7);
            e.setFireTicks(Math.max(e.getFireTicks(), 60));
            Vector out = e.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (out.lengthSquared() > 1.0E-4) {
                out.normalize();
            }
            Combat.setVelocity(e, out.multiply(0.9).setY(0.6));
        }
        // the crater: a still-hot fragment with something useful in it
        ItemStack material = randomMaterial();
        if (material != null) {
            at.getWorld().dropItem(at, material).setGlowing(true);
        }
    }


    // ---- Arc V · Eternal Hearts ----

    /** The sun never rises: every overworld is held at midnight (sleeping doesn't help either). */
    private void holdNight() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL && Math.abs(world.getTime() - 18000) > 40) {
                world.setTime(18000);
            }
        }
    }

    private void applyHearts(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) {
            return;
        }
        max.removeModifier(heartsKey);
        int h = hearts.getOrDefault(player.getUniqueId(), 0);
        if (twist(Arc.V) && h != 0) {
            max.addTransientModifier(new AttributeModifier(heartsKey, h * 2.0, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (player.getHealth() > max.getValue()) {
            player.setHealth(max.getValue());
        }
    }

    /** Every death costs a heart; a player kill hands it to the killer. */
    private void stealHeart(Player victim, @Nullable Player killer) {
        int lost = Math.max(-5, hearts.getOrDefault(victim.getUniqueId(), 0) - 1);
        hearts.put(victim.getUniqueId(), lost);
        if (killer != null) {
            int gained = Math.min(10, hearts.getOrDefault(killer.getUniqueId(), 0) + 1);
            hearts.put(killer.getUniqueId(), gained);
            applyHearts(killer);
            killer.setHealth(Math.min(killer.getHealth() + 2, killer.getAttribute(Attribute.MAX_HEALTH).getValue()));
            Bukkit.broadcast(Text.plain("♥ " + killer.getName(), Arc.V.color())
                    .append(Text.plain(" took a heart from " + victim.getName() + " (" + (gained >= 0 ? "+" : "") + gained + ")", NamedTextColor.GRAY)));
            Fx.particle(killer.getLocation().add(0, 1.2, 0), Particle.HEART, 6, 0.4, 0);
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        }
        save();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline()) {
                applyHearts(victim);
            }
        });
    }

    // ---------------------------------------------------------------- status

    /** A few lines describing the current arc, for /customs arc. */
    public List<Component> status() {
        List<Component> out = new ArrayList<>();
        if (current == null) {
            out.add(Text.plain("No arc is running: every weapon can be forged and there are no twists.", NamedTextColor.GRAY));
            out.add(Text.plain("Start Arc I with /customs arc start.", NamedTextColor.YELLOW));
            return out;
        }
        long hours = (System.currentTimeMillis() - startedAt) / 3_600_000L;
        out.add(Text.plain(current.label() + " · " + current.title(), current.color()).decorate(TextDecoration.BOLD)
                .append(Text.plain("  (running for " + (hours < 1 ? "under an hour" : hours + "h") + ")", NamedTextColor.DARK_GRAY)));
        out.add(Text.plain("Twist: ", NamedTextColor.GRAY).append(Text.plain(current.twist(), current.color())));
        out.add(Text.plain(current.twistText(), NamedTextColor.WHITE));
        if (current == Arc.II && orders.active()) {
            out.add(Text.plain("Kills: ", NamedTextColor.GRAY).append(orders.scoreLine()));
        }
        Arc next = current.next();
        out.add(next == null ? Text.plain("This is the last arc.", NamedTextColor.GRAY)
                : Text.plain("Next: " + next.label() + " · " + next.title() + " (/customs arc next)", NamedTextColor.YELLOW));
        return out;
    }
}
