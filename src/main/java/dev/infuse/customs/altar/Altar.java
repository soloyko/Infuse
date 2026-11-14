package dev.infuse.customs.altar;

import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** One placed altar: a barrier block and the weapon it forges (null until an admin picks one). */
final class Altar {

    /** A floating ingredient icon and, for more than one, its "×4" label. */
    record Ingredient(Display icon, @Nullable Display label) {
    }

    final Location block;
    @Nullable SwordType type;

    /** Display entities currently showing this altar (empty while its chunk is unloaded). */
    final List<Display> visuals = new ArrayList<>();
    final List<Ingredient> ingredients = new ArrayList<>();
    @Nullable Display weaponDisplay;
    @Nullable Display halo;
    /** Small lights that orbit the floating weapon. */
    final List<Display> motes = new ArrayList<>();
    int spin;
    /** Animation step until which the ingredient ring stays hidden after a forge. */
    int hiddenUntil;

    Altar(Location block, @Nullable SwordType type) {
        this.block = block;
        this.type = type;
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
