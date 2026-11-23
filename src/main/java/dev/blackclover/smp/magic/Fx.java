package dev.blackclover.smp.magic;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.grimoire.GrimoireType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Shared spell toolkit: targeting, damage, projectiles, beams, areas, particles, safe teleports. */
public final class Fx {

    /** True while a PvP-permission probe event is being fired. */
    public static boolean probing;
    /** True while spell damage is being applied. */
    public static boolean spellDamage;

    /** How a travelling projectile looks and behaves. */
    public record ProjectileSpec(Particle particle, Object data, double speed, double range, double radius,
                                 double gravity, boolean pierce, int particleCount) {
        public static ProjectileSpec of(Particle particle, Object data, double speed, double range, double radius) {
            return new ProjectileSpec(particle, data, speed, range, radius, 0, false, 3);
        }

        public ProjectileSpec gravity(double g) {
            return new ProjectileSpec(particle, data, speed, range, radius, g, pierce, particleCount);
        }

        public ProjectileSpec piercing() {
            return new ProjectileSpec(particle, data, speed, range, radius, gravity, true, particleCount);
        }

        public ProjectileSpec count(int n) {
            return new ProjectileSpec(particle, data, speed, range, radius, gravity, pierce, n);
        }
    }

    private final BlackCloverPlugin plugin;
    private final StatusService status;
    private final TempBlocks temp;
    private final Map<String, Boolean> probeCache = new HashMap<>();
    private int probeTick = -1;

    public Fx(BlackCloverPlugin plugin, StatusService status, TempBlocks temp) {
        this.plugin = plugin;
        this.status = status;
        this.temp = temp;
    }

    public TempBlocks temp() {
        return temp;
    }

    public BlackCloverPlugin plugin() {
        return plugin;
    }

    // ---------------------------------------------------------------- targeting rules

    /** Can {@code caster}'s magic affect {@code target}? Respects grace, game modes, immunity and protection plugins. */
    public boolean canAffect(Player caster, Entity target) {
        if (!(target instanceof LivingEntity le) || target.equals(caster) || !le.isValid() || le.isDead()
                || target instanceof ArmorStand) {
            return false;
        }
        if (target instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                return false;
            }
            if (plugin.gameService().isGrace() || status.isImmune(p.getUniqueId())) {
                return false;
            }
            return pvpAllowed(caster, p);
        }
        return true;
    }

    private boolean pvpAllowed(Player caster, Player target) {
        int tick = Bukkit.getCurrentTick();
        if (tick != probeTick) {
            probeCache.clear();
            probeTick = tick;
        }
        String key = caster.getUniqueId() + ">" + target.getUniqueId();
        Boolean cached = probeCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean allowed;
        probing = true;
        try {
            DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                    .withCausingEntity(caster).withDirectEntity(caster).build();
            EntityDamageByEntityEvent probe = new EntityDamageByEntityEvent(caster, target,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 0.0);
            Bukkit.getPluginManager().callEvent(probe);
            allowed = !probe.isCancelled();
        } finally {
            probing = false;
        }
        probeCache.put(key, allowed);
        return allowed;
    }

    /** Spell damage. Anti-Magic carriers take 30% less. Returns false if the target couldn't be affected. */
    public boolean hit(Player caster, LivingEntity target, double damage) {
        if (!canAffect(caster, target)) {
            return false;
        }
        double amount = damage;
        if (target instanceof Player p && plugin.grimoires().carried(p).orElse(null) == GrimoireType.ANTI_MAGIC) {
            amount *= 0.7;
        }
        target.setNoDamageTicks(0);
        spellDamage = true;
        try {
            target.damage(amount, caster);
        } finally {
            spellDamage = false;
        }
        return true;
    }

    public boolean effect(Player caster, LivingEntity target, PotionEffectType type, int ticks, int amplifier) {
        if (!canAffect(caster, target)) {
            return false;
        }
        target.addPotionEffect(new PotionEffect(type, ticks, amplifier));
        return true;
    }

    public static void self(Player p, PotionEffectType type, int ticks, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier));
    }

    public static PotionEffect pe(PotionEffectType type, int ticks, int amplifier) {
        return new PotionEffect(type, ticks, amplifier);
    }

    public void push(Player caster, LivingEntity target, Vector velocity) {
        if (canAffect(caster, target)) {
            target.setVelocity(velocity);
        }
    }

    /** Knocks {@code target} away from {@code from}. */
    public void knockback(Player caster, LivingEntity target, Location from, double strength, double up) {
        Vector dir = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 1e-6) {
            dir = caster.getLocation().getDirection().setY(0);
        }
        push(caster, target, dir.normalize().multiply(strength).setY(up));
    }

    // ---------------------------------------------------------------- shapes

    /** A travelling projectile. {@code onHit} runs per entity hit, {@code onEnd} once where it stops. */
    public void projectile(Player caster, Location start, Vector direction, ProjectileSpec spec,
                           Consumer<LivingEntity> onHit, Consumer<Location> onEnd) {
        Location pos = start.clone();
        Vector dir = direction.clone().normalize();
        World world = start.getWorld();
        Set<UUID> hit = new HashSet<>();
        double step = 0.5;
        double[] travelled = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, (BukkitTask task) -> {
            if (!caster.isOnline()) {
                task.cancel();
                return;
            }
            for (double moved = 0; moved < spec.speed(); moved += step) {
                Vector velocity = dir.clone().multiply(step);
                velocity.setY(velocity.getY() - spec.gravity() * step);
                dir.copy(velocity.clone().normalize());
                pos.add(velocity);
                travelled[0] += step;
                particle(world, spec.particle(), pos, spec.particleCount(), 0.08, spec.data());
                Block block = pos.getBlock();
                if (!block.isPassable() || travelled[0] >= spec.range() || pos.getY() < world.getMinHeight()) {
                    task.cancel();
                    if (onEnd != null) {
                        onEnd.accept(pos.clone());
                    }
                    return;
                }
                for (Entity e : world.getNearbyEntities(pos, spec.radius(), spec.radius(), spec.radius())) {
                    if (e instanceof LivingEntity le && !hit.contains(e.getUniqueId()) && canAffect(caster, le)) {
                        hit.add(e.getUniqueId());
                        onHit.accept(le);
                        if (!spec.pierce()) {
                            task.cancel();
                            if (onEnd != null) {
                                onEnd.accept(pos.clone());
                            }
                            return;
                        }
                    }
                }
            }
        }, 0L, 1L);
    }

    /** Fires a projectile from the caster's eyes in the direction they look. */
    public void shoot(Player caster, ProjectileSpec spec, Consumer<LivingEntity> onHit, Consumer<Location> onEnd) {
        Location eye = caster.getEyeLocation();
        projectile(caster, eye.clone().add(eye.getDirection().multiply(0.6)), eye.getDirection(), spec, onHit, onEnd);
    }

    /** Instant hitscan beam. Returns the first entity hit. */
    public Optional<LivingEntity> beam(Player caster, double range, double width, Particle particle, Object data) {
        Location eye = caster.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = caster.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, width,
                e -> e instanceof LivingEntity && canAffect(caster, e));
        Location end = r == null ? eye.clone().add(dir.clone().multiply(range)) : r.getHitPosition().toLocation(caster.getWorld());
        line(caster.getWorld(), eye, end, particle, data, 0.3);
        if (r != null && r.getHitEntity() instanceof LivingEntity le) {
            return Optional.of(le);
        }
        return Optional.empty();
    }

    /** Every entity along a straight line (stops at solid blocks). */
    public List<LivingEntity> line(Player caster, Location from, Vector direction, double range, double width,
                                   Particle particle, Object data) {
        Vector dir = direction.clone().normalize();
        Set<LivingEntity> found = new LinkedHashSet<>();
        Location pos = from.clone();
        World world = from.getWorld();
        for (double d = 0; d < range; d += 0.5) {
            pos.add(dir.clone().multiply(0.5));
            if (!pos.getBlock().isPassable()) {
                break;
            }
            particle(world, particle, pos, 2, width * 0.3, data);
            for (Entity e : world.getNearbyEntities(pos, width, width, width)) {
                if (e instanceof LivingEntity le && canAffect(caster, le)) {
                    found.add(le);
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Entities in front of the caster within {@code range} and a cone of {@code degrees}. */
    public List<LivingEntity> cone(Player caster, double range, double degrees) {
        List<LivingEntity> out = new ArrayList<>();
        Location eye = caster.getEyeLocation();
        Vector look = eye.getDirection();
        double cos = Math.cos(Math.toRadians(degrees / 2));
        for (Entity e : caster.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le) || !canAffect(caster, le)) {
                continue;
            }
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist <= range && (dist < 1 || to.normalize().dot(look) >= cos) && caster.hasLineOfSight(le)) {
                out.add(le);
            }
        }
        return out;
    }

    /** Entities within {@code radius} of {@code centre}. */
    public List<LivingEntity> area(Player caster, Location centre, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : centre.getWorld().getNearbyEntities(centre, radius, radius, radius)) {
            if (e instanceof LivingEntity le && le.getLocation().distanceSquared(centre) <= radius * radius
                    && canAffect(caster, le)) {
                out.add(le);
            }
        }
        return out;
    }

    /** The block point the caster looks at, or the point at {@code range}. */
    public Location targetPoint(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        RayTraceResult r = caster.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (r != null) {
            Vector back = eye.getDirection().multiply(0.5);
            return r.getHitPosition().toLocation(caster.getWorld()).subtract(back);
        }
        return eye.clone().add(eye.getDirection().multiply(range));
    }

    /** The living entity the caster looks at (blocks stop the search). */
    public Optional<LivingEntity> targetEntity(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        RayTraceResult r = caster.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.8,
                e -> e instanceof LivingEntity && canAffect(caster, e));
        return r != null && r.getHitEntity() instanceof LivingEntity le ? Optional.of(le) : Optional.empty();
    }

    /** A nearby spot where a player can stand: solid floor, two free blocks, inside the border, not in the void. */
    public Optional<Location> safeSpot(Location near) {
        World w = near.getWorld();
        int bx = near.getBlockX();
        int bz = near.getBlockZ();
        int by = near.getBlockY();
        for (int offset = 0; offset <= 8; offset++) {
            for (int sign : new int[]{-1, 1}) {
                int y = by + offset * sign;
                if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 2) {
                    continue;
                }
                Block feet = w.getBlockAt(bx, y, bz);
                if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()
                        && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    Location spot = new Location(w, bx + 0.5, y, bz + 0.5, near.getYaw(), near.getPitch());
                    if (w.getWorldBorder().isInside(spot)) {
                        return Optional.of(spot);
                    }
                }
                if (offset == 0) {
                    break;
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- scheduling

    public void repeat(int periodTicks, int times, IntConsumer step) {
        int[] i = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, (BukkitTask task) -> {
            if (i[0] >= times) {
                task.cancel();
                return;
            }
            step.accept(i[0]++);
        }, 0L, periodTicks);
    }

    public void later(int ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    // ---------------------------------------------------------------- particles & sound

    public static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb & 0xFFFFFF), size);
    }

    public static void particle(World w, Particle particle, Location at, int count, double spread, Object data) {
        if (data == null) {
            w.spawnParticle(particle, at, count, spread, spread, spread, 0);
        } else {
            w.spawnParticle(particle, at, count, spread, spread, spread, 0, data);
        }
    }

    public static void line(World w, Location from, Location to, Particle particle, Object data, double spacing) {
        Vector dir = to.toVector().subtract(from.toVector());
        double length = dir.length();
        if (length < 1e-6) {
            return;
        }
        dir.normalize();
        for (double d = 0; d <= length; d += spacing) {
            particle(w, particle, from.clone().add(dir.clone().multiply(d)), 1, 0.02, data);
        }
    }

    public static void ring(Location centre, double radius, Particle particle, Object data, int points) {
        World w = centre.getWorld();
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            particle(w, particle, centre.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0.02, data);
        }
    }

    public static void sphere(Location centre, double radius, Particle particle, Object data, int points) {
        World w = centre.getWorld();
        for (int i = 0; i < points; i++) {
            double u = Math.random() * 2 - 1;
            double a = Math.random() * 2 * Math.PI;
            double s = Math.sqrt(1 - u * u);
            particle(w, particle, centre.clone().add(s * Math.cos(a) * radius, u * radius, s * Math.sin(a) * radius), 1, 0, data);
        }
    }

    public static void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, volume, pitch);
    }
}
