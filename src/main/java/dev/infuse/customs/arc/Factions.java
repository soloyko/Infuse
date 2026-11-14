package dev.infuse.customs.arc;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.sword.SwordType;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Arc II's faction war. When an admin starts a session, three random players become the leaders of
 * the Order of the Blade, the House of the Forge and the Covenant of the Fallen, each carrying their
 * faction's weapon, and two more random players join each of them. Factions can't hurt their own,
 * each has a blessing, every kill scores, and an unsworn player slain by a faction's weapon is
 * sworn into that faction. When the session ends the faction with the most points wins.
 */
public final class Factions implements Listener {

    public enum Faction {
        BLADE("blade", "Order of the Blade", "Blade", "⚔", 0x4DA3FF, NamedTextColor.BLUE, SwordType.HEROSWORD, Material.BLUE_BANNER,
                "Sharpened", "Haste I, and your melee hits on anyone outside the Order deal 10% more damage."),
        FORGE("forge", "House of the Forge", "Forge", "⚒", 0xE0A030, NamedTextColor.GOLD, SwordType.MOLTEN, Material.ORANGE_BANNER,
                "Ironclad", "Two extra hearts and 30% knockback resistance."),
        FALLEN("fallen", "Covenant of the Fallen", "Fallen", "✝", 0xD83A3A, NamedTextColor.RED, SwordType.FALLEN, Material.RED_BANNER,
                "Swiftstep", "Speed I and half a block more reach.");

        public final String id;
        public final String title;
        public final String shortName;
        public final String symbol;
        public final TextColor color;
        public final NamedTextColor teamColor;
        public final SwordType weapon;
        public final Material banner;
        public final String blessing;
        public final String blessingText;

        Faction(String id, String title, String shortName, String symbol, int color, NamedTextColor teamColor, SwordType weapon, Material banner,
                String blessing, String blessingText) {
            this.id = id;
            this.title = title;
            this.shortName = shortName;
            this.symbol = symbol;
            this.color = TextColor.color(color);
            this.teamColor = teamColor;
            this.weapon = weapon;
            this.banner = banner;
            this.blessing = blessing;
            this.blessingText = blessingText;
        }

        public Component label() {
            return Text.plain(symbol + " " + title, color);
        }

        /** "⚔ BLADE ⚔": short enough for a title on any screen. */
        public Component banner() {
            return Text.plain(symbol + " " + shortName.toUpperCase(Locale.ROOT) + " " + symbol, color).decorate(TextDecoration.BOLD);
        }

        public static @Nullable Faction of(SwordType weapon) {
            for (Faction f : values()) {
                if (f.weapon == weapon) {
                    return f;
                }
            }
            return null;
        }

        public static @Nullable Faction parse(@Nullable String input) {
            if (input == null) {
                return null;
            }
            String in = input.toLowerCase(Locale.ROOT);
            // the factions' names before their weapons became swords (2.8)
            in = switch (in) {
                case "hammer" -> "forge";
                case "spear" -> "fallen";
                default -> in;
            };
            for (Faction f : values()) {
                if (f.id.equals(in) || f.name().equalsIgnoreCase(in) || f.title.equalsIgnoreCase(input)
                        || f.title.toLowerCase(Locale.ROOT).contains(in) || f.weapon.id().equals(in)) {
                    return f;
                }
            }
            return null;
        }
    }

    /** The fewest players a war can be formed with: one leader per faction. */
    public static final int MIN_PLAYERS = 3;
    private static final int MEMBERS_PER_LEADER = 2;

    private final CustomsPlugin plugin;
    private final ArcService arcs;
    /** Marks a faction weapon that was handed to a leader (soulbound; an admin's /customs give copy isn't). */
    private final NamespacedKey grantedKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey knockbackKey;
    private final NamespacedKey reachKey;

    private final Map<UUID, Faction> members = new LinkedHashMap<>();
    private final EnumMap<Faction, UUID> leaders = new EnumMap<>(Faction.class);
    private final EnumMap<Faction, Integer> scores = new EnumMap<>(Faction.class);
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** Players sworn in by a faction's weapon, waiting to respawn and be told. */
    private final java.util.Set<UUID> justSworn = new java.util.HashSet<>();
    private boolean active;

    Factions(CustomsPlugin plugin, ArcService arcs) {
        this.plugin = plugin;
        this.arcs = arcs;
        this.grantedKey = new NamespacedKey(plugin, "faction_granted");
        this.healthKey = new NamespacedKey(plugin, "faction_health");
        this.knockbackKey = new NamespacedKey(plugin, "faction_knockback");
        this.reachKey = new NamespacedKey(plugin, "faction_reach");
    }

    // ---------------------------------------------------------------- state

    /** The war is running (Arc II, during a session). */
    public boolean active() {
        return active;
    }

    /** The leaders and members have been picked for this session. */
    public boolean formed() {
        return !leaders.isEmpty();
    }

    public @Nullable Faction of(Player player) {
        return active ? members.get(player.getUniqueId()) : null;
    }

    public @Nullable UUID leader(Faction faction) {
        return leaders.get(faction);
    }

    public boolean isLeader(Player player) {
        return active && leaders.containsValue(player.getUniqueId());
    }

    public int score(Faction faction) {
        return scores.getOrDefault(faction, 0);
    }

    public List<UUID> roster(Faction faction) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, Faction> e : members.entrySet()) {
            if (e.getValue() == faction) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Two players in the same faction: they can't hurt each other. */
    public boolean allies(Player a, Player b) {
        if (!active || a == b) {
            return false;
        }
        Faction fa = members.get(a.getUniqueId());
        return fa != null && fa == members.get(b.getUniqueId());
    }

    private int leaderPoints() {
        return Math.max(1, plugin.getConfig().getInt("arcs.factions.leader-kill-points", 3));
    }

    // ---------------------------------------------------------------- saving

    void read(@Nullable ConfigurationSection section) {
        members.clear();
        leaders.clear();
        scores.clear();
        if (section == null) {
            return;
        }
        ConfigurationSection m = section.getConfigurationSection("members");
        if (m != null) {
            for (String key : m.getKeys(false)) {
                Faction f = Faction.parse(m.getString(key));
                UUID id = uuid(key);
                if (f != null && id != null) {
                    members.put(id, f);
                }
            }
        }
        for (Faction f : Faction.values()) {
            UUID id = uuid(section.getString("leaders." + f.id));
            if (id != null) {
                leaders.put(f, id);
                members.put(id, f);
            }
            scores.put(f, section.getInt("scores." + f.id, 0));
        }
    }

    void write(ConfigurationSection section) {
        for (Map.Entry<UUID, Faction> e : members.entrySet()) {
            section.set("members." + e.getKey(), e.getValue().id);
        }
        for (Map.Entry<Faction, UUID> e : leaders.entrySet()) {
            section.set("leaders." + e.getKey().id, e.getValue().toString());
        }
        for (Faction f : Faction.values()) {
            section.set("scores." + f.id, score(f));
        }
    }

    private static @Nullable UUID uuid(@Nullable String s) {
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- the war starting and ending

    /** Puts the war in play: teams, blessings and weapons for whoever has already been sworn. */
    void start() {
        if (active) {
            return;
        }
        active = true;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Faction f : Faction.values()) {
            Team team = board.getTeam(teamName(f));
            if (team == null) {
                team = board.registerNewTeam(teamName(f));
            }
            team.color(f.teamColor);
            team.prefix(Text.plain(f.symbol + " ", f.color));
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            equip(p);
        }
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::bless, 20L, 20L));
    }

    /**
     * Picks three random leaders and two random members for each from the players online. Returns
     * false (and picks nobody) when fewer than {@link #MIN_PLAYERS} can fight.
     */
    boolean form() {
        if (formed()) {
            return true;
        }
        List<Player> pool = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                pool.add(p);
            }
        }
        if (pool.size() < MIN_PLAYERS) {
            return false;
        }
        Collections.shuffle(pool);
        List<Faction> order = new ArrayList<>(List.of(Faction.values()));
        Collections.shuffle(order);
        Faction[] all = Faction.values();
        for (int i = 0; i < all.length; i++) {
            leaders.put(all[i], pool.get(i).getUniqueId());
            members.put(pool.get(i).getUniqueId(), all[i]);
        }
        // two members each, handed out in turn so the factions stay even when players are short
        int next = all.length;
        for (int round = 0; round < MEMBERS_PER_LEADER; round++) {
            for (Faction f : order) {
                if (next < pool.size()) {
                    members.put(pool.get(next++).getUniqueId(), f);
                }
            }
        }
        arcs.save();
        announceFormation(pool);
        for (Player p : Bukkit.getOnlinePlayers()) {
            equip(p);
        }
        arcs.refreshBar();
        return true;
    }

    private void announceFormation(List<Player> pool) {
        Component header = Text.plain("━━━━━━  ", NamedTextColor.GOLD)
                .append(Text.plain("The Factions Rise", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Text.plain("  ━━━━━━", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(header);
        for (Faction f : Faction.values()) {
            Bukkit.broadcast(rosterLine(f));
        }
        long unsworn = pool.stream().filter(p -> !members.containsKey(p.getUniqueId())).count();
        if (unsworn > 0) {
            Bukkit.broadcast(Text.plain(unsworn + " unsworn. Fall to a faction's weapon and you join that faction.", NamedTextColor.GRAY));
        }
        Bukkit.broadcast(Component.empty());
        Title.Times times = Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800));
        for (Player p : Bukkit.getOnlinePlayers()) {
            Faction f = members.get(p.getUniqueId());
            Title title;
            if (f == null) {
                title = Title.title(Text.plain("UNSWORN", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                        Text.caps("no faction claimed you", NamedTextColor.WHITE), times);
            } else if (p.getUniqueId().equals(leaders.get(f))) {
                title = Title.title(Text.plain(f.symbol + " FACTION LEADER", f.color).decorate(TextDecoration.BOLD),
                        Text.caps("you lead the " + f.title + " and carry " + f.weapon.swordName(), NamedTextColor.WHITE), times);
            } else {
                title = Title.title(f.banner(),
                        Text.caps(f.title + " · led by " + name(leaders.get(f)), NamedTextColor.WHITE), times);
            }
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1f);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.8f);
            if (f != null) {
                Location at = p.getLocation();
                plugin.fx().flash(at.clone().add(0, 1, 0), f.color.value(), 2.4f);
                plugin.fx().castCircle(at, f.color.value(), 1.8f);
                if (p.getUniqueId().equals(leaders.get(f))) {
                    plugin.fx().beam(at.clone().add(0, 30, 0), at, f.color.value(), 1.4f, 40);
                    plugin.fx().shockwave(at.clone().add(0, 0.05, 0), f.color.value(), 6f, 12);
                    p.playSound(at, Sound.ITEM_TOTEM_USE, 0.6f, 1.2f);
                }
            }
        }
    }

    /** "⚔ Order of the Blade  Leader: Steve · Alex, Sam". */
    public Component rosterLine(Faction f) {
        Component line = f.label().decorate(TextDecoration.BOLD)
                .append(Text.plain("  " + score(f) + (score(f) == 1 ? " pt" : " pts"), NamedTextColor.GRAY))
                .append(Text.plain("   ★ " + name(leaders.get(f)), NamedTextColor.WHITE));
        List<String> rest = new ArrayList<>();
        for (UUID id : roster(f)) {
            if (!id.equals(leaders.get(f))) {
                rest.add(name(id));
            }
        }
        if (!rest.isEmpty()) {
            line = line.append(Text.plain("  ·  " + String.join(", ", rest), NamedTextColor.GRAY));
        }
        return line;
    }

    private static String name(@Nullable UUID id) {
        if (id == null) {
            return "nobody";
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(id);
        return p.getName() == null ? "someone" : p.getName();
    }

    /** Ends the war: optionally crowns the winner, then disbands the factions and takes the weapons back. */
    void stop(boolean announce) {
        if (!active) {
            return;
        }
        if (announce && formed()) {
            crownWinner();
        }
        active = false;
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Faction f : Faction.values()) {
            Team team = board.getTeam(teamName(f));
            if (team != null) {
                team.unregister();
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            unequip(p);
        }
        for (UUID id : leaders.values()) {
            uncrown(id);
        }
        justSworn.clear();
    }

    /** Removes faction teams left in the saved scoreboard (after a crash mid-war); start() makes them again if due. */
    void purgeTeams() {
        if (active) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Faction f : Faction.values()) {
            Team team = board.getTeam(teamName(f));
            if (team != null) {
                team.unregister();
            }
        }
    }

    /** Clears the factions for the next session. */
    void reset() {
        members.clear();
        leaders.clear();
        scores.clear();
        leaderAway.clear();
    }

    private static String teamName(Faction f) {
        return "customs_" + f.id;
    }

    private void crownWinner() {
        Faction best = null;
        int top = -1;
        boolean tie = false;
        for (Faction f : Faction.values()) {
            int s = score(f);
            if (s > top) {
                best = f;
                top = s;
                tie = false;
            } else if (s == top) {
                tie = true;
            }
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(800));
        Component line;
        Title title;
        if (best == null || tie || top <= 0) {
            line = Text.plain("The faction war ends in a draw.", NamedTextColor.GOLD);
            title = Title.title(Text.plain("A DRAW", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), scoreLine(), times);
        } else {
            line = best.label().decorate(TextDecoration.BOLD).append(Text.plain(" wins the faction war with "
                    + top + (top == 1 ? " point!" : " points!"), NamedTextColor.GOLD));
            title = Title.title(best.banner(),
                    Text.caps("the " + best.title + " wins the war", NamedTextColor.WHITE), times);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (members.get(p.getUniqueId()) == best) {
                    arcs.reward(p, p.getUniqueId().equals(leaders.get(best)) ? 5 : 3);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    celebrate(p, best);
                }
            }
        }
        Bukkit.broadcast(line);
        for (Faction f : Faction.values()) {
            Bukkit.broadcast(rosterLine(f));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
        }
    }

    /**
     * Firework-like bursts in the faction's colours over a winner (made of effect displays and
     * particles: real fireworks would hurt the players around them).
     */
    private void celebrate(Player p, Faction f) {
        int[] colours = {f.color.value(), 0xFFFFFF, f.color.value(), 0xF2C94C};
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            int colour = colours[i % colours.length];
            long delay = 4L + i * 9L + r.nextInt(4);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                Location at = p.getLocation().add(r.nextDouble(-3, 3), r.nextDouble(5, 8), r.nextDouble(-3, 3));
                plugin.fx().burst(at, colour, 3.2f);
                plugin.fx().flash(at, colour, 2.4f);
                Fx.particle(at, org.bukkit.Particle.FIREWORK, 40, 0.2, 0.25);
                Fx.particle(at, org.bukkit.Particle.DUST, 30, 1.2, 0, Fx.dust(colour, 1.6f));
                Fx.sound(at, r.nextBoolean() ? Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST : Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 1f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> Fx.sound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1f), 8L);
            }, delay);
        }
    }

    /** "⚔ 12  ⚒ 9  ♆ 15". */
    public Component scoreLine() {
        Component out = Component.empty();
        Faction[] all = Faction.values();
        for (int i = 0; i < all.length; i++) {
            out = out.append(Text.plain(all[i].symbol + " " + score(all[i]) + (i + 1 < all.length ? "  " : ""), all[i].color));
        }
        return out;
    }

    // ---------------------------------------------------------------- membership

    /**
     * Puts a player in a faction (or takes them out with null), optionally as its leader. The old
     * leader of that faction becomes a plain member.
     */
    public void assign(Player player, @Nullable Faction faction, boolean leader) {
        UUID id = player.getUniqueId();
        Faction old = members.get(id);
        if (old != null && id.equals(leaders.get(old)) && (old != faction || !leader)) {
            leaders.remove(old);
        }
        if (faction == null) {
            members.remove(id);
        } else {
            members.put(id, faction);
            if (leader) {
                UUID previous = leaders.put(faction, id);
                Player prev = previous == null ? null : Bukkit.getPlayer(previous);
                if (prev != null && prev != player) {
                    equip(prev);
                }
            }
        }
        arcs.save();
        equip(player);
        if (faction != null) {
            player.showTitle(Title.title(faction.banner(),
                    Text.caps(leader ? "you lead the " + faction.title + " now" : "sworn to the " + faction.title, NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.8f);
        }
        arcs.refreshBar();
    }

    /** An unsworn player slain by a faction's weapon is sworn into that faction. */
    private void convert(Player victim, Faction faction, Player killer) {
        members.put(victim.getUniqueId(), faction);
        justSworn.add(victim.getUniqueId());
        arcs.save();
        Bukkit.broadcast(Text.plain(victim.getName(), NamedTextColor.WHITE)
                .append(Text.plain(" fell to " + killer.getName() + "'s ", NamedTextColor.GRAY))
                .append(Text.sword(faction.weapon))
                .append(Text.plain(" and is sworn into the ", NamedTextColor.GRAY))
                .append(faction.label()).append(Text.plain(".", NamedTextColor.GRAY)));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline()) {
                equip(victim);
            }
        });
    }

    /** Team, glow and weapon for a player as things stand (or none of them). */
    private void equip(Player player) {
        Faction f = of(player);
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Faction o : Faction.values()) {
            Team team = board.getTeam(teamName(o));
            if (team != null) {
                if (o == f) {
                    team.addEntry(player.getName());
                } else if (team.hasEntry(player.getName())) {
                    team.removeEntry(player.getName());
                }
            }
        }
        boolean leader = f != null && player.getUniqueId().equals(leaders.get(f));
        stripWeapons(player, leader ? f.weapon : null);
        if (leader) {
            giveWeapon(player, f);
        }
        player.setGlowing(leader);
        clearAttributes(player);
        if (leader) {
            crown(player, f);
        } else {
            uncrown(player.getUniqueId());
        }
    }

    /** "⚔ LEADER ⚔" floating over a leader's name, for everyone but the leader. */
    private void crown(Player player, Faction faction) {
        if (arcs.emblems().has(player.getUniqueId())) {
            return;
        }
        Component text = Text.plain(faction.symbol + " ", faction.color)
                .append(Text.caps("leader", faction.color).decorate(TextDecoration.BOLD))
                .append(Text.plain(" " + faction.symbol, faction.color));
        arcs.emblems().set(player, text, 2.55);
    }

    private void uncrown(UUID id) {
        arcs.emblems().remove(id);
    }

    private void unequip(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Faction f : Faction.values()) {
            Team team = board.getTeam(teamName(f));
            if (team != null && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
        if (stripWeapons(player, null) || leaders.containsValue(player.getUniqueId())) {
            player.setGlowing(false);
        }
        clearAttributes(player);
        uncrown(player.getUniqueId());
    }

    // ---------------------------------------------------------------- faction weapons

    public boolean isGranted(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(grantedKey);
    }

    private void giveWeapon(Player player, Faction faction) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isGranted(stack) && plugin.items().typeOf(stack) == faction.weapon) {
                return;
            }
        }
        ItemStack weapon = plugin.items().create(faction.weapon);
        weapon.editMeta(meta -> meta.getPersistentDataContainer().set(grantedKey, PersistentDataType.STRING, faction.id));
        for (ItemStack overflow : player.getInventory().addItem(weapon).values()) {
            // a full inventory: swap it into the hand slot's place rather than dropping a soulbound weapon
            ItemStack hand = player.getInventory().getItemInMainHand();
            player.getInventory().setItemInMainHand(overflow);
            if (!hand.isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), hand);
            }
        }
    }

    /** Takes away every granted faction weapon except {@code keep}; returns whether any was taken. */
    private boolean stripWeapons(Player player, @Nullable SwordType keep) {
        boolean took = false;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGranted(contents[i]) && plugin.items().typeOf(contents[i]) != keep) {
                player.getInventory().setItem(i, null);
                took = true;
            }
        }
        if (isGranted(player.getItemOnCursor()) && plugin.items().typeOf(player.getItemOnCursor()) != keep) {
            player.setItemOnCursor(null);
            took = true;
        }
        return took;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isGranted(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Granted weapons stay in their leader's own inventory: no chests, hoppers or other players. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (top == InventoryType.CRAFTING || top == InventoryType.CREATIVE) {
            return;
        }
        boolean involved = isGranted(event.getCurrentItem()) || isGranted(event.getCursor());
        if (!involved && event.getHotbarButton() >= 0) {
            involved = isGranted(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        }
        if (involved) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (top != InventoryType.CRAFTING && top != InventoryType.CREATIVE && isGranted(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame
                && isGranted(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isGranted(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- blessings

    /** When each faction's leader was first seen offline, for succession. */
    private final EnumMap<Faction, Long> leaderAway = new EnumMap<>(Faction.class);
    private static final long SUCCESSION_MS = 120_000L;

    /**
     * A faction whose leader has been offline for two minutes passes the weapon to a random online
     * member, so a war can't be won by logging out.
     */
    private void succession() {
        long now = System.currentTimeMillis();
        for (Faction f : Faction.values()) {
            UUID id = leaders.get(f);
            if (id == null || Bukkit.getPlayer(id) != null) {
                leaderAway.remove(f);
                continue;
            }
            long since = leaderAway.computeIfAbsent(f, k -> now);
            if (now - since < SUCCESSION_MS) {
                continue;
            }
            List<Player> heirs = new ArrayList<>();
            for (UUID member : roster(f)) {
                Player p = Bukkit.getPlayer(member);
                if (p != null && !member.equals(id) && !p.isDead()) {
                    heirs.add(p);
                }
            }
            if (heirs.isEmpty()) {
                continue;
            }
            Player heir = heirs.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(heirs.size()));
            leaderAway.remove(f);
            assign(heir, f, true);
            Bukkit.broadcast(f.label().append(Text.plain(": " + name(id) + " has abandoned the fight. ", NamedTextColor.GRAY))
                    .append(Text.plain(heir.getName(), NamedTextColor.WHITE))
                    .append(Text.plain(" takes up the ", NamedTextColor.GRAY)).append(Text.sword(f.weapon)).append(Text.plain(".", NamedTextColor.GRAY)));
            Location at = heir.getLocation();
            plugin.fx().beam(at.clone().add(0, 30, 0), at, f.color.value(), 1.4f, 40);
            plugin.fx().shockwave(at.clone().add(0, 0.05, 0), f.color.value(), 6f, 12);
        }
    }

    private void bless() {
        succession();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Faction f = of(p);
            boolean leader = f != null && p.getUniqueId().equals(leaders.get(f));
            // a granted weapon that isn't theirs (left over from an older session, or passed around)
            stripWeapons(p, leader ? f.weapon : null);
            if (f == null) {
                clearAttributes(p);
                continue;
            }
            if (leader && !p.isGlowing()) {
                p.setGlowing(true);
            }
            if (leader && !p.isDead()) {
                crown(p, f);
            }
            switch (f) {
                case BLADE -> quiet(p, PotionEffectType.HASTE, 50, 0);
                case FORGE -> {
                    ensure(p, Attribute.MAX_HEALTH, healthKey, 4);
                    ensure(p, Attribute.KNOCKBACK_RESISTANCE, knockbackKey, 0.3);
                }
                case FALLEN -> {
                    quiet(p, PotionEffectType.SPEED, 50, 0);
                    ensure(p, Attribute.ENTITY_INTERACTION_RANGE, reachKey, 0.5);
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

    private static void ensure(Player p, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst != null && inst.getModifier(key) == null) {
            inst.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void clearAttributes(Player p) {
        remove(p, Attribute.MAX_HEALTH, healthKey);
        remove(p, Attribute.KNOCKBACK_RESISTANCE, knockbackKey);
        remove(p, Attribute.ENTITY_INTERACTION_RANGE, reachKey);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && p.getHealth() > max.getValue()) {
            p.setHealth(max.getValue());
        }
    }

    private static void remove(Player p, Attribute attribute, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst != null && inst.getModifier(key) != null) {
            inst.removeModifier(key);
        }
    }

    // ---------------------------------------------------------------- fighting

    private static @Nullable Player attacker(EntityDamageEvent event) {
        Entity source = event.getDamageSource().getCausingEntity();
        if (source == null && event instanceof EntityDamageByEntityEvent byEntity) {
            source = byEntity.getDamager() instanceof Projectile p && p.getShooter() instanceof Entity shooter ? shooter : byEntity.getDamager();
        }
        return source instanceof Player p ? p : null;
    }

    /** Factions don't hurt their own; the Order of the Blade cuts deeper. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!active || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker == victim) {
            return;
        }
        Faction a = members.get(attacker.getUniqueId());
        if (a != null && a == members.get(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (a == Faction.BLADE && event.getDamager() == attacker && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.setDamage(event.getDamage() * 1.1);
        }
    }

    /**
     * Kills of anyone outside the faction score for it (a leader is worth more), a granted weapon
     * never drops, and an unsworn player slain by a faction's weapon joins that faction.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        event.getDrops().removeIf(this::isGranted);
        Player killer = victim.getKiller();
        if (!active || killer == null || killer == victim) {
            return;
        }
        Faction a = members.get(killer.getUniqueId());
        Faction v = members.get(victim.getUniqueId());
        if (a == null || a == v) {
            return;
        }
        boolean leaderSlain = v != null && victim.getUniqueId().equals(leaders.get(v));
        int points = leaderSlain ? leaderPoints() : 1;
        scores.merge(a, points, Integer::sum);
        Component line = Text.plain(a.symbol + " " + killer.getName(), a.color)
                .append(Text.plain(leaderSlain ? " slew the leader " : " slew ", NamedTextColor.GRAY))
                .append(Text.plain((v == null ? "" : v.symbol + " ") + victim.getName(), v == null ? NamedTextColor.WHITE : v.color))
                .append(Text.plain("  +" + points + "   ", NamedTextColor.GRAY)).append(scoreLine());
        Bukkit.broadcast(line);
        // the points rise from where the victim fell, in the killer's colours
        Location at = victim.getLocation().add(0, 1.6, 0);
        org.bukkit.entity.TextDisplay pop = plugin.displays().text(at,
                Text.plain("+" + points + " " + a.symbol, a.color).decorate(TextDecoration.BOLD), leaderSlain ? 2.2f : 1.5f, 34);
        pop.setTeleportDuration(30);
        plugin.displays().later(2, () -> {
            if (pop.isValid()) {
                pop.teleport(at.clone().add(0, 1.4, 0));
            }
        });
        plugin.fx().burst(at.clone().subtract(0, 0.6, 0), a.color.value(), leaderSlain ? 3f : 2f);
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, leaderSlain ? 0.7f : 1.2f);
        if (v == null && killedByWeapon(killer, a)) {
            convert(victim, a, killer);
        }
        if (leaderSlain) {
            Fx.sound(victim.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.4f);
        }
        arcs.save();
        arcs.refreshBar();
    }

    /** Whether the killing blow came from the faction's own weapon, in the hands of its leader. */
    private boolean killedByWeapon(Player killer, Faction faction) {
        return killer.getUniqueId().equals(leaders.get(faction)) && plugin.items().heldType(killer) == faction.weapon;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (active) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) {
                    equip(p);
                }
            });
            if (justSworn.remove(p.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> sworn(p), 10L);
            }
        }
    }

    /**
     * A player joining after the factions formed takes an open member slot (each leader gets two
     * members), in the faction with the fewest; once the slots are full, newcomers stay unsworn.
     */
    private void fillSlot(Player p) {
        if (!active || !formed() || !p.isOnline() || members.containsKey(p.getUniqueId())
                || (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE)) {
            return;
        }
        Faction pick = null;
        int fewest = MEMBERS_PER_LEADER;
        for (Faction f : Faction.values()) {
            int n = roster(f).size() - (leaders.containsKey(f) ? 1 : 0);
            if (n < fewest) {
                fewest = n;
                pick = f;
            }
        }
        if (pick == null) {
            return;
        }
        members.put(p.getUniqueId(), pick);
        arcs.save();
        equip(p);
        Bukkit.broadcast(Text.plain(p.getName(), NamedTextColor.WHITE).append(Text.plain(" joins the ", NamedTextColor.GRAY))
                .append(pick.label()).append(Text.plain(".", NamedTextColor.GRAY)));
        p.showTitle(Title.title(pick.banner(),
                Text.caps(pick.title + " · led by " + name(leaders.get(pick)), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        Location at = p.getLocation();
        plugin.fx().flash(at.clone().add(0, 1, 0), pick.color.value(), 2.4f);
        plugin.fx().castCircle(at, pick.color.value(), 1.8f);
        p.playSound(at, Sound.BLOCK_BELL_USE, 1f, 0.8f);
        arcs.refreshBar();
    }

    /** The moment a converted player comes back: their new faction's title, light and bell. */
    private void sworn(Player p) {
        Faction f = of(p);
        if (f == null || !p.isOnline()) {
            return;
        }
        p.showTitle(Title.title(f.banner(),
                Text.caps("you fell to its weapon · now you fight for the " + f.title, NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        Location at = p.getLocation();
        plugin.fx().beam(at.clone().add(0, 20, 0), at, f.color.value(), 1.0f, 24);
        plugin.fx().castCircle(at, f.color.value(), 1.8f);
        plugin.fx().flash(at.clone().add(0, 1, 0), f.color.value(), 2.6f);
        p.playSound(at, Sound.BLOCK_BELL_USE, 1f, 0.7f);
        p.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        uncrown(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (active) {
            equip(p);
            Bukkit.getScheduler().runTaskLater(plugin, () -> fillSlot(p), 50L);
        } else {
            unequip(p);
        }
    }

    /** Lines for /customs faction. */
    public List<Component> status() {
        List<Component> out = new ArrayList<>();
        out.add(Text.plain("Faction war  ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).append(scoreLine()));
        for (Faction f : Faction.values()) {
            out.add(rosterLine(f));
            out.add(Text.plain("   " + f.blessing + ": " + f.blessingText, NamedTextColor.DARK_GRAY));
        }
        return out;
    }

}
