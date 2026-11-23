package dev.blackclover.smp.grimoire;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Creates grimoire items and reads their identity tags. */
public final class GrimoireItems {

    private final NamespacedKey typeKey;
    private final NamespacedKey instanceKey;

    public GrimoireItems(Plugin plugin) {
        this.typeKey = new NamespacedKey(plugin, "grimoire_type");
        this.instanceKey = new NamespacedKey(plugin, "grimoire_instance");
    }

    public ItemStack create(GrimoireType type, UUID instance, int holderLevel, String selectedSpell) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        pdc.set(instanceKey, PersistentDataType.STRING, instance.toString());
        meta.setMaxStackSize(1);
        meta.setEnchantmentGlintOverride(true);
        dev.blackclover.smp.pack.PackItems.tag(meta, dev.blackclover.smp.pack.PackModels.grimoireKey(type));
        item.setItemMeta(meta);
        decorate(item, type, holderLevel, selectedSpell);
        return item;
    }

    /** Rewrites name and lore, keeping identity tags. */
    public void decorate(ItemStack item, GrimoireType type, int holderLevel, String selectedSpell) {
        ItemMeta meta = item.getItemMeta();
        String title = type == GrimoireType.FIVE_LEAF ? "✤ Five-Leaf Grimoire" : type.displayName() + " Grimoire";
        meta.displayName(Component.text(title, TextColor.color(type.rgb()))
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Magic: ", NamedTextColor.GRAY).append(Component.text(type.displayName(), TextColor.color(type.rgb()))));
        if (holderLevel > 0) {
            lore.add(line("Your level: ", NamedTextColor.GRAY).append(Component.text(holderLevel, NamedTextColor.WHITE)));
        }
        if (selectedSpell != null) {
            lore.add(line("Selected: ", NamedTextColor.GRAY).append(Component.text(selectedSpell, NamedTextColor.AQUA)));
        }
        lore.add(Component.empty());
        lore.add(line("Right-click: cast spell", NamedTextColor.DARK_GRAY));
        lore.add(line("Shift + right-click: switch spell", NamedTextColor.DARK_GRAY));
        lore.add(line("Unique. Dropped when you die.", NamedTextColor.DARK_RED));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public boolean isGrimoire(ItemStack item) {
        return typeOf(item).isPresent();
    }

    public Optional<GrimoireType> typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(GrimoireType.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<UUID> instanceOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        try {
            return raw == null ? Optional.empty() : Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
