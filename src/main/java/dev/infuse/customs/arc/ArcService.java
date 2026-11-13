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
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
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
 * which weapons can be forged (its own and every earlier arc's) and which twist is in play.
 * While no arc is running, nothing is locked and there are no twists. Saved in arcs.yml.
 */
public final class ArcService implements Listener {

    /** Materials too rare to hand out as twist rewards. */
    private static final Set<Material> TOO_RARE = EnumSet.of(Material.NETHER_STAR, Material.BEACON, Material.TOTEM_OF_UNDYING,
            Material.CONDUIT, Material.DRAGON_HEAD, Material.HEART_OF_THE_SEA, Material.END_CRYSTAL, Material.WITHER_SKELETON_SKULL,
            Material.DRAGON_BREATH, Material.RECOVERY_COMPASS, Material.SCULK_CATALYST, Material.SCULK_SHRIEKER, Material.NETHERITE_INGOT,
            Material.NETHERITE_SWORD, Material.NETHERITE_SPEAR, Material.NETHERITE_AXE, Material.MACE, Material.TRIDENT, Material.ELYTRA,
            Material.ENCHANTED_GOLDEN_APPLE, Material.ECHO_SHARD, Material.NETHERITE_SCRAP, Material.BEEHIVE);

    private final CustomsPlugin plugin;
    private final File file;
    private final NamespacedKey awakenedKey;
    private final NamespacedKey gravityKey;
    private final NamespacedKey fallKey;

    private @Nullable Arc current;
    private long startedAt;
    private @Nullable BossBar bar;
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** Dominion: players we made glow. */
    private final Set<UUID> glowing = new HashSet<>();
    /** Eternity: when each player last cheated death. */
    private final Map<UUID, Long> graceUsed = new HashMap<>();

    public ArcService(CustomsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arcs.yml");
        this.awakenedKey = new NamespacedKey(plugin, "awakened");
        this.gravityKey = new NamespacedKey(plugin, "arc_featherfall");
        this.fallKey = new NamespacedKey(plugin, "arc_featherfall_fall");
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

    /** Ability cooldowns are shorter during Ascension. */
    public double cooldownMultiplier() {
        return twist(Arc.II) ? 0.75 : 1.0;
    }

    /** Loads the saved arc and puts its twist back in play (after a restart or reload). */
    public void load() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        current = Arc.parse(data.getString("current"));
        startedAt = data.getLong("started", 0);
        if (current != null) {
            applyTwist();
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.options().setHeader(List.of("The server's current arc. Managed by the plugin: use /customs arc."));
        data.set("current", current == null ? null : current.name());
        data.set("started", startedAt);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arcs.yml: " + e.getMessage());
        }
    }

    /** Starts an arc (ending the twist of the one before) with a ceremony for everyone online. */
    public void begin(Arc arc) {
        removeTwist();
        current = arc;
        startedAt = System.currentTimeMillis();
        save();
        applyTwist();
        ceremony(arc);
        plugin.altars().refreshAll();
    }

    /** Ends the arcs: everything unlocks and the twists stop. */
    public void end() {
        removeTwist();
        current = null;
        save();
        plugin.altars().refreshAll();
        Bukkit.broadcast(Text.plain("The arcs have ended. Every weapon may now be forged.", NamedTextColor.GOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
        }
    }

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
            // a pillar of the arc's light comes down on everyone
            Location at = p.getLocation();
            plugin.fx().beam(at.clone().add(0, 30, 0), at, arc.color().value(), 1.4f, 30);
            plugin.fx().flash(at.clone().add(0, 1.2, 0), arc.color().value(), 3f);
            plugin.fx().castCircle(at, arc.color().value(), 2.4f);
        }
    }

    private void showBar() {
        hideBar();
        if (current == null || !plugin.getConfig().getBoolean("arcs.boss-bar", true)) {
            return;
        }
        Component name = Text.plain(current.label() + " · " + current.title(), current.color()).decorate(TextDecoration.BOLD)
                .append(Text.plain("   " + current.twist(), NamedTextColor.WHITE));
        BossBar.Color color = switch (current) {
            case I -> BossBar.Color.YELLOW;
            case II -> BossBar.Color.GREEN;
            case III -> BossBar.Color.PURPLE;
            case IV -> BossBar.Color.RED;
            case V -> BossBar.Color.WHITE;
        };
        bar = BossBar.bossBar(name, (current.ordinal() + 1) / (float) Arc.values().length, color, BossBar.Overlay.NOTCHED_10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
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
        if (twist(Arc.II)) {
            featherfall(player, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        featherfall(event.getPlayer(), false);
        if (glowing.remove(event.getPlayer().getUniqueId())) {
            event.getPlayer().setGlowing(false);
        }
    }

    // ---------------------------------------------------------------- twists

    private void applyTwist() {
        showBar();
        if (current == null) {
            return;
        }
        if (twist(Arc.II)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                featherfall(p, true);
            }
        }
        if (twist(Arc.III)) {
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::dominionGlow, 20L, 20L));
        }
        if (twist(Arc.IV)) {
            long minutes = Math.max(1, plugin.getConfig().getLong("arcs.meteor-interval-minutes", 4));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::meteorStorm, 20L * 60 * minutes, 20L * 60 * minutes));
        }
        if (twist(Arc.V)) {
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::holdNight, 1L, 20L));
        }
    }

    private void removeTwist() {
        hideBar();
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            featherfall(p, false);
        }
        for (UUID id : glowing) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setGlowing(false);
            }
        }
        glowing.clear();
    }

    // ---- Arc I · Grace of Dawn ----

    /** No player can hurt another, by any means. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!twist(Arc.I) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity source = event.getDamageSource().getCausingEntity();
        if (source == null) {
            source = event.getDamager() instanceof Projectile p && p.getShooter() instanceof Entity shooter ? shooter : event.getDamager();
        }
        if (source instanceof Player attacker && attacker != victim) {
            event.setCancelled(true);
        }
    }

    /** Some monsters rise Awakened. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!twist(Arc.I) || !(event.getEntity() instanceof Monster monster)
                || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                || ThreadLocalRandom.current().nextDouble() > 0.08) {
            return;
        }
        monster.getPersistentDataContainer().set(awakenedKey, PersistentDataType.BYTE, (byte) 1);
        AttributeInstance health = monster.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * 2);
            monster.setHealth(health.getValue());
        }
        AttributeInstance damage = monster.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * 1.3);
        }
        monster.setGlowing(true);
        monster.customName(Text.plain("Awakened " + pretty(monster.getType().name()), Arc.I.color()));
        monster.setCustomNameVisible(false);
    }

    /** Awakened monsters (and meteors) leave forging materials behind. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAwakenedDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(awakenedKey)) {
            return;
        }
        int count = ThreadLocalRandom.current().nextInt(1, 3);
        for (int i = 0; i < count; i++) {
            ItemStack material = randomMaterial();
            if (material != null) {
                event.getDrops().add(material);
            }
        }
        Location at = event.getEntity().getLocation().add(0, 1, 0);
        Fx.particle(at, Particle.END_ROD, 20, 0.4, 0.1);
        Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }

    /** A random ingredient from the recipes of the weapons that can be forged right now. */
    private @Nullable ItemStack randomMaterial() {
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

    private static String pretty(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String part : enumName.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    // ---- Arc II · Featherfall ----

    private void featherfall(Player player, boolean on) {
        AttributeInstance gravity = player.getAttribute(Attribute.GRAVITY);
        AttributeInstance fall = player.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER);
        if (gravity != null) {
            gravity.removeModifier(gravityKey);
            if (on) {
                gravity.addTransientModifier(new AttributeModifier(gravityKey, -0.35, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
        if (fall != null) {
            fall.removeModifier(fallKey);
            if (on) {
                fall.addTransientModifier(new AttributeModifier(fallKey, -0.5, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
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

    /** Whoever kills a player takes every legendary weapon they carried. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (!twist(Arc.III) || killer == null || killer == victim) {
            return;
        }
        List<ItemStack> spoils = new ArrayList<>();
        event.getDrops().removeIf(stack -> {
            if (plugin.items().isSword(stack)) {
                spoils.add(stack);
                return true;
            }
            return false;
        });
        if (event.getKeepInventory()) {
            ItemStack[] contents = victim.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (plugin.items().isSword(contents[i])) {
                    spoils.add(contents[i]);
                    victim.getInventory().setItem(i, null);
                }
            }
            event.getItemsToKeep().removeIf(stack -> plugin.items().isSword(stack));
        }
        if (spoils.isEmpty()) {
            return;
        }
        for (ItemStack overflow : killer.getInventory().addItem(spoils.toArray(new ItemStack[0])).values()) {
            killer.getWorld().dropItem(killer.getLocation(), overflow);
        }
        Component taken = Text.plain(killer.getName(), NamedTextColor.WHITE)
                .append(Text.plain(" claimed ", NamedTextColor.GRAY));
        for (int i = 0; i < spoils.size(); i++) {
            SwordType type = plugin.items().typeOf(spoils.get(i));
            if (type != null) {
                taken = taken.append(i > 0 ? Text.plain(", ", NamedTextColor.DARK_GRAY) : Component.empty()).append(Text.sword(type));
            }
        }
        Bukkit.broadcast(taken.append(Text.plain(" from " + victim.getName() + ".", NamedTextColor.GRAY)));
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        Fx.particle(killer.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 30, 0.5, 0.3);
    }

    // ---- Arc IV · Meteor Storms ----

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

    /** Monsters hit harder in the cataclysm. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMonsterHit(EntityDamageByEntityEvent event) {
        if (!twist(Arc.IV) || !(event.getEntity() instanceof Player)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Monster) {
            event.setDamage(event.getDamage() * 1.5);
        }
    }

    // ---- Arc V · Endless Night ----

    /** The sun never rises: every overworld is held at midnight (sleeping doesn't help either). */
    private void holdNight() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL && Math.abs(world.getTime() - 18000) > 40) {
                world.setTime(18000);
            }
        }
    }

    /** Eternity's grace: once every ten minutes a killing blow revives you instead. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!twist(Arc.V) || !(event.getEntity() instanceof Player player)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID
                || player.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = 1000L * 60 * Math.max(1, plugin.getConfig().getLong("arcs.eternal-grace-minutes", 10));
        Long last = graceUsed.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            return;
        }
        graceUsed.put(player.getUniqueId(), now);
        event.setCancelled(true);
        player.setHealth(Math.min(8, player.getHealth() + 8));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
        player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
        Location at = player.getLocation().add(0, 1, 0);
        plugin.fx().flash(at, Arc.V.color().value(), 3f);
        plugin.fx().beam(player.getLocation().add(0, 20, 0), player.getLocation(), Arc.V.color().value(), 1.0f, 20);
        Fx.particle(at, Particle.END_ROD, 40, 0.5, 0.15);
        player.sendActionBar(Text.caps("eternity will not let you go yet", Arc.V.color()));
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
        Arc next = current.next();
        out.add(next == null ? Text.plain("This is the last arc.", NamedTextColor.GRAY)
                : Text.plain("Next: " + next.label() + " · " + next.title() + " (/customs arc next)", NamedTextColor.YELLOW));
        return out;
    }
}
