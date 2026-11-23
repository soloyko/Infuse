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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

final class WaterSpells {

    private static final Particle.DustOptions BLUE = Fx.dust(0x3399FF, 1.4f);
    private static final Particle.DustOptions DEEP = Fx.dust(0x1A5FCC, 2.0f);

    private WaterSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.WATER,
                new Spell("water_bullet", "Water Bullet", 1, 10, 1.5, 4, WaterSpells::bullet),
                new Spell("water_bubble_shield", "Bubble Shield", 5, 30, 20, 0, WaterSpells::bubbleShield),
                new Spell("water_tidal_wave", "Tidal Wave", 10, 35, 12, 6, WaterSpells::tidalWave),
                new Spell("water_sea_dragon", "Sea Dragon", 20, 80, 60, 14, WaterSpells::seaDragon));
    }

    private static boolean bullet(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.6f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, BLUE, 2.0, 30, 0.8),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().knockback(p, t, p.getLocation(), 0.8, 0.25);
                    }
                },
                end -> Fx.particle(end.getWorld(), Particle.SPLASH, end, 15, 0.3, null));
        return true;
    }

    private static boolean bubbleShield(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "bubble_shield", 100,
                Fx.pe(PotionEffectType.ABSORPTION, 100, 1), Fx.pe(PotionEffectType.RESISTANCE, 100, 0));
        Fx.sound(p.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1f, 1f);
        ctx.fx().repeat(5, 20, step -> Fx.sphere(p.getLocation().add(0, 1, 0), 1.3, Particle.DUST, BLUE, 20));
        return true;
    }

    private static boolean tidalWave(SpellContext ctx) {
        Player p = ctx.caster();
        Vector forward = SpellUtil.horizontalLook(p);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        Location base = p.getLocation();
        Fx.sound(base, Sound.ENTITY_GENERIC_SPLASH, 1f, 0.6f);
        ctx.fx().repeat(1, 7, step -> {
            double dist = step + 1;
            for (double i = -dist * 0.8; i <= dist * 0.8; i += 0.5) {
                Location at = base.clone().add(forward.clone().multiply(dist)).add(right.clone().multiply(i)).add(0, 0.6, 0);
                Fx.particle(at.getWorld(), Particle.SPLASH, at, 3, 0.2, null);
                Fx.particle(at.getWorld(), Particle.DUST, at, 1, 0.2, BLUE);
            }
        });
        for (LivingEntity t : ctx.fx().cone(p, 7, 90)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().knockback(p, t, base, 1.6, 0.4);
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 0);
            }
        }
        return true;
    }

    private static boolean seaDragon(SpellContext ctx) {
        Player p = ctx.caster();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        Fx.sound(eye, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 0.8f);
        Fx.sound(eye, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1f, 1.2f);
        Vector up = new Vector(0, 1, 0);
        Vector side = dir.clone().crossProduct(up);
        if (side.lengthSquared() < 1e-6) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        Vector upPerp = side.clone().crossProduct(dir).normalize();
        for (double d = 0; d < 25; d += 0.4) {
            double a = d * 0.9;
            Location at = eye.clone().add(dir.clone().multiply(d))
                    .add(side.clone().multiply(Math.cos(a) * 0.8)).add(upPerp.clone().multiply(Math.sin(a) * 0.8));
            Fx.particle(at.getWorld(), Particle.DUST, at, 1, 0.05, DEEP);
        }
        for (LivingEntity t : ctx.fx().line(p, eye, dir, 25, 1.5, Particle.SPLASH, null)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().push(p, t, dir.clone().multiply(1.2).setY(0.4));
            }
        }
        SpellUtil.heal(p, ctx.dmg(6));
        return true;
    }
}
