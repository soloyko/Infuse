package me.infuse.effect;

import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.util.Msg;
import me.infuse.util.Status;
import me.infuse.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Activated abilities ("sparks"). */
public final class SparkManager {
    private final InfusePlugin plugin;
    private final Status status;

    public SparkManager(InfusePlugin plugin, Status status) {
        this.plugin = plugin;
        this.status = status;
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    public long cooldownMillis(Equipped e) {
        double seconds = cfg().getDouble("cooldowns." + e.type().id(), 60);
        if (e.augmented()) seconds *= cfg().getDouble("cooldowns.augmented-multiplier", 0.5);
        return (long) (seconds * 1000);
    }

    /** Spark duration for this player: augmented sparks last longer (durations.augmented-multiplier). */
    public long durationFor(PlayerData d, EffectType type) {
        long base = durationMillis(type);
        return d != null && d.isAugmented(type) ? (long) (base * cfg().getDouble("durations.augmented-multiplier", 1.25)) : base;
    }

    public long durationMillis(EffectType type) {
        return (long) (cfg().getDouble("durations." + type.id(), 0) * 1000);
    }

    /** Ends a spark early (Thief backstab, Feather slam landing) so its cooldown starts now. */
    public void endEarly(Player p, PlayerData d, EffectType type) {
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < 2; slot++) {
            Equipped e = d.get(slot);
            if (e == null || !e.type().grants().contains(type)) continue;
            long total = d.cooldownTotal(slot);
            if (d.cooldownRemaining(slot) > total) d.setCooldownRaw(slot, now + total, total);
            d.setSlotActive(slot, 0);
        }
        plugin.data().save(d);
    }

    /** Uses the spark in a slot (0 = primary/left, 1 = secondary/right). */
    public void use(Player p, int slot) {
        PlayerData d = plugin.data().get(p);
        Equipped e = d.get(slot);
        if (e == null) {
            Msg.send(p, "spark-empty-slot", Msg.comp("slot", Msg.get(slot == 0 ? "slot-primary" : "slot-secondary")));
            return;
        }
        var name = me.infuse.effect.ItemFactory.name(e.type(), e.augmented());
        if (!e.type().enabled()) {
            Msg.send(p, "effect-disabled", Msg.comp("effect", name));
            return;
        }
        if (cfg().getStringList("sparks.disabled-worlds").contains(p.getWorld().getName())) {
            me.infuse.util.Fx.sparkFail(p);
            Msg.send(p, "spark-disabled-world");
            return;
        }
        long silenced = status.silencedRemaining(p);
        if (silenced > 0) {
            me.infuse.util.Fx.sparkFail(p);
            Msg.send(p, "spark-silenced", Msg.text("time", Util.time(silenced)));
            return;
        }
        long rem = d.cooldownRemaining(slot);
        if (rem > 0) {
            me.infuse.util.Fx.sparkFail(p);
            long activeLeft = rem - d.cooldownTotal(slot);
            if (activeLeft > 0) {
                Msg.send(p, "spark-active", Msg.comp("effect", name), Msg.text("time", Util.time(activeLeft)));
            } else {
                Msg.send(p, "spark-cooldown", Msg.comp("effect", name), Msg.text("time", Util.time(rem)));
            }
            return;
        }
        if (!activate(p, d, e.type())) {
            me.infuse.util.Fx.sparkFail(p);
            return;
        }
        me.infuse.util.Fx.spark(p, e.type());
        long now = System.currentTimeMillis();
        long duration = durationFor(d, e.type());
        long cooldown = (long) (cooldownMillis(e) * plugin.resonance().cooldownMultiplier(d.xp(slot)));
        // The cooldown only starts once the spark's effect has ended
        d.setCooldown(slot, cooldown, duration);
        plugin.resonance().gain(p, d, slot, plugin.resonance().reward("spark", 20));
        d.setSlotActive(slot, now + Math.max(duration, 1000));
        plugin.effects().refresh(p);
        plugin.data().save(d);
    }

    private boolean activate(Player p, PlayerData d, EffectType type) {
        long now = System.currentTimeMillis();
        long dur = durationFor(d, type);
        World w = p.getWorld();
        switch (type) {
            case STRENGTH, HASTE, REGENERATION -> {
                d.setSparkActive(type, now + dur);
                if (type == EffectType.HASTE && d.isAugmented(EffectType.HASTE)) {
                    Util.potion(p, PotionEffectType.HASTE, cfg().getInt("augmented.haste.spark-haste-level", 3) - 1, (int) (dur / 50), false);
                }
                w.playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 1.4f);
                w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5,
                        new Particle.DustOptions(Color.fromRGB(type.rgb()), 1.4f));
            }
            case FROST -> {
                d.setSparkActive(type, now + dur);
                w.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
                w.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 60, 0.7, 0.8, 0.7, 0.02);
            }
            case EMERALD -> emerald(p, d, now, dur);
            case HEART -> heart(p);
            case FIRE -> fire(p);
            case ENDER -> {
                if (!enderTeleport(p)) return false;
                d.setSparkActive(type, now + dur);
            }
            case FEATHER -> {
                p.setVelocity(new Vector(0, cfg().getDouble("effects.feather.launch-velocity", 2.2), 0));
                status.noFall(p, 15000);
                d.setFeatherSlamUntil(now + dur);
                me.infuse.util.Fx.trail(p, EffectType.FEATHER.rgb(), 20, Particle.WHITE_ASH);
                w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
                w.spawnParticle(Particle.GUST, p.getLocation(), 6, 0.5, 0.2, 0.5, 0);
            }
            case SPEED -> {
                Vector dir = p.getLocation().getDirection().normalize()
                        .multiply(cfg().getDouble("effects.speed.dash-velocity", 2.2));
                dir.setY(Math.max(dir.getY(), 0.25));
                p.setVelocity(dir);
                status.noFall(p, 4000);
                me.infuse.util.Fx.trail(p, EffectType.SPEED.rgb(), 14, Particle.CLOUD);
                w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.4f);
                w.spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.3, 0.1, 0.3, 0.05);
            }
            case THUNDER -> thunder(p);
            case INVISIBILITY -> invisibilityRing(p, dur);
            case OCEAN -> whirlhole(p, dur);
            case THIEF -> {
                long until = now + dur;
                d.setSparkActive(type, until);
                me.infuse.util.Fx.vanish(p, true);
                Msg.send(p, "thief-vanish");
                Bukkit.getScheduler().runTask(plugin, () -> plugin.effects().syncThieves());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Only if the vanish ran out on its own (a backstab ends it early with its own message)
                    if (p.isOnline() && d.sparkActiveUntil(EffectType.THIEF) == until) {
                        Msg.send(p, "thief-cover-blown");
                        me.infuse.util.Fx.vanish(p, false);
                    }
                }, dur / 50 + 2);
            }
            case WEAVING -> webSnare(p, dur);
            case OOZING -> {
                d.setSparkActive(type, now + dur);
                d.setAirFall(0);
                w.playSound(p.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.6f);
                w.playSound(p.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.8f);
                w.spawnParticle(Particle.ITEM_SLIME, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.1);
            }
            case RESISTANCE -> {
                d.setSparkActive(type, now + dur);
                int ticks = (int) (dur / 50);
                Util.potion(p, PotionEffectType.RESISTANCE, 3, ticks, false);
                double r = cfg().getDouble("effects.resistance.ally-radius", 6);
                for (Player ally : w.getPlayers()) {
                    if (ally.equals(p) || !Util.isAlly(p, ally) || ally.getLocation().distanceSquared(p.getLocation()) > r * r) continue;
                    Util.potion(ally, PotionEffectType.RESISTANCE, 1, ticks, false);
                    me.infuse.util.Fx.line(p.getLocation().add(0, 1, 0), ally.getLocation().add(0, 1, 0), EffectType.RESISTANCE.rgb(), false);
                }
                w.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
                w.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
                me.infuse.util.Fx.helix(p, EffectType.RESISTANCE.rgb(), (int) Math.min(40, ticks), 1.0);
            }
            case APOPHIS -> {
                fire(p);
                emerald(p, d, now, durationMillis(EffectType.EMERALD));
                heart(p);
                d.setSparkActive(EffectType.APOPHIS, now + dur);
                w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ individual sparks

    private void webSnare(Player p, long dur) {
        double r = me.infuse.effect.Tune.d(p, "effects.weaving.snare-radius", 6);
        World w = p.getWorld();
        int rooted = 0;
        for (LivingEntity le : Util.enemiesNear(p, p.getLocation(), r)) {
            plugin.tempBlocks().place(le.getLocation().getBlock(), org.bukkit.Material.COBWEB, dur);
            plugin.tempBlocks().place(le.getEyeLocation().getBlock(), org.bukkit.Material.COBWEB, dur);
            Util.potion(le, PotionEffectType.SLOWNESS, 3, (int) (dur / 50), false);
            status.root(le, dur);
            if (le instanceof Player t) Msg.send(t, "weaving-rooted", Msg.text("time", Util.time(dur)));
            me.infuse.util.Fx.line(p.getLocation().add(0, 1, 0), le.getLocation().add(0, 1, 0), 0xF5F5F0, false);
            rooted++;
        }
        // Web burst rings
        for (double rr = 1; rr <= r; rr += 1.25) me.infuse.util.Fx.ring(p.getLocation().add(0, 0.2, 0), 0xEDEDE6, rr, (int) (rr * 10), 1.0f);
        w.spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 1, 0), 80, r / 2, 1, r / 2, 0);
        w.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 1f, 0.6f);
        if (rooted == 0) w.playSound(p.getLocation(), Sound.ENTITY_SPIDER_STEP, 1f, 1f);
    }

    /** Gelatinous Form: landing on enemies hits them and splashes slime around. */
    public void oozingSlam(Player p) {
        double dmg = cfg().getDouble("effects.oozing.slam-damage", 8);
        double splash = cfg().getDouble("effects.oozing.splash-radius", 3);
        World w = p.getWorld();
        boolean hit = false;
        for (LivingEntity le : Util.enemiesNear(p, p.getLocation(), splash)) {
            double dist = le.getLocation().distance(p.getLocation());
            if (dist <= 1.6) {
                le.damage(dmg, p);
                hit = true;
            }
            Util.potion(le, PotionEffectType.SLOWNESS, 1, 60, false);
            status.slime(le, 3000);
        }
        w.spawnParticle(Particle.ITEM_SLIME, p.getLocation(), hit ? 60 : 25, splash / 2, 0.2, splash / 2, 0.1);
        me.infuse.util.Fx.shockwave(p.getLocation(), EffectType.OOZING.rgb(), splash);
        w.playSound(p.getLocation(), hit ? Sound.ENTITY_SLIME_ATTACK : Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 0.7f);
    }

    private void emerald(Player p, PlayerData d, long now, long dur) {
        d.setSparkActive(EffectType.EMERALD, now + dur);
        Util.potion(p, PotionEffectType.HERO_OF_THE_VILLAGE, 254, (int) (dur / 50), false);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6);
    }

    private void heart(Player p) {
        double target = cfg().getDouble("effects.heart.spark-heal-to", 40);
        double radius = cfg().getDouble("effects.heart.spark-heal-radius", 8);
        for (Player ally : p.getWorld().getPlayers()) {
            if (!Util.isAlly(p, ally) || ally.getLocation().distanceSquared(p.getLocation()) > radius * radius) continue;
            double max = Util.maxHealth(ally);
            ally.setHealth(Math.min(max, Math.max(ally.getHealth(), target)));
            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 8, 0.5, 0.3, 0.5);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
    }

    private void fire(Player p) {
        double r = me.infuse.effect.Tune.d(p, "effects.fire.spark-radius", 5);
        int ticks = me.infuse.effect.Tune.i(p, "effects.fire.spark-fire-ticks", 100);
        for (LivingEntity le : Util.enemiesNear(p, p.getLocation(), r)) {
            le.setFireTicks(Math.max(le.getFireTicks(), ticks));
        }
        World w = p.getWorld();
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            w.spawnParticle(Particle.FLAME, p.getLocation().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 2, 0.1, 0.1, 0.1, 0.01);
        }
        w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
    }

    private boolean enderTeleport(Player p) {
        double range = me.infuse.effect.Tune.d(p, "effects.ender.teleport-range", 15);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        Location target = hit != null
                ? hit.getHitPosition().toLocation(p.getWorld()).subtract(dir.clone().multiply(0.6))
                : eye.clone().add(dir.clone().multiply(range));
        Location safe = findSafe(target);
        if (safe == null) {
            Msg.send(p, "ender-no-safe-spot");
            return false;
        }
        safe.setYaw(p.getLocation().getYaw());
        safe.setPitch(p.getLocation().getPitch());
        Location from = p.getLocation();
        p.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
        me.infuse.util.Fx.line(from.clone().add(0, 1, 0), safe.clone().add(0, 1, 0), EffectType.ENDER.rgb(), false);
        p.teleport(safe);
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, safe.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.1);
        p.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        return true;
    }

    private static Location findSafe(Location target) {
        Block base = target.getBlock();
        for (int dy = 0; dy <= 3; dy++) {
            Block feet = base.getRelative(0, dy, 0);
            for (int down = 0; down <= 1; down++) {
                Block f = feet.getRelative(0, -down, 0);
                if (f.isPassable() && f.getRelative(0, 1, 0).isPassable() && !f.isLiquid()) {
                    return f.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return null;
    }

    private void thunder(Player p) {
        double r = cfg().getDouble("effects.thunder.spark-radius", 10);
        for (LivingEntity le : Util.enemiesNear(p, p.getLocation(), r)) {
            if (!(le instanceof Player) && !(le instanceof Monster)) continue;
            strike(p, le);
        }
        World w = p.getWorld();
        int stormTicks = cfg().getInt("effects.thunder.storm-seconds", 60) * 20;
        if (stormTicks > 0) {
            w.setStorm(true);
            w.setThundering(true);
            w.setWeatherDuration(stormTicks);
            w.setThunderDuration(stormTicks);
        }
        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
    }

    /** Real lightning on the target, then chain to nearby enemies. */
    public void strike(Player source, LivingEntity target) {
        target.getWorld().strikeLightning(target.getLocation());
        chain(source, target);
    }

    public void chain(Player source, LivingEntity origin) {
        int count = me.infuse.effect.Tune.i(source, "effects.thunder.chain-targets", 3);
        double radius = cfg().getDouble("effects.thunder.chain-radius", 6);
        double damage = cfg().getDouble("effects.thunder.chain-damage", 4);
        int done = 0;
        for (LivingEntity le : Util.enemiesNear(source, origin.getLocation(), radius)) {
            if (done >= count) break;
            if (le.equals(origin)) continue;
            le.getWorld().strikeLightningEffect(le.getLocation());
            me.infuse.util.Fx.line(origin.getLocation().add(0, origin.getHeight() * 0.6, 0),
                    le.getLocation().add(0, le.getHeight() * 0.6, 0), EffectType.THUNDER.rgb(), true);
            le.damage(damage, DamageSource.builder(DamageType.LIGHTNING_BOLT).withCausingEntity(source)
                    .withDirectEntity(source).build());
            done++;
        }
    }

    private void invisibilityRing(Player p, long dur) {
        Location center = p.getLocation().clone();
        double r = me.infuse.effect.Tune.d(p, "effects.invisibility.ring-radius", 2.5);
        Set<UUID> hiddenAllies = new HashSet<>();
        new BukkitRunnable() {
            int ticks = 0;
            final int max = (int) (dur / 50);

            @Override
            public void run() {
                if (!p.isOnline() || ticks >= max || center.getWorld() == null) {
                    finish();
                    cancel();
                    return;
                }
                World w = center.getWorld();
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x111111), 1.6f);
                for (int i = 0; i < 40; i++) {
                    double a = Math.PI * 2 * i / 40;
                    w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r),
                            1, 0.02, 0.05, 0.02, dust);
                }
                if (ticks % 10 == 0) {
                    for (Player other : w.getPlayers()) {
                        boolean inside = other.getLocation().distanceSquared(center) <= r * r
                                && Math.abs(other.getLocation().getY() - center.getY()) < 3;
                        if (Util.isAlly(p, other)) {
                            if (inside) {
                                Util.potion(other, PotionEffectType.INVISIBILITY, 0, 30, false);
                                hiddenAllies.add(other.getUniqueId());
                                for (Player viewer : Bukkit.getOnlinePlayers()) {
                                    if (!Util.isAlly(p, viewer)) viewer.hideEntity(plugin, other);
                                }
                            } else if (hiddenAllies.remove(other.getUniqueId())) {
                                showToAll(other);
                            }
                        } else if (inside) {
                            Util.potion(other, PotionEffectType.BLINDNESS, 0, 40, false);
                        }
                    }
                }
                ticks += 2;
            }

            private void finish() {
                for (UUID id : hiddenAllies) {
                    Player ally = Bukkit.getPlayer(id);
                    if (ally != null) showToAll(ally);
                }
                hiddenAllies.clear();
            }
        }.runTaskTimer(plugin, 0L, 2L);
        p.getWorld().playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
    }

    private void showToAll(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showEntity(plugin, target);
    }

    private void whirlhole(Player p, long dur) {
        double range = cfg().getDouble("effects.ocean.whirlhole-range", 20);
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        Location center = hit != null ? hit.getHitPosition().toLocation(p.getWorld()).add(0, 1, 0)
                : eye.clone().add(eye.getDirection().multiply(8));
        double radius = me.infuse.effect.Tune.d(p, "effects.ocean.whirlhole-radius", 7);
        double pull = me.infuse.effect.Tune.d(p, "effects.ocean.whirlhole-pull", 0.35);
        new BukkitRunnable() {
            int ticks = 0;
            final int max = (int) (dur / 50);
            double angle = 0;

            @Override
            public void run() {
                if (ticks >= max || !p.isOnline()) {
                    cancel();
                    return;
                }
                World w = center.getWorld();
                angle += 0.5;
                for (int arm = 0; arm < 4; arm++) {
                    for (double rr = 0.5; rr < radius; rr += 0.8) {
                        double a = angle + arm * Math.PI / 2 + rr * 0.6;
                        w.spawnParticle(Particle.BUBBLE_POP, center.clone().add(Math.cos(a) * rr, rr * 0.15, Math.sin(a) * rr),
                                1, 0, 0, 0, 0);
                    }
                }
                w.spawnParticle(Particle.NAUTILUS, center, 6, 0.3, 0.3, 0.3, 0.5);
                for (LivingEntity le : Util.enemiesNear(p, center, radius)) {
                    Vector toCenter = center.toVector().subtract(le.getLocation().toVector());
                    if (toCenter.lengthSquared() < 0.01) continue;
                    le.setVelocity(le.getVelocity().multiply(0.5).add(toCenter.normalize().multiply(pull)));
                    if (ticks % 20 == 0 && le.getLocation().distanceSquared(center) < 4) {
                        if (le instanceof Player t) t.setRemainingAir(Math.max(0, t.getRemainingAir() - 60));
                        le.damage(2.0, DamageSource.builder(DamageType.DROWN).withCausingEntity(p).build());
                    }
                }
                if (ticks % 20 == 0) w.playSound(center, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1f, 0.8f);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
