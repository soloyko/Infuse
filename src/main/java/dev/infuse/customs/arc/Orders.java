package dev.infuse.customs.arc;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Arc II's War of the Three Orders. Every player belongs to one order: orders can't hurt their own,
 * each has a blessing and a relic with its own power, and every kill of another order's member
 * scores a point. When the arc ends the order with the most points wins.
 */
public final class Orders implements Listener {

    public enum Order {
        SOLAR("solar", "Solar Order", "☀", 0xF5B841, NamedTextColor.GOLD, Material.SUNFLOWER, Material.YELLOW_BANNER, "Sunstone",
                "Radiance", "Fire Resistance, and your hits set enemies of other orders alight.",
                "Solar Flare", "Blind and burn every enemy around you, while your order nearby regenerates.", 60),
        LUNAR("lunar", "Lunar Covenant", "☾", 0x9FB8FF, NamedTextColor.AQUA, Material.ENDER_EYE, Material.LIGHT_BLUE_BANNER, "Moonstone",
                "Moonlit", "Night Vision; at night, Speed I and 15% more damage.",
                "Eclipse Veil", "You and your order around you vanish from enemy eyes and run faster for 6 seconds.", 75),
        ASTRAL("astral", "Astral Syndicate", "✦", 0xB46BFF, NamedTextColor.LIGHT_PURPLE, Material.AMETHYST_SHARD, Material.PURPLE_BANNER, "Starstone",
                "Starborn", "No fall damage, and every fifth hit on an enemy of another order deals 4 extra damage.",
                "Starfall Gate", "Step through the stars to the nearest member of your order (or blink ahead if none).", 60);

        public final String id;
        public final String title;
        public final String symbol;
        public final TextColor color;
        public final NamedTextColor teamColor;
        public final Material relicItem;
        public final Material banner;
        public final String relicName;
        public final String blessing;
        public final String blessingText;
        public final String power;
        public final String powerText;
        public final int cooldownSeconds;

        Order(String id, String title, String symbol, int color, NamedTextColor teamColor, Material relicItem, Material banner,
              String relicName, String blessing, String blessingText, String power, String powerText, int cooldownSeconds) {
            this.id = id;
            this.title = title;
            this.symbol = symbol;
            this.color = TextColor.color(color);
            this.teamColor = teamColor;
            this.relicItem = relicItem;
            this.banner = banner;
            this.relicName = relicName;
            this.blessing = blessing;
            this.blessingText = blessingText;
            this.power = power;
            this.powerText = powerText;
            this.cooldownSeconds = cooldownSeconds;
        }

        public Component label() {
            return Text.plain(symbol + " " + title, color);
        }

        public static @Nullable Order parse(@Nullable String input) {
            if (input == null) {
                return null;
            }
            String in = input.toLowerCase(Locale.ROOT);
            for (Order o : values()) {
                if (o.id.equals(in) || o.title.equalsIgnoreCase(input) || o.title.toLowerCase(Locale.ROOT).contains(in)) {
                    return o;
                }
            }
            return null;
        }
    }

    private final CustomsPlugin plugin;
    private final ArcService arcs;
    private final NamespacedKey relicKey;
    private final Map<UUID, Order> members = new HashMap<>();
    private final EnumMap<Order, Integer> scores = new EnumMap<>(Order.class);
    private final Map<UUID, Long> powerUsed = new HashMap<>();
    /** Astral hits on enemies, for Starborn's fifth-hit bonus. */
    private final Map<UUID, Integer> astralHits = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private boolean active;

    Orders(CustomsPlugin plugin, ArcService arcs) {
        this.plugin = plugin;
        this.arcs = arcs;
        this.relicKey = new NamespacedKey(plugin, "relic");
    }

    public boolean active() {
        return active;
    }

    public @Nullable Order of(Player player) {
        return active ? members.get(player.getUniqueId()) : null;
    }

    public int score(Order order) {
        return scores.getOrDefault(order, 0);
    }

    public int count(Order order) {
        int n = 0;
        for (Order o : members.values()) {
            if (o == order) {
                n++;
            }
        }
        return n;
    }

    /** Two players on the same side: they can't hurt each other. */
    public boolean allies(Player a, Player b) {
        if (!active || a == b) {
            return false;
        }
        Order oa = members.get(a.getUniqueId());
        return oa != null && oa == members.get(b.getUniqueId());
    }

    // ---------------------------------------------------------------- saving

    void read(@Nullable ConfigurationSection section) {
        members.clear();
        scores.clear();
        if (section == null) {
            return;
        }
        ConfigurationSection m = section.getConfigurationSection("members");
        if (m != null) {
            for (String key : m.getKeys(false)) {
                Order o = Order.parse(m.getString(key));
                try {
                    if (o != null) {
                        members.put(UUID.fromString(key), o);
                    }
                } catch (IllegalArgumentException ignored) {
                    // a bad entry in arcs.yml
                }
            }
        }
        for (Order o : Order.values()) {
            scores.put(o, section.getInt("scores." + o.id, 0));
        }
    }

    void write(ConfigurationSection section) {
        for (Map.Entry<UUID, Order> e : members.entrySet()) {
            section.set("members." + e.getKey(), e.getValue().id);
        }
        for (Order o : Order.values()) {
            section.set("scores." + o.id, score(o));
        }
    }

    // ---------------------------------------------------------------- the war starting and ending

    void start() {
        if (active) {
            return;
        }
        active = true;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Order o : Order.values()) {
            Team team = board.getTeam(teamName(o));
            if (team == null) {
                team = board.registerNewTeam(teamName(o));
            }
            team.color(o.teamColor);
            team.prefix(Text.plain(o.symbol + " ", o.color));
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            welcome(p);
        }
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::bless, 20L, 20L));
    }

    /** Ends the war: optionally names the winner, then takes the teams and relics away. */
    void stop(boolean announce) {
        if (!active) {
            return;
        }
        if (announce) {
            crownWinner();
        }
        active = false;
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Order o : Order.values()) {
            Team team = board.getTeam(teamName(o));
            if (team != null) {
                team.unregister();
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            stripRelics(p);
        }
    }

    /** Clears memberships and scores for the next war. */
    void reset() {
        members.clear();
        scores.clear();
        powerUsed.clear();
        astralHits.clear();
    }

    private static String teamName(Order o) {
        return "customs_" + o.id;
    }

    private void crownWinner() {
        Order best = null;
        int top = -1;
        boolean tie = false;
        for (Order o : Order.values()) {
            int s = score(o);
            if (s > top) {
                best = o;
                top = s;
                tie = false;
            } else if (s == top) {
                tie = true;
            }
        }
        Component line;
        Title title;
        if (best == null || tie || top <= 0) {
            line = Text.plain("The War of the Three Orders ends in a draw.", NamedTextColor.GOLD);
            title = Title.title(Text.plain("A DRAW", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), scoreLine(),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(800)));
        } else {
            line = best.label().decorate(TextDecoration.BOLD).append(Text.plain(" wins the War of the Three Orders with "
                    + top + (top == 1 ? " kill!" : " kills!"), NamedTextColor.GOLD));
            title = Title.title(Text.plain(best.symbol + " " + best.title.toUpperCase(Locale.ROOT), best.color).decorate(TextDecoration.BOLD),
                    Text.caps("wins the war", NamedTextColor.WHITE), Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(800)));
            // the victors share the spoils
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (members.get(p.getUniqueId()) == best) {
                    for (int i = 0; i < 3; i++) {
                        ItemStack reward = arcs.randomMaterial();
                        if (reward != null) {
                            for (ItemStack overflow : p.getInventory().addItem(reward).values()) {
                                p.getWorld().dropItem(p.getLocation(), overflow);
                            }
                        }
                    }
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
            }
        }
        Bukkit.broadcast(line);
        Bukkit.broadcast(scoreLine());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
        }
    }

    /** "☀ 12   ☾ 9   ✦ 15". */
    public Component scoreLine() {
        Component out = Component.empty();
        for (Order o : Order.values()) {
            out = out.append(Text.plain(o.symbol + " " + score(o) + "   ", o.color));
        }
        return out;
    }

    // ---------------------------------------------------------------- joining

    /** A player arriving during the war: their team and relic, or the choice of an order. */
    void welcome(Player player) {
        Order order = members.get(player.getUniqueId());
        if (order != null) {
            addToTeam(player, order);
            giveRelic(player, order);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active && player.isOnline() && !members.containsKey(player.getUniqueId())) {
                new dev.infuse.customs.gui.OrderMenu(plugin).open(player);
                Text.send(player, "<gold>Choose your order with <white>/customs order</white>. If you don't, one will choose you in a minute.");
            }
        }, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active && player.isOnline() && !members.containsKey(player.getUniqueId())) {
                join(player, smallest(), true);
            }
        }, 20L * 60);
    }

    public Order smallest() {
        Order best = Order.SOLAR;
        for (Order o : Order.values()) {
            if (count(o) < count(best)) {
                best = o;
            }
        }
        return best;
    }

    /** Whether a player may join an order: orders can't get two members ahead of the smallest. */
    public @Nullable String refuse(Player player, Order order) {
        if (!active) {
            return "The orders only exist during Arc II.";
        }
        if (members.containsKey(player.getUniqueId())) {
            return "You have already sworn yourself to the " + members.get(player.getUniqueId()).title + ".";
        }
        if (count(order) - count(smallest()) >= 2) {
            return "The " + order.title + " has too many members right now. Pick another order.";
        }
        return null;
    }

    /** Swears a player to an order (forced: an admin or the automatic pick). */
    public void join(Player player, Order order, boolean forced) {
        Order old = members.put(player.getUniqueId(), order);
        if (old != null && old != order) {
            stripRelics(player);
        }
        arcs.save();
        addToTeam(player, order);
        giveRelic(player, order);
        player.showTitle(Title.title(Text.plain(order.symbol + " " + order.title.toUpperCase(Locale.ROOT), order.color).decorate(TextDecoration.BOLD),
                Text.caps(forced ? "you have been sworn in" : "welcome, sworn one", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.8f);
        Location at = player.getLocation().add(0, 1, 0);
        plugin.fx().flash(at, order.color.value(), 2.4f);
        plugin.fx().castCircle(player.getLocation(), order.color.value(), 1.6f);
        Bukkit.broadcast(Text.plain(player.getName(), NamedTextColor.WHITE).append(Text.plain(" joins the ", NamedTextColor.GRAY))
                .append(order.label()).append(Text.plain(".", NamedTextColor.GRAY)));
        arcs.refreshBar();
    }

    private void addToTeam(Player player, Order order) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Order o : Order.values()) {
            Team team = board.getTeam(teamName(o));
            if (team != null) {
                if (o == order) {
                    team.addEntry(player.getName());
                } else {
                    team.removeEntry(player.getName());
                }
            }
        }
    }

    // ---------------------------------------------------------------- blessings

    private void bless() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Order o = members.get(p.getUniqueId());
            if (o == null) {
                continue;
            }
            switch (o) {
                case SOLAR -> quiet(p, PotionEffectType.FIRE_RESISTANCE, 50, 0);
                case LUNAR -> {
                    quiet(p, PotionEffectType.NIGHT_VISION, 260, 0);
                    if (night(p)) {
                        quiet(p, PotionEffectType.SPEED, 50, 0);
                    }
                }
                case ASTRAL -> {
                }
            }
        }
    }

    private static void quiet(Player p, PotionEffectType type, int ticks, int amplifier) {
        PotionEffect current = p.getPotionEffect(type);
        if (current == null || current.getAmplifier() <= amplifier && current.getDuration() < ticks - 20) {
            p.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, false, true));
        }
    }

    private static boolean night(Player p) {
        long time = p.getWorld().getTime();
        return time > 13000 && time < 23000;
    }

    /** Orders don't hurt their own; blessings sharpen hits on the other orders. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!active || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity source = event.getDamageSource().getCausingEntity();
        if (source == null) {
            source = event.getDamager() instanceof Projectile p && p.getShooter() instanceof Entity shooter ? shooter : event.getDamager();
        }
        if (!(source instanceof Player attacker) || attacker == victim) {
            return;
        }
        Order a = members.get(attacker.getUniqueId());
        Order v = members.get(victim.getUniqueId());
        if (a != null && a == v) {
            event.setCancelled(true);
            return;
        }
        if (a == null) {
            return;
        }
        switch (a) {
            case SOLAR -> victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
            case LUNAR -> {
                if (night(attacker)) {
                    event.setDamage(event.getDamage() * 1.15);
                }
            }
            case ASTRAL -> {
                int hits = astralHits.merge(attacker.getUniqueId(), 1, Integer::sum);
                if (hits % 5 == 0) {
                    event.setDamage(event.getDamage() + 4);
                    Fx.particle(Combat.center(victim), Particle.END_ROD, 10, 0.3, 0.05);
                    Fx.sound(victim.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (active && event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player p
                && members.get(p.getUniqueId()) == Order.ASTRAL) {
            event.setCancelled(true);
        }
    }

    /** Kills of another order's members score for the killer's order. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        event.getDrops().removeIf(this::isRelic);
        if (!active || killer == null || killer == victim) {
            return;
        }
        Order a = members.get(killer.getUniqueId());
        Order v = members.get(victim.getUniqueId());
        if (a == null || a == v) {
            return;
        }
        scores.merge(a, 1, Integer::sum);
        arcs.save();
        arcs.refreshBar();
        Bukkit.broadcast(Text.plain(a.symbol + " " + killer.getName(), a.color)
                .append(Text.plain(" slew ", NamedTextColor.GRAY))
                .append(Text.plain((v == null ? "" : v.symbol + " ") + victim.getName(), v == null ? NamedTextColor.WHITE : v.color))
                .append(Text.plain("   ", NamedTextColor.GRAY)).append(scoreLine()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Order o = of(p);
        if (o != null) {
            Bukkit.getScheduler().runTask(plugin, () -> giveRelic(p, o));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active) {
            welcome(event.getPlayer());
        } else {
            stripRelics(event.getPlayer());
        }
    }

    // ---------------------------------------------------------------- relics

    public boolean isRelic(@Nullable ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(relicKey);
    }

    private ItemStack relic(Order order) {
        ItemStack item = ItemStack.of(order.relicItem);
        item.editMeta(meta -> {
            meta.itemName(Text.plain(order.symbol + " " + order.relicName, order.color).decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Text.caps(order.title + " relic", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Text.plain("Right-click: " + order.power, order.color).decoration(TextDecoration.ITALIC, false),
                    Text.plain(order.powerText, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Text.plain("Recharges in " + order.cooldownSeconds + "s", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Text.plain("Blessing: " + order.blessing, order.color).decoration(TextDecoration.ITALIC, false),
                    Text.plain(order.blessingText, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, order.id);
        });
        return item;
    }

    private void giveRelic(Player player, Order order) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isRelic(stack)) {
                return;
            }
        }
        for (ItemStack overflow : player.getInventory().addItem(relic(order)).values()) {
            player.getWorld().dropItem(player.getLocation(), overflow);
        }
    }

    private void stripRelics(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isRelic(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isRelic(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Relics are never crafting ingredients (a sunflower relic must not become dye). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (isRelic(stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isRelic(event.getItem())
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Order order = of(player);
        if (order == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = powerUsed.get(player.getUniqueId());
        if (last != null && now - last < order.cooldownSeconds * 1000L) {
            long left = (order.cooldownSeconds * 1000L - (now - last) + 999) / 1000;
            player.sendActionBar(Text.plain(order.power + " recharges in " + left + "s", NamedTextColor.RED));
            return;
        }
        powerUsed.put(player.getUniqueId(), now);
        switch (order) {
            case SOLAR -> solarFlare(player);
            case LUNAR -> eclipseVeil(player);
            case ASTRAL -> starfallGate(player);
        }
    }

    private boolean enemy(Player caster, LivingEntity e) {
        return plugin.combat().canHit(caster, e);
    }

    private void solarFlare(Player player) {
        Location at = player.getLocation().add(0, 1, 0);
        int gold = Order.SOLAR.color.value();
        plugin.fx().flash(at, 0xFFF3C4, 5f);
        plugin.fx().burst(at, gold, 4f);
        plugin.fx().wave(at, new Vector(0, 1, 0), gold, 8f, 8);
        Fx.sound(at, Sound.ITEM_TOTEM_USE, 0.6f, 1.6f);
        Fx.sound(at, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
        for (LivingEntity e : player.getLocation().getNearbyLivingEntities(8)) {
            if (e instanceof Player p && allies(player, p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                Fx.particle(p.getLocation().add(0, 2, 0), Particle.HEART, 3, 0.3, 0);
            } else if (e != player && enemy(player, e)) {
                plugin.combat().damage(player, e, 3);
                e.setFireTicks(Math.max(e.getFireTicks(), 80));
                e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                Fx.particle(Combat.center(e), Particle.FLAME, 15, 0.3, 0.05);
            }
        }
    }

    private void eclipseVeil(Player player) {
        Location at = player.getLocation().add(0, 1, 0);
        plugin.fx().flash(at, 0x1A1F3A, 4f);
        plugin.fx().wave(at, new Vector(0, 1, 0), Order.LUNAR.color.value(), 8f, 8);
        Fx.sound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.4f, 0.8f);
        Fx.sound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.6f);
        List<Player> veiled = new ArrayList<>();
        for (Player p : player.getLocation().getNearbyPlayers(8)) {
            if (p == player || allies(player, p)) {
                veiled.add(p);
            }
        }
        for (Player p : veiled) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, false, true));
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.SQUID_INK, 20, 0.4, 0.05);
            // enemies don't see them at all (armour and weapon too); allies still do
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != p && !allies(other, p)) {
                    other.hidePlayer(plugin, p);
                }
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : veiled) {
                if (!p.isOnline()) {
                    continue;
                }
                for (Player other : Bukkit.getOnlinePlayers()) {
                    other.showPlayer(plugin, p);
                }
                Fx.particle(p.getLocation().add(0, 1, 0), Particle.END_ROD, 10, 0.3, 0.03);
            }
        }, 120L);
    }

    private void starfallGate(Player player) {
        Player nearest = null;
        double best = 300 * 300;
        for (Player p : player.getWorld().getPlayers()) {
            if (p != player && allies(player, p) && p.getGameMode() != GameMode.SPECTATOR) {
                double d = p.getLocation().distanceSquared(player.getLocation());
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
        }
        Location from = player.getLocation();
        Location to;
        if (nearest != null) {
            Vector back = nearest.getLocation().getDirection().setY(0);
            back = back.lengthSquared() < 1.0E-4 ? new Vector(0, 0, -1) : back.normalize().multiply(-1.5);
            to = Combat.safeSpot(nearest.getLocation().add(back));
            if (to == null) {
                to = nearest.getLocation();
            }
        } else {
            Vector look = from.getDirection().setY(0);
            look = look.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : look.normalize();
            to = Combat.safeSpot(from.clone().add(look.multiply(10)));
            if (to == null) {
                player.sendActionBar(Text.plain("Nowhere to go.", NamedTextColor.RED));
                powerUsed.remove(player.getUniqueId());
                return;
            }
        }
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        int violet = Order.ASTRAL.color.value();
        plugin.fx().flash(from.clone().add(0, 1, 0), violet, 2.6f);
        Fx.particle(from.clone().add(0, 1, 0), Particle.END_ROD, 30, 0.4, 0.1);
        player.teleport(to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        plugin.fx().flash(to.clone().add(0, 1, 0), violet, 2.6f);
        Fx.particle(to.clone().add(0, 1, 0), Particle.END_ROD, 30, 0.4, 0.1);
        Fx.sound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
        Fx.sound(to, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
    }
}
