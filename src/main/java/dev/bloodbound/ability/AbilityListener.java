package dev.bloodbound.ability;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.data.PlayerData;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;

/** Routes vanilla events to the active player's weapon kit. Everything here is session-only. */
public final class AbilityListener implements Listener {
    private final BloodboundPlugin plugin;

    public AbilityListener(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean live() {
        return plugin.sessions().isActive();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!live()) return;
        Player player = event.getPlayer();
        if (plugin.weapons().heldWeapon(player).isEmpty()) return;
        event.setCancelled(true);
        plugin.abilities().trigger(player, player.isSneaking());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!live() || Abilities.isAbilityDamage()) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        Player attacker = null;
        boolean ranged = false;
        if (event.getDamager() instanceof Player p
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && plugin.weapons().heldWeapon(p).isPresent()) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p
                && isOwnWeaponProjectile(p, proj)) {
            attacker = p;
            ranged = true;
        }
        if (attacker == null || attacker.equals(target)) return;

        Ctx ctx = plugin.abilities().ctx(attacker);
        event.setDamage(event.getDamage() + ctx.cfg().damagePerTier() * (ctx.tier() - 1));
        plugin.abilities().kit(plugin.players().get(attacker).type()).onWeaponHit(ctx, target, event, ranged);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onOwnerDamaged(EntityDamageEvent event) {
        if (!live() || !(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && plugin.abilities().consumeFallImmunity(player)) {
            event.setCancelled(true);
            return;
        }
        PlayerData data = plugin.players().get(player);
        if (data.type() == null) return;
        plugin.abilities().kit(data.type()).onOwnerDamaged(plugin.abilities().ctx(player), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onShoot(EntityShootBowEvent event) {
        if (!live() || !(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        PlayerData data = plugin.players().get(player);
        if (!plugin.weapons().isValidFor(event.getBow(), data)) return;
        tag(arrow, data.type());
        plugin.abilities().kit(data.type()).onShoot(plugin.abilities().ctx(player), arrow, event.getForce());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!live()) return;
        if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player player) {
            PlayerData data = plugin.players().get(player);
            if (plugin.weapons().isValidFor(trident.getItemStack(), data)) tag(trident, data.type());
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!live()) return;
        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player player) || !isOwnWeaponProjectile(player, proj)) return;
        plugin.abilities().kit(plugin.players().get(player).type()).onProjectileHit(plugin.abilities().ctx(player), proj, event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.abilities().clear(event.getPlayer());
    }

    private void tag(Projectile projectile, WeaponType type) {
        projectile.getPersistentDataContainer().set(plugin.keys().projectileWeapon, PersistentDataType.STRING, type.key());
    }

    private boolean isOwnWeaponProjectile(Player player, Projectile projectile) {
        String tag = projectile.getPersistentDataContainer().get(plugin.keys().projectileWeapon, PersistentDataType.STRING);
        PlayerData data = plugin.players().get(player);
        return tag != null && data.type() != null && data.type().key().equals(tag);
    }
}
