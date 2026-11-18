package me.infuse.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/** Temporary blocks (Weaving cobwebs) that revert on their own and never drop items. */
public final class TempBlocks {
    private final Plugin plugin;
    private final Map<Location, Long> blocks = new HashMap<>();

    public TempBlocks(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Places a temporary block if the spot is empty (air, grass, etc.). Returns true if placed. */
    public boolean place(Block block, Material type, long millis) {
        Material cur = block.getType();
        if (cur != type && !(cur.isAir() || block.isReplaceable()) || block.isLiquid()) return false;
        Location key = block.getLocation();
        if (cur != type) block.setType(type, false);
        else if (!blocks.containsKey(key)) return false; // a real block of this type, leave it alone
        long until = System.currentTimeMillis() + millis;
        blocks.merge(key, until, Math::max);
        Bukkit.getScheduler().runTaskLater(plugin, () -> expire(key, type), Math.max(1, millis / 50) + 1);
        return true;
    }

    private void expire(Location key, Material type) {
        Long until = blocks.get(key);
        if (until == null || until > System.currentTimeMillis()) return;
        blocks.remove(key);
        Block b = key.getBlock();
        if (b.getType() == type) b.setType(Material.AIR, false);
    }

    public boolean isTemp(Block block) {
        return blocks.containsKey(block.getLocation());
    }

    public void forget(Block block) {
        blocks.remove(block.getLocation());
    }

    public void clearAll() {
        for (Location l : blocks.keySet()) {
            Block b = l.getBlock();
            if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
        }
        blocks.clear();
    }
}
