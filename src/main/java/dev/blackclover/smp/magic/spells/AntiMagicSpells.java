package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.Spell.Role;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import dev.blackclover.smp.pack.PackItems;
import dev.blackclover.smp.pack.PackModels;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Anti-Magic uses no mana. Its passive (30% less spell damage) lives in Fx.hit. */
final class AntiMagicSpells {

    private static final Particle.DustOptions BLACK = Fx.dust(0x0A0A0A, 1.8f);

    private AntiMagicSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.ANTI_MAGIC,
                new Spell("anti_magic_slash", "Demon-Slayer Slash", 1, Role.ATTACK, 0, 2.2, 8,
                        "A huge sweep of the demon-slayer sword that strips enemy buffs.", AntiMagicSpells::slash),
                new Spell("anti_magic_black_dash", "Black Dash", 5, Role.MOBILITY, 0, 6, 5,
                        "Rush forward sword-first, cutting through anyone in your way.", AntiMagicSpells::blackDash),
                new Spell("anti_magic_leap", "Anti-Magic Leap", 10, Role.CONTROL, 0, 11, 8,
                        "Leap onto your target and smash them, cancelling their magic for 3s.", AntiMagicSpells::leap),
                new Spell("anti_magic_black_form", "Black Form", 20, Role.ULTIMATE, 0, 70, 0,
                        "Black Asta: strength, speed and full immunity to magic for 12s.", AntiMagicSpells::blackForm));
    }

    /** The Demon-Slayer greatsword sweeps through the cone in front of the caster. */
    private static void swordSweep(SpellContext ctx) {
        Player p = ctx.caster();
        float yaw = (float) Math.toRadians(p.getLocation().getYaw());
        Location at = p.getLocation().add(0, 1.1, 0).add(SpellUtil.horizontalLook(p).multiply(0.4));
        ItemDisplay sword = ctx.fx().spawnModel(at, PackItems.model(Material.NETHERITE_SWORD, PackModels.DEMON_SLAYER), 1.0f,
                new Vector(0, 1, 0), false);
        sword.setTransformation(sweepTransform(yaw, -1.1f));
        ctx.fx().repeat(1, 7, step -> {
            if (!sword.isValid()) {
                return;
            }
            sword.setInterpolationDelay(0);
            sword.setInterpolationDuration(1);
            sword.setTransformation(sweepTransform(yaw, -1.1f + step * (2.2f / 6)));
        });
        ctx.fx().later(9, () -> Fx.removeModel(sword));
    }

    /** Blade (+Y in the model) laid flat and pointing along the caster's yaw, offset by {@code sweep} radians. */
    private static Transformation sweepTransform(float yaw, float sweep) {
        Quaternionf rot = new Quaternionf().rotateY(-yaw + sweep).rotateX((float) Math.PI / 2);
        return new Transformation(new Vector3f(), rot, new Vector3f(1f, 1f, 1f), new Quaternionf());
    }

    private static boolean slash(SpellContext ctx) {
        Player p = ctx.caster();
        Location eye = p.getEyeLocation();
        Vector forward = SpellUtil.horizontalLook(p);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        for (double a = -1.2; a <= 1.2; a += 0.12) {
            Location at = eye.clone().add(forward.clone().multiply(2.2 + Math.cos(a))).add(right.clone().multiply(Math.sin(a) * 2.8));
            Fx.particle(eye.getWorld(), Particle.DUST, at, 2, 0.05, BLACK);
        }
        Fx.particle(eye.getWorld(), Particle.SWEEP_ATTACK, eye.clone().add(forward.clone().multiply(2)), 3, 0.5, null);
        Fx.sounds(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f, Sound.ITEM_MACE_SMASH_AIR, 0.8f, 0.8f);
        swordSweep(ctx);
        for (LivingEntity t : ctx.fx().cone(p, 5, 110)) {
            if (ctx.fx().hit(p, t, ctx.dmg()) && ctx.status().stripBuffs(t)) {
                Fx.particle(t.getWorld(), Particle.SMOKE, t.getLocation().add(0, 1, 0), 20, 0.4, null);
            }
        }
        return true;
    }

    private static boolean blackDash(SpellContext ctx) {
        Player p = ctx.caster();
        Vector dir = SpellUtil.horizontalLook(p);
        p.setVelocity(dir.clone().multiply(2.3).setY(0.2));
        ctx.status().immune(p, 20);
        swordSweep(ctx);
        Fx.sounds(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.5f, 1.5f, Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 0.6f);
        Set<UUID> hit = new HashSet<>();
        ctx.fx().repeat(1, 10, i -> {
            Fx.particle(p.getWorld(), Particle.DUST, p.getLocation().add(0, 1, 0), 10, 0.4, BLACK);
            for (LivingEntity t : ctx.fx().area(p, p.getLocation(), 2)) {
                if (hit.add(t.getUniqueId()) && ctx.fx().hit(p, t, ctx.dmg())) {
                    ctx.fx().knockback(p, t, p.getLocation(), 1.0, 0.4);
                }
            }
        });
        return true;
    }

    private static boolean leap(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 16);
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity t = target.get();
        Vector back = t.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        back = back.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : back.normalize();
        Optional<Location> spot = ctx.fx().safeSpot(t.getLocation().subtract(back.clone().multiply(1.2)));
        if (spot.isEmpty()) {
            ctx.casting().tell(p, "<red>ɴᴏ ʀᴏᴏᴍ ᴛᴏ ʟᴀɴᴅ");
            return false;
        }
        Location land = spot.get();
        land.setDirection(t.getLocation().toVector().subtract(land.toVector()));
        Fx.line(p.getWorld(), p.getLocation().add(0, 1, 0), land.clone().add(0, 1, 0), Particle.DUST, BLACK, 0.3);
        p.teleport(land);
        p.setFallDistance(0);
        ctx.fx().shockwave(land.clone().add(0, 0.2, 0), 3, 4, Particle.DUST, BLACK);
        Fx.sounds(land, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f, Sound.ENTITY_RAVAGER_ATTACK, 1f, 0.7f);
        if (ctx.fx().hit(p, t, ctx.dmg())) {
            ctx.status().silence(t, 60);
            ctx.status().stripBuffs(t);
            ctx.fx().push(p, t, new Vector(0, 0.6, 0));
        }
        return true;
    }

    private static boolean blackForm(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), 0x444444);
        ctx.status().addBuff(p, "black_form", 240,
                Fx.pe(PotionEffectType.STRENGTH, 240, 1), Fx.pe(PotionEffectType.SPEED, 240, 1));
        ctx.status().immune(p, 240);
        Fx.sounds(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.5f, Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.2f);
        ctx.fx().shockwave(p.getLocation().add(0, 0.2, 0), 6, 6, Particle.DUST, BLACK);
        ctx.fx().repeat(5, 48, step -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "black_form")) {
                Location at = p.getLocation();
                Fx.particle(p.getWorld(), Particle.DUST, at.clone().add(0, 1, 0), 12, 0.5, BLACK);
                Fx.particle(p.getWorld(), Particle.SMOKE, at.clone().add(0, 1.9, 0), 4, 0.3, null);
                // one black wing of the devil
                for (double s = 0.2; s <= 1.4; s += 0.2) {
                    Vector side = SpellUtil.horizontalLook(p).crossProduct(new Vector(0, 1, 0)).normalize().multiply(-s);
                    Fx.particle(p.getWorld(), Particle.DUST, at.clone().add(0, 1.5 + Math.sin(s * 2) * 0.3, 0).add(side), 1, 0.02, BLACK);
                }
            }
        });
        return true;
    }
}
