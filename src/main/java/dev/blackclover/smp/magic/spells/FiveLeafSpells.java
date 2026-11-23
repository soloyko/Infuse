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

/** Devil magic of the Five-Leaf Grimoire. */
final class FiveLeafSpells {

    private static final Particle.DustOptions DEVIL = Fx.dust(0x6B0000, 2.0f);
    private static final Particle.DustOptions SHADOW = Fx.dust(0x150000, 1.6f);

    private FiveLeafSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.FIVE_LEAF,
                new Spell("five_leaf_devils_claw", "Devil's Claw", 1, Role.ATTACK, 13, 1.4, 7,
                        "Three claw marks of devil fire that wither whatever they rake.", FiveLeafSpells::claw),
                new Spell("five_leaf_demon_wings", "Demon Wings", 5, Role.MOBILITY, 24, 11, 0,
                        "Unfold devil wings: launch into the air and glide for 6 seconds.", FiveLeafSpells::wings),
                new Spell("five_leaf_curse_chains", "Curse Chains", 10, Role.CONTROL, 38, 13, 5,
                        "Cursed chains bind up to three foes: rooted and silenced for 3s.", FiveLeafSpells::chains),
                new Spell("five_leaf_devil_union", "Devil Union", 20, Role.ULTIMATE, 90, 90, 2,
                        "Merge with the devil for 15s: power, armour, halved cooldowns, a burning aura.", FiveLeafSpells::devilUnion));
    }

    private static boolean claw(SpellContext ctx) {
        Player p = ctx.caster();
        Location from = ctx.fx().origin(p);
        Vector aim = ctx.fx().aimPoint(p, 30).toVector().subtract(from.toVector()).normalize();
        Vector up = new Vector(0, 1, 0);
        Vector side = aim.clone().crossProduct(up);
        side = side.lengthSquared() < 1e-4 ? new Vector(1, 0, 0) : side.normalize();
        Fx.sounds(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.7f, Sound.ENTITY_RAVAGER_ATTACK, 0.6f, 1.4f);
        for (double off : new double[]{-0.45, 0, 0.45}) {
            Location start = from.clone().add(side.clone().multiply(off));
            ctx.fx().projectile(p, start, aim, ProjectileSpec.of(Particle.DUST, DEVIL, 2.2, 30, 0.8).count(3),
                    t -> {
                        if (ctx.fx().hit(p, t, ctx.dmg() / 3 * 1.4)) {
                            ctx.fx().effect(p, t, PotionEffectType.WITHER, 50, 1);
                        }
                    },
                    end -> Fx.particle(end.getWorld(), Particle.SOUL_FIRE_FLAME, end, 6, 0.2, null));
        }
        return true;
    }

    static void wingParticles(Player p) {
        Location base = p.getLocation().add(0, 1.4, 0);
        Vector back = SpellUtil.horizontalLook(p).multiply(-0.4);
        Vector right = SpellUtil.horizontalLook(p).crossProduct(new Vector(0, 1, 0)).normalize();
        for (double s = 0.2; s <= 1.8; s += 0.18) {
            double lift = Math.sin(s * 1.8) * 0.5;
            for (int side = -1; side <= 1; side += 2) {
                Location at = base.clone().add(back).add(right.clone().multiply(s * side)).add(0, lift, 0);
                Fx.particle(p.getWorld(), Particle.DUST, at, 1, 0.02, DEVIL);
                if (s > 1.4) {
                    Fx.particle(p.getWorld(), Particle.DUST, at.clone().add(0, -0.3, 0), 1, 0.02, SHADOW);
                }
            }
        }
    }

    private static boolean wings(SpellContext ctx) {
        Player p = ctx.caster();
        p.setVelocity(SpellUtil.horizontalLook(p).multiply(1.4).setY(1.3));
        ctx.status().addBuff(p, "no_fall", 160);
        Fx.sounds(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.8f, Sound.ENTITY_PHANTOM_FLAP, 1f, 0.6f);
        ctx.fx().repeat(3, 40, step -> {
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            wingParticles(p);
            if (step > 5 && !p.isOnGround()) {
                Vector v = p.getVelocity();
                Vector glide = p.getLocation().getDirection().setY(0).normalize().multiply(0.75);
                p.setVelocity(new Vector(glide.getX(), Math.max(v.getY(), -0.12), glide.getZ()));
            }
        });
        return true;
    }

    private static boolean chains(SpellContext ctx) {
        Player p = ctx.caster();
        List<LivingEntity> targets = ctx.fx().cone(p, 20, 50).stream()
                .sorted(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())))
                .limit(3).toList();
        if (targets.isEmpty()) {
            ctx.casting().tell(p, "<red>ɴᴏ ᴛᴀʀɢᴇᴛ ɪɴ ꜱɪɢʜᴛ");
            return false;
        }
        Fx.sound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.5f);
        for (LivingEntity t : targets) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.status().root(t, 60);
                ctx.status().silence(t, 60);
            }
            ctx.fx().repeat(4, 15, step -> {
                if (p.isOnline() && t.isValid()) {
                    Fx.line(p.getWorld(), ctx.fx().origin(p), t.getEyeLocation().subtract(0, 0.4, 0), Particle.DUST, SHADOW, 0.3);
                    Fx.ring(t.getLocation().add(0, 0.6, 0), 0.6, Particle.DUST, DEVIL, 10);
                }
            });
        }
        return true;
    }

    private static boolean devilUnion(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.FIVE_LEAF.rgb());
        ctx.status().addBuff(p, "devil_union", 300,
                Fx.pe(PotionEffectType.STRENGTH, 300, 1), Fx.pe(PotionEffectType.RESISTANCE, 300, 0));
        Fx.sounds(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.6f);
        ctx.fx().shockwave(p.getLocation().add(0, 0.2, 0), 6, 8, Particle.SOUL_FIRE_FLAME, null);
        ctx.fx().repeat(10, 30, step -> {
            if (!p.isOnline() || p.isDead() || !ctx.status().hasBuff(p.getUniqueId(), "devil_union")) {
                return;
            }
            wingParticles(p);
            Fx.ring(p.getLocation().add(0, 0.2, 0), 4, Particle.SOUL_FIRE_FLAME, null, 30);
            if (step % 2 == 0) {
                for (LivingEntity t : ctx.fx().area(p, p.getLocation(), 4)) {
                    ctx.fx().hit(p, t, ctx.dmg());
                }
            }
        });
        return true;
    }
}
