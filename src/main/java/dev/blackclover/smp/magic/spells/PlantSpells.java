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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

final class PlantSpells {

    private static final Particle.DustOptions GREEN = Fx.dust(0x33BB33, 1.3f);
    private static final Particle.DustOptions VINE = Fx.dust(0x1E6B1E, 1.5f);

    private PlantSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.PLANT,
                new Spell("plant_thorn_shot", "Thorn Shot", 1, 12, 1.5, 4, PlantSpells::thornShot),
                new Spell("plant_healing_bloom", "Healing Bloom", 5, 30, 18, 6, PlantSpells::healingBloom),
                new Spell("plant_binding_vines", "Binding Vines", 10, 35, 14, 3, PlantSpells::bindingVines),
                new Spell("plant_thorn_forest", "Thorn Forest", 20, 80, 60, 12, PlantSpells::thornForest));
    }

    private static boolean thornShot(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, GREEN, 2.0, 30, 0.8),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 40, 0);
                    }
                }, null);
        return true;
    }

    private static boolean healingBloom(SpellContext ctx) {
        Player p = ctx.caster();
        SpellUtil.heal(p, ctx.dmg());
        ctx.status().addBuff(p, "healing_bloom", 100, Fx.pe(PotionEffectType.REGENERATION, 100, 0));
        Fx.particle(p.getWorld(), Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.6, null);
        Fx.particle(p.getWorld(), Particle.CHERRY_LEAVES, p.getLocation().add(0, 2, 0), 20, 0.8, null);
        Fx.sound(p.getLocation(), Sound.BLOCK_FLOWERING_AZALEA_PLACE, 1f, 1.2f);
        return true;
    }

    private static boolean bindingVines(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 25);
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity t = target.get();
        if (ctx.fx().hit(p, t, ctx.dmg())) {
            ctx.status().root(t, 60);
        }
        Fx.sound(t.getLocation(), Sound.BLOCK_ROOTS_PLACE, 1f, 0.7f);
        ctx.fx().repeat(4, 15, step -> {
            Location base = t.getLocation();
            for (double h = 0; h < 2; h += 0.25) {
                double a = h * 4 + step;
                Fx.particle(base.getWorld(), Particle.DUST, base.clone().add(Math.cos(a) * 0.6, h, Math.sin(a) * 0.6), 1, 0, VINE);
            }
        });
        return true;
    }

    private static boolean thornForest(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        List<LivingEntity> caught = ctx.fx().area(p, c, 7);
        for (LivingEntity t : caught) {
            ctx.status().root(t, 60);
        }
        Fx.self(p, PotionEffectType.REGENERATION, 80, 1);
        Fx.sound(c, Sound.BLOCK_SWEET_BERRY_BUSH_PLACE, 1.5f, 0.5f);
        double perPulse = ctx.dmg() / 4.0;
        ctx.fx().repeat(20, 4, step -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 25; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = rnd.nextDouble() * 7;
                Location at = c.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                for (double h = 0; h < 1.5; h += 0.3) {
                    Fx.particle(at.getWorld(), Particle.DUST, at.clone().add(0, h, 0), 1, 0.05, VINE);
                }
            }
            for (LivingEntity t : caught) {
                ctx.fx().hit(p, t, perPulse);
            }
        });
        return true;
    }
}
