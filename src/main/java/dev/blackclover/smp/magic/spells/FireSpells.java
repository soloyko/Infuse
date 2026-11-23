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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class FireSpells {

    private static final Particle.DustOptions EMBER = Fx.dust(0xFF7A1A, 1.4f);
    private static final Particle.DustOptions CORE = Fx.dust(0xFFD25A, 1.0f);

    private FireSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.FIRE,
                new Spell("fire_flame_bolt", "Flame Bolt", 1, Role.ATTACK, 12, 1.2, 5,
                        "A blazing bolt that explodes on impact and sets foes alight.", FireSpells::flameBolt),
                new Spell("fire_blaze_dash", "Blaze Dash", 5, Role.MOBILITY, 22, 7, 2,
                        "Dash forward on flames, leaving a burning trail behind you.", FireSpells::blazeDash),
                new Spell("fire_inferno_pillar", "Inferno Pillar", 10, Role.CONTROL, 35, 11, 7,
                        "A column of fire erupts under your target and hurls them skyward.", FireSpells::infernoPillar),
                new Spell("fire_salamander_nova", "Salamander Nova", 20, Role.ULTIMATE, 80, 50, 11,
                        "Explode in a ring of fire, then burn everything near you for 8s.", FireSpells::salamanderNova));
    }

    private static void burst(Location at) {
        Fx.particle(at.getWorld(), Particle.FLAME, at, 25, 0.5, null);
        Fx.particle(at.getWorld(), Particle.LAVA, at, 6, 0.3, null);
        Fx.particle(at.getWorld(), Particle.EXPLOSION, at, 1, 0, null);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
    }

    private static boolean flameBolt(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sounds(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.1f, Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.4f);
        ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.FLAME, null, 1.9, 32, 0.9).count(6),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        t.setFireTicks(Math.max(t.getFireTicks(), 70));
                    }
                },
                end -> {
                    burst(end);
                    for (LivingEntity t : ctx.fx().area(p, end, 2.5)) {
                        if (ctx.fx().hit(p, t, ctx.dmg() * 0.5)) {
                            t.setFireTicks(Math.max(t.getFireTicks(), 50));
                        }
                    }
                });
        return true;
    }

    private static boolean blazeDash(SpellContext ctx) {
        Player p = ctx.caster();
        Vector dir = SpellUtil.horizontalLook(p);
        p.setVelocity(dir.clone().multiply(2.0).setY(0.25));
        Fx.self(p, PotionEffectType.FIRE_RESISTANCE, 100, 0);
        Fx.sounds(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f, Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 0.8f);
        List<Location> trail = new ArrayList<>();
        ctx.fx().repeat(1, 10, step -> {
            Location at = p.getLocation().add(0, 0.2, 0);
            trail.add(at);
            Fx.particle(p.getWorld(), Particle.FLAME, at, 10, 0.3, null);
        });
        // the trail burns for 3 seconds
        ctx.fx().repeat(5, 12, step -> {
            Set<UUID> burnt = new HashSet<>();
            for (Location at : trail) {
                Fx.particle(at.getWorld(), Particle.FLAME, at, 2, 0.25, null);
                Fx.particle(at.getWorld(), Particle.DUST, at, 1, 0.2, EMBER);
                if (step % 4 == 0) {
                    for (LivingEntity t : ctx.fx().area(p, at, 1.4)) {
                        if (burnt.add(t.getUniqueId()) && ctx.fx().hit(p, t, ctx.dmg())) {
                            t.setFireTicks(Math.max(t.getFireTicks(), 60));
                        }
                    }
                }
            }
        });
        return true;
    }

    private static boolean infernoPillar(SpellContext ctx) {
        Player p = ctx.caster();
        Location target = SpellUtil.groundAt(ctx.fx().aimPoint(p, 28));
        Fx.sound(target, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.6f);
        ctx.fx().repeat(2, 6, i -> Fx.ring(target.clone().add(0, 0.1, 0), 2.2, Particle.DUST, EMBER, 24));
        ctx.fx().later(12, () -> {
            Fx.sounds(target, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
            ctx.fx().repeat(1, 10, i -> {
                for (double h = 0; h < 7; h += 0.35) {
                    double a = h * 1.6 + i * 0.7;
                    double rad = 1.3 - h * 0.08;
                    Location at = target.clone().add(Math.cos(a) * rad, h, Math.sin(a) * rad);
                    Fx.particle(at.getWorld(), Particle.FLAME, at, 1, 0.05, null);
                    if (i % 3 == 0) {
                        Fx.particle(at.getWorld(), Particle.DUST, at, 1, 0.1, CORE);
                    }
                }
            });
            for (LivingEntity t : ctx.fx().area(p, target, 2.6)) {
                if (ctx.fx().hit(p, t, ctx.dmg())) {
                    t.setFireTicks(Math.max(t.getFireTicks(), 100));
                    ctx.fx().push(p, t, new Vector(0, 1.05, 0));
                }
            }
        });
        return true;
    }

    private static boolean salamanderNova(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.FIRE.rgb());
        Fx.sounds(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f, Sound.ENTITY_BLAZE_DEATH, 1f, 0.6f, Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);
        Fx.particle(c.getWorld(), Particle.EXPLOSION_EMITTER, c.clone().add(0, 1, 0), 1, 0, null);
        ctx.fx().shockwave(c.clone().add(0, 0.3, 0), 8, 8, Particle.FLAME, null);
        ctx.fx().shockwave(c.clone().add(0, 0.6, 0), 7, 8, Particle.DUST, EMBER);
        for (LivingEntity t : ctx.fx().area(p, c, 8)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                t.setFireTicks(Math.max(t.getFireTicks(), 140));
                ctx.fx().knockback(p, t, c, 1.4, 0.55);
            }
        }
        ctx.status().addBuff(p, "salamander", 160, Fx.pe(PotionEffectType.FIRE_RESISTANCE, 160, 0),
                Fx.pe(PotionEffectType.STRENGTH, 160, 0));
        ctx.fx().repeat(10, 16, i -> {
            if (!p.isOnline() || p.isDead() || !ctx.status().hasBuff(p.getUniqueId(), "salamander")) {
                return;
            }
            Location at = p.getLocation();
            for (int k = 0; k < 3; k++) {
                double a = i * 0.6 + k * Math.PI * 2 / 3;
                Fx.particle(at.getWorld(), Particle.FLAME, at.clone().add(Math.cos(a) * 1.2, 1 + k * 0.4, Math.sin(a) * 1.2), 3, 0.05, null);
            }
            if (i % 2 == 0) {
                for (LivingEntity t : ctx.fx().area(p, at, 4)) {
                    if (ctx.fx().hit(p, t, ctx.dmg(2))) {
                        t.setFireTicks(Math.max(t.getFireTicks(), 40));
                    }
                }
            }
        });
        return true;
    }
}
