package me.infuse.effect;

import me.infuse.InfusePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Creates and reads effect bottles. */
public final class ItemFactory {
    private static final NamespacedKey AUG_MODEL = new NamespacedKey("infuse", "aug");

    private final NamespacedKey typeKey;
    private final NamespacedKey augKey;

    public ItemFactory(InfusePlugin plugin) {
        this.typeKey = new NamespacedKey(plugin, "effect");
        this.augKey = new NamespacedKey(plugin, "augmented");
    }

    public ItemStack create(EffectType type, boolean augmented) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(type.rgb()));
        meta.displayName(name(type, augmented));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Passive:", type.color()).decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD));
        for (String line : type.passives()) lore.add(line(line));
        lore.add(Component.empty());
        lore.add(Component.text("Spark:", type.color()).decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD));
        for (String line : type.sparks()) lore.add(line(line));
        lore.add(Component.empty());
        lore.add(Component.text("Drink to equip", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(augmented);
        meta.setMaxStackSize(1);
        if (augmented) meta.setItemModel(AUG_MODEL);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.id());
        pdc.set(augKey, PersistentDataType.BOOLEAN, augmented);
        item.setItemMeta(meta);
        // Hide the vanilla "No Effects" potion line
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.POTION_CONTENTS).build());
        return item;
    }

    public static Component name(EffectType type, boolean augmented) {
        Component c = Component.text((augmented ? "Augmented " : "") + type.displayName() + " Effect", type.color())
                .decoration(TextDecoration.ITALIC, false);
        return augmented ? c.decorate(TextDecoration.BOLD) : c;
    }

    private static Component line(String text) {
        return Component.text(" • ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(text, NamedTextColor.GRAY));
    }

    /** Returns the effect stored on an item, or null if it isn't an effect bottle. */
    public Equipped read(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        EffectType type = EffectType.fromId(pdc.get(typeKey, PersistentDataType.STRING));
        if (type == null) return null;
        Boolean aug = pdc.get(augKey, PersistentDataType.BOOLEAN);
        return new Equipped(type, aug != null && aug);
    }
}
