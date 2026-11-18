package dev.bloodbound.altar;

import dev.bloodbound.BloodboundPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** The admin-only lodestone that becomes an altar when placed. */
public final class AltarItem {
    private final BloodboundPlugin plugin;

    public AltarItem(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.LODESTONE);
        item.editMeta(meta -> {
            meta.customName(plugin.messages().item("altar.item-name"));
            meta.lore(plugin.messages().itemLines("altar.item-lore"));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(plugin.keys().altar, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public boolean isAltar(ItemStack item) {
        return item != null && item.getType() == Material.LODESTONE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.keys().altar, PersistentDataType.BYTE);
    }
}
