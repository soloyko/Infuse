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

final class DarkSpells {

    private static final Particle.DustOptions PURPLE = Fx.dust(0x5A1E99, 1.8f);
    private static final Particle.DustOptions BLACK = Fx.dust(0x111111, 2.0f);

    private DarkSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.DARK,
                new Spell("dark_orb", "Dark Orb", 1, 14, 2.0, 6, DarkSpells::orb),
                new Spell("dark_shadow_cloak", "Shadow Cloak", 5, 25, 18, 0, DarkSpells::cloak),
                new Spell("dark_black_hole", "Black Hole", 10, 45, 15, 2, DarkSpells::blackHole),
                new Spell("dark_dimension_slash", "Dimension Slash", 20, 85, 60, 16, DarkSpells::dimensionSlash));
    }

    private static boolean orb(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.5f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, PURPLE, 1.0, 30, 1.0).count(5),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.BLINDNESS, 20, 0);
                    }
                },
                end -> Fx.particle(end.getWorld(), Particle.SQUID_INK, end, 12, 0.3, null));
        return true;
    }

    private static boolean cloak(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "shadow_cloak", 100,
                Fx.pe(PotionEffectType.INVISIBILITY, 100, 0), Fx.pe(PotionEffectType.SPEED, 100, 1));
        Fx.particle(p.getWorld(), Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 30, 0.5, null);
        Fx.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.7f);
        return true;
    }

    private static boolean blackHole(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = ctx.fx().targetPoint(p, 25);
        Fx.sound(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 0.5f);
        ctx.fx().repeat(10, 6, step -> {
            Fx.sphere(c, 0.8, Particle.DUST, BLACK, 25);
            Fx.ring(c, 3 - step * 0.4, Particle.PORTAL, null, 30);
            for (LivingEntity t : ctx.fx().area(p, c, 6)) {
                Vector pull = c.toVector().subtract(t.getLocation().toVector());
                if (pull.lengthSquared() > 0.25) {
                    ctx.fx().push(p, t, pull.normalize().multiply(0.5).setY(0.1));
                }
                ctx.fx().hit(p, t, ctx.dmg());
            }
        });
        return true;
    }

    private static boolean dimensionSlash(SpellContext ctx) {
        Player p = ctx.caster();
        Location eye = p.getEyeLocation();
        Vector dir = SpellUtil.horizontalLook(p);
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Fx.sound(eye, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.5f);
        Fx.sound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        for (double w = -2; w <= 2; w += 0.5) {
            Location from = eye.clone().add(right.clone().multiply(w)).add(0, -w * 0.3, 0);
            Fx.line(p.getWorld(), from, from.clone().add(dir.clone().multiply(20)), Particle.DUST, BLACK, 0.5);
        }
        for (LivingEntity t : ctx.fx().line(p, eye.clone().subtract(0, 0.6, 0), dir, 20, 2.0, Particle.SWEEP_ATTACK, null)) {
            ctx.fx().hit(p, t, ctx.dmg());
        }
        return true;
    }
}
