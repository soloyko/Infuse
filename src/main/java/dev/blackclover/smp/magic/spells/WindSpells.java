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

import java.util.concurrent.ThreadLocalRandom;

final class WindSpells {

    private WindSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.WIND,
                new Spell("wind_blade", "Wind Blade", 1, 12, 1.5, 5, WindSpells::blade),
                new Spell("wind_gust_jump", "Gust Jump", 5, 15, 6, 0, WindSpells::gustJump),
                new Spell("wind_tornado", "Tornado", 10, 40, 15, 2, WindSpells::tornado),
                new Spell("wind_storm_of_blades", "Storm of Blades", 20, 85, 60, 16, WindSpells::storm));
    }

    private static boolean blade(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.SWEEP_ATTACK, null, 2.5, 30, 0.8).count(1),
                t -> ctx.fx().hit(p, t, ctx.dmg()),
                end -> Fx.particle(end.getWorld(), Particle.CLOUD, end, 6, 0.2, null));
        return true;
    }

    private static boolean gustJump(SpellContext ctx) {
        Player p = ctx.caster();
        Vector v = SpellUtil.horizontalLook(p).multiply(1.2);
        v.setY(Math.max(0.9, p.getLocation().getDirection().getY() * 1.4));
        p.setVelocity(v);
        ctx.status().addBuff(p, "no_fall", 100);
        Fx.particle(p.getWorld(), Particle.CLOUD, p.getLocation(), 25, 0.5, null);
        Fx.sound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f);
        return true;
    }

    private static boolean tornado(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = ctx.fx().targetPoint(p, 25);
        Fx.sound(c, Sound.ENTITY_BREEZE_IDLE_AIR, 1.5f, 0.6f);
        ctx.fx().repeat(10, 8, step -> {
            for (double h = 0; h < 5; h += 0.4) {
                double a = h * 2 + step * 0.8;
                double rad = 0.6 + h * 0.4;
                Fx.particle(c.getWorld(), Particle.CLOUD,
                        c.clone().add(Math.cos(a) * rad, h, Math.sin(a) * rad), 1, 0.05, null);
            }
            for (LivingEntity t : ctx.fx().area(p, c, 3.5)) {
                if (ctx.fx().hit(p, t, ctx.dmg())) {
                    Vector swirl = c.toVector().subtract(t.getLocation().toVector()).setY(0);
                    if (swirl.lengthSquared() > 0.01) {
                        swirl.normalize().multiply(0.3);
                    }
                    ctx.fx().push(p, t, swirl.setY(0.6));
                }
            }
        });
        return true;
    }

    private static boolean storm(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = ctx.fx().targetPoint(p, 30);
        Fx.sound(c, Sound.ENTITY_BREEZE_SHOOT, 1.5f, 0.5f);
        double perPulse = ctx.dmg() / 6.0;
        ctx.fx().repeat(10, 6, step -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 30; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = rnd.nextDouble() * 8;
                Fx.particle(c.getWorld(), Particle.SWEEP_ATTACK,
                        c.clone().add(Math.cos(a) * d, rnd.nextDouble() * 3, Math.sin(a) * d), 1, 0, null);
            }
            Fx.sound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f + step * 0.1f);
            for (LivingEntity t : ctx.fx().area(p, c, 8)) {
                ctx.fx().hit(p, t, perPulse);
            }
        });
        return true;
    }
}
