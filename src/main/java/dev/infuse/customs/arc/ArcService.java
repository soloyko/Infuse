package dev.infuse.customs.arc;

import dev.infuse.customs.CustomsPlugin;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the server's arcs and sessions. Admins start Arc I and move on through Arc V; the current arc
 * decides which weapons can be forged (its own and every earlier arc's). Arcs I and II have a PvP
 * twist that only runs during a session, which an admin starts and ends. While no arc is running,
 * nothing is locked and there are no twists. Saved in arcs.yml.
 */
public final class ArcService implements Listener {

    /** Materials too rare to hand out as rewards. */
    private static final Set<Material> TOO_RARE = EnumSet.of(Material.NETHER_STAR, Material.BEACON, Material.TOTEM_OF_UNDYING,
            Material.CONDUIT, Material.DRAGON_HEAD, Material.HEART_OF_THE_SEA, Material.END_CRYSTAL, Material.WITHER_SKELETON_SKULL,
            Material.DRAGON_BREATH, Material.RECOVERY_COMPASS, Material.SCULK_CATALYST, Material.SCULK_SHRIEKER, Material.NETHERITE_INGOT,
            Material.NETHERITE_SWORD, Material.NETHERITE_SPEAR, Material.NETHERITE_AXE, Material.MACE, Material.TRIDENT, Material.ELYTRA,
            Material.ENCHANTED_GOLDEN_APPLE, Material.ECHO_SHARD, Material.NETHERITE_SCRAP, Material.BEEHIVE);

    private final CustomsPlugin plugin;
    private final File file;
    private final Factions factions;
    private final SessionBoard board = new SessionBoard();

    private @Nullable Arc current;
    private long startedAt;
    private @Nullable BossBar bar;
    /** The bar and session clock, once a second while the plugin runs. */
    private @Nullable BukkitTask ticker;
    /** The running twist's own tasks. */
    private final List<BukkitTask> tasks = new ArrayList<>();

    /** Session: whether one is running, when it began, and when it ends (0: when an admin ends it). */
    private boolean session;
    private long sessionStarted;
    private long sessionEnds;
    private final Map<UUID, Integer> sessionKills = new HashMap<>();

    /** Bounty Hunt: the current bounty and when they win. */
    private @Nullable UUID bounty;
    private long bountyUntil;

    public ArcService(CustomsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arcs.yml");
        this.factions = new Factions(plugin, this);
    }

    public Factions factions() {
        return factions;
    }

    // ---------------------------------------------------------------- state

    public @Nullable Arc current() {
        return current;
    }

    public boolean running() {
        return current != null;
    }

    public boolean sessionActive() {
        return session;
    }

    /** Whether a weapon can be forged right now: always when no arc runs, else up to the current arc. Faction weapons never. */
    public boolean unlocked(SwordType type) {
        Arc arc = type.arc();
        if (arc == null) {
            return false;
        }
        return current == null || arc.ordinal() <= current.ordinal();
    }

    /** Why a weapon can't be forged, or null if it can. */
    public @Nullable String lockReason(SwordType type) {
        if (unlocked(type)) {
            return null;
        }
        Arc arc = type.arc();
        return arc == null ? "is carried by a faction leader in Arc II and is never forged" : "unlocks when " + arc.label() + " begins";
    }

    /** Whether an arc's twist is in play: its arc is current, a session is running and it isn't turned off. */
    private boolean twist(Arc arc) {
        return session && current == arc && arc.hasTwist()
                && plugin.getConfig().getBoolean("arcs.twists." + arc.title().toLowerCase(Locale.ROOT), true);
    }

    /** Ability cooldowns aren't changed by any arc at the moment. */
    public double cooldownMultiplier() {
        return 1.0;
    }

    /** Players on the same side (the same faction in Arc II): abilities and hits pass them by. */
    public boolean allies(Player a, Player b) {
        return factions.allies(a, b);
    }

    // ---------------------------------------------------------------- saving

    /** Loads the saved arc and session and puts the twist back in play (after a restart or reload). */
    public void load() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        current = Arc.parse(data.getString("current"));
        startedAt = data.getLong("started", 0);
        session = data.getBoolean("session.active", false);
        sessionStarted = data.getLong("session.started", 0);
        sessionEnds = data.getLong("session.ends", 0);
        sessionKills.clear();
        ConfigurationSection k = data.getConfigurationSection("session.kills");
        if (k != null) {
            for (String key : k.getKeys(false)) {
                try {
                    sessionKills.put(UUID.fromString(key), k.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    // a bad entry
                }
            }
        }
        factions.read(data.getConfigurationSection("factions"));
        migrate(data);
        applyTwist();
        if (ticker == null) {
            ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
    }

    /** 2.7 to 2.8: Collapse and Eternal Hearts are gone; put a shrunken border back the way it was. */
    private void migrate(YamlConfiguration data) {
        ConfigurationSection b = data.getConfigurationSection("borders-before");
        if (b == null) {
            return;
        }
        for (String name : b.getKeys(false)) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                WorldBorder border = world.getWorldBorder();
                border.setCenter(b.getDouble(name + ".x"), b.getDouble(name + ".z"));
                border.setSize(b.getDouble(name + ".size"));
            }
        }
        save();
    }

    void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.options().setHeader(List.of("The server's current arc and session. Managed by the plugin: use /customs arc and /customs session."));
        data.set("current", current == null ? null : current.name());
        data.set("started", startedAt);
        data.set("session.active", session);
        data.set("session.started", sessionStarted);
        data.set("session.ends", sessionEnds);
        for (Map.Entry<UUID, Integer> e : sessionKills.entrySet()) {
            data.set("session.kills." + e.getKey(), e.getValue());
        }
        factions.write(data.createSection("factions"));
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save arcs.yml: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- arcs

    /** Starts an arc (closing the one before) with a ceremony for everyone online. */
    public void begin(Arc arc) {
        // a war just ended: let its winner have the screen for a few seconds before the next arc
        boolean crowned = factions.active() && factions.formed();
        closeTwist(true);
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

    /** Ends the arcs: everything unlocks and the twists stop (a running session carries on). */
    public void end() {
        closeTwist(true);
        current = null;
        save();
        applyTwist();
        plugin.altars().refreshAll();
        Bukkit.broadcast(Text.plain("The arcs have ended. Every weapon may now be forged.", NamedTextColor.GOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
        }
    }

    /** Server stopping or reloading: pause the twist (it comes back on load). */
    public void shutdown() {
        pauseTwist();
        hideBar();
        board.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    // ---------------------------------------------------------------- sessions

    /**
     * Starts a session (admins only): the current arc's twist comes into play until the session
     * ends. {@code minutes} of 0 or less runs it until an admin ends it.
     */
    public boolean startSession(int minutes) {
        if (session) {
            return false;
        }
        session = true;
        sessionStarted = System.currentTimeMillis();
        sessionEnds = minutes > 0 ? sessionStarted + minutes * 60_000L : 0;
        sessionKills.clear();
        save();
        Title title = Title.title(Text.plain("SESSION STARTED", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                Text.caps(minutes > 0 ? minutes + " minutes · fight well" : "fight well", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800)));
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Text.plain("━━━━━━  ", NamedTextColor.GREEN)
                .append(Text.plain("A session has begun", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Text.plain("  ━━━━━━", NamedTextColor.GREEN)));
        Bukkit.broadcast(Text.plain(minutes > 0 ? "It lasts " + minutes + " minutes." : "It lasts until an admin ends it.", NamedTextColor.GRAY));
        if (current != null && current.hasTwist()) {
            Bukkit.broadcast(Text.plain("Twist: ", NamedTextColor.GRAY).append(Text.plain(current.twist(), current.color()).decorate(TextDecoration.BOLD)));
            Bukkit.broadcast(Text.plain(current.twistText(), NamedTextColor.WHITE));
        }
        Bukkit.broadcast(Component.empty());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 1.1f);
            Location at = p.getLocation();
            plugin.fx().beam(at.clone().add(0, 24, 0), at, 0x5CF27A, 0.9f, 24);
            plugin.fx().shockwave(at.clone().add(0, 0.05, 0), 0x5CF27A, 4f, 10);
        }
        // let the session title have the screen before the factions' own titles
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session) {
                applyTwist();
            }
        }, 60L);
        refreshBar();
        return true;
    }

    /** Ends the session: the twist's results are announced and it stops until the next session. */
    public boolean endSession() {
        if (!session) {
            return false;
        }
        closeTwist(true);
        long length = System.currentTimeMillis() - sessionStarted;
        session = false;
        save();
        applyTwist();
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Text.plain("━━━━━━  ", NamedTextColor.RED)
                .append(Text.plain("The session is over", NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Text.plain("  ━━━━━━", NamedTextColor.RED)));
        Bukkit.broadcast(Text.plain("It lasted " + clock(length) + ".", NamedTextColor.GRAY));
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(sessionKills.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        net.kyori.adventure.text.format.TextColor[] medals = {net.kyori.adventure.text.format.TextColor.color(0xF2C94C),
                net.kyori.adventure.text.format.TextColor.color(0xC9D1D9), net.kyori.adventure.text.format.TextColor.color(0xCD7F32)};
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(top.get(i).getKey());
            int kills = top.get(i).getValue();
            Bukkit.broadcast(Text.plain("  #" + (i + 1) + " ", medals[i]).decorate(TextDecoration.BOLD)
                    .append(Text.plain(p.getName() == null ? "someone" : p.getName(), NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Text.plain("  " + kills + (kills == 1 ? " kill" : " kills"), NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
        }
        if (top.isEmpty()) {
            Bukkit.broadcast(Text.plain("  No one was slain.", NamedTextColor.GRAY));
        }
        Bukkit.broadcast(Component.empty());
        Title over = Title.title(Text.plain("SESSION OVER", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Text.caps("lasted " + clock(length), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800)));
        boolean crowned = current == Arc.II;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.5f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
            if (!crowned) {
                p.showTitle(over); // in Arc II the winner's title has the screen
            }
            plugin.fx().shockwave(p.getLocation().add(0, 0.05, 0), 0xE5482C, 4f, 10);
        }
        board.clear();
        sessionKills.clear();
        save();
        return true;
    }

    /** "1:05:09" or "4:09". */
    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s / 60) % 60;
        return h > 0 ? h + ":" + String.format("%02d:%02d", m, s % 60) : m + ":" + String.format("%02d", s % 60);
    }

    /** Once a second: the session's clock, its automatic end and the bar. */
    private void tick() {
        if (session && sessionEnds > 0) {
            long left = sessionEnds - System.currentTimeMillis();
            if (left <= 0) {
                endSession();
                return;
            }
            if (left <= 60_000 && left > 59_000) {
                Bukkit.broadcast(Text.plain("The session ends in one minute.", NamedTextColor.RED));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f);
                }
            }
            if (left <= 10_000 && left > 0) {
                // the last ten seconds count down on screen
                long secs = (left + 999) / 1000;
                Title.Times quick = Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(Component.empty(), Text.plain(String.valueOf(secs), NamedTextColor.RED).decorate(TextDecoration.BOLD), quick));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, secs <= 3 ? 1.6f : 1.0f);
                }
            }
        }
        refreshBar();
        updateBoard();
        updateTab();
    }

    /** Whether we put a header and footer on the tab list (so we clear it once when it's no longer due). */
    private boolean tabShown;

    /** The tab list's header and footer: the arc, the session and, in Arc II, the faction points (arcs.tab-list). */
    private void updateTab() {
        boolean due = (current != null || session) && plugin.getConfig().getBoolean("arcs.tab-list", true);
        if (!due) {
            if (tabShown) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
                }
                tabShown = false;
            }
            return;
        }
        Component header = Component.newline()
                .append(Text.gradient("⚔ Customs ⚔", net.kyori.adventure.text.format.TextColor.color(0xECBA44),
                        net.kyori.adventure.text.format.TextColor.color(0xC4415B)).decorate(TextDecoration.BOLD));
        if (current != null) {
            header = header.append(Component.newline()).append(Text.plain(current.label() + " · " + current.title(), current.color()));
        }
        header = header.append(Component.newline());
        long now = System.currentTimeMillis();
        Component footer = Component.newline();
        if (session) {
            footer = footer.append(Text.plain("● ", NamedTextColor.GREEN))
                    .append(Text.caps("session " + (sessionEnds > 0 ? clock(sessionEnds - now) + " left" : clock(now - sessionStarted)), NamedTextColor.GREEN));
            if (current != null && twist(current)) {
                footer = footer.append(Text.plain("  ·  " + current.twist(), NamedTextColor.WHITE));
            }
        } else {
            footer = footer.append(Text.caps("no session running", NamedTextColor.DARK_GRAY));
        }
        if (factions.active() && factions.formed()) {
            footer = footer.append(Component.newline()).append(factions.scoreLine());
        }
        footer = footer.append(Component.newline());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(header, footer);
        }
        tabShown = true;
    }

    /** The sidebar while a session runs (arcs.sidebar). */
    private void updateBoard() {
        if (!session || !plugin.getConfig().getBoolean("arcs.sidebar", true)) {
            board.clear();
            return;
        }
        List<SessionBoard.Line> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (current != null) {
            String what = twist(current) ? current.twist() : current.title();
            lines.add(new SessionBoard.Line(Text.plain(current.label() + " · " + what, current.color()), null));
        }
        lines.add(new SessionBoard.Line(Text.caps("time", NamedTextColor.GRAY),
                Text.plain(sessionEnds > 0 ? clock(sessionEnds - now) + " left" : clock(now - sessionStarted), NamedTextColor.GREEN)));
        if (factions.active()) {
            lines.add(SessionBoard.Line.blank());
            if (factions.formed()) {
                for (Factions.Faction f : Factions.Faction.values()) {
                    lines.add(new SessionBoard.Line(Text.plain(f.symbol + " " + f.shortName, f.color)
                            .append(Text.plain("  " + factions.roster(f).size() + "♟", NamedTextColor.DARK_GRAY)),
                            Text.plain(String.valueOf(factions.score(f)), f.color).decorate(TextDecoration.BOLD)));
                }
            } else {
                lines.add(new SessionBoard.Line(Text.plain("Factions form at " + Factions.MIN_PLAYERS + " players", NamedTextColor.GRAY), null));
            }
        }
        if (twist(Arc.I)) {
            lines.add(SessionBoard.Line.blank());
            Player target = bounty == null ? null : Bukkit.getPlayer(bounty);
            lines.add(new SessionBoard.Line(Text.plain("☠ Bounty", Arc.I.color()),
                    target == null ? Text.plain("soon", NamedTextColor.DARK_GRAY)
                            : Text.plain(target.getName() + " " + clock(bountyUntil - now), NamedTextColor.WHITE)));
        }
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(sessionKills.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        if (!top.isEmpty()) {
            lines.add(SessionBoard.Line.blank());
            lines.add(new SessionBoard.Line(Text.caps("top killers", NamedTextColor.GRAY), null));
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(top.get(i).getKey());
                lines.add(new SessionBoard.Line(Text.plain((i + 1) + ". " + (p.getName() == null ? "someone" : p.getName()), NamedTextColor.WHITE),
                        Text.plain(String.valueOf(top.get(i).getValue()), NamedTextColor.RED)));
            }
        }
        board.update(Text.gradient("⚔ Session", net.kyori.adventure.text.format.TextColor.color(0xECBA44),
                net.kyori.adventure.text.format.TextColor.color(0xC4415B)).decorate(TextDecoration.BOLD), lines);
    }

    // ---------------------------------------------------------------- twists

    /** Puts whatever twist is due in play (and the bar on screen). Safe to call again. */
    private void applyTwist() {
        pauseTwist();
        showBar();
        if (twist(Arc.I)) {
            long minutes = Math.max(1, plugin.getConfig().getLong("arcs.bounty-interval-minutes", 10));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::pickBounty, 20L * 60, 20L * 60 * minutes));
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::trackBounty, 10L, 10L));
        }
        if (twist(Arc.II)) {
            factions.start();
            if (!factions.formed() && !factions.form()) {
                Bukkit.broadcast(Text.plain("The factions need at least " + Factions.MIN_PLAYERS
                        + " players in survival. They form as soon as enough are online.", NamedTextColor.GRAY));
            }
            refreshBar();
        }
    }

    /** Takes the twist out of play for now (it comes back with {@link #applyTwist}). */
    private void pauseTwist() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        factions.stop(false);
        if (bounty != null) {
            Player p = Bukkit.getPlayer(bounty);
            if (p != null) {
                p.setGlowing(false);
            }
        }
    }

    /** Takes the twist out of play for good: results announced, its progress cleared. */
    private void closeTwist(boolean announce) {
        if (factions.active()) {
            factions.stop(announce);
        }
        if (announce && bounty != null) {
            Player p = Bukkit.getPlayer(bounty);
            Bukkit.broadcast(Text.plain("☠ The hunt is called off" + (p == null ? "." : ": " + p.getName() + " is no longer the bounty."), Arc.I.color()));
        }
        factions.reset();
        clearBounty();
        pauseTwist();
    }

    /** Every player kill: the session's tally and the killer's rewards from the twist. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        boolean pvp = killer != null && killer != victim;
        if (session && pvp) {
            sessionKills.merge(killer.getUniqueId(), 1, Integer::sum);
            save();
        }
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
                clearBounty();
            }
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

    private long bountyMillis() {
        return Math.max(1, plugin.getConfig().getLong("arcs.bounty-minutes", 5)) * 60_000L;
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
        bountyUntil = System.currentTimeMillis() + bountyMillis();
        long minutes = bountyMillis() / 60_000L;
        String span = minutes == 1 ? "one minute" : minutes + " minutes";
        target.setGlowing(true);
        target.showTitle(Title.title(Text.plain("YOU ARE THE BOUNTY", Arc.I.color()).decorate(TextDecoration.BOLD),
                Text.caps("survive " + span, NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        Bukkit.broadcast(Text.plain("☠ " + target.getName() + " is the bounty! ", Arc.I.color()).decorate(TextDecoration.BOLD)
                .append(Text.plain("Kill them in the next " + span + " for a reward.", NamedTextColor.WHITE)));
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
            clearBounty();
            return;
        }
        long left = bountyUntil - System.currentTimeMillis();
        if (left <= 0) {
            Bukkit.broadcast(Text.plain("☠ " + target.getName() + " survived the bounty and claims the reward!", Arc.I.color()));
            reward(target, 4);
            clearBounty();
            return;
        }
        target.setGlowing(true);
        target.sendActionBar(Text.caps("you are the bounty · " + clock(left), Arc.I.color()));
        String[] arrows = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};
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
            String arrow = arrows[(int) Math.round(((rel + 360) % 360) / 45.0) % 8];
            p.sendActionBar(Text.plain("☠ Bounty: ", Arc.I.color()).append(Text.plain(target.getName() + "  ", NamedTextColor.WHITE))
                    .append(Text.plain(arrow + " " + (int) distance + "m", Arc.I.color())));
        }
    }

    private void clearBounty() {
        if (bounty != null) {
            Player p = Bukkit.getPlayer(bounty);
            if (p != null) {
                p.setGlowing(false);
            }
        }
        bounty = null;
    }

    // ---------------------------------------------------------------- ceremony and boss bar

    private void ceremony(Arc arc) {
        Title title = Title.title(
                Text.plain(arc.label().toUpperCase(Locale.ROOT), arc.color()).decorate(TextDecoration.BOLD),
                Text.caps(arc.title(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(600), Duration.ofSeconds(4), Duration.ofMillis(1200)));
        Component header = Text.plain("━━━━━━━━  ", arc.color())
                .append(Text.plain(arc.label() + " · " + arc.title(), arc.color()).decorate(TextDecoration.BOLD))
                .append(Text.plain("  ━━━━━━━━", arc.color()));
        List<Component> twistLines = new ArrayList<>();
        if (arc.hasTwist()) {
            twistLines.add(Text.plain("Twist: ", NamedTextColor.GRAY).append(Text.plain(arc.twist(), arc.color()).decorate(TextDecoration.BOLD))
                    .append(Text.plain(session ? "  (in play now)" : "  (during sessions)", NamedTextColor.DARK_GRAY)));
            twistLines.add(Text.plain(arc.twistText(), NamedTextColor.WHITE));
        } else {
            twistLines.add(Text.plain("No twist this arc: forge its weapons and fight.", NamedTextColor.GRAY));
        }
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
            twistLines.forEach(p::sendMessage);
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

    /** "Arc II · Faction War  ● session 9:37 left  ⚔ 1  ⚒ 0  ♆ 1" (kept short enough for small screens). */
    private Component barName() {
        Component name;
        if (current == null) {
            name = Text.plain("Customs", NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
        } else {
            // the twist names the arc while it's in play; otherwise its title does
            String what = twist(current) ? current.twist() : current.title();
            name = Text.plain(current.label() + " · " + what, current.color()).decorate(TextDecoration.BOLD);
        }
        if (session) {
            long now = System.currentTimeMillis();
            String time = sessionEnds > 0 ? clock(sessionEnds - now) + " left" : clock(now - sessionStarted);
            name = name.append(Text.plain("  ● ", NamedTextColor.GREEN)).append(Text.caps("session " + time, NamedTextColor.GREEN));
        } else if (current != null) {
            name = name.append(Text.plain("  ○ ", NamedTextColor.DARK_GRAY)).append(Text.caps("no session", NamedTextColor.GRAY));
        }
        if (factions.active() && factions.formed()) {
            name = name.append(Text.plain("   ", NamedTextColor.WHITE)).append(factions.scoreLine());
        }
        return name;
    }

    private float barProgress() {
        if (session && sessionEnds > 0) {
            float total = sessionEnds - sessionStarted;
            return Math.max(0f, Math.min(1f, (sessionEnds - System.currentTimeMillis()) / total));
        }
        return current == null ? 1f : (current.ordinal() + 1) / (float) Arc.values().length;
    }

    private void showBar() {
        hideBar();
        if ((current == null && !session) || !plugin.getConfig().getBoolean("arcs.boss-bar", true)) {
            return;
        }
        BossBar.Color color = current == null ? BossBar.Color.WHITE : switch (current) {
            case I -> BossBar.Color.YELLOW;
            case II -> BossBar.Color.GREEN;
            case III -> BossBar.Color.PURPLE;
            case IV -> BossBar.Color.RED;
            case V -> BossBar.Color.WHITE;
        };
        bar = BossBar.bossBar(barName(), barProgress(), color, session && sessionEnds > 0 ? BossBar.Overlay.PROGRESS : BossBar.Overlay.NOTCHED_10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
        }
    }

    /** Updates the bar's text and progress (the session clock, Arc II's scores). */
    void refreshBar() {
        if (bar == null && (current != null || session)) {
            showBar();
            return;
        }
        if (bar != null) {
            bar.name(barName());
            bar.progress(barProgress());
            bar.overlay(session && sessionEnds > 0 ? BossBar.Overlay.PROGRESS : BossBar.Overlay.NOTCHED_10);
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
        if (twist(Arc.II) && !factions.formed()) {
            // the factions were waiting for enough players
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (twist(Arc.II) && factions.active() && !factions.formed() && factions.form()) {
                    refreshBar();
                }
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getUniqueId().equals(bounty)) {
            Bukkit.broadcast(Text.plain("☠ The bounty, " + player.getName() + ", fled the server.", Arc.I.color()));
            clearBounty();
        }
    }

    // ---------------------------------------------------------------- status

    /** A few lines describing the current arc and session, for /customs arc. */
    public List<Component> status() {
        List<Component> out = new ArrayList<>();
        if (current == null) {
            out.add(Text.plain("No arc is running: every weapon can be forged and there are no twists.", NamedTextColor.GRAY));
            out.add(Text.plain("Start Arc I with /customs arc start.", NamedTextColor.YELLOW));
        } else {
            long hours = (System.currentTimeMillis() - startedAt) / 3_600_000L;
            out.add(Text.plain(current.label() + " · " + current.title(), current.color()).decorate(TextDecoration.BOLD)
                    .append(Text.plain("  (running for " + (hours < 1 ? "under an hour" : hours + "h") + ")", NamedTextColor.DARK_GRAY)));
            if (current.hasTwist()) {
                out.add(Text.plain("Twist: ", NamedTextColor.GRAY).append(Text.plain(current.twist(), current.color()))
                        .append(Text.plain(twist(current) ? "  (in play)" : "  (waits for a session)", NamedTextColor.DARK_GRAY)));
                out.add(Text.plain(current.twistText(), NamedTextColor.WHITE));
            } else {
                out.add(Text.plain("No twist this arc.", NamedTextColor.GRAY));
            }
            if (factions.active() && factions.formed()) {
                out.add(Text.plain("Faction points: ", NamedTextColor.GRAY).append(factions.scoreLine()));
            }
            Arc next = current.next();
            out.add(next == null ? Text.plain("This is the last arc.", NamedTextColor.GRAY)
                    : Text.plain("Next: " + next.label() + " · " + next.title() + " (/customs arc next)", NamedTextColor.YELLOW));
        }
        out.add(sessionLine());
        return out;
    }

    /** "Session: running for 12:04 (ends in 47:56)" or "No session is running". */
    public Component sessionLine() {
        if (!session) {
            return Text.plain("○ No session is running (an admin starts one with /customs session start).", NamedTextColor.GRAY);
        }
        long now = System.currentTimeMillis();
        return Text.plain("● Session running for " + clock(now - sessionStarted)
                + (sessionEnds > 0 ? " (ends in " + clock(sessionEnds - now) + ")" : " (until an admin ends it)"), NamedTextColor.GREEN);
    }

    /** Lines for /customs session. */
    public List<Component> sessionStatus() {
        List<Component> out = new ArrayList<>();
        out.add(sessionLine());
        if (session) {
            List<Map.Entry<UUID, Integer>> top = new ArrayList<>(sessionKills.entrySet());
            top.sort((a, b) -> b.getValue() - a.getValue());
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(top.get(i).getKey());
                out.add(Text.plain("  " + (i + 1) + ". " + (p.getName() == null ? "someone" : p.getName()) + "  " + top.get(i).getValue()
                        + (top.get(i).getValue() == 1 ? " kill" : " kills"), NamedTextColor.GRAY));
            }
            if (current != null && twist(current)) {
                out.add(Text.plain("Twist in play: ", NamedTextColor.GRAY).append(Text.plain(current.twist(), current.color())));
            }
        }
        return out;
    }
}
