package me.infuse.effect;

import me.infuse.InfusePlugin;
import me.infuse.util.Msg;
import me.infuse.util.Style;
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
    private final NamespacedKey xpKey;
    private Resonance resonance;

    public void setResonance(Resonance r) { this.resonance = r; }

    public int readXp(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(xpKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public ItemFactory(InfusePlugin plugin) {
        this.typeKey = new NamespacedKey(plugin, "effect");
        this.augKey = new NamespacedKey(plugin, "augmented");
        this.xpKey = new NamespacedKey(plugin, "resonance_xp");
    }

    public ItemStack create(EffectType type, boolean augmented) {
        return create(type, augmented, 0);
    }

    /** A bottle carrying Resonance XP (shown in the lore when above zero). */
    public ItemStack create(EffectType type, boolean augmented, int xp) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setColor(Color.fromRGB(type.rgb()));
        meta.displayName(name(type, augmented));

        List<Component> lore = new ArrayList<>();
        lore.add(Style.colored(plainCaps("item-passive-header"), type.rgb()).decorate(TextDecoration.BOLD));
        for (String line : type.passives()) lore.add(line(line));
        lore.add(Component.empty());
        lore.add(Style.colored(plainCaps("item-spark-header"), type.rgb()).decorate(TextDecoration.BOLD));
        for (String line : type.sparks()) lore.add(line(line));
        lore.add(Component.empty());
        if (augmented && Tune.BONUS.containsKey(type)) {
            lore.add(Component.empty());
            lore.add(Style.colored(plainCaps("item-augmented-header"), 0xFFD36B).decorate(TextDecoration.BOLD));
            lore.add(line(Tune.summary()));
            for (String b : Tune.BONUS.get(type)) lore.add(line(b));
        }
        if (xp > 0 && resonance != null) {
            lore.add(Style.colored(resonance.label(xp), type.rgb()).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Msg.get("item-footer").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(augmented);
        meta.setMaxStackSize(1);
        if (augmented) meta.setItemModel(AUG_MODEL);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.id());
        pdc.set(augKey, PersistentDataType.BOOLEAN, augmented);
        if (xp > 0) pdc.set(xpKey, PersistentDataType.INTEGER, xp);
        item.setItemMeta(meta);
        // Hide the vanilla "No Effects" potion line
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.POTION_CONTENTS).build());
        return item;
    }

    public static Component name(EffectType type, boolean augmented) {
        return Style.effectName(type, augmented);
    }

    /** A header like "ᴘᴀꜱꜱɪᴠᴇ" taken from messages.yml as plain text (so it can be recoloured). */
    private static String plainCaps(String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.get(key));
    }

    private static Component line(String text) {
        return Msg.get("item-line", Msg.text("text", text)).decoration(TextDecoration.ITALIC, false);
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
