package me.infuse.craft;

import me.infuse.InfusePlugin;
import me.infuse.effect.EffectType;
import me.infuse.effect.ItemFactory;
import me.infuse.util.Fx;
import me.infuse.util.Msg;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import me.infuse.util.Util;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Ten-minute rituals that end by dropping an augmented effect at the crafting location. */
public final class RitualManager {
    private final InfusePlugin plugin;
    private final List<Ritual> rituals = new ArrayList<>();
    private BukkitTask task;
    private long ticks;

    private static final class Ritual {
        final EffectType type;
        final Location location;
        final long end;
        final long total;
        final BossBar bar;
        org.bukkit.entity.ItemDisplay bottle;
        org.bukkit.entity.TextDisplay label;

        Ritual(EffectType type, Location location, long end, long total) {
            this.type = type;
            this.location = location;
            this.end = end;
            this.total = total;
            this.bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        }
    }

    public RitualManager(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        load();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        save();
        for (Ritual r : rituals) {
            hideBar(r);
            removeDisplays(r);
        }
        rituals.clear();
    }

    public boolean isActive(EffectType type) {
        return rituals.stream().anyMatch(r -> r.type == type);
    }

    public void begin(EffectType type, Location location) {
        long total = plugin.getConfig().getLong("ritual.per-effect-seconds." + type.id(),
                plugin.getConfig().getLong("ritual.duration-seconds", 600)) * 1000L;
        Location loc = location.getBlock().getLocation();
        Ritual r = new Ritual(type, loc, System.currentTimeMillis() + total, total);
        rituals.add(r);
        updateBar(r);
        if (plugin.getConfig().getBoolean("ritual.bossbar", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(r.bar);
        }
        save();
        Msg.broadcast("ritual-start", Msg.comp("effect", ItemFactory.name(type, true)),
                Msg.text("coords", Util.coords(loc)), Msg.text("time", Util.time(total)));
        loc.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_SPAWN, 2f, 1.2f);
    }

    public void showBars(Player p) {
        if (!plugin.getConfig().getBoolean("ritual.bossbar", true)) return;
        for (Ritual r : rituals) p.showBossBar(r.bar);
    }

    private void tick() {
        ticks++;
        long now = System.currentTimeMillis();
        Iterator<Ritual> it = rituals.iterator();
        while (it.hasNext()) {
            Ritual r = it.next();
            if (now >= r.end) {
                it.remove();
                finish(r);
                save();
                continue;
            }
            if (ticks % 2 == 0) updateBar(r);
            particles(r);
            displays(r);
        }
    }

    private void updateBar(Ritual r) {
        long rem = Math.max(0, r.end - System.currentTimeMillis());
        r.bar.name(Msg.get("ritual-bossbar", Msg.comp("effect", ItemFactory.name(r.type, true)),
                Msg.text("time", Util.time(rem)), Msg.text("coords", Util.coords(r.location))));
        r.bar.progress(Math.max(0f, Math.min(1f, rem / (float) r.total)));
    }

    private void particles(Ritual r) {
        World w = r.location.getWorld();
        if (w == null || !w.isChunkLoaded(r.location.getBlockX() >> 4, r.location.getBlockZ() >> 4)) return;
        int rgb = r.type.rgb();
        int light = Fx.lighten(rgb, 0.6);
        Location base = r.location.clone().add(0.5, 0.1, 0.5);
        Location c = base.clone().add(0, 1.1, 0);
        long rem = r.end - System.currentTimeMillis();
        double progress = 1 - Math.max(0, rem) / (double) r.total;
        double t = ticks * 0.25;

        // Rotating double rune circle on the ground
        Fx.ring(base, rgb, 3.0, 36, 1.1f);
        for (int i = 0; i < 6; i++) {
            double a = t + i * Math.PI / 3;
            w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2), 2, 0.05, 0, 0.05, 0,
                    Fx.dust(light, 1.4f));
            w.spawnParticle(Particle.ENCHANT, base.clone().add(Math.cos(-a) * 3.0, 0.3, Math.sin(-a) * 3.0), 3, 0, 0.2, 0, 0.5);
        }
        // Three orbiting motes that rise as the ritual progresses
        double orbitY = 0.2 + progress * 2.5;
        for (int i = 0; i < 3; i++) {
            double a = t * 1.6 + i * Math.PI * 2 / 3;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(Math.cos(a) * 1.4, orbitY, Math.sin(a) * 1.4),
                    3, 0.05, 0.05, 0.05, 0, Fx.fade(rgb, light, 1.6f));
        }
        // Core
        w.spawnParticle(Particle.ENCHANT, c, 25, 0.5, 0.5, 0.5, 1.2);
        w.spawnParticle(Particle.DUST, c, 4, 0.15, 0.15, 0.15, 0, Fx.dust(rgb, 2f));
        // Beam to the sky, brighter near the end
        int beam = progress > 0.9 ? 20 : 8;
        for (int i = 0; i < beam; i++) {
            w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1 + i * 2.5 + (ticks % 5) * 0.5, 0), 1, 0.02, 0.2, 0.02, 0);
        }
        // Heartbeat every 5 seconds, faster in the last minute
        int beatEvery = rem < 60_000 ? 4 : 10;
        if (ticks % beatEvery == 0) {
            w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.6f + (float) progress);
            Fx.shockwave(base, rgb, 3.5);
        }
        // Final countdown for nearby players
        if (rem <= 10_000 && ticks % 2 == 0) {
            long s = (rem + 999) / 1000;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(c) > 64 * 64) continue;
                p.showTitle(Title.title(Component.text(String.valueOf(s), r.type.color()),
                        ItemFactory.name(r.type, true),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f + (10 - s) * 0.12f);
            }
        }
    }

    private void finish(Ritual r) {
        hideBar(r);
        removeDisplays(r);
        World w = r.location.getWorld();
        if (w == null) return;
        Location drop = r.location.clone().add(0.5, 1.2, 0.5);
        Item item = w.dropItem(drop, plugin.items().create(r.type, true));
        item.setVelocity(new Vector());
        item.setGlowing(true);
        item.setUnlimitedLifetime(true);
        item.setInvulnerable(true);
        item.setCanMobPickup(false);
        w.strikeLightningEffect(r.location);
        Fx.firework(drop.clone().add(0, 1.5, 0), r.type.rgb(), true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Fx.firework(drop.clone().add(1.2, 3, 0), r.type.rgb(), false), 6L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> Fx.firework(drop.clone().add(-1.2, 3.5, 0.8), r.type.rgb(), false), 12L);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, drop, 120, 0.5, 1.2, 0.5, 0.6);
        Fx.shockwave(r.location.clone().add(0.5, 0, 0.5), r.type.rgb(), 6);
        w.playSound(drop, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(drop) > 64 * 64) continue;
            var eff = Msg.comp("effect", ItemFactory.name(r.type, true));
            p.showTitle(Title.title(Msg.get("ritual-complete-title", eff), Msg.get("ritual-complete-subtitle", eff),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(800))));
        }
        Msg.broadcast("ritual-complete", Msg.comp("effect", ItemFactory.name(r.type, true)),
                Msg.text("coords", Util.coords(r.location)));
    }

    /** Floating, spinning augmented bottle with a live name + countdown above the ritual. */
    private void displays(Ritual r) {
        if (!plugin.getConfig().getBoolean("ritual.hologram", true)) return;
        World w = r.location.getWorld();
        if (w == null || !w.isChunkLoaded(r.location.getBlockX() >> 4, r.location.getBlockZ() >> 4)) return;
        Location top = r.location.clone().add(0.5, 2.0, 0.5);
        if (r.bottle == null || !r.bottle.isValid()) {
            r.bottle = w.spawn(top, org.bukkit.entity.ItemDisplay.class, d -> {
                d.setItemStack(plugin.items().create(r.type, true));
                d.setPersistent(false);
                d.setGlowing(true);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setInterpolationDuration(10);
                d.setTeleportDuration(10);
            });
        }
        if (r.label == null || !r.label.isValid()) {
            r.label = w.spawn(top.clone().add(0, 0.85, 0), org.bukkit.entity.TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(90, 10, 5, 20));
                d.setShadowed(true);
                d.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            });
        }
        // Spin and bob the bottle (interpolated client-side between updates)
        float angle = (float) (ticks * 0.6 % (Math.PI * 2));
        double bob = Math.sin(ticks * 0.35) * 0.12;
        r.bottle.setInterpolationDelay(0);
        r.bottle.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0, (float) bob, 0), new org.joml.AxisAngle4f(angle, 0, 1, 0),
                new org.joml.Vector3f(0.9f, 0.9f, 0.9f), new org.joml.AxisAngle4f()));
        long rem = Math.max(0, r.end - System.currentTimeMillis());
        r.label.text(Msg.get("ritual-hologram", Msg.comp("effect", ItemFactory.name(r.type, true)),
                Msg.text("time", Util.time(rem))));
    }

    private void removeDisplays(Ritual r) {
        if (r.bottle != null) r.bottle.remove();
        if (r.label != null) r.label.remove();
        r.bottle = null;
        r.label = null;
    }

    private void hideBar(Ritual r) {
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(r.bar);
    }

    // ------------------------------------------------------------------ persistence

    private void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Ritual r : rituals) {
            Map<String, Object> m = new HashMap<>();
            m.put("effect", r.type.id());
            m.put("world", r.location.getWorld().getName());
            m.put("x", r.location.getBlockX());
            m.put("y", r.location.getBlockY());
            m.put("z", r.location.getBlockZ());
            m.put("end", r.end);
            m.put("total", r.total);
            out.add(m);
        }
        plugin.data().global().set("rituals", out);
        plugin.data().saveGlobal();
    }

    private void load() {
        rituals.clear();
        for (Map<?, ?> m : plugin.data().global().getMapList("rituals")) {
            EffectType type = EffectType.fromId(String.valueOf(m.get("effect")));
            World w = Bukkit.getWorld(String.valueOf(m.get("world")));
            if (type == null || w == null) continue;
            Location loc = new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z")));
            long end = (long) num(m.get("end"));
            Object totalObj = m.get("total");
            long total = totalObj == null ? 600_000L : (long) num(totalObj);
            Ritual r = new Ritual(type, loc, end, total);
            updateBar(r);
            rituals.add(r);
        }
        if (plugin.getConfig().getBoolean("ritual.bossbar", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) showBars(p);
        }
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));
    }
}
