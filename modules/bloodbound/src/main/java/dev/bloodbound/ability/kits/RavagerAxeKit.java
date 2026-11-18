package dev.bloodbound.ability.kits;

import dev.bloodbound.ability.Abilities;
import dev.bloodbound.ability.Ctx;
import dev.bloodbound.ability.WeaponKit;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sunder passive, Ground Slam active, Berserk ultimate (strong, but takes more damage). */
public final class RavagerAxeKit implements WeaponKit {
    private final Map<UUID, Long> berserkUntil = new HashMap<>();

    @Override
    public WeaponType type() {
        return WeaponType.RAVAGER_AXE;
    }

    @Override
    public void onWeaponHit(Ctx ctx, LivingEntity target, EntityDamageByEntityEvent event, boolean ranged) {
        AttributeInstance armor = target.getAttribute(Attribute.ARMOR);
        if (armor != null && armor.getValue() > 0) {
            double bonus = armor.getValue() * ctx.cfg().num("sunder-per-armor-per-tier", 0.04) * ctx.tier();
            event.setDamage(event.getDamage() + bonus);
        }
        if (target instanceof Player p && p.isBlocking()) {
            int ticks = (int) (ctx.cfg().millis("shield-stun", 5000) / 50);
            p.setCooldown(Material.SHIELD, ticks);
            p.clearActiveItem();
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.9f);
        }
    }

    @Override
    public boolean active(Ctx ctx) {
        Player user = ctx.player();
        @SuppressWarnings("deprecation")
        boolean onGround = user.isOnGround();
        if (!onGround) return false;
        Vector forward = user.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() > 0.001) forward.normalize().multiply(0.6);
        user.setVelocity(forward.setY(1.0));
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);
        ctx.plugin().abilities().grantFallImmunity(user, 5000);

        double radius = ctx.cfg().num("slam-radius", 5.0);
        double damage = ctx.cfg().num("slam-damage", 7.0);
        Abilities.whenLanded(ctx.plugin(), user, 80, () -> {
            var loc = user.getLocation();
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 4, radius / 3, 0.2, radius / 3, 0);
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 80, radius / 2, 0.2, radius / 2,
                    loc.clone().subtract(0, 1, 0).getBlock().getBlockData());
            for (LivingEntity t : Abilities.targetsAround(user, loc, radius)) {
                Abilities.damage(user, t, damage);
                Abilities.knockAway(loc, t, 0.6, 0.8);
            }
        });
        return true;
    }

    @Override
    public boolean ultimate(Ctx ctx) {
        Player user = ctx.player();
        long duration = ctx.cfg().millis("berserk-duration", 8000);
        int ticks = (int) (duration / 50);
        berserkUntil.put(user.getUniqueId(), System.currentTimeMillis() + duration);
        user.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
        user.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
        user.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, user.getLocation().add(0, 1.5, 0), 12, 0.6, 0.6, 0.6, 0);
        return true;
    }

    @Override
    public void onOwnerDamaged(Ctx ctx, EntityDamageEvent event) {
        Long until = berserkUntil.get(ctx.player().getUniqueId());
        if (until != null && System.currentTimeMillis() <= until) {
            event.setDamage(event.getDamage() * (1 + ctx.cfg().num("berserk-damage-taken", 0.25)));
        }
    }

    @Override
    public void clear(Player player) {
        berserkUntil.remove(player.getUniqueId());
    }
}
