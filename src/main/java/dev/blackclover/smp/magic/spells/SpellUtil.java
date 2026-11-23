package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.SpellContext;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Small helpers shared by spell sets. */
final class SpellUtil {

    private SpellUtil() {
    }

    static void heal(LivingEntity e, double amount) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        e.setHealth(Math.max(0, Math.min(cap, e.getHealth() + amount)));
    }

    /** Teleports the caster up to {@code range} blocks where they look, to a safe spot. */
    static boolean blink(SpellContext ctx, double range, org.bukkit.Particle trail, Object data) {
        Player p = ctx.caster();
        Location target = ctx.fx().targetPoint(p, range).subtract(0, 1.0, 0);
        Optional<Location> spot = ctx.fx().safeSpot(target);
        if (spot.isEmpty()) {
            ctx.casting().tell(p, "<red>Nowhere safe to land there.");
            return false;
        }
        Location from = p.getLocation().add(0, 1, 0);
        Location to = spot.get();
        to.setYaw(p.getLocation().getYaw());
        to.setPitch(p.getLocation().getPitch());
        Fx.line(p.getWorld(), from, to.clone().add(0, 1, 0), trail, data, 0.4);
        p.teleport(to);
        p.setFallDistance(0);
        return true;
    }

    /** The target the caster looks at, or a "No target" notice. */
    static Optional<LivingEntity> target(SpellContext ctx, double range) {
        Optional<LivingEntity> t = ctx.fx().targetEntity(ctx.caster(), range);
        if (t.isEmpty()) {
            ctx.casting().tell(ctx.caster(), "<red>No target in sight.");
        }
        return t;
    }

    /** Living, affectable entities near the caster, closest first. */
    static List<LivingEntity> nearest(SpellContext ctx, double radius, int max) {
        Player p = ctx.caster();
        return ctx.fx().area(p, p.getLocation(), radius).stream()
                .filter(p::hasLineOfSight)
                .sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())))
                .limit(max)
                .toList();
    }

    static Vector horizontalLook(Player p) {
        Vector v = p.getLocation().getDirection().setY(0);
        return v.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : v.normalize();
    }

    /** The cardinal direction the player faces. */
    static BlockFace cardinal(Player p) {
        Vector v = horizontalLook(p);
        if (Math.abs(v.getX()) > Math.abs(v.getZ())) {
            return v.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return v.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }
}
