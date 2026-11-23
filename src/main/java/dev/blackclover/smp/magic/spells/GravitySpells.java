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

import java.util.List;

final class GravitySpells {

    private static final Particle.DustOptions INDIGO = Fx.dust(0x4B3BCC, 1.6f);

    private GravitySpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.GRAVITY,
                new Spell("gravity_orb", "Gravity Orb", 1, Role.ATTACK, 11, 1.2, 5,
                        "An orb of crushing weight that yanks its target towards you.", GravitySpells::orb),
                new Spell("gravity_weightless", "Weightless", 5, Role.MOBILITY, 18, 9, 0,
                        "Shed your weight: leap high and drift down slowly at speed.", GravitySpells::weightless),
                new Spell("gravity_crush", "Gravity Crush", 10, Role.CONTROL, 38, 12, 7,
                        "Slam everyone in an area into the ground and pin them there.", GravitySpells::crush),
                new Spell("gravity_singularity", "Singularity", 20, Role.ULTIMATE, 85, 55, 16,
                        "Pull every enemy around you into the air, then crush them down.", GravitySpells::singularity));
    }

    private static boolean orb(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sounds(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1.1f, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.8f);
        ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.DUST, INDIGO, 2.0, 30, 0.9).count(3),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
                        if (pull.lengthSquared() > 0.01) {
                            ctx.fx().push(p, t, pull.normalize().multiply(1.1).setY(0.35));
                        }
                        Fx.sphere(t.getLocation().add(0, 1, 0), 0.8, Particle.REVERSE_PORTAL, null, 25);
                    }
                },
                end -> Fx.particle(end.getWorld(), Particle.REVERSE_PORTAL, end, 20, 0.3, null));
        return true;
    }

    private static boolean weightless(SpellContext ctx) {
        Player p = ctx.caster();
        p.setVelocity(p.getVelocity().setY(1.25).add(SpellUtil.horizontalLook(p).multiply(0.6)));
        ctx.status().addBuff(p, "weightless", 140, Fx.pe(PotionEffectType.SLOW_FALLING, 140, 0), Fx.pe(PotionEffectType.SPEED, 140, 1));
        ctx.status().addBuff(p, "no_fall", 140);
        Fx.ring(p.getLocation().add(0, 0.1, 0), 1.3, Particle.DUST, INDIGO, 24);
        Fx.particle(p.getWorld(), Particle.REVERSE_PORTAL, p.getLocation(), 40, 0.5, null);
        Fx.sounds(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1.8f, Sound.ENTITY_BREEZE_JUMP, 1f, 0.7f);
        return true;
    }

    private static boolean crush(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = SpellUtil.groundAt(ctx.fx().aimPoint(p, 26));
        for (double h = 5; h > 0; h -= 0.35) {
            Fx.ring(c.clone().add(0, h, 0), 4.5 * (h / 5), Particle.DUST, INDIGO, 16);
        }
        ctx.fx().shockwave(c.clone().add(0, 0.2, 0), 5, 5, Particle.REVERSE_PORTAL, null);
        Fx.sounds(c, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.5f);
        for (LivingEntity t : ctx.fx().area(p, c, 5)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().push(p, t, new Vector(0, -2, 0));
                ctx.status().root(t, 40);
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 3);
            }
        }
        return true;
    }

    private static boolean singularity(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.GRAVITY.rgb());
        Location core = p.getLocation().add(SpellUtil.horizontalLook(p).multiply(3)).add(0, 3, 0);
        List<LivingEntity> caught = ctx.fx().area(p, p.getLocation(), 12);
        Fx.sounds(core, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 0.5f);
        ctx.fx().repeat(2, 14, i -> {
            Fx.sphere(core, 1.4 - i * 0.06, Particle.REVERSE_PORTAL, null, 40);
            Fx.ring(core, 3 - i * 0.15, Particle.DUST, INDIGO, 24);
            for (LivingEntity t : caught) {
                if (!t.isValid()) {
                    continue;
                }
                Vector pull = core.toVector().subtract(t.getLocation().toVector());
                if (pull.lengthSquared() > 1) {
                    ctx.fx().push(p, t, pull.normalize().multiply(0.7).setY(Math.max(0.25, pull.getY() * 0.15)));
                }
            }
        });
        ctx.fx().later(30, () -> {
            if (!p.isOnline()) {
                return;
            }
            Fx.sounds(core, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.6f);
            Fx.particle(core.getWorld(), Particle.EXPLOSION_EMITTER, core, 1, 0, null);
            for (LivingEntity t : caught) {
                if (t.isValid() && ctx.fx().hit(p, t, ctx.dmg())) {
                    ctx.fx().push(p, t, new Vector(0, -3, 0));
                    ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 2);
                }
            }
        });
        return true;
    }
}
