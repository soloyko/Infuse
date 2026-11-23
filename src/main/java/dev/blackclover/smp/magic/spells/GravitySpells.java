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

import java.util.List;

final class GravitySpells {

    private static final Particle.DustOptions INDIGO = Fx.dust(0x4B3BCC, 1.6f);

    private GravitySpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.GRAVITY,
                new Spell("gravity_bullet", "Gravity Bullet", 1, 12, 1.5, 4, GravitySpells::bullet),
                new Spell("gravity_float", "Float", 5, 15, 10, 0, GravitySpells::floatSpell),
                new Spell("gravity_crush", "Crush", 10, 40, 14, 7, GravitySpells::crush),
                new Spell("gravity_singularity", "Singularity", 20, 85, 60, 15, GravitySpells::singularity));
    }

    private static boolean bullet(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1.2f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, INDIGO, 1.8, 30, 0.9),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
                        if (pull.lengthSquared() > 0.01) {
                            ctx.fx().push(p, t, pull.normalize().multiply(0.9).setY(0.3));
                        }
                    }
                },
                end -> Fx.particle(end.getWorld(), Particle.REVERSE_PORTAL, end, 20, 0.3, null));
        return true;
    }

    private static boolean floatSpell(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "float", 120,
                Fx.pe(PotionEffectType.SLOW_FALLING, 120, 0), Fx.pe(PotionEffectType.JUMP_BOOST, 120, 2));
        p.setVelocity(p.getVelocity().setY(0.6));
        Fx.particle(p.getWorld(), Particle.REVERSE_PORTAL, p.getLocation(), 40, 0.5, null);
        Fx.sound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1.8f);
        return true;
    }

    private static boolean crush(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = ctx.fx().targetPoint(p, 25);
        for (double h = 4; h > 0; h -= 0.3) {
            Fx.ring(c.clone().add(0, h, 0), 4 * (h / 4), Particle.DUST, INDIGO, 12);
        }
        Fx.sound(c, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        for (LivingEntity t : ctx.fx().area(p, c, 4)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().push(p, t, new Vector(0, -1.5, 0));
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 2);
            }
        }
        return true;
    }

    private static boolean singularity(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        List<LivingEntity> caught = ctx.fx().area(p, c, 10);
        for (LivingEntity t : caught) {
            ctx.fx().effect(p, t, PotionEffectType.LEVITATION, 30, 1);
        }
        Fx.sound(c, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.5f);
        ctx.fx().repeat(3, 10, step -> Fx.sphere(p.getLocation().add(0, 1, 0), 2.5 - step * 0.2, Particle.REVERSE_PORTAL, null, 40));
        ctx.fx().later(30, () -> {
            if (!p.isOnline()) {
                return;
            }
            Fx.sound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            for (LivingEntity t : caught) {
                if (!t.isValid()) {
                    continue;
                }
                t.removePotionEffect(PotionEffectType.LEVITATION);
                if (ctx.fx().hit(p, t, ctx.dmg())) {
                    ctx.fx().push(p, t, new Vector(0, -3, 0));
                    Fx.particle(t.getWorld(), Particle.EXPLOSION, t.getLocation(), 1, 0, null);
                }
            }
        });
        return true;
    }
}
