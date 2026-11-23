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

final class BloodSpells {

    private static final Particle.DustOptions RED = Fx.dust(0xB00020, 1.4f);

    private BloodSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.BLOOD,
                new Spell("blood_needle", "Blood Needle", 1, 12, 1.5, 5, BloodSpells::needle),
                new Spell("blood_pact", "Blood Pact", 5, 0, 20, 4, BloodSpells::pact),
                new Spell("blood_crimson_lash", "Crimson Lash", 10, 35, 12, 7, BloodSpells::crimsonLash),
                new Spell("blood_moon", "Blood Moon", 20, 80, 60, 3, BloodSpells::bloodMoon));
    }

    private static boolean needle(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1f, 1.6f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, RED, 2.2, 30, 0.8),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        SpellUtil.heal(p, 1);
                    }
                }, null);
        return true;
    }

    /** Costs health instead of mana: the spell's "damage" value is the health paid. */
    private static boolean pact(SpellContext ctx) {
        Player p = ctx.caster();
        double price = ctx.spell().damage();
        if (p.getHealth() <= price) {
            ctx.casting().tell(p, "<red>You are too weak to pay the blood price.");
            return false;
        }
        p.setHealth(p.getHealth() - price);
        ctx.casting().addMana(p, 40);
        Fx.particle(p.getWorld(), Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.5, RED);
        Fx.particle(p.getWorld(), Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1.5, 0), 6, 0.3, null);
        Fx.sound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        return true;
    }

    private static boolean crimsonLash(SpellContext ctx) {
        Player p = ctx.caster();
        Location eye = p.getEyeLocation();
        Vector forward = SpellUtil.horizontalLook(p);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        for (double a = -1; a <= 1; a += 0.1) {
            Location at = eye.clone().add(forward.clone().multiply(3 + Math.cos(a) * 2)).add(right.clone().multiply(a * 3));
            Fx.particle(eye.getWorld(), Particle.DUST, at, 2, 0.05, RED);
        }
        Fx.sound(eye, Sound.ITEM_TRIDENT_HIT, 1f, 0.7f);
        boolean any = false;
        for (LivingEntity t : ctx.fx().cone(p, 6, 70)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().effect(p, t, PotionEffectType.WITHER, 60, 0);
                any = true;
            }
        }
        if (any) {
            SpellUtil.heal(p, ctx.dmg(3));
        }
        return true;
    }

    private static boolean bloodMoon(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.8f);
        ctx.fx().repeat(20, 4, step -> {
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            Fx.ring(p.getLocation().add(0, 0.2, 0), 8, Particle.DUST, RED, 60);
            Fx.sound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1f);
            double drained = 0;
            for (LivingEntity t : ctx.fx().area(p, p.getLocation(), 8)) {
                if (ctx.fx().hit(p, t, ctx.dmg())) {
                    drained += ctx.dmg();
                    Fx.line(p.getWorld(), t.getEyeLocation(), p.getEyeLocation(), Particle.DUST, RED, 0.4);
                }
            }
            SpellUtil.heal(p, drained / 2);
        });
        return true;
    }
}
