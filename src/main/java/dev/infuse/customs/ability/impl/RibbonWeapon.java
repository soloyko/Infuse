package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ribbon Cleaver, the Puppeteer's Cleaver: strings, and the things tied to them.
 * Passive Graceful, on-hit Entangle, F Marionette, Sneak+F Tangle.
 */
public final class RibbonWeapon extends SwordAbility implements Listener {

    private static final int PINK = 0xE8446A;
    private static final int WHITE = 0xF2F2F2;
    private static final int MARIONETTE_TICKS = 100;
    private static final int TANGLE_TICKS = 120;
    private static final int TANGLE_MAX = 5;
    private static final double TANGLE_SLACK = 4;

    /** A group of enemies tied together, and who tied them. */
    private record Knot(Player caster, List<LivingEntity> members) {
    }

    /** Tangled entity -> its knot. */
    private final Map<UUID, Knot> knots = new HashMap<>();
    /** Guards against shared damage being shared again. */
    private boolean sharing;

    public RibbonWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.RIBBON);
    }

    /** Graceful: quicker on your feet while sprinting. */
    @Override
    public void tickHeld(Player player, long tick) {
        if (player.isSprinting()) {
            applyEffect(player, PotionEffectType.SPEED, 15, 0);
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        applyEffect(target, PotionEffectType.SLOWNESS, 40, 2);
        Location at = Combat.center(target);
        for (int i = 0; i < 16; i++) {
            double a = i * 0.8;
            Location p = at.clone().add(Math.cos(a) * 0.5, -0.8 + i * 0.1, Math.sin(a) * 0.5);
            Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(i % 2 == 0 ? PINK : WHITE, 0.9f));
        }
        Fx.sound(at, Sound.BLOCK_WOOL_PLACE, 1f, 1.2f);
    }

    /** A ribbon between two points: alternating pink and white, rippling a little. */
    private static void ribbon(Location from, Location to, int tick) {
        Vector span = to.toVector().subtract(from.toVector());
        double length = span.length();
        if (length < 0.4) {
            return;
        }
        int steps = (int) (length / 0.3);
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            double ripple = Math.sin(t * Math.PI * 3 + tick * 0.5) * 0.08;
            Location p = from.clone().add(span.clone().multiply(t)).add(0, ripple, 0);
            Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(i % 4 < 2 ? PINK : WHITE, 0.7f));
        }
    }

    // ---- Marionette ----

    /**
     * Marionette: ribbons tie your hands to the enemy under your crosshair for five seconds. Every
     * step you take, it takes the same step; jump and it jumps. Walk it into walls, into its
     * friends, or off a cliff.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity puppet = combat().lookTarget(player, 20, 0.6);
        if (puppet == null) {
            tell(player, "Look at an enemy to tie your ribbons to.");
            return false;
        }
        Location at = Combat.center(puppet);
        fx().burst(at, PINK, 1.8f);
        Fx.sound(at, Sound.BLOCK_WOOL_BREAK, 1.2f, 0.8f);
        Fx.sound(player.getLocation(), Sound.ITEM_LEAD_TIED, 1.2f, 1f);

        new BukkitRunnable() {
            int ticks;
            Location last = player.getLocation();

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !puppet.isValid() || puppet.isDead() || puppet.getWorld() != player.getWorld()
                        || ticks > MARIONETTE_TICKS || puppet.getLocation().distanceSquared(player.getLocation()) > 40 * 40) {
                    snip();
                    return;
                }
                Location now = player.getLocation();
                Vector step = now.toVector().subtract(last.toVector());
                last = now;
                // it takes the same step you did; a jump becomes its jump
                double vy = puppet.getVelocity().getY();
                if (player.getVelocity().getY() > 0.25 && !puppet.getLocation().subtract(0, 0.1, 0).getBlock().isPassable()) {
                    vy = 0.45;
                }
                Combat.setVelocity(puppet, new Vector(step.getX(), vy, step.getZ()));
                Location hands = player.getLocation().add(0, 1.2, 0);
                Location body = Combat.center(puppet);
                Vector side = new Vector(-now.getDirection().getZ(), 0, now.getDirection().getX()).normalize().multiply(0.35);
                ribbon(hands.clone().add(side), body.clone().add(0, 0.4, 0), ticks);
                ribbon(hands.clone().subtract(side), body.clone().subtract(0, 0.2, 0), ticks + 3);
                if (ticks % 20 == 0) {
                    combat().damage(player, puppet, 1, true);
                    Fx.sound(body, Sound.BLOCK_WOOL_STEP, 1f, 1.4f);
                }
            }

            private void snip() {
                cancel();
                if (puppet.isValid()) {
                    Fx.particle(Combat.center(puppet), Particle.DUST, 12, 0.3, 0, Fx.dust(PINK, 1.0f));
                    Fx.sound(puppet.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ---- Tangle ----

    /**
     * Tangle: ribbons lash up to five nearby enemies together for six seconds. They can't pull
     * apart, and half of any damage one of them takes is dealt to all the others.
     */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> members = new ArrayList<>();
        for (LivingEntity target : combat().nearby(player, player.getLocation(), 8)) {
            if (player.hasLineOfSight(target) && !knots.containsKey(target.getUniqueId())) {
                members.add(target);
            }
            if (members.size() >= TANGLE_MAX) {
                break;
            }
        }
        if (members.size() < 2) {
            tell(player, "You need at least two enemies nearby to tangle.");
            return false;
        }
        Knot knot = new Knot(player, members);
        for (LivingEntity m : members) {
            knots.put(m.getUniqueId(), knot);
            fx().burst(Combat.center(m), PINK, 1.4f);
        }
        Fx.sound(player.getLocation(), Sound.ITEM_LEAD_TIED, 1.4f, 0.7f);
        Fx.sound(player.getLocation(), Sound.BLOCK_WOOL_PLACE, 1.4f, 0.8f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                members.removeIf(m -> {
                    if (!m.isValid() || m.isDead()) {
                        knots.remove(m.getUniqueId());
                        return true;
                    }
                    return false;
                });
                if (members.size() < 2 || ticks > TANGLE_TICKS || !alive(player)) {
                    untie();
                    return;
                }
                // the knot sits at the middle of the group; nobody gets far from it
                Vector middle = new Vector();
                for (LivingEntity m : members) {
                    middle.add(Combat.center(m).toVector());
                }
                middle.multiply(1.0 / members.size());
                Location knotAt = middle.toLocation(members.get(0).getWorld());
                for (int i = 0; i < members.size(); i++) {
                    LivingEntity m = members.get(i);
                    Location body = Combat.center(m);
                    ribbon(body, knotAt, ticks + i * 2);
                    Vector out = body.toVector().subtract(middle);
                    double distance = out.length();
                    if (distance > TANGLE_SLACK) {
                        Combat.setVelocity(m, out.multiply(-Math.min(0.9, (distance - TANGLE_SLACK) * 0.35 + 0.2) / distance).setY(0.12));
                    }
                }
                if (ticks % 4 == 0) {
                    Fx.particle(knotAt, Particle.DUST, 3, 0.15, 0, Fx.dust(PINK, 1.3f));
                }
            }

            private void untie() {
                cancel();
                for (LivingEntity m : members) {
                    knots.remove(m.getUniqueId());
                    if (m.isValid() && !m.isDead() && alive(player)) {
                        combat().damage(player, m, 3, true);
                        Fx.particle(Combat.center(m), Particle.DUST, 10, 0.3, 0, Fx.dust(WHITE, 1.0f));
                    }
                }
                Fx.sound(player.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.2f, 0.8f);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Whatever hurts one of a tangled group runs down the ribbons to the rest. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTangledHurt(EntityDamageEvent event) {
        if (sharing) {
            return;
        }
        Knot knot = knots.get(event.getEntity().getUniqueId());
        if (knot == null || !alive(knot.caster())) {
            return;
        }
        double share = event.getFinalDamage() * 0.5;
        if (share < 0.5) {
            return;
        }
        sharing = true;
        try {
            for (LivingEntity other : knot.members()) {
                if (other != event.getEntity() && other.isValid() && !other.isDead()) {
                    combat().damage(knot.caster(), other, share, true);
                    Fx.particle(Combat.center(other), Particle.DUST, 6, 0.3, 0, Fx.dust(PINK, 1.0f));
                }
            }
        } finally {
            sharing = false;
        }
    }

    @Override
    public void shutdown() {
        knots.clear();
    }
}
