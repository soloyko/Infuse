package me.infuse.craft;

import me.infuse.InfusePlugin;
import me.infuse.effect.EffectType;
import me.infuse.effect.ItemFactory;
import me.infuse.util.Msg;
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
        for (Ritual r : rituals) hideBar(r);
        rituals.clear();
    }

    public boolean isActive(EffectType type) {
        return rituals.stream().anyMatch(r -> r.type == type);
    }

    public void begin(EffectType type, Location location) {
        long total = plugin.getConfig().getLong("ritual.duration-seconds", 600) * 1000L;
        Location loc = location.getBlock().getLocation();
        Ritual r = new Ritual(type, loc, System.currentTimeMillis() + total, total);
        rituals.add(r);
        updateBar(r);
        if (plugin.getConfig().getBoolean("ritual.bossbar", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(r.bar);
        }
        save();
        Msg.broadcast("<gray>A ritual for the </gray><" + Msg.hex(type.rgb()) + "><bold>Augmented " + type.displayName()
                + " Effect</bold></" + Msg.hex(type.rgb()) + "><gray> has begun at </gray><white>" + Util.coords(loc)
                + "</white><gray>! It completes in </gray><white>" + Util.time(total) + "</white><gray>.</gray>");
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
        }
    }

    private void updateBar(Ritual r) {
        long rem = Math.max(0, r.end - System.currentTimeMillis());
        r.bar.name(Component.text("Ritual: ", NamedTextColor.GRAY)
                .append(ItemFactory.name(r.type, true))
                .append(Component.text(" — " + Util.time(rem) + " — " + Util.coords(r.location), NamedTextColor.GRAY)));
        r.bar.progress(Math.max(0f, Math.min(1f, rem / (float) r.total)));
    }

    private void particles(Ritual r) {
        World w = r.location.getWorld();
        if (w == null || !w.isChunkLoaded(r.location.getBlockX() >> 4, r.location.getBlockZ() >> 4)) return;
        Location c = r.location.clone().add(0.5, 1.2, 0.5);
        double a = ticks * 0.4;
        for (int i = 0; i < 3; i++) {
            double ang = a + i * Math.PI * 2 / 3;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * 1.5, 0, Math.sin(ang) * 1.5), 2, 0.05, 0.3, 0.05,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(r.type.rgb()), 1.5f));
        }
        w.spawnParticle(Particle.ENCHANT, c, 20, 0.6, 0.6, 0.6, 1);
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 2, 0.05, 2, 0.05, 0.01);
    }

    private void finish(Ritual r) {
        hideBar(r);
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
        w.playSound(drop, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        Msg.broadcast("<gray>The ritual is complete! The </gray><" + Msg.hex(r.type.rgb()) + "><bold>Augmented "
                + r.type.displayName() + " Effect</bold></" + Msg.hex(r.type.rgb()) + "><gray> is waiting at </gray><white>"
                + Util.coords(r.location) + "</white><gray>.</gray>");
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
