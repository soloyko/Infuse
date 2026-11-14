package dev.bloodbound.ability.kits;

import dev.bloodbound.ability.Abilities;
import dev.bloodbound.ability.Ctx;
import dev.bloodbound.ability.WeaponKit;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lifesteal passive, Blood Dash active, Hemorrhage (bleed on hit) ultimate. */
public final class CrimsonBladeKit implements WeaponKit {
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(190, 0, 25), 1.4f);

    private final Map<UUID, Long> hemorrhageUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> bleeds = new HashMap<>();

    @Override
    public WeaponType type() {
        return WeaponType.CRIMSON_BLADE;
    }

    @Override
    public void onWeaponHit(Ctx ctx, LivingEntity target, EntityDamageByEntityEvent event, boolean ranged) {
        Player user = ctx.player();
        double heal = event.getFinalDamage() * ctx.cfg().num("lifesteal-per-tier", 0.04) * ctx.tier();
        AttributeInstance max = user.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && heal > 0) user.setHealth(Math.min(max.getValue(), user.getHealth() + heal));

        Long until = hemorrhageUntil.get(user.getUniqueId());
        if (until != null && System.currentTimeMillis() <= until) bleed(ctx, target);
    }

    @Override
    public boolean active(Ctx ctx) {
        Player user = ctx.player();
        Vector dir = user.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.001) return false;
        dir.normalize().multiply(ctx.cfg().num("dash-power", 1.8)).setY(0.25);
        user.setVelocity(dir);
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        ctx.plugin().abilities().grantFallImmunity(user, 3000);

        double damage = ctx.cfg().num("dash-damage", 6.0);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (++ticks > 12 || !user.isOnline() || user.isDead()) {
                    cancel();
                    return;
                }
                user.getWorld().spawnParticle(Particle.DUST, user.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, BLOOD);
                for (LivingEntity t : Abilities.targetsAround(user, user.getLocation().add(0, 1, 0), 1.8)) {
                    if (!hit.add(t.getUniqueId())) continue;
                    Abilities.damage(user, t, damage);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    t.getWorld().playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
                }
            }
        }.runTaskTimer(ctx.plugin(), 1L, 1L);
        return true;
    }

    @Override
    public boolean ultimate(Ctx ctx) {
        Player user = ctx.player();
        long duration = ctx.cfg().millis("hemorrhage-duration", 6000);
        hemorrhageUntil.put(user.getUniqueId(), System.currentTimeMillis() + duration);
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.8f);
        user.getWorld().playSound(user.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 0.5f);
        user.getWorld().spawnParticle(Particle.DUST, user.getLocation().add(0, 1, 0), 80, 0.7, 1, 0.7, BLOOD);
        new BukkitRunnable() {
            @Override
            public void run() {
                Long until = hemorrhageUntil.get(user.getUniqueId());
                if (until == null || System.currentTimeMillis() > until || !user.isOnline()) {
                    cancel();
                    return;
                }
                user.getWorld().spawnParticle(Particle.DUST, user.getLocation().add(0, 1.2, 0), 4, 0.3, 0.5, 0.3, BLOOD);
            }
        }.runTaskTimer(ctx.plugin(), 0L, 4L);
        return true;
    }

    private void bleed(Ctx ctx, LivingEntity target) {
        BukkitTask old = bleeds.remove(target.getUniqueId());
        if (old != null) old.cancel();
        Player user = ctx.player();
        double damage = ctx.cfg().num("bleed-damage", 1.5);
        int pulses = ctx.cfg().integer("bleed-ticks", 4);
        BukkitTask task = new BukkitRunnable() {
            int count;

            @Override
            public void run() {
                if (++count > pulses || target.isDead() || !target.isValid() || !user.isOnline()) {
                    bleeds.remove(target.getUniqueId());
                    cancel();
                    return;
                }
                Abilities.damage(user, target, damage);
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10, 0.25, 0.4, 0.25, BLOOD);
            }
        }.runTaskTimer(ctx.plugin(), 20L, 20L);
        bleeds.put(target.getUniqueId(), task);
    }

    @Override
    public void clear(Player player) {
        hemorrhageUntil.remove(player.getUniqueId());
    }
}
