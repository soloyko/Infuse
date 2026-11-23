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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PoisonSpells {

    private static final Particle.DustOptions LIME = Fx.dust(0x88CC00, 1.5f);
    private static final Particle.DustOptions MURK = Fx.dust(0x4A6B00, 2.0f);

    private PoisonSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.POISON,
                new Spell("poison_toxic_spit", "Toxic Spit", 1, Role.ATTACK, 11, 1.2, 4,
                        "Spit a glob of venom that keeps eating away at its target.", PoisonSpells::spit),
                new Spell("poison_venom_coat", "Venom Coat", 5, Role.SUPPORT, 20, 14, 0,
                        "For 10s your attacks poison and weaken, and you move faster.", PoisonSpells::venomCoat),
                new Spell("poison_miasma", "Miasma", 10, Role.CONTROL, 38, 14, 1,
                        "A choking cloud that poisons, weakens and slows all inside it.", PoisonSpells::miasma),
                new Spell("poison_plague", "Plague", 20, Role.ULTIMATE, 85, 55, 8,
                        "Infect everyone near you with a plague that spreads between victims.", PoisonSpells::plague));
    }

    private static boolean spit(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1f, 0.8f);
        ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.DUST, LIME, 1.9, 26, 0.85).gravity(0.008).count(4),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.POISON, 80, 1);
                    }
                },
                end -> {
                    Fx.particle(end.getWorld(), Particle.DUST, end, 14, 0.4, MURK);
                    Fx.particle(end.getWorld(), Particle.ITEM_SLIME, end, 6, 0.3, null);
                });
        return true;
    }

    private static boolean venomCoat(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().setVenom(p, 99, 200);
        ctx.status().addBuff(p, "venom_coat", 200, Fx.pe(PotionEffectType.SPEED, 200, 0));
        Fx.sphere(p.getLocation().add(0, 1, 0), 1, Particle.DUST, LIME, 40);
        Fx.sounds(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.2f, Sound.ENTITY_SPIDER_AMBIENT, 0.8f, 1.4f);
        ctx.fx().repeat(10, 20, i -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "venom_coat")) {
                Fx.particle(p.getWorld(), Particle.DUST, p.getLocation().add(0, 1, 0), 4, 0.4, LIME);
            }
        });
        return true;
    }

    private static boolean miasma(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = SpellUtil.groundAt(ctx.fx().aimPoint(p, 26)).add(0, 1, 0);
        Fx.sound(c, Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.4f);
        ctx.fx().repeat(10, 14, i -> {
            Fx.sphere(c, 4.5, Particle.DUST, i % 2 == 0 ? LIME : MURK, 60);
            Fx.particle(c.getWorld(), Particle.ENTITY_EFFECT, c, 10, 2, org.bukkit.Color.fromRGB(0x88CC00));
            for (LivingEntity t : ctx.fx().area(p, c, 4.5)) {
                ctx.fx().effect(p, t, PotionEffectType.POISON, 40, 1);
                ctx.fx().effect(p, t, PotionEffectType.WEAKNESS, 40, 0);
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 40, 0);
                ctx.fx().effect(p, t, PotionEffectType.NAUSEA, 100, 0);
                if (i % 2 == 0) {
                    ctx.fx().hit(p, t, ctx.dmg());
                }
            }
        });
        return true;
    }

    private static void infect(SpellContext ctx, LivingEntity t) {
        Player p = ctx.caster();
        if (ctx.fx().hit(p, t, ctx.dmg())) {
            ctx.fx().effect(p, t, PotionEffectType.WITHER, 120, 1);
            ctx.fx().effect(p, t, PotionEffectType.POISON, 120, 1);
            ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 120, 1);
            ctx.fx().effect(p, t, PotionEffectType.WEAKNESS, 120, 0);
            Fx.particle(t.getWorld(), Particle.DUST, t.getLocation().add(0, 1, 0), 20, 0.4, MURK);
        }
    }

    private static boolean plague(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.POISON.rgb());
        Location c = p.getLocation();
        Fx.sounds(c, Sound.ENTITY_WITHER_AMBIENT, 1f, 1.5f, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.6f, 0.6f);
        ctx.fx().shockwave(c.clone().add(0, 0.3, 0), 12, 10, Particle.DUST, LIME);
        ctx.fx().shockwave(c.clone().add(0, 0.8, 0), 11, 10, Particle.DUST, MURK);
        List<LivingEntity> first = ctx.fx().area(p, c, 12);
        Set<UUID> infected = new HashSet<>();
        for (LivingEntity t : first) {
            infect(ctx, t);
            infected.add(t.getUniqueId());
        }
        // two seconds later the plague jumps from every victim to anyone standing near them
        ctx.fx().later(40, () -> {
            for (LivingEntity carrier : first) {
                if (!carrier.isValid()) {
                    continue;
                }
                for (LivingEntity t : ctx.fx().area(p, carrier.getLocation(), 4)) {
                    if (infected.add(t.getUniqueId())) {
                        Fx.line(t.getWorld(), carrier.getEyeLocation(), t.getEyeLocation(), Particle.DUST, LIME, 0.3);
                        infect(ctx, t);
                    }
                }
            }
        });
        return true;
    }
}
