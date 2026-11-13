package dev.infuse.opswords.altar;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One placed altar: a barrier block, its owner (null for a public shrine) and the weapon on it. */
final class Altar {

    final Location block;
    @Nullable UUID owner;
    @Nullable String ownerName;
    @Nullable ItemStack item;

    /** Display entities currently showing this altar (empty while its chunk is unloaded). */
    final List<Display> visuals = new ArrayList<>();
    @Nullable Display weaponDisplay;
    int spin;

    Altar(Location block, @Nullable UUID owner, @Nullable String ownerName, @Nullable ItemStack item) {
        this.block = block;
        this.owner = owner;
        this.ownerName = ownerName;
        this.item = item;
    }

    boolean isShrine() {
        return owner == null;
    }

    Location center() {
        return block.clone().add(0.5, 0.5, 0.5);
    }

    String key() {
        return key(block);
    }

    static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }
}
