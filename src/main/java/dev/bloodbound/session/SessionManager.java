package dev.bloodbound.session;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.config.Messages;
import dev.bloodbound.core.Durations;
import dev.bloodbound.data.SessionStore;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.OptionalLong;

import static dev.bloodbound.config.Messages.comp;
import static dev.bloodbound.config.Messages.text;

/** Admin-run sessions: the window where altars, abilities and blood are live. */
public final class SessionManager implements Listener {
    private final BloodboundPlugin plugin;
    private final SessionStore store;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
    private BukkitTask ticker;

    public SessionManager(BloodboundPlugin plugin, SessionStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public boolean isActive() {
        return store.active;
    }

    /** Remaining milliseconds, or -1 for an open-ended session. */
    public long remainingMillis() {
        return store.endsAt <= 0 ? -1 : Math.max(0, store.endsAt - System.currentTimeMillis());
    }

    public boolean start(OptionalLong duration) {
        if (store.active) return false;
        long now = System.currentTimeMillis();
        store.active = true;
        store.startedAt = now;
        store.endsAt = duration.isPresent() ? now + duration.getAsLong() : 0;
        store.saveAsync();

        Messages m = plugin.messages();
        if (duration.isPresent()) {
            m.send(Bukkit.getServer(), "session.started-timed", text("time", Durations.format(duration.getAsLong())));
        } else {
            m.send(Bukkit.getServer(), "session.started");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            m.title(p, "session.title", "session.subtitle");
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.4f);
        }
        beginTicking();
        return true;
    }

    public boolean stop() {
        if (!store.active) return false;
        plugin.events().stopCurrent(true);
        store.active = false;
        store.endsAt = 0;
        store.saveAsync();
        endTicking();
        plugin.killCooldowns().clear();

        Messages m = plugin.messages();
        m.send(Bukkit.getServer(), "session.ended");
        for (Player p : Bukkit.getOnlinePlayers()) {
            m.title(p, "session.end-title", "session.end-subtitle");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.7f);
        }
        return true;
    }

    /** Called on enable: resumes a session that survived a restart. */
    public void resume() {
        store.load();
        if (!store.active) return;
        if (store.endsAt > 0 && System.currentTimeMillis() >= store.endsAt) {
            store.active = false;
            store.saveAsync();
            return;
        }
        beginTicking();
    }

    /** Called on disable: keeps the session state on disk but removes live UI. */
    public void shutdown() {
        endTicking();
        store.saveNow();
    }

    public void refreshBar() {
        if (!store.active) return;
        Messages m = plugin.messages();
        long remaining = remainingMillis();
        String time = remaining < 0 ? m.raw("session.bossbar-open") : Durations.format(remaining);
        Component event = plugin.events().current()
                .map(type -> m.get("session.bossbar-event", comp("event", plugin.events().displayName(type))))
                .orElse(Component.empty());
        bar.name(m.get("session.bossbar", text("time", time), comp("event", event)));
        if (remaining >= 0 && store.endsAt > store.startedAt) {
            bar.progress((float) Math.max(0, Math.min(1, remaining / (double) (store.endsAt - store.startedAt))));
        } else {
            bar.progress(1f);
        }
        bar.color(plugin.events().current().map(plugin.events()::barColor).orElse(BossBar.Color.RED));
    }

    private void beginTicking() {
        endTicking();
        refreshBar();
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (store.endsAt > 0 && System.currentTimeMillis() >= store.endsAt) {
                stop();
                return;
            }
            refreshBar();
        }, 20L, 20L);
    }

    private void endTicking() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (store.active) event.getPlayer().showBossBar(bar);
    }
}
