package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cinderwing, the Phoenix Sword: fire everywhere, and it won't let you die.
 * Passive Rebirth, on-hit Searing Edge, RC Ember Volley, Sneak+RC Firestorm.
 */
public final class PhoenixSword extends SwordAbility implements Listener {

    private static final long REBIRTH_COOLDOWN_MS = 5 * 60_000L;

    private final NamespacedKey fireballKey;
    /** Player -> time Rebirth is ready again. */
    private final Map<UUID, Long> rebirthReadyAt = new HashMap<>();

    public PhoenixSword(CustomsPlugin plugin) {
        super(plugin, SwordType.PHOENIX);
        this.fireballKey = new NamespacedKey(plugin, "inferno_fireball");
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.FIRE_RESISTANCE, 0, 0));
    }

    /** Rebirth: a killing blow while holding Cinderwing revives you instead (5 minute cooldown). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.WORLD_BORDER || cause == EntityDamageEvent.DamageCause.SUICIDE) {
            return;
        }
        if (plugin.items().heldType(player) != type || !plugin.canUse(player, type)
                || event.getFinalDamage() < player.getHealth() + player.getAbsorptionAmount()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (rebirthReadyAt.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        event.setCancelled(true);
        rebirthReadyAt.put(player.getUniqueId(), now + REBIRTH_COOLDOWN_MS);
        reborn(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                tell(player, Text.plain("🔥 Rebirth is ready again.", type.color()));
                player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.6f);
            }
        }, REBIRTH_COOLDOWN_MS / 50);
    }

    private void reborn(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(Math.max(1, (max == null ? 20 : max.getValue()) / 2));
        player.setFireTicks(0);
        player.setFallDistance(0);
        applyEffect(player, PotionEffectType.REGENERATION, 100, 1);
        applyEffect(player, PotionEffectType.FIRE_RESISTANCE, 200, 0);
        Location at = player.getLocation().add(0, 1, 0);
        Fx.burst(at, Particle.FLAME, 90, 0.35);
        Fx.burst(at, Particle.LAVA, 12, 0.2);
        Fx.ring(player.getLocation().add(0, 0.2, 0), 2.5, 30, p -> Fx.particle(p, Particle.FLAME, 2, 0.05, 0.05));
        Fx.sound(at, Sound.ITEM_TOTEM_USE, 0.7f, 1.4f);
        Fx.sound(at, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.5f);
        // wings of fire unfold from your back, and a ring of flame bursts up around you
        Vector back = player.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < 1.0E-4) {
            back = new Vector(0, 0, 1);
        }
        back.normalize().multiply(-1);
        Vector side = new Vector(-back.getZ(), 0, back.getX());
        for (int w = -1; w <= 1; w += 2) {
            Vector out = side.clone().multiply(w).add(back.clone().multiply(0.35)).normalize();
            Location wingRoot = at.clone().add(back.clone().multiply(0.3)).add(0, 0.6, 0);
            fx().slash(wingRoot.clone().add(out.clone().multiply(0.3)), out.clone().setY(0.55), w * 1.2, 0xFF8A2A, 2.4f, 6);
            int ww = w;
            displays().later(3, () -> fx().slash(wingRoot.clone().add(out.clone().multiply(0.2)), out.clone().setY(0.9), ww * 1.3,
                    0xFFD24A, 2.0f, 6));
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            fx().flamePillar(Combat.ground(player.getLocation().add(Math.cos(a) * 2.6, 1.5, Math.sin(a) * 2.6)), 0.9f, 2.2f, 16, false);
        }
        fx().beam(player.getLocation(), player.getLocation().add(0, 9, 0), 0xFFB52E, 1.6f, 12);
        for (LivingEntity target : combat().nearby(player, at, 5)) {
            combat().damage(player, target, 4);
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
            Combat.knockback(target, at, 1.2, 0.5);
        }
        Bukkit.broadcast(Text.PREFIX.append(player.displayName())
                .append(Text.plain(" rose from the ashes!", type.color())));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        target.setFireTicks(Math.max(target.getFireTicks(), 100));
        if (!chance(0.15)) {
            return;
        }
        event.setDamage(event.getDamage() + 3);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.FLAME, 40, 0.4, 0.12);
        Fx.particle(at, Particle.LAVA, 8, 0.3, 0);
        Fx.sound(at, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.7f);
        for (LivingEntity nearby : combat().nearby(player, at, 3)) {
            if (nearby != target) {
                combat().damage(player, nearby, 3);
                nearby.setFireTicks(Math.max(nearby.getFireTicks(), 80));
            }
        }
    }

    /** Ember Volley: three small fireballs that also burst on impact. */
    @Override
    public boolean primary(Player player) {
        Vector dir = player.getEyeLocation().getDirection();
        for (int angle : new int[]{-12, 0, 12}) {
            Vector shot = dir.clone().rotateAroundY(Math.toRadians(angle));
            SmallFireball fireball = player.launchProjectile(SmallFireball.class, shot.clone().multiply(1.4));
            fireball.setIsIncendiary(false); // no fire blocks / griefing
            fireball.setAcceleration(shot.clone().multiply(0.1));
            fireball.getPersistentDataContainer().set(fireballKey, PersistentDataType.BYTE, (byte) 1);
            fx().follow(fireball, 0xFF8A2A, 1.3f, 60);
        }
        Fx.sound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        Fx.sound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.8f);
        return true;
    }

    @EventHandler
    public void onFireballHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getPersistentDataContainer().has(fireballKey) || !(projectile.getShooter() instanceof Player player)) {
            return;
        }
        Location at = projectile.getLocation();
        Fx.particle(at, Particle.FLAME, 30, 0.5, 0.1);
        Fx.particle(at, Particle.EXPLOSION, 1, 0, 0);
        Fx.sound(at, Sound.ENTITY_GENERIC_BURN, 0.8f, 1.2f);
        fx().burst(at, 0xFF8A2A, 1.6f);
        fx().flash(at, 0xFFD24A, 1.4f);
        for (LivingEntity target : combat().nearby(player, at, 2.5)) {
            combat().damage(player, target, 4);
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
        }
    }

    /** Firestorm: an expanding ring of fire around the caster. */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        Fx.sound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
        Fx.sound(center, Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
        Fx.particle(center.clone().add(0, 1, 0), Particle.FLAME, 60, 0.4, 0.2);
        fx().shockwave(center, 0xFF6A1F, 9f, 16);
        fx().shockwave(center, 0xFFC23A, 6f, 11);
        fx().flash(center.clone().add(0, 1, 0), 0xFF8A2A, 2.5f);
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            double radius = 1;

            @Override
            public void run() {
                if (!alive(player) || player.getWorld() != center.getWorld() || radius > 9) {
                    cancel();
                    return;
                }
                Location ring = center.clone().add(0, 0.2, 0);
                // pillars of real fire race outward with the ring
                if (radius >= 2.5 && ((int) (radius * 2)) % 3 == 0) {
                    int pillars = (int) (5 + radius * 1.6);
                    double offset = radius * 0.7;
                    for (int i = 0; i < pillars; i++) {
                        double a = i * Math.PI * 2 / pillars + offset;
                        Location spot = dev.infuse.customs.ability.Combat.ground(center.clone().add(Math.cos(a) * radius, 1.5, Math.sin(a) * radius));
                        fx().flamePillar(spot, 0.9f, (float) (1.2 + Math.random() * 1.1), 12, ((int) radius) % 3 == 2);
                    }
                }
                Fx.ring(ring, radius, (int) (radius * 12), p -> Fx.particle(p, Particle.FLAME, 1, 0.05, 0.02));
                Fx.ring(ring.clone().add(0, 0.8, 0), radius, (int) (radius * 5), p -> Fx.particle(p, Particle.SOUL_FIRE_FLAME, 1, 0.05, 0.01));
                if (((int) (radius * 2)) % 3 == 0) {
                    Fx.ring(ring, radius, 6, p -> Fx.particle(p, Particle.LAVA, 1, 0, 0));
                }
                for (LivingEntity target : combat().nearby(player, center, radius + 0.6)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 8);
                        target.setFireTicks(Math.max(target.getFireTicks(), 120));
                        Combat.knockback(target, center, 1.0, 0.45);
                    }
                }
                radius += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}
