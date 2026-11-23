package dev.blackclover.smp.grimoire;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.util.Msg;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Enforces the grimoire item rules: one per player, no containers, indestructible, tracked. */
public final class GrimoireListener implements Listener {

    private static final Set<EntityRemoveEvent.Cause> IGNORED_REMOVALS =
            EnumSet.of(EntityRemoveEvent.Cause.PICKUP, EntityRemoveEvent.Cause.UNLOAD, EntityRemoveEvent.Cause.PLAYER_QUIT);

    private final BlackCloverPlugin plugin;
    private final GrimoireService service;
    private final GrimoireItems items;
    private final GrimoireRegistry registry;

    public GrimoireListener(BlackCloverPlugin plugin, GrimoireService service) {
        this.plugin = plugin;
        this.service = service;
        this.items = service.items();
        this.registry = service.registry();
    }

    public void startTasks() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                service.validate(p);
            }
        }, 40L, 40L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, service::checkGroundItems, 200L, 200L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, service::returnFromInactiveHolders, 1200L, 12000L);
    }

    // ---------------------------------------------------------------- pickup

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Item itemEntity = event.getItem();
        Optional<GrimoireType> type = items.typeOf(itemEntity.getItemStack());
        if (type.isEmpty()) {
            return;
        }
        GrimoireState state = registry.get(type.get());
        boolean liveGroundCopy = service.isLive(itemEntity.getItemStack())
                && state.status() == GrimoireStatus.GROUND
                && itemEntity.getUniqueId().equals(state.itemEntity());
        if (!liveGroundCopy) {
            event.setCancelled(true);
            itemEntity.remove();
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (registry.heldBy(player.getUniqueId()).isPresent()) {
            event.setCancelled(true);
            Msg.actionBar(player, "<red>You can only carry one grimoire.");
            return;
        }
        registry.markHeld(type.get(), player.getUniqueId());
        Msg.send(player, "<gray>You picked up the <c><type></c> grimoire.",
                Msg.arg("type", type.get().displayName()), Msg.color("c", type.get().rgb()));
        plugin.getServer().getScheduler().runTask(plugin, () -> service.refreshLore(player));
        plugin.saveAsync();
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (items.isGrimoire(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- containers

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if ((items.isGrimoire(cursor) && isBundle(current)) || (items.isGrimoire(current) && isBundle(cursor))) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        boolean clickedTop = clicked.equals(top);
        if (clickedTop) {
            ItemStack swapped = null;
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
                swapped = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                swapped = event.getWhoClicked().getInventory().getItemInOffHand();
            }
            if (items.isGrimoire(cursor) || items.isGrimoire(swapped)) {
                event.setCancelled(true);
            }
        } else if (event.isShiftClick() && items.isGrimoire(current) && top.getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!items.isGrimoire(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static boolean isBundle(ItemStack item) {
        return item != null && item.getType().name().endsWith("BUNDLE");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack inHand = event.getPlayer().getInventory().getItem(event.getHand());
        if (items.isGrimoire(inHand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !items.isGrimoire(event.getItem())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null && storesItems(block.getType())) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    private static boolean storesItems(Material m) {
        String n = m.name();
        return m == Material.CHISELED_BOOKSHELF || m == Material.DECORATED_POT || m == Material.LECTERN
                || n.endsWith("_SHELF");
    }

    // ---------------------------------------------------------------- ground items

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item drop = event.getItemDrop();
        Optional<GrimoireType> type = items.typeOf(drop.getItemStack());
        if (type.isEmpty()) {
            return;
        }
        GrimoireState state = registry.get(type.get());
        boolean ownCopy = service.isLive(drop.getItemStack()) && state.status() == GrimoireStatus.HELD
                && event.getPlayer().getUniqueId().equals(state.holder());
        if (!ownCopy) {
            drop.remove();
            return;
        }
        service.applyGroundProps(drop);
        registry.markGround(type.get(), drop.getUniqueId(), GrimoireService.toBlockLoc(drop.getLocation()));
        plugin.saveAsync();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (items.isGrimoire(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item) || IGNORED_REMOVALS.contains(event.getCause())) {
            return;
        }
        Optional<GrimoireType> type = items.typeOf(item.getItemStack());
        if (type.isEmpty()) {
            return;
        }
        GrimoireState state = registry.get(type.get());
        if (state.status() == GrimoireStatus.GROUND && item.getUniqueId().equals(state.itemEntity())) {
            String reason = event.getCause() == EntityRemoveEvent.Cause.OUT_OF_WORLD ? "fell into the void" : "item was destroyed";
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                GrimoireState now = registry.get(type.get());
                if (now.status() == GrimoireStatus.GROUND && item.getUniqueId().equals(now.itemEntity())) {
                    service.returnHome(type.get(), reason);
                }
            });
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (!(e instanceof Item item)) {
                continue;
            }
            Optional<GrimoireType> type = items.typeOf(item.getItemStack());
            if (type.isEmpty()) {
                continue;
            }
            GrimoireState state = registry.get(type.get());
            boolean live = service.isLive(item.getItemStack()) && state.status() == GrimoireStatus.GROUND
                    && item.getUniqueId().equals(state.itemEntity());
            if (live) {
                service.applyGroundProps(item);
            } else {
                item.remove();
            }
        }
    }

    // ---------------------------------------------------------------- join / quit

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        PlayerData pd = plugin.players().computeIfAbsent(p.getUniqueId(), id -> new PlayerData(id, p.getName()));
        pd.setName(p.getName());
        pd.setLastSeenMillis(System.currentTimeMillis());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                service.validate(p);
                service.refreshLore(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerData pd = plugin.players().get(event.getPlayer().getUniqueId());
        if (pd != null) {
            pd.setLastSeenMillis(System.currentTimeMillis());
        }
    }
}
