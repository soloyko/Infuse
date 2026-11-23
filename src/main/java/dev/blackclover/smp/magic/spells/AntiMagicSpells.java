package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
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

/** Anti-Magic uses no mana. Its passive (30% less spell damage) lives in Fx.hit. */
final class AntiMagicSpells {

    private static final Particle.DustOptions BLACK = Fx.dust(0x0A0A0A, 1.8f);

    private AntiMagicSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.ANTI_MAGIC,
                new Spell("anti_magic_slash", "Anti-Magic Slash", 1, 0, 3, 7, AntiMagicSpells::slash),
                new Spell("anti_magic_deflect", "Deflect", 5, 0, 15, 0, AntiMagicSpells::deflect),
                new Spell("anti_magic_demon_leap", "Demon Leap", 10, 0, 12, 8, AntiMagicSpells::demonLeap),
                new Spell("anti_magic_black_form", "Black Form", 20, 0, 80, 0, AntiMagicSpells::blackForm));
    }

    private static boolean slash(SpellContext ctx) {
        Player p = ctx.caster();
        Location eye = p.getEyeLocation();
        Vector forward = SpellUtil.horizontalLook(p);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        for (double a = -1.2; a <= 1.2; a += 0.15) {
            Location at = eye.clone().add(forward.clone().multiply(2 + Math.cos(a))).add(right.clone().multiply(Math.sin(a) * 2.5));
            Fx.particle(eye.getWorld(), Particle.DUST, at, 2, 0.05, BLACK);
        }
        Fx.particle(eye.getWorld(), Particle.SWEEP_ATTACK, eye.clone().add(forward.clone().multiply(2)), 3, 0.5, null);
        Fx.sound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        for (LivingEntity t : ctx.fx().cone(p, 4.5, 100)) {
            if (ctx.fx().hit(p, t, ctx.dmg()) && ctx.status().stripBuffs(t)) {
                Fx.particle(t.getWorld(), Particle.SMOKE, t.getLocation().add(0, 1, 0), 20, 0.4, null);
            }
        }
        return true;
    }

    private static boolean deflect(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().immune(p, 60);
        ctx.fx().repeat(5, 12, step -> Fx.sphere(p.getLocation().add(0, 1, 0), 1.2, Particle.DUST, BLACK, 15));
        Fx.sound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
        return true;
    }

    private static boolean demonLeap(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 12);
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity t = target.get();
        Vector back = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        back = back.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : back.normalize();
        Optional<Location> spot = ctx.fx().safeSpot(t.getLocation().subtract(back.clone().multiply(1.2)));
        if (spot.isEmpty()) {
            ctx.casting().tell(p, "<red>No room to land next to them.");
            return false;
        }
        Location land = spot.get();
        land.setDirection(t.getLocation().toVector().subtract(land.toVector()));
        Fx.line(p.getWorld(), p.getLocation().add(0, 1, 0), land.clone().add(0, 1, 0), Particle.DUST, BLACK, 0.3);
        p.teleport(land);
        p.setFallDistance(0);
        if (ctx.fx().hit(p, t, ctx.dmg())) {
            ctx.status().silence(t, 60);
        }
        Fx.sound(land, Sound.ENTITY_RAVAGER_ATTACK, 1f, 0.8f);
        return true;
    }

    private static boolean blackForm(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "black_form", 240,
                Fx.pe(PotionEffectType.STRENGTH, 240, 1), Fx.pe(PotionEffectType.SPEED, 240, 1));
        ctx.status().immune(p, 240);
        Fx.sound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.5f);
        ctx.fx().repeat(5, 48, step -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "black_form")) {
                Fx.particle(p.getWorld(), Particle.DUST, p.getLocation().add(0, 1, 0), 10, 0.5, BLACK);
                Fx.particle(p.getWorld(), Particle.SMOKE, p.getLocation().add(0, 1.8, 0), 3, 0.3, null);
            }
        });
        return true;
    }
}
