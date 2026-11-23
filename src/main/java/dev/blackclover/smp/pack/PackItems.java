package dev.blackclover.smp.pack;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.List;

/** Items that the resource pack swaps for 3D models (players without the pack see the base item). */
public final class PackItems {

    private PackItems() {
    }

    /** Tags {@code meta} so the pack's item definition picks the model {@code key}. */
    public static void tag(ItemMeta meta, String key) {
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(key));
        meta.setCustomModelDataComponent(cmd);
    }

    public static ItemStack model(Material base, String key) {
        ItemStack item = new ItemStack(base);
        ItemMeta meta = item.getItemMeta();
        tag(meta, key);
        item.setItemMeta(meta);
        return item;
    }
}
