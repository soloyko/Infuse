package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.Spell.Role;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

final class PlantSpells {

    private static final Particle.DustOptions GREEN = Fx.dust(0x33BB33, 1.3f);
    private static final Particle.DustOptions VINE = Fx.dust(0x1E6B1E, 1.5f);
    private static final Particle.DustOptions PETAL = Fx.dust(0xFF9ED8, 1.1f);
    private static BlockData leaves;

    private PlantSpells() {
    }

    private static BlockData leaves() {
        if (leaves == null) {
            leaves = Material.FLOWERING_AZALEA_LEAVES.createBlockData();
        }
        return leaves;
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.PLANT,
                new Spell("plant_thorn_volley", "Thorn Volley", 1, Role.ATTACK, 11, 1.2, 3,
                        "A fan of three thorns that slow whatever they pierce.", PlantSpells::thornVolley),
                new Spell("plant_verdant_bloom", "Verdant Bloom", 5, Role.SUPPORT, 28, 16, 0,
                        "Bloom with life: strong regeneration and all bad effects cleansed.", PlantSpells::bloom),
                new Spell("plant_binding_vines", "Binding Vines", 10, Role.CONTROL, 34, 12, 4,
                        "Vines burst up and root your target in place for 3 seconds.", PlantSpells::bindingVines),
                new Spell("plant_thorn_garden", "Thorn Garden", 20, Role.ULTIMATE, 80, 50, 14,
                        "A thicket of thorns roots and shreds everyone in a wide area.", PlantSpells::thornGarden));
    }

    private static boolean thornVolley(SpellContext ctx) {
        Player p = ctx.caster();
        Location from = ctx.fx().origin(p);
        Vector aim = ctx.fx().aimPoint(p, 30).toVector().subtract(from.toVector()).normalize();
        Fx.sound(p.getLocation(), Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.2f, 1.4f);
        for (double spread : new double[]{-0.14, 0, 0.14}) {
            Vector dir = aim.clone().rotateAroundY(spread);
            ctx.fx().projectile(p, from, dir, ProjectileSpec.of(Particle.DUST, GREEN, 2.3, 30, 0.75),
                    t -> {
                        if (ctx.fx().hit(p, t, ctx.dmg())) {
                            ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 40, 1);
                        }
                    },
                    end -> Fx.particle(end.getWorld(), Particle.HAPPY_VILLAGER, end, 4, 0.2, null));
        }
        return true;
    }

    private static boolean bloom(SpellContext ctx) {
        Player p = ctx.caster();
        for (PotionEffect e : List.copyOf(p.getActivePotionEffects())) {
            PotionEffectType t = e.getType();
            if (t == PotionEffectType.POISON || t == PotionEffectType.WITHER || t == PotionEffectType.SLOWNESS
                    || t == PotionEffectType.WEAKNESS || t == PotionEffectType.BLINDNESS || t == PotionEffectType.NAUSEA
                    || t == PotionEffectType.MINING_FATIGUE) {
                p.removePotionEffect(t);
            }
        }
        p.setFireTicks(0);
        ctx.status().addBuff(p, "bloom", 100, Fx.pe(PotionEffectType.REGENERATION, 100, 2));
        SpellUtil.heal(p, 4);
        Fx.sounds(p.getLocation(), Sound.BLOCK_FLOWERING_AZALEA_PLACE, 1.2f, 1.2f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        ctx.fx().repeat(4, 25, i -> {
            Location c = p.getLocation();
            double a = i * 0.5;
            Fx.particle(c.getWorld(), Particle.DUST, c.clone().add(Math.cos(a) * 1.1, 0.2 + i * 0.07, Math.sin(a) * 1.1), 1, 0, PETAL);
            Fx.particle(c.getWorld(), Particle.HAPPY_VILLAGER, c.clone().add(0, 1, 0), 1, 0.5, null);
            if (i % 5 == 0) {
                Fx.particle(c.getWorld(), Particle.CHERRY_LEAVES, c.clone().add(0, 2.2, 0), 6, 0.6, null);
            }
        });
        return true;
    }

    private static void vinesAround(SpellContext ctx, LivingEntity t, int ticks) {
        ctx.fx().repeat(3, ticks / 3, i -> {
            if (!t.isValid()) {
                return;
            }
            Location base = t.getLocation();
            for (double h = 0; h < 2; h += 0.2) {
                double a = h * 4 + i * 0.6;
                Fx.particle(base.getWorld(), Particle.DUST, base.clone().add(Math.cos(a) * 0.55, h, Math.sin(a) * 0.55), 1, 0, VINE);
            }
        });
    }

    private static boolean bindingVines(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 28);
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity t = target.get();
        if (!ctx.fx().hit(p, t, ctx.dmg())) {
            return false;
        }
        ctx.status().root(t, 60);
        vinesAround(ctx, t, 60);
        Location at = t.getLocation();
        BlockDisplay bush = ctx.fx().blockDisplay(at, leaves(), 1.3f, 0.01f, 1.3f, 60);
        ctx.fx().animate(bush, new Transformation(new Vector3f(-0.65f, 0, -0.65f), new Quaternionf(),
                new Vector3f(1.3f, 1.1f, 1.3f), new Quaternionf()), 4, 1);
        Fx.sounds(at, Sound.BLOCK_ROOTS_PLACE, 1.5f, 0.6f, Sound.BLOCK_VINE_STEP, 1.5f, 0.5f);
        ctx.fx().repeat(20, 3, i -> {
            if (t.isValid()) {
                ctx.fx().hit(p, t, 1.0);
            }
        });
        return true;
    }

    private static boolean thornGarden(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = SpellUtil.groundAt(ctx.fx().aimPoint(p, 30));
        ctx.fx().announce(p, ctx.spell(), GrimoireType.PLANT.rgb());
        Fx.sounds(c, Sound.BLOCK_SWEET_BERRY_BUSH_PLACE, 2f, 0.5f, Sound.BLOCK_ROOTS_BREAK, 2f, 0.4f);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int k = 0; k < 22; k++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * 9;
            Location at = SpellUtil.groundAt(c.clone().add(Math.cos(a) * d, 1.5, Math.sin(a) * d));
            ctx.fx().later(rnd.nextInt(8), () -> {
                BlockDisplay b = ctx.fx().blockDisplay(at, leaves(), 0.9f, 0.01f, 0.9f, 70);
                ctx.fx().animate(b, new Transformation(new Vector3f(-0.45f, 0, -0.45f), new Quaternionf(),
                        new Vector3f(0.9f, 1.6f, 0.9f), new Quaternionf()), 5, 1);
                Fx.particle(at.getWorld(), Particle.DUST, at.clone().add(0, 0.5, 0), 8, 0.3, VINE);
            });
        }
        List<LivingEntity> caught = ctx.fx().area(p, c, 9);
        for (LivingEntity t : caught) {
            ctx.status().root(t, 60);
            vinesAround(ctx, t, 60);
        }
        Fx.self(p, PotionEffectType.REGENERATION, 100, 1);
        double perPulse = ctx.dmg() / 4.0;
        ctx.fx().repeat(15, 4, i -> {
            for (LivingEntity t : caught) {
                if (t.isValid()) {
                    ctx.fx().hit(p, t, perPulse);
                    Fx.particle(t.getWorld(), Particle.DUST, t.getLocation().add(0, 1, 0), 10, 0.3, GREEN);
                }
            }
            Fx.sound(c, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.5f, 0.6f);
        });
        return true;
    }
}
