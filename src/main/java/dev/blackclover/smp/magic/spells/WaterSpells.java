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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class WaterSpells {

    private static final Particle.DustOptions BLUE = Fx.dust(0x3399FF, 1.4f);
    private static final Particle.DustOptions DEEP = Fx.dust(0x1A5FCC, 2.0f);
    private static final Particle.DustOptions FOAM = Fx.dust(0xCFEFFF, 1.2f);

    private WaterSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.WATER,
                new Spell("water_bullets", "Water Bullets", 1, Role.ATTACK, 12, 1.4, 2.5,
                        "Three high-pressure bullets in a rapid burst.", WaterSpells::bullets),
                new Spell("water_bubble_barrier", "Bubble Barrier", 5, Role.DEFENSE, 28, 18, 0,
                        "A water sphere: 6 absorption hearts, no knockback, cleanses fire and poison.", WaterSpells::barrier),
                new Spell("water_tidal_wave", "Tidal Wave", 10, Role.CONTROL, 35, 12, 6,
                        "A wall of water rolls forward, sweeping enemies away and slowing them.", WaterSpells::tidalWave),
                new Spell("water_sea_dragon", "Sea Dragon's Roar", 20, Role.ULTIMATE, 80, 50, 14,
                        "A water dragon tears through a long line of enemies and heals you.", WaterSpells::seaDragon));
    }

    private static boolean bullets(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().repeat(3, 3, i -> {
            if (!p.isOnline()) {
                return;
            }
            Fx.sound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.7f, 1.6f + i * 0.1f);
            ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.DUST, BLUE, 2.6, 32, 0.75).count(2),
                    t -> {
                        if (ctx.fx().hit(p, t, ctx.dmg())) {
                            ctx.fx().knockback(p, t, p.getLocation(), 0.45, 0.12);
                        }
                    },
                    end -> Fx.particle(end.getWorld(), Particle.SPLASH, end, 14, 0.3, null));
        });
        return true;
    }

    private static boolean barrier(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "bubble_barrier", 120, Fx.pe(PotionEffectType.ABSORPTION, 120, 2));
        p.setFireTicks(0);
        p.removePotionEffect(PotionEffectType.POISON);
        p.removePotionEffect(PotionEffectType.WITHER);
        var kb = p.getAttribute(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE);
        var key = new org.bukkit.NamespacedKey(ctx.fx().plugin(), "bubble_barrier");
        if (kb != null) {
            kb.removeModifier(key);
            kb.addTransientModifier(new org.bukkit.attribute.AttributeModifier(key, 1.0,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            ctx.fx().later(120, () -> kb.removeModifier(key));
        }
        Fx.sounds(p.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2f, 1f, Sound.ITEM_BUCKET_EMPTY, 1f, 1.2f);
        ctx.fx().repeat(3, 40, i -> {
            if (!ctx.status().hasBuff(p.getUniqueId(), "bubble_barrier")) {
                return;
            }
            Location c = p.getLocation().add(0, 1, 0);
            for (int k = 0; k < 14; k++) {
                double a = i * 0.25 + k * Math.PI * 2 / 14;
                double y = Math.sin(i * 0.2 + k) * 0.9;
                double rad = Math.sqrt(Math.max(0, 1.6 - y * y)) ;
                Fx.particle(c.getWorld(), Particle.DUST, c.clone().add(Math.cos(a) * rad, y, Math.sin(a) * rad), 1, 0, k % 3 == 0 ? FOAM : BLUE);
            }
        });
        return true;
    }

    private static boolean tidalWave(SpellContext ctx) {
        Player p = ctx.caster();
        Vector forward = SpellUtil.horizontalLook(p);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location base = p.getLocation();
        Fx.sounds(base, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.5f, Sound.AMBIENT_UNDERWATER_ENTER, 1f, 0.7f);
        Set<UUID> hit = new HashSet<>();
        ctx.fx().repeat(1, 14, i -> {
            Location front = base.clone().add(forward.clone().multiply(1 + i));
            for (double w = -4; w <= 4; w += 0.5) {
                for (double h = 0; h <= 2.4 - Math.abs(w) * 0.15; h += 0.6) {
                    Location at = front.clone().add(right.clone().multiply(w)).add(0, h, 0);
                    Fx.particle(at.getWorld(), Particle.DUST, at, 1, 0.05, h > 1.6 ? FOAM : BLUE);
                }
                Fx.particle(front.getWorld(), Particle.SPLASH, front.clone().add(right.clone().multiply(w)).add(0, 2, 0), 2, 0.1, null);
            }
            for (LivingEntity t : ctx.fx().area(p, front.clone().add(0, 1, 0), 4.5)) {
                Vector off = t.getLocation().toVector().subtract(front.toVector());
                if (Math.abs(off.dot(forward)) > 1.5) {
                    continue;
                }
                t.setFireTicks(0);
                ctx.fx().push(p, t, forward.clone().multiply(0.9).setY(0.25));
                if (hit.add(t.getUniqueId()) && ctx.fx().hit(p, t, ctx.dmg())) {
                    ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 70, 1);
                }
            }
        });
        return true;
    }

    private static boolean seaDragon(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.WATER.rgb());
        Location from = ctx.fx().origin(p);
        Vector dir = ctx.fx().aimPoint(p, 32).toVector().subtract(from.toVector()).normalize();
        Fx.sounds(from, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.4f, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 0.7f);
        Set<UUID> hit = new HashSet<>();
        ctx.fx().repeat(1, 12, i -> {
            double len = (i + 1) * 2.8;
            Location head = from.clone().add(dir.clone().multiply(len));
            Fx.helix(from.clone().add(dir.clone().multiply(Math.max(0, len - 4))), dir, 4, 1.1, 1.5, Particle.DUST, DEEP);
            Fx.helix(from.clone().add(dir.clone().multiply(Math.max(0, len - 4))), dir, 4, 0.5, 1.5, Particle.DUST, FOAM);
            Fx.particle(head.getWorld(), Particle.SPLASH, head, 30, 0.6, null);
            Fx.particle(head.getWorld(), Particle.BUBBLE_POP, head, 10, 0.5, null);
            for (LivingEntity t : ctx.fx().area(p, head, 2.4)) {
                ctx.fx().push(p, t, dir.clone().multiply(0.8).setY(0.3));
                if (hit.add(t.getUniqueId()) && ctx.fx().hit(p, t, ctx.dmg())) {
                    Fx.lifesteal(p, ctx.dmg(8) / 2);
                }
            }
        });
        Fx.lifesteal(p, 4);
        return true;
    }
}
