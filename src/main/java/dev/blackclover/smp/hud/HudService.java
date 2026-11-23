package dev.blackclover.smp.hud;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.core.LevelCurve;
import dev.blackclover.smp.core.WizardKing;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.squad.Squad;
import dev.blackclover.smp.squad.SquadRoster;
import dev.blackclover.smp.squad.SquadService;
import dev.blackclover.smp.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything on screen that isn't a menu: the sidebar, squad name tags, the tab list, chat badges,
 * the Wizard King's crown and the Demon Possessed's health bar.
 */
public final class HudService implements Listener {

    private static final int LINES = 15;
    private static final String TEAM_PREFIX = "bc_";
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xFFD34E), 0.8f);

    private record TeamSpec(Component prefix, NamedTextColor color, Set<String> entries) {
    }

    private static final class Board {
        final Scoreboard scoreboard;
        Objective sidebar;
        final String[] lines = new String[LINES];
        final Map<String, TeamSpec> teams = new HashMap<>();

        Board(Scoreboard scoreboard) {
            this.scoreboard = scoreboard;
        }
    }

    private final BlackCloverPlugin plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, Component> listNames = new HashMap<>();
    private final Map<UUID, Component> chatPrefix = new ConcurrentHashMap<>();
    private final Map<UUID, Component> chatSuffix = new ConcurrentHashMap<>();
    private final BossBar demonBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private final Set<UUID> seeingDemon = new HashSet<>();
    private Map<String, TeamSpec> desiredTeams = Map.of();
    private Component header = Component.empty();
    private Component footer = Component.empty();

    public HudService(BlackCloverPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::slowTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::fastTick, 25L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::demonTick, 30L, 5L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            attach(p);
        }
    }

    public void removeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(demonBar);
            if (boards.containsKey(p.getUniqueId())) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    /** The Wizard King's UUID, if someone holds the crown. */
    public Optional<UUID> king() {
        return Optional.ofNullable(plugin.game().wizardKing());
    }

    // ---------------------------------------------------------------- players

    private void attach(Player p) {
        if (!plugin.settings().hudSidebar() && !plugin.settings().hudNametags()) {
            return;
        }
        Board board = new Board(Bukkit.getScoreboardManager().getNewScoreboard());
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board.scoreboard);
        syncTeams(board);
        updateSidebar(p, board);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, this::slowTick);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        boards.remove(id);
        listNames.remove(id);
        chatPrefix.remove(id);
        chatSuffix.remove(id);
        seeingDemon.remove(id);
    }

    public void toggleSidebar(Player p) {
        PlayerData pd = plugin.mage().data(p.getUniqueId());
        pd.setHudHidden(!pd.hudHidden());
        Board board = boards.get(p.getUniqueId());
        if (board != null) {
            updateSidebar(p, board);
        }
        Msg.send(p, pd.hudHidden() ? "<gray>Sidebar hidden. <white>/magic hud</white> brings it back."
                : "<gray>Sidebar shown. <white>/magic hud</white> hides it.");
    }

    // ---------------------------------------------------------------- ticks

    private void fastTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(p.getUniqueId());
            if (board != null) {
                updateSidebar(p, board);
            }
        }
        king().map(Bukkit::getPlayer).ifPresent(this::crown);
    }

    private void slowTick() {
        updateKing();
        desiredTeams = plugin.settings().hudNametags() ? computeTeams() : Map.of();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(p.getUniqueId());
            if (board != null) {
                syncTeams(board);
            }
        }
        updateTabAndChat();
    }

    // ---------------------------------------------------------------- wizard king

    private void updateKing() {
        UUID demon = plugin.demonRef().get();
        List<WizardKing.Candidate> candidates = new ArrayList<>();
        for (GrimoireState s : plugin.registry().all()) {
            if (s.status() == GrimoireStatus.HELD && s.holder() != null && !s.holder().equals(demon)) {
                PlayerData pd = plugin.mage().data(s.holder());
                candidates.add(new WizardKing.Candidate(s.holder(), plugin.mage().level(s.holder(), s.type()), pd.xp(s.type())));
            }
        }
        UUID current = plugin.game().wizardKing();
        UUID next = WizardKing.pick(candidates, current, plugin.settings().kingMinLevel()).orElse(null);
        if (Objects.equals(current, next)) {
            return;
        }
        plugin.game().setWizardKing(next);
        plugin.saveAsync();
        if (next == null) {
            Msg.broadcast("<gold>♛</gold> <gray>The Wizard King's throne stands empty.");
            return;
        }
        String name = plugin.mage().data(next).name();
        Msg.broadcast("<gold>♛</gold> <white><k></white> <gold>has been crowned Wizard King!</gold> <dark_gray>(highest level mage)",
                Msg.arg("k", name));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 0.9f);
        }
        Player k = Bukkit.getPlayer(next);
        if (k != null) {
            Msg.title(k, "<gold><b>♛ " + Msg.sc("Wizard King") + "</b>", "<gray>" + Msg.sc("the strongest mage in the kingdom"));
        }
    }

    /** A small golden crown of light above the king's head. */
    private void crown(Player k) {
        if (k.isDead() || k.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return;
        }
        Location top = k.getLocation().add(0, k.getHeight() + 0.45, 0);
        long t = k.getWorld().getGameTime();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + t * 0.05;
            k.getWorld().spawnParticle(Particle.DUST, top.clone().add(Math.cos(a) * 0.32, (i % 2) * 0.12, Math.sin(a) * 0.32), 1, 0, 0, 0, 0, GOLD);
        }
    }

    // ---------------------------------------------------------------- sidebar

    private static String bar(double fraction, int cells) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * cells);
        return "<green>" + "▮".repeat(filled) + "</green><dark_gray>" + "▮".repeat(cells - filled) + "</dark_gray>";
    }

    private static String clock(long millis) {
        long s = Math.max(0, millis / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    private List<String> sidebarLines(Player p) {
        List<String> l = new ArrayList<>();
        PlayerData pd = plugin.mage().data(p.getUniqueId());
        l.add("");
        Optional<GrimoireType> carried = plugin.grimoires().carried(p);
        if (carried.isPresent()) {
            GrimoireType type = carried.get();
            String c = Msg.hex(type.rgb());
            LevelCurve curve = plugin.settings().levelCurve();
            int xp = pd.xp(type);
            int level = curve.levelForXp(xp);
            double progress = level >= curve.cap() ? 1 : (xp - curve.totalXpForLevel(level)) / (double) curve.xpToNext(level);
            l.add(" <gray>" + Msg.sc("Grimoire") + " <" + c + "><b>" + Msg.sc(type.displayName()));
            l.add(" <gray>" + Msg.sc("Level") + " <white>" + level + "</white> " + (level >= curve.cap() ? "<gold>" + Msg.sc("max") : bar(progress, 8)));
            if (type.usesMana()) {
                l.add(" <gray>" + Msg.sc("Mana") + " <aqua>" + (int) plugin.casting().mana(p.getUniqueId()) + "</aqua><dark_gray>/"
                        + (int) plugin.casting().maxMana(p));
            } else {
                l.add(" <gray>" + Msg.sc("Mana") + " <dark_gray>" + Msg.sc("none needed"));
            }
        } else {
            l.add(" <gray>" + Msg.sc("Grimoire") + " <dark_gray>" + Msg.sc("none"));
            l.add(" <dark_gray>" + Msg.sc("find a library tower"));
        }
        l.add("");
        l.add(" <gray>" + Msg.sc("Kills") + " <white>" + pd.kills() + "</white>  " + Msg.sc("Deaths") + " <white>" + pd.deaths());
        l.add("");
        Optional<Squad> squad = plugin.squads().squadOf(p);
        if (squad.isPresent()) {
            Squad s = squad.get();
            l.add(" <gray>" + Msg.sc("Squad") + " <" + s.color() + ">" + Msg.sc(s.name()));
            l.add(" <gold>★ " + s.stars() + "</gold> <dark_gray>" + Msg.sc("rank") + " #" + plugin.squads().roster().rank(s));
        } else {
            l.add(" <gray>" + Msg.sc("Squad") + " <dark_gray>" + Msg.sc("none"));
        }
        l.add("");
        UUID king = plugin.game().wizardKing();
        l.add(king == null ? " <gold>♛</gold> <dark_gray>" + Msg.sc("no wizard king") : " <gold>♛ " + plugin.mage().data(king).name());
        long now = System.currentTimeMillis();
        if (!plugin.game().isStarted()) {
            l.add(" <gray>" + Msg.sc("waiting for start"));
        } else if (plugin.game().isGrace(now)) {
            l.add(" <green>" + Msg.sc("PvP in") + " <white>" + clock(plugin.game().graceEndMillis() - now));
        } else {
            l.add(" <red>⚔ " + Msg.sc("PvP on"));
        }
        String footerLine = plugin.settings().hudFooter();
        if (footerLine != null && !footerLine.isBlank()) {
            l.add(" " + footerLine);
        }
        return l.subList(0, Math.min(LINES, l.size()));
    }

    private void updateSidebar(Player p, Board board) {
        boolean show = plugin.settings().hudSidebar() && !plugin.mage().data(p.getUniqueId()).hudHidden();
        if (!show) {
            if (board.sidebar != null) {
                board.sidebar.unregister();
                board.sidebar = null;
                java.util.Arrays.fill(board.lines, null);
            }
            return;
        }
        if (board.sidebar == null) {
            board.sidebar = board.scoreboard.registerNewObjective("bc_sidebar", Criteria.DUMMY,
                    Msg.mm("<#4caf63><b>☘ " + Msg.sc("Black Clover") + "</b>"));
            board.sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
            board.sidebar.numberFormat(NumberFormat.blank());
        }
        List<String> lines = sidebarLines(p);
        for (int i = 0; i < LINES; i++) {
            String entry = "§" + Integer.toHexString(i % 16) + "§" + (i >= 16 ? "r§r" : "r");
            String text = i < lines.size() ? lines.get(i) : null;
            if (Objects.equals(text, board.lines[i])) {
                continue;
            }
            board.lines[i] = text;
            if (text == null) {
                board.scoreboard.resetScores(entry);
                continue;
            }
            Score score = board.sidebar.getScore(entry);
            score.setScore(LINES - i);
            score.customName(Msg.mm(text));
        }
    }

    // ---------------------------------------------------------------- name tags

    private Map<String, TeamSpec> computeTeams() {
        Map<String, TeamSpec> teams = new LinkedHashMap<>();
        UUID king = plugin.game().wizardKing();
        UUID demon = plugin.demonRef().get();
        for (Squad s : plugin.squads().roster().all()) {
            Set<String> entries = new HashSet<>();
            for (UUID m : s.members()) {
                if (!m.equals(king) && !m.equals(demon)) {
                    entries.add(plugin.squads().nameOf(m));
                }
            }
            teams.put(TEAM_PREFIX + s.id().toString().substring(0, 8),
                    new TeamSpec(Msg.mm("<" + s.color() + ">[" + s.name() + "]</" + s.color() + "> "), SquadService.color(s), entries));
        }
        if (king != null && !king.equals(demon)) {
            Optional<Squad> ks = plugin.squads().roster().squadOf(king);
            Component prefix = Msg.mm("<gold>♛</gold> " + ks.map(s -> "<" + s.color() + ">[" + s.name() + "]</" + s.color() + "> ").orElse(""));
            teams.put(TEAM_PREFIX + "king", new TeamSpec(prefix, ks.map(SquadService::color).orElse(NamedTextColor.GOLD),
                    Set.of(plugin.mage().data(king).name())));
        }
        if (demon != null) {
            teams.put(TEAM_PREFIX + "demon", new TeamSpec(Msg.mm("<dark_red>☠</dark_red> "), NamedTextColor.DARK_RED,
                    Set.of(plugin.squads().nameOf(demon))));
        }
        return teams;
    }

    private void syncTeams(Board board) {
        Scoreboard sb = board.scoreboard;
        for (Team t : Set.copyOf(sb.getTeams())) {
            String name = t.getName();
            if (name.startsWith(TEAM_PREFIX) && !desiredTeams.containsKey(name)) {
                t.unregister();
                board.teams.remove(name);
            }
        }
        for (Map.Entry<String, TeamSpec> e : desiredTeams.entrySet()) {
            TeamSpec want = e.getValue();
            TeamSpec have = board.teams.get(e.getKey());
            if (want.equals(have)) {
                continue;
            }
            Team t = sb.getTeam(e.getKey());
            if (t == null) {
                t = sb.registerNewTeam(e.getKey());
                t.setCanSeeFriendlyInvisibles(true);
            }
            if (have == null || !want.prefix().equals(have.prefix())) {
                t.prefix(want.prefix());
            }
            if (have == null || want.color() != have.color()) {
                t.color(want.color());
            }
            Set<String> current = new HashSet<>(t.getEntries());
            for (String entry : current) {
                if (!want.entries().contains(entry)) {
                    t.removeEntry(entry);
                }
            }
            for (String entry : want.entries()) {
                if (!current.contains(entry)) {
                    t.addEntry(entry);
                }
            }
            board.teams.put(e.getKey(), want);
        }
    }

    // ---------------------------------------------------------------- tab list and chat

    private void updateTabAndChat() {
        UUID king = plugin.game().wizardKing();
        UUID demon = plugin.demonRef().get();
        SquadRoster roster = plugin.squads().roster();
        int online = Bukkit.getOnlinePlayers().size();
        long now = System.currentTimeMillis();
        String pvp = !plugin.game().isStarted() ? "<gray>" + Msg.sc("waiting for start")
                : plugin.game().isGrace(now) ? "<green>" + Msg.sc("grace") + " <white>" + clock(plugin.game().graceEndMillis() - now)
                : "<red>⚔ " + Msg.sc("PvP on");
        String kingText = king == null ? "<dark_gray>" + Msg.sc("none yet") : "<gold>" + plugin.mage().data(king).name();
        List<Squad> ranking = roster.ranking();
        String topSquad = ranking.isEmpty() ? "<dark_gray>" + Msg.sc("none yet")
                : SquadService.tag(ranking.get(0)) + " <gold>★ " + ranking.get(0).stars();
        header = Msg.mm("\n<#4caf63><b>☘  " + Msg.sc("Black Clover SMP") + "  ☘</b></#4caf63>\n<dark_gray>"
                + Msg.sc("magic knights online") + " <white>" + online + "\n");
        footer = Msg.mm("\n<gray>" + Msg.sc("Wizard King") + " <gold>♛</gold> " + kingText
                + "   <dark_gray>·</dark_gray>   <gray>" + Msg.sc("Top squad") + " " + topSquad
                + "\n" + pvp + "\n");

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Optional<Squad> squad = roster.squadOf(id);
            Optional<GrimoireType> carried = plugin.grimoires().carried(p);
            StringBuilder pre = new StringBuilder();
            if (id.equals(king)) {
                pre.append("<gold>♛</gold> ");
            }
            if (id.equals(demon)) {
                pre.append("<dark_red>☠</dark_red> ");
            }
            String squadTag = squad.map(s -> "<" + s.color() + ">[" + s.name() + "]</" + s.color() + "> ").orElse("");
            String nameColor = id.equals(demon) ? "dark_red" : squad.map(Squad::color).orElse("white");
            String levelBadge = carried.map(t -> " <dark_gray>[<" + Msg.hex(t.rgb()) + ">" + plugin.mage().level(id, t)
                    + "</" + Msg.hex(t.rgb()) + ">]</dark_gray>").orElse("");
            chatPrefix.put(id, Msg.mm(pre + squadTag));
            chatSuffix.put(id, Msg.mm(levelBadge));
            if (plugin.settings().hudTabList()) {
                Component listName = Msg.mm(pre + squadTag + "<" + nameColor + "><n></" + nameColor + ">" + levelBadge, Msg.arg("n", p.getName()));
                if (!listName.equals(listNames.get(id))) {
                    listNames.put(id, listName);
                    p.playerListName(listName);
                }
                p.sendPlayerListHeaderAndFooter(header, footer);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.settings().chatFormat()) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        Component prefix = chatPrefix.getOrDefault(id, Component.empty());
        Component suffix = chatSuffix.getOrDefault(id, Component.empty());
        Component arrow = Msg.mm(" <dark_gray>»</dark_gray> ");
        event.renderer((source, displayName, message, viewer) -> Component.empty()
                .append(prefix).append(displayName).append(suffix).append(arrow).append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }

    // ---------------------------------------------------------------- demon health bar

    private void demonTick() {
        UUID id = plugin.demonRef().get();
        Player demon = id == null ? null : Bukkit.getPlayer(id);
        if (demon == null || demon.isDead()) {
            for (UUID v : Set.copyOf(seeingDemon)) {
                Player p = Bukkit.getPlayer(v);
                if (p != null) {
                    p.hideBossBar(demonBar);
                }
            }
            seeingDemon.clear();
            return;
        }
        AttributeInstance max = demon.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20 : max.getValue();
        demonBar.progress((float) Math.max(0, Math.min(1, demon.getHealth() / maxHp)));
        demonBar.name(Msg.mm("<dark_red><b>☠ " + Msg.sc("Demon Possessed") + "</b></dark_red> <dark_gray>·</dark_gray> <white><n></white> <red>"
                + (int) Math.ceil(demon.getHealth()) + " ❤", Msg.arg("n", demon.getName())));
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean near = p.getWorld().equals(demon.getWorld()) && p.getLocation().distanceSquared(demon.getLocation()) <= 64 * 64;
            if (near && seeingDemon.add(p.getUniqueId())) {
                p.showBossBar(demonBar);
            } else if (!near && seeingDemon.remove(p.getUniqueId())) {
                p.hideBossBar(demonBar);
            }
        }
    }
}
