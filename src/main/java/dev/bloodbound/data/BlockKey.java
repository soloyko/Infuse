package dev.bloodbound.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A block position that survives worlds being unloaded. */
public record BlockKey(String world, int x, int y, int z) {
    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public Location center() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public boolean isLoaded() {
        World w = Bukkit.getWorld(world);
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    public String serialize() {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static BlockKey parse(String raw) {
        String[] p = raw.split(";");
        if (p.length != 4) return null;
        try {
            return new BlockKey(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
