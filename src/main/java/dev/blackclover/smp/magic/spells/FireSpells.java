package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class FireSpells {

    private FireSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.FIRE,
                new Spell("fire_fireball", "Fireball", 1, 15, 2.0, 5, FireSpells::fireball),
                new Spell("fire_flame_wall", "Flame Wall", 5, 30, 12, 3, FireSpells::flameWall),
                new Spell("fire_blazing_dash", "Blazing Dash", 10, 25, 8, 5, FireSpells::blazingDash),
                new Spell("fire_inferno", "Inferno", 20, 80, 60, 12, FireSpells::inferno));
    }

    private static boolean fireball(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.FLAME, null, 1.6, 30, 0.9).count(4),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        t.setFireTicks(Math.max(t.getFireTicks(), 60));
                    }
                },
                end -> Fx.particle(end.getWorld(), Particle.LAVA, end, 6, 0.3, null));
        return true;
    }

    private static boolean flameWall(SpellContext ctx) {
        Player p = ctx.caster();
        Vector forward = SpellUtil.horizontalLook(p);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location centre = p.getLocation().add(forward.clone().multiply(4));
        Fx.sound(centre, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        ctx.fx().repeat(10, 10, step -> {
            for (double i = -3.5; i <= 3.5; i += 0.5) {
                for (double h = 0; h <= 2.5; h += 0.8) {
                    Fx.particle(centre.getWorld(), Particle.FLAME, centre.clone().add(right.clone().multiply(i)).add(0, h, 0), 1, 0.1, null);
                }
            }
            if (step % 2 != 0) {
                return;
            }
            for (LivingEntity t : ctx.fx().area(p, centre, 4.5)) {
                Vector off = t.getLocation().toVector().subtract(centre.toVector());
                if (Math.abs(off.dot(forward)) < 1.2 && Math.abs(off.dot(right)) <= 3.8 && ctx.fx().hit(p, t, ctx.dmg())) {
                    t.setFireTicks(Math.max(t.getFireTicks(), 40));
                }
            }
        });
        return true;
    }

    private static boolean blazingDash(SpellContext ctx) {
        Player p = ctx.caster();
        p.setVelocity(SpellUtil.horizontalLook(p).multiply(1.8).setY(0.25));
        Fx.sound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        Set<UUID> hit = new HashSet<>();
        ctx.fx().repeat(1, 10, step -> {
            Fx.particle(p.getWorld(), Particle.FLAME, p.getLocation().add(0, 0.5, 0), 8, 0.3, null);
            for (LivingEntity t : ctx.fx().area(p, p.getLocation(), 1.8)) {
                if (hit.add(t.getUniqueId()) && ctx.fx().hit(p, t, ctx.dmg())) {
                    t.setFireTicks(Math.max(t.getFireTicks(), 60));
                }
            }
        });
        return true;
    }

    private static boolean inferno(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        Fx.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        Fx.sound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);
        Fx.particle(c.getWorld(), Particle.EXPLOSION_EMITTER, c, 1, 0, null);
        ctx.fx().repeat(1, 6, step -> Fx.ring(c.clone().add(0, 0.3, 0), 1 + step, Particle.FLAME, null, 24 + step * 8));
        for (LivingEntity t : ctx.fx().area(p, c, 6)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                t.setFireTicks(Math.max(t.getFireTicks(), 120));
                ctx.fx().knockback(p, t, c, 1.2, 0.5);
            }
        }
        return true;
    }
}
