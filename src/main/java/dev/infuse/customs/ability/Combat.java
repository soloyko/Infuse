package dev.infuse.customs.ability;

import dev.infuse.customs.CustomsPlugin;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Targeting, damage and movement helpers used by every ability. */
public final class Combat {

    private final CustomsPlugin plugin;
    /** >0 while an ability is dealing damage (Paper reports it as an ENTITY_ATTACK by the player). */
    private int abilityDamageDepth;

    public Combat(CustomsPlugin plugin) {
        this.plugin = plugin;
    }

    /** True while the current damage event was caused by an ability rather than a real swing. */
    public boolean isAbilityDamage() {
        return abilityDamageDepth > 0;
    }

    /** Whether an ability cast by {@code caster} may affect {@code entity}. */
    public boolean canHit(Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == caster || living.isDead() || !living.isValid()) {
            return false;
        }
        if (entity instanceof ArmorStand || entity.isInvulnerable() || entity.hasMetadata("NPC")) {
            return false;
        }
        if (entity instanceof Player player) {
            if (!plugin.settings().abilitiesHitPlayers) {
                return false;
            }
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                return false;
            }
        }
        return !(entity instanceof Tameable pet) || !pet.isTamed()
                || !caster.getUniqueId().equals(pet.getOwnerUniqueId());
    }

    /** Ability damage. Attributed to the caster so kills, aggro and protection plugins all work. */
    public void damage(Player caster, LivingEntity target, double amount) {
        damage(caster, target, amount, false);
    }

    /**
     * @param ignoreInvulnerability clears the target's damage cooldown first, for abilities
     *                              that hit more often than every 10 ticks
     */
    public void damage(Player caster, LivingEntity target, double amount, boolean ignoreInvulnerability) {
        if (!canHit(caster, target)) {
            return;
        }
        if (ignoreInvulnerability) {
            target.setNoDamageTicks(0);
        }
        DamageSource source = DamageSource.builder(DamageType.INDIRECT_MAGIC)
                .withCausingEntity(caster)
                .withDirectEntity(caster)
                .build();
        abilityDamageDepth++;
        try {
            double multiplier = plugin.settings().abilityDamageMultiplier;
            if (target instanceof Player) {
                multiplier *= plugin.settings().playerDamageMultiplier;
            }
            target.damage(amount * multiplier, source);
        } finally {
            abilityDamageDepth--;
        }
    }

    public void heal(LivingEntity entity, double amount) {
        if (amount > 0 && !entity.isDead()) {
            entity.heal(amount, EntityRegainHealthEvent.RegainReason.MAGIC);
        }
    }

    /** Valid targets whose body is within {@code radius} of {@code center}. */
    public List<LivingEntity> nearby(Player caster, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, radius + 2)) {
            if (canHit(caster, e) && distanceToBody(e, center) <= radius) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
        return out;
    }

    /** Distance from a point to the closest point on the entity's hitbox. */
    public static double distanceToBody(Entity e, Location point) {
        BoundingBox box = e.getBoundingBox();
        double x = Math.max(box.getMinX(), Math.min(point.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(point.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(point.getZ(), box.getMaxZ()));
        return Math.sqrt(point.toVector().distanceSquared(new Vector(x, y, z)));
    }

    public static Location center(Entity e) {
        return e.getLocation().add(0, e.getHeight() / 2, 0);
    }

    /** The first valid entity in the caster's crosshair, not behind blocks. */
    public @Nullable LivingEntity lookTarget(Player caster, double range, double raySize) {
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTrace(eye, eye.getDirection(), range,
                FluidCollisionMode.NEVER, true, raySize, e -> canHit(caster, e));
        return hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    /** Where the caster is aiming: first entity or block hit, or null if nothing within range. */
    public @Nullable Location aimPoint(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTrace(eye, eye.getDirection(), range,
                FluidCollisionMode.NEVER, true, 0.3, e -> canHit(caster, e));
        if (hit == null) {
            return null;
        }
        if (hit.getHitEntity() != null) {
            return hit.getHitEntity().getLocation();
        }
        return hit.getHitPosition().toLocation(caster.getWorld());
    }

    /** Distance the caster can see before a block gets in the way (capped at range). */
    public static double clearDistance(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        return hit == null ? range : hit.getHitPosition().distance(eye.toVector());
    }

    /** Snaps a point down onto the ground below it (or returns it unchanged if there is none nearby). */
    public static Location ground(Location point) {
        RayTraceResult hit = point.getWorld().rayTraceBlocks(point.clone().add(0, 2, 0), new Vector(0, -1, 0), 40,
                FluidCollisionMode.NEVER, true);
        return hit == null ? point : hit.getHitPosition().toLocation(point.getWorld());
    }

    /** Pushes the entity away from {@code from}. */
    public static void knockback(Entity entity, Location from, double strength, double up) {
        Vector push = entity.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (push.lengthSquared() < 1.0E-4) {
            push = new Vector(0, 0, 0);
        } else {
            push.normalize().multiply(strength);
        }
        push.setY(up);
        setVelocity(entity, entity.getVelocity().multiply(0.3).add(push));
    }

    /** setVelocity that refuses NaN/infinite vectors and clamps extreme speeds. */
    public static void setVelocity(Entity entity, Vector velocity) {
        if (!Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY()) || !Double.isFinite(velocity.getZ())) {
            return;
        }
        if (velocity.lengthSquared() > 16) {
            velocity = velocity.normalize().multiply(4);
        }
        entity.setVelocity(velocity);
    }

    /** True if a player-sized hitbox fits with its feet at {@code feet}. */
    public static boolean fits(Location feet) {
        if (feet.getY() < feet.getWorld().getMinHeight() || feet.getY() > feet.getWorld().getMaxHeight()) {
            return false;
        }
        BoundingBox box = new BoundingBox(feet.getX() - 0.3, feet.getY(), feet.getZ() - 0.3,
                feet.getX() + 0.3, feet.getY() + 1.8, feet.getZ() + 0.3);
        if (feet.getWorld().hasCollisionsIn(box)) {
            return false;
        }
        Block feetBlock = feet.getBlock();
        Block head = feetBlock.getRelative(0, 1, 0);
        return feetBlock.getType() != Material.LAVA && head.getType() != Material.LAVA
                && !Tag.FIRE.isTagged(feetBlock.getType());
    }

    /** Finds a spot the player fits at, searching slightly up and down from {@code feet}. */
    public static @Nullable Location safeSpot(Location feet) {
        for (double dy : new double[]{0, 1, -1, 0.5, 2}) {
            Location candidate = feet.clone().add(0, dy, 0);
            if (fits(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
