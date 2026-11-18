package dev.bloodbound.ability;

import dev.bloodbound.weapon.WeaponType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;

/** One weapon's passive, active and ultimate. Kits are singletons and keep their own timed state. */
public interface WeaponKit {
    WeaponType type();

    /** Passive hook, called for melee hits and for this weapon's projectile hits. */
    default void onWeaponHit(Ctx ctx, LivingEntity target, EntityDamageByEntityEvent event, boolean ranged) {}

    /** Active ability (F). Return true if it fired and should go on cooldown. */
    boolean active(Ctx ctx);

    /** Ultimate (Sneak + F). Return true if it fired and should go on cooldown. */
    boolean ultimate(Ctx ctx);

    /** The owner takes damage while holding or having used this weapon. */
    default void onOwnerDamaged(Ctx ctx, EntityDamageEvent event) {}

    default void onShoot(Ctx ctx, AbstractArrow arrow, float force) {}

    default void onProjectileHit(Ctx ctx, Projectile projectile, ProjectileHitEvent event) {}

    /** Clear per-player state (logout, session end). */
    default void clear(Player player) {}
}
