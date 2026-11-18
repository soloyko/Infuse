package dev.bloodbound.weapon;

import dev.bloodbound.BloodboundPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Weapons only work for their owner, are never dropped on death, and can't be picked up by others. */
public final class OwnershipListener implements Listener {
    private final BloodboundPlugin plugin;
    private final Map<UUID, List<ItemStack>> keptOnDeath = new HashMap<>();
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public OwnershipListener(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isForeign(Player player, ItemStack item) {
        return plugin.weapons().read(item).map(t -> !t.owner().equals(player.getUniqueId())).orElse(false);
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last != null && now - last < 2000) return;
        lastWarning.put(player.getUniqueId(), now);
        plugin.messages().send(player, "weapon.not-yours");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isForeign(player, player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && isForeign(player, event.getBow())) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onThrow(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof Trident trident && trident.getShooter() instanceof Player player
                && isForeign(player, trident.getItemStack())) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isForeign(player, event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;
        Player player = event.getPlayer();
        List<ItemStack> kept = new ArrayList<>();
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            boolean own = plugin.weapons().read(drop).map(t -> t.owner().equals(player.getUniqueId())).orElse(false);
            if (!own) continue;
            it.remove();
            if (plugin.weapons().isValidFor(drop, plugin.players().get(player))) kept.add(drop);
        }
        if (!kept.isEmpty()) keptOnDeath.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(kept);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> kept = keptOnDeath.remove(event.getPlayer().getUniqueId());
        if (kept == null) return;
        Player player = event.getPlayer();
        for (ItemStack item : kept) {
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
    }
}
