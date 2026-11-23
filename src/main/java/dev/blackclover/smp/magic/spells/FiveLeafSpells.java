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

import java.util.Optional;

/** Devil magic of the Five-Leaf Grimoire. */
final class FiveLeafSpells {

    private static final Particle.DustOptions DEVIL = Fx.dust(0x6B0000, 2.0f);
    private static final Particle.DustOptions SHADOW = Fx.dust(0x150000, 1.6f);

    private FiveLeafSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.FIVE_LEAF,
                new Spell("five_leaf_devils_claw", "Devil's Claw", 1, 15, 2.0, 7, FiveLeafSpells::claw),
                new Spell("five_leaf_demon_wings", "Demon Wings", 5, 25, 12, 0, FiveLeafSpells::wings),
                new Spell("five_leaf_curse_chains", "Curse Chains", 10, 40, 14, 6, FiveLeafSpells::chains),
                new Spell("five_leaf_devil_union", "Devil Union", 20, 90, 120, 2, FiveLeafSpells::devilUnion));
    }

    private static boolean claw(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.8f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, DEVIL, 1.8, 30, 1.0).count(5),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.WITHER, 40, 0);
                    }
                },
                end -> Fx.particle(end.getWorld(), Particle.SOUL_FIRE_FLAME, end, 12, 0.3, null));
        return true;
    }

    static void wingParticles(Player p) {
        Location base = p.getLocation().add(0, 1.4, 0);
        Vector back = SpellUtil.horizontalLook(p).multiply(-0.4);
        Vector right = SpellUtil.horizontalLook(p).crossProduct(new Vector(0, 1, 0)).normalize();
        for (double s = 0.2; s <= 1.6; s += 0.2) {
            double lift = Math.sin(s * 2) * 0.4;
            for (int side = -1; side <= 1; side += 2) {
                Location at = base.clone().add(back).add(right.clone().multiply(s * side)).add(0, lift, 0);
                Fx.particle(p.getWorld(), Particle.DUST, at, 1, 0.02, DEVIL);
            }
        }
    }

    private static boolean wings(SpellContext ctx) {
        Player p = ctx.caster();
        p.setVelocity(SpellUtil.horizontalLook(p).multiply(1.5).setY(1.2));
        ctx.status().addBuff(p, "no_fall", 160);
        Fx.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.8f);
        ctx.fx().repeat(5, 24, step -> {
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            wingParticles(p);
            if (step > 3 && !p.isOnGround()) {
                Vector v = p.getVelocity();
                Vector glide = SpellUtil.horizontalLook(p).multiply(0.7);
                p.setVelocity(new Vector(glide.getX(), Math.max(v.getY(), -0.15), glide.getZ()));
            }
        });
        return true;
    }

    private static boolean chains(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 20);
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity t = target.get();
        if (ctx.fx().hit(p, t, ctx.dmg())) {
            ctx.status().root(t, 60);
            ctx.status().silence(t, 60);
        }
        Fx.sound(t.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.5f);
        ctx.fx().repeat(4, 15, step -> {
            if (p.isOnline() && t.isValid()) {
                Fx.line(p.getWorld(), p.getEyeLocation().subtract(0, 0.4, 0), t.getEyeLocation().subtract(0, 0.4, 0),
                        Particle.DUST, SHADOW, 0.35);
            }
        });
        return true;
    }

    private static boolean devilUnion(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "devil_union", 300,
                Fx.pe(PotionEffectType.STRENGTH, 300, 1), Fx.pe(PotionEffectType.RESISTANCE, 300, 0));
        Fx.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f);
        Fx.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
        ctx.fx().repeat(20, 15, step -> {
            if (!p.isOnline() || p.isDead() || !ctx.status().hasBuff(p.getUniqueId(), "devil_union")) {
                return;
            }
            wingParticles(p);
            Fx.ring(p.getLocation().add(0, 0.2, 0), 4, Particle.SOUL_FIRE_FLAME, null, 30);
            for (LivingEntity t : ctx.fx().area(p, p.getLocation(), 4)) {
                ctx.fx().hit(p, t, ctx.dmg());
            }
        });
        return true;
    }
}
