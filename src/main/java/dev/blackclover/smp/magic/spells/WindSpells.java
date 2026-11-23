package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.Spell.Role;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

final class WindSpells {

    private static final Particle.DustOptions MINT = Fx.dust(0xA8FFD0, 1.1f);

    private WindSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.WIND,
                new Spell("wind_blade", "Wind Blade", 1, Role.ATTACK, 10, 1.0, 5,
                        "A razor crescent of air that slices through up to three enemies.", WindSpells::blade),
                new Spell("wind_gale_step", "Gale Step", 5, Role.MOBILITY, 18, 5, 0,
                        "Burst of wind in the way you look, even in mid-air. No fall damage.", WindSpells::galeStep),
                new Spell("wind_tornado", "Tornado", 10, Role.CONTROL, 38, 13, 1.5,
                        "A twister pulls enemies in, lifts them for 3s, then flings them away.", WindSpells::tornado),
                new Spell("wind_gale_annihilation", "Gale Annihilation", 20, Role.ULTIMATE, 85, 50, 3,
                        "A storm of wind blades rains on an area while you ride the wind.", WindSpells::annihilation));
    }

    private static boolean blade(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sounds(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f, Sound.ENTITY_BREEZE_SHOOT, 0.6f, 1.6f);
        int[] pierced = {0};
        ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.SWEEP_ATTACK, null, 3.0, 34, 1.0).count(1).piercing(),
                t -> {
                    if (pierced[0]++ < 3) {
                        ctx.fx().hit(p, t, ctx.dmg());
                        Fx.particle(t.getWorld(), Particle.CLOUD, t.getLocation().add(0, 1, 0), 8, 0.3, null);
                    }
                },
                end -> Fx.particle(end.getWorld(), Particle.CLOUD, end, 8, 0.2, null));
        return true;
    }

    private static boolean galeStep(SpellContext ctx) {
        Player p = ctx.caster();
        Vector dir = p.getLocation().getDirection().normalize();
        p.setVelocity(dir.multiply(1.6).setY(Math.max(0.45, dir.getY() * 1.6 + 0.35)));
        p.setFallDistance(0);
        ctx.status().addBuff(p, "no_fall", 100);
        Location at = p.getLocation();
        Fx.particle(at.getWorld(), Particle.CLOUD, at, 30, 0.4, null);
        Fx.ring(at.clone().add(0, 0.1, 0), 1.4, Particle.DUST, MINT, 24);
        Fx.sounds(at, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1f, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.2f);
        ctx.fx().repeat(1, 8, i -> Fx.particle(p.getWorld(), Particle.CLOUD, p.getLocation(), 3, 0.2, null));
        return true;
    }

    private static boolean tornado(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = SpellUtil.groundAt(ctx.fx().aimPoint(p, 26));
        Fx.sound(c, Sound.ENTITY_BREEZE_IDLE_AIR, 2f, 0.5f);
        ctx.fx().repeat(2, 30, i -> {
            for (double h = 0; h < 6; h += 0.3) {
                double a = h * 2.2 + i * 0.9;
                double rad = 0.4 + h * 0.38;
                Fx.particle(c.getWorld(), Particle.CLOUD, c.clone().add(Math.cos(a) * rad, h, Math.sin(a) * rad), 1, 0.02, null);
                if (((int) (h * 10)) % 6 == 0) {
                    Fx.particle(c.getWorld(), Particle.DUST, c.clone().add(Math.cos(a + 3) * rad, h, Math.sin(a + 3) * rad), 1, 0, MINT);
                }
            }
            for (LivingEntity t : ctx.fx().area(p, c, 5)) {
                Vector toward = c.toVector().subtract(t.getLocation().toVector()).setY(0);
                Vector swirl = new Vector(-toward.getZ(), 0, toward.getX());
                if (toward.lengthSquared() > 0.01) {
                    toward.normalize().multiply(0.35);
                    swirl.normalize().multiply(0.25);
                }
                ctx.fx().push(p, t, toward.add(swirl).setY(0.32));
                if (i % 5 == 0) {
                    ctx.fx().hit(p, t, ctx.dmg());
                }
            }
            if (i == 29) {
                for (LivingEntity t : ctx.fx().area(p, c, 5)) {
                    ctx.fx().knockback(p, t, c, 1.6, 0.9);
                }
                Fx.sound(c, Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.6f);
            }
        });
        return true;
    }

    private static boolean annihilation(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = SpellUtil.groundAt(ctx.fx().aimPoint(p, 32));
        ctx.fx().announce(p, ctx.spell(), GrimoireType.WIND.rgb());
        ctx.status().addBuff(p, "gale", 120, Fx.pe(PotionEffectType.SPEED, 120, 2));
        ctx.status().addBuff(p, "no_fall", 120);
        Fx.sounds(c, Sound.ENTITY_BREEZE_SHOOT, 2f, 0.5f, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.5f, 1.2f);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ctx.fx().repeat(3, 14, i -> {
            for (int k = 0; k < 3; k++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = rnd.nextDouble() * 8;
                Location land = c.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                Location sky = land.clone().add(rnd.nextDouble(-2, 2), 9, rnd.nextDouble(-2, 2));
                Fx.line(c.getWorld(), sky, land, Particle.SWEEP_ATTACK, null, 1.2);
                Fx.line(c.getWorld(), sky, land, Particle.DUST, MINT, 0.4);
                Fx.particle(c.getWorld(), Particle.CLOUD, land, 6, 0.3, null);
                for (LivingEntity t : ctx.fx().area(p, land, 2.2)) {
                    ctx.fx().hit(p, t, ctx.dmg());
                }
            }
            Fx.sound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f + i * 0.05f);
        });
        return true;
    }
}
