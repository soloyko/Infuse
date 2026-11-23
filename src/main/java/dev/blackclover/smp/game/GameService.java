package dev.blackclover.smp.game;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.config.Settings;
import dev.blackclover.smp.core.LibraryPlacer;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** /start, the grace period and resetting the game. */
public final class GameService implements Listener {

    private static final int MAX_ATTEMPTS = 12;

    private final BlackCloverPlugin plugin;
    private final GrimoireService grimoires;
    private final LibraryBuilder builder;
    private final AltarService altars;
    private final BossBar graceBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private long graceTotalMillis;
    private boolean graceAnnounced;
    private boolean building;
    private final Random random = new Random();

    public GameService(BlackCloverPlugin plugin, GrimoireService grimoires, LibraryBuilder builder, AltarService altars) {
        this.plugin = plugin;
        this.grimoires = grimoires;
        this.builder = builder;
        this.altars = altars;
    }

    private GameState game() {
        return plugin.game();
    }

    public boolean isGrace() {
        return game().isGrace(System.currentTimeMillis());
    }

    public void startTasks() {
        long remaining = game().graceEndMillis() - System.currentTimeMillis();
        graceTotalMillis = Math.max(remaining, 1);
        graceAnnounced = !isGrace();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickGrace, 20L, 20L);
    }

    // ---------------------------------------------------------------- start

    public void start(CommandSender sender, World world, int border, long graceSeconds) {
        Settings s = plugin.settings();
        if (game().isStarted() || building) {
            Msg.send(sender, "<red>The game has already started. Use <white>/bcadmin reset confirm</white> to start over.");
            return;
        }
        int minimum = LibraryPlacer.minimumBorder(s.libraryMinFromOrigin(), s.libraryBorderMargin());
        if (border < minimum) {
            Msg.send(sender, "<red>The border must be at least <white><min></white> so libraries can be <white><dist></white>+ blocks from 0,0.",
                    Msg.arg("min", minimum), Msg.arg("dist", s.libraryMinFromOrigin()));
            return;
        }
        // Every craftable grimoire must be waiting at home for a fresh game.
        for (GrimoireType type : GrimoireType.craftables()) {
            if (plugin.registry().get(type).status() != GrimoireStatus.LIBRARY) {
                grimoires.returnHome(type, "new game");
            }
        }
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(border);

        long now = System.currentTimeMillis();
        game().setStarted(true);
        game().setWorld(world.getName());
        game().setBorderSize(border);
        game().setGraceEndMillis(now + graceSeconds * 1000L);
        graceTotalMillis = Math.max(1, graceSeconds * 1000L);
        graceAnnounced = graceSeconds <= 0;
        plugin.saveAsync();

        Msg.send(sender, "<green>Game started. Border <white><border></white>, grace <white><grace>s</white>. Building <white>13</white> libraries…",
                Msg.arg("border", border), Msg.arg("grace", graceSeconds));
        List<LibraryPlacer.Point> points = LibraryPlacer.place(GrimoireType.craftables().size(), border,
                s.libraryMinFromOrigin(), s.libraryBorderMargin(), s.librarySpacing(), random, List.of());
        building = true;
        buildNext(sender, world, 0, points, new ArrayList<>(points), 0);
    }

    private void buildNext(CommandSender sender, World world, int index, List<LibraryPlacer.Point> points,
                           List<LibraryPlacer.Point> used, int attempt) {
        List<GrimoireType> types = GrimoireType.craftables();
        if (index >= types.size()) {
            building = false;
            plugin.saveAsync();
            Msg.send(sender, "<green>All libraries are built.");
            Msg.titleAll("<#4caf63><b>ᴛʜᴇ ʟɪʙʀᴀʀɪᴇꜱ ʜᴀᴠᴇ ᴀᴘᴘᴇᴀʀᴇᴅ</b>", "<gray>13 ɢʀɪᴍᴏɪʀᴇꜱ ᴡᴀɪᴛ ʙᴇʏᴏɴᴅ 1,000 ʙʟᴏᴄᴋꜱ");
            Msg.broadcast("<gray>Thirteen grimoire libraries have appeared across the world. Find one, bring the ingredients, and claim your magic.");
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
            }
            return;
        }
        GrimoireType type = types.get(index);
        LibraryPlacer.Point point = points.get(index);
        boolean force = attempt >= MAX_ATTEMPTS;
        CompletableFuture<Optional<Library>> future = builder.build(world, type, point, force);
        future.whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!game().isStarted()) {
                building = false;
                return;
            }
            if (error != null) {
                plugin.getLogger().severe("Library build failed for " + type + ": " + error);
            }
            if (result != null && result.isPresent()) {
                Library lib = result.get();
                game().libraries().put(type, lib);
                altars.onLibraryBuilt(lib);
                plugin.getLogger().info("Built " + type.displayName() + " library at " + lib.altarX() + " " + lib.altarY() + " " + lib.altarZ());
                Msg.send(sender, "<gray>Built <c><name></c> library <dark_gray>(<n>/13)</dark_gray> at <white><x> <y> <z>",
                        Msg.color("c", type.rgb()), Msg.arg("name", type.displayName()), Msg.arg("n", index + 1),
                        Msg.arg("x", lib.altarX()), Msg.arg("y", lib.altarY()), Msg.arg("z", lib.altarZ()));
                buildNext(sender, world, index + 1, points, used, 0);
            } else {
                Settings s = plugin.settings();
                LibraryPlacer.Point replacement = LibraryPlacer.place(1, game().borderSize(), s.libraryMinFromOrigin(),
                        s.libraryBorderMargin(), s.librarySpacing(), random, used).get(0);
                used.add(replacement);
                points.set(index, replacement);
                buildNext(sender, world, index, points, used, attempt + 1);
            }
        }));
    }

    // ---------------------------------------------------------------- grace

    private void tickGrace() {
        long now = System.currentTimeMillis();
        if (game().isGrace(now)) {
            long remaining = game().graceEndMillis() - now;
            graceBar.name(Msg.mm("<green>ɢʀᴀᴄᴇ ᴘᴇʀɪᴏᴅ</green>  <gray>ᴘᴠᴘ ᴏꜰꜰ</gray>  <white><t></white>", Msg.arg("t", format(remaining))));
            graceBar.progress((float) Math.max(0, Math.min(1, remaining / (double) graceTotalMillis)));
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.showBossBar(graceBar);
            }
            return;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.hideBossBar(graceBar);
        }
        if (game().isStarted() && !graceAnnounced) {
            graceAnnounced = true;
            Msg.titleAll("<red><b>ᴘᴠᴘ ᴇɴᴀʙʟᴇᴅ</b>", "<gray>ᴛʜᴇ ɢʀᴀᴄᴇ ᴘᴇʀɪᴏᴅ ɪꜱ ᴏᴠᴇʀ");
            Msg.broadcast("<red>The grace period is over. PvP is now enabled.");
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1f);
            }
        }
    }

    private static String format(long millis) {
        long s = Math.max(0, millis / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    public void endGrace() {
        if (isGrace()) {
            game().setGraceEndMillis(System.currentTimeMillis());
            plugin.saveAsync();
            tickGrace();
        }
    }

    public long graceRemainingMillis() {
        return Math.max(0, game().graceEndMillis() - System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isGrace()) {
            event.getPlayer().showBossBar(graceBar);
        }
    }

    // ---------------------------------------------------------------- reset

    /** Clears the game so /start can run again. Old library buildings stay as ruins. */
    public void reset(CommandSender sender) {
        altars.removeAllDisplays();
        for (GrimoireState s : List.copyOf(plugin.registry().all())) {
            grimoires.returnHome(s.type(), "game reset");
        }
        game().clear();
        plugin.players().values().forEach(dev.blackclover.smp.mage.PlayerData::clearXp);
        building = false;
        graceAnnounced = true;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.hideBossBar(graceBar);
        }
        plugin.saveAsync();
        Msg.send(sender, "<yellow>The game was reset. Run <white>/start</white> to begin a new one.");
    }
}
