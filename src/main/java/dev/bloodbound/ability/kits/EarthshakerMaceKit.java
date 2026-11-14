package dev.bloodbound.ability.kits;

import dev.bloodbound.ability.Abilities;
import dev.bloodbound.ability.Ctx;
import dev.bloodbound.ability.WeaponKit;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Tremor passive (slows), Skyward launch active, Meteor shockwave-on-landing ultimate. */
public final class EarthshakerMaceKit implements WeaponKit {
    @Override
    public WeaponType type() {
        return WeaponType.EARTHSHAKER_MACE;
    }

    @Override
    public void onWeaponHit(Ctx ctx, LivingEntity target, EntityDamageByEntityEvent event, boolean ranged) {
        int ticks = (int) Math.round((1.0 + ctx.cfg().num("tremor-seconds-per-tier", 0.5) * ctx.tier()) * 20);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, ctx.tier() >= 4 ? 1 : 0));
    }

    @Override
    public boolean active(Ctx ctx) {
        Player user = ctx.player();
        user.setVelocity(new Vector(0, ctx.cfg().num("skyward-power", 1.6), 0));
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 0.8f);
        user.getWorld().spawnParticle(Particle.GUST, user.getLocation(), 3, 0.3, 0.1, 0.3, 0);
        ctx.plugin().abilities().grantFallImmunity(user, 8000);
        return true;
    }

    @Override
    public boolean ultimate(Ctx ctx) {
        Player user = ctx.player();
        user.setVelocity(new Vector(0, 1.8, 0));
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.6f);
        ctx.plugin().abilities().grantFallImmunity(user, ctx.cfg().millis("meteor-duration", 8000) + 2000);
        double radius = ctx.cfg().num("meteor-radius", 6.0);
        double damage = ctx.cfg().num("meteor-damage", 10.0);
        int maxTicks = (int) (ctx.cfg().millis("meteor-duration", 8000) / 50);
        Abilities.whenLanded(ctx.plugin(), user, maxTicks, () -> {
            Location loc = user.getLocation();
            loc.getWorld().playSound(loc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.7f);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            for (int ring = 1; ring <= 3; ring++) {
                double r = radius * ring / 3;
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 3,
                            0.1, 0.1, 0.1, loc.clone().subtract(0, 1, 0).getBlock().getBlockData());
                }
            }
            for (LivingEntity t : Abilities.targetsAround(user, loc, radius)) {
                Abilities.damage(user, t, damage);
                Abilities.knockAway(loc, t, 0.8, 1.1);
            }
        });
        return true;
    }
}
