package dev.infuse.customs.ability;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Crosshair helpers for abilities the caster steers or aims while they play out. */
public final class Aim {

    private Aim() {
    }

    /** The point under the caster's crosshair: an enemy's body, a block, or {@code range} blocks out. */
    public static Location crosshair(Player player, Combat combat, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTrace(eye, eye.getDirection(), range,
                FluidCollisionMode.NEVER, true, 0.4, e -> combat.canHit(player, e));
        if (hit == null) {
            return eye.add(eye.getDirection().multiply(range));
        }
        if (hit.getHitEntity() != null) {
            return Combat.center(hit.getHitEntity());
        }
        // just above the block looked at, so things steered there skim the ground
        return hit.getHitPosition().toLocation(player.getWorld()).add(0, 0.8, 0);
    }

    /** Turns {@code from} towards {@code to} (both unit vectors) by at most {@code maxAngle} radians. */
    public static Vector turnToward(Vector from, Vector to, double maxAngle) {
        double angle = Math.acos(Math.max(-1, Math.min(1, from.dot(to))));
        if (angle <= maxAngle) {
            return to.clone();
        }
        Vector axis = from.clone().crossProduct(to);
        if (axis.lengthSquared() < 1.0E-6) {
            axis = new Vector(0, 1, 0);
        }
        return from.clone().rotateAroundAxis(axis.normalize(), maxAngle).normalize();
    }

    /** The caster's look direction flattened onto the ground (never zero). */
    public static Vector flatLook(Player player) {
        Vector look = player.getEyeLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            Vector facing = player.getLocation().getDirection().setY(0);
            return facing.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : facing.normalize();
        }
        return look.normalize();
    }
}
