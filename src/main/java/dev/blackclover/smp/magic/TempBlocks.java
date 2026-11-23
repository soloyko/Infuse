package dev.blackclover.smp.magic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Spell blocks (Stone Wall) that only replace air and always restore it, including on shutdown. */
public final class TempBlocks implements Listener {

    private final Plugin plugin;
    private final Predicate<Block> forbidden;
    private final Map<Block, BlockData> placed = new HashMap<>();

    /** @param forbidden blocks where spells may never place (library interiors) */
    public TempBlocks(Plugin plugin, Predicate<Block> forbidden) {
        this.plugin = plugin;
        this.forbidden = forbidden;
    }

    public boolean place(Location loc, Material material, int ticks) {
        Block block = loc.getBlock();
        if (!block.getType().isAir() || placed.containsKey(block) || forbidden.test(block)) {
            return false;
        }
        placed.put(block, block.getBlockData());
        block.setType(material, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> restore(block), ticks);
        return true;
    }

    private void restore(Block block) {
        BlockData original = placed.remove(block);
        if (original != null) {
            block.setBlockData(original, false);
        }
    }

    public boolean isTemp(Block block) {
        return placed.containsKey(block);
    }

    public void restoreAll() {
        for (Block b : Map.copyOf(placed).keySet()) {
            restore(b);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isTemp(event.getBlock())) {
            event.setDropItems(false);
            placed.remove(event.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTemp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTemp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isTemp)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isTemp)) {
            event.setCancelled(true);
        }
    }
}
