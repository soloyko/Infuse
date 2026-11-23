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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
                                 double gravity, boolean pierce, int particleCount, ItemStack model, float modelScale,
                                 boolean alignModel) {
        public static ProjectileSpec of(Particle particle, Object data, double speed, double range, double radius) {
            return new ProjectileSpec(particle, data, speed, range, radius, 0, false, 3, null, 1f, false);
        }

        public ProjectileSpec gravity(double g) {
            return new ProjectileSpec(particle, data, speed, range, radius, g, pierce, particleCount, model, modelScale, alignModel);
        }

        public ProjectileSpec piercing() {
            return new ProjectileSpec(particle, data, speed, range, radius, gravity, true, particleCount, model, modelScale, alignModel);
        }

        public ProjectileSpec count(int n) {
            return new ProjectileSpec(particle, data, speed, range, radius, gravity, pierce, n, model, modelScale, alignModel);
        }

        /** Shows a 3D model flying with the projectile. {@code align}: point the model's +Y along the flight path. */
        public ProjectileSpec model(ItemStack item, float scale, boolean align) {
            return new ProjectileSpec(particle, data, speed, range, radius, gravity, pierce, particleCount, item, scale, align);
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
            // magic damage ignores armour points (Protection enchantments still reduce it)
            target.damage(amount, DamageSource.builder(DamageType.INDIRECT_MAGIC)
                    .withCausingEntity(caster).withDirectEntity(caster).build());
        } finally {
            spellDamage = false;
        }
        Location at = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        particle(target.getWorld(), Particle.DAMAGE_INDICATOR, at, 3, 0.25, null);
        particle(target.getWorld(), Particle.CRIT, at, 6, 0.3, null);
        if (target instanceof Player) {
            caster.playSound(caster.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.5f, 1.4f);
        }
        return true;
    }

    /** Heals the caster for a share of damage dealt. */
    public static void lifesteal(Player caster, double amount) {
        org.bukkit.attribute.AttributeInstance max = caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        caster.setHealth(Math.min(cap, caster.getHealth() + amount));
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
        ItemDisplay display = spec.model() == null ? null : spawnModel(pos, spec.model(), spec.modelScale(), dir, spec.alignModel());
        int[] age = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, (BukkitTask task) -> {
            if (!caster.isOnline()) {
                task.cancel();
                removeModel(display);
                return;
            }
            if (display != null) {
                age[0]++;
                display.teleport(pos);
                display.setInterpolationDelay(0);
                display.setTransformation(modelTransform(dir, spec.modelScale(), spec.alignModel(), age[0]));
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
                    removeModel(display);
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
                            removeModel(display);
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

    /** A non-persistent 3D model entity (the resource pack turns the item into the model). */
    public ItemDisplay spawnModel(Location at, ItemStack item, float scale, Vector dir, boolean align) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setTransformation(modelTransform(dir, scale, align, 0));
        });
    }

    public static Transformation modelTransform(Vector dir, float scale, boolean align, int age) {
        Quaternionf rot = align && dir.lengthSquared() > 1e-6
                ? new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()).normalize())
                : new Quaternionf().rotationXYZ(age * 0.35f, age * 0.5f, 0);
        return new Transformation(new Vector3f(), rot, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    public static void removeModel(Entity display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    /** Where spells come from: the caster's floating grimoire, or just in front of their eyes. */
    public Location origin(Player caster) {
        return plugin.hover() == null ? eyeFront(caster)
                : plugin.hover().bookLocation(caster).orElseGet(() -> eyeFront(caster));
    }

    private static Location eyeFront(Player caster) {
        Location eye = caster.getEyeLocation();
        return eye.add(eye.getDirection().multiply(0.6));
    }

    /** What the caster's crosshair points at (blocks and entities), or the point at {@code range}. */
    public Location aimPoint(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        RayTraceResult r = caster.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.4,
                e -> e instanceof LivingEntity && !e.equals(caster));
        return r == null ? eye.clone().add(eye.getDirection().multiply(range)) : r.getHitPosition().toLocation(caster.getWorld());
    }

    /** Fires a projectile from the caster's grimoire towards their crosshair. */
    public void shootFromBook(Player caster, ProjectileSpec spec, Consumer<LivingEntity> onHit, Consumer<Location> onEnd) {
        Location from = origin(caster);
        Vector dir = aimPoint(caster, spec.range()).toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1e-4) {
            dir = caster.getEyeLocation().getDirection();
        }
        projectile(caster, from, dir, spec, onHit, onEnd);
    }

    /** A jagged lightning bolt between two points. */
    public static void zigzag(Location from, Location to, Particle particle, Object data) {
        World w = from.getWorld();
        Vector step = to.toVector().subtract(from.toVector());
        int segments = Math.max(3, (int) (step.length() / 1.5));
        step.multiply(1.0 / segments);
        Location a = from.clone();
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < segments; i++) {
            Location b = from.clone().add(step.clone().multiply(i + 1));
            if (i < segments - 1) {
                b.add(rnd.nextDouble(-0.45, 0.45), rnd.nextDouble(-0.45, 0.45), rnd.nextDouble(-0.45, 0.45));
            }
            line(w, a, b, particle, data, 0.18);
            a = b;
        }
    }

    /** A spiral of particles along a line (dragons, tornadoes, beams). */
    public static void helix(Location from, Vector dir, double length, double radius, double turns, Particle particle, Object data) {
        Vector d = dir.clone().normalize();
        Vector side = d.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 1e-4) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        Vector up = side.clone().crossProduct(d).normalize();
        World w = from.getWorld();
        for (double t = 0; t <= length; t += 0.25) {
            double a = t / length * turns * Math.PI * 2;
            Location p = from.clone().add(d.clone().multiply(t))
                    .add(side.clone().multiply(Math.cos(a) * radius)).add(up.clone().multiply(Math.sin(a) * radius));
            particle(w, particle, p, 1, 0, data);
        }
    }

    /** An expanding ring along the ground. */
    public void shockwave(Location centre, double maxRadius, int ticks, Particle particle, Object data) {
        repeat(1, ticks, i -> {
            double r = maxRadius * (i + 1) / ticks;
            ring(centre, r, particle, data, (int) (12 + r * 10));
        });
    }

    /** A temporary block shape (spikes, walls, pillars) that animates and removes itself. */
    public BlockDisplay blockDisplay(Location at, org.bukkit.block.data.BlockData data, float sx, float sy, float sz, int lifeTicks) {
        BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, b -> {
            b.setPersistent(false);
            b.setBlock(data);
            b.setTransformation(new Transformation(new Vector3f(-sx / 2, -sy, -sz / 2), new Quaternionf(),
                    new Vector3f(sx, sy, sz), new Quaternionf()));
        });
        later(lifeTicks, () -> removeModel(d));
        return d;
    }

    /** Smoothly moves/scales a display to a new transform over {@code ticks}. */
    public void animate(Display d, Transformation to, int ticks, int delay) {
        later(Math.max(1, delay), () -> {
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(ticks);
                d.setTransformation(to);
            }
        });
    }

    /** Ultimate callout: a title for the caster and an action-bar line for everyone nearby. */
    public void announce(Player caster, Spell spell, int rgb) {
        String name = dev.blackclover.smp.util.SmallCaps.of(spell.name());
        dev.blackclover.smp.util.Msg.title(caster, "<c><b><n></b></c>", "", dev.blackclover.smp.util.Msg.color("c", rgb),
                dev.blackclover.smp.util.Msg.arg("n", name));
        for (Player p : caster.getWorld().getPlayers()) {
            if (!p.equals(caster) && p.getLocation().distanceSquared(caster.getLocation()) < 48 * 48) {
                dev.blackclover.smp.util.Msg.actionBar(p, "<c>☘ <who> ‣ <n>", dev.blackclover.smp.util.Msg.color("c", rgb),
                        dev.blackclover.smp.util.Msg.arg("who", caster.getName()), dev.blackclover.smp.util.Msg.arg("n", name));
            }
        }
    }

    public static void sounds(Location at, Object... soundVolumePitch) {
        for (int i = 0; i + 2 < soundVolumePitch.length; i += 3) {
            sound(at, (Sound) soundVolumePitch[i], ((Number) soundVolumePitch[i + 1]).floatValue(),
                    ((Number) soundVolumePitch[i + 2]).floatValue());
        }
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

    /** A spot to land near {@code near}: at or below it (up to 8), or at most 1 block up. */
    public Optional<Location> safeSpot(Location near) {
        return safeSpot(near, 1, 8);
    }

    /** A spot where a player can stand: solid floor, two free blocks, inside the border, not in the void. */
    public Optional<Location> safeSpot(Location near, int maxUp, int maxDown) {
        World w = near.getWorld();
        int bx = near.getBlockX();
        int bz = near.getBlockZ();
        java.util.OptionalInt y = dev.blackclover.smp.core.LandingSearch.findY(near.getBlockY(), maxUp, maxDown, cy -> {
            if (cy <= w.getMinHeight() || cy >= w.getMaxHeight() - 2) {
                return false;
            }
            Block feet = w.getBlockAt(bx, cy, bz);
            return feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()
                    && feet.getRelative(0, -1, 0).getType().isSolid();
        });
        if (y.isEmpty()) {
            return Optional.empty();
        }
        Location spot = new Location(w, bx + 0.5, y.getAsInt(), bz + 0.5, near.getYaw(), near.getPitch());
        return w.getWorldBorder().isInside(spot) ? Optional.of(spot) : Optional.empty();
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
