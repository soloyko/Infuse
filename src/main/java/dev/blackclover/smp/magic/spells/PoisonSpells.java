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

final class PoisonSpells {

    private static final Particle.DustOptions LIME = Fx.dust(0x88CC00, 1.5f);

    private PoisonSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.POISON,
                new Spell("poison_toxic_spit", "Toxic Spit", 1, 12, 1.5, 3, PoisonSpells::spit),
                new Spell("poison_venom_blade", "Venom Blade", 5, 20, 12, 0, PoisonSpells::venomBlade),
                new Spell("poison_toxic_cloud", "Toxic Cloud", 10, 40, 15, 0, PoisonSpells::toxicCloud),
                new Spell("poison_plague", "Plague", 20, 85, 60, 6, PoisonSpells::plague));
    }

    private static boolean spit(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1f, 1f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, LIME, 1.6, 25, 0.8).gravity(0.01),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.POISON, 80, 1);
                    }
                }, null);
        return true;
    }

    private static boolean venomBlade(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().setVenom(p, 3, 200);
        ctx.status().addBuff(p, "venom_blade", 200);
        Fx.particle(p.getWorld(), Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.5, LIME);
        Fx.sound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.2f);
        return true;
    }

    private static boolean toxicCloud(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = ctx.fx().targetPoint(p, 25);
        Fx.sound(c, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
        ctx.fx().repeat(10, 12, step -> {
            Fx.sphere(c, 4, Particle.DUST, LIME, 40);
            for (LivingEntity t : ctx.fx().area(p, c, 4)) {
                ctx.fx().effect(p, t, PotionEffectType.POISON, 40, 1);
                ctx.fx().effect(p, t, PotionEffectType.NAUSEA, 100, 0);
            }
        });
        return true;
    }

    private static boolean plague(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        Fx.sound(c, Sound.ENTITY_WITHER_AMBIENT, 1f, 1.5f);
        ctx.fx().repeat(1, 10, step -> Fx.ring(c.clone().add(0, 0.3, 0), 1 + step, Particle.DUST, LIME, 20 + step * 6));
        for (LivingEntity t : ctx.fx().area(p, c, 10)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().effect(p, t, PotionEffectType.WITHER, 120, 1);
                ctx.fx().effect(p, t, PotionEffectType.POISON, 120, 0);
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 120, 0);
            }
        }
        return true;
    }
}
