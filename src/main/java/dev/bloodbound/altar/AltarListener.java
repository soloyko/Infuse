package dev.bloodbound.altar;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.data.BlockKey;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

import static dev.bloodbound.config.Messages.comp;
import static dev.bloodbound.config.Messages.text;

/** Altar placement, protection, interaction and the forge menu. */
public final class AltarListener implements Listener {
    private static final String ADMIN = "bloodbound.admin";

    private final BloodboundPlugin plugin;
    private final AltarItem altarItem;
    private final AltarCrafting crafting;

    public AltarListener(BloodboundPlugin plugin, AltarItem altarItem, AltarCrafting crafting) {
        this.plugin = plugin;
        this.altarItem = altarItem;
        this.crafting = crafting;
    }

    private boolean isAltar(Block block) {
        return block.getType() == Material.LODESTONE && plugin.altars().contains(BlockKey.of(block));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (!altarItem.isAltar(event.getItemInHand())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN)) {
            event.setCancelled(true);
            plugin.messages().send(player, "altar.admin-only");
            return;
        }
        Block b = event.getBlockPlaced();
        plugin.altars().add(BlockKey.of(b));
        plugin.messages().send(player, "altar.placed", text("x", b.getX()), text("y", b.getY()), text("z", b.getZ()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (!isAltar(b)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN)) {
            event.setCancelled(true);
            plugin.messages().send(player, "altar.admin-only");
            return;
        }
        plugin.altars().remove(BlockKey.of(b));
        event.setDropItems(false);
        if (player.getGameMode() != GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), altarItem.create());
        }
        plugin.messages().send(player, "altar.removed", text("x", b.getX()), text("y", b.getY()), text("z", b.getZ()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isAltar);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isAltar);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyAltar(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyAltar(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (isAltar(event.getBlock())) event.setCancelled(true);
    }

    private boolean anyAltar(List<Block> blocks) {
        for (Block b : blocks) if (isAltar(b)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block b = event.getClickedBlock();
        if (b == null || !isAltar(b)) return;
        Player player = event.getPlayer();
        // Let admins sneak-place blocks against altars; everyone else always gets the altar.
        if (player.isSneaking() && player.hasPermission(ADMIN) && player.getInventory().getItemInMainHand().getType().isBlock()) return;
        event.setCancelled(true);
        if (!plugin.sessions().isActive()) {
            plugin.messages().send(player, "altar.dormant");
            return;
        }
        new AltarMenu(plugin, player).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof AltarMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        WeaponType type = menu.typeAt(event.getRawSlot());
        if (type == null) return;
        AltarCrafting.Result result = crafting.tryCraft(player, type);
        switch (result) {
            case OK -> player.closeInventory();
            case NOT_IN_SESSION -> {
                player.closeInventory();
                plugin.messages().send(player, "altar.dormant");
            }
            case ALREADY_OWNS -> plugin.messages().send(player, "altar.already-owns",
                    comp("weapon", plugin.weapons().displayName(type)));
            case MISSING_ITEMS -> {
                plugin.messages().send(player, "altar.missing-items", comp("weapon", plugin.weapons().displayName(type)));
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof AltarMenu) event.setCancelled(true);
    }
}
