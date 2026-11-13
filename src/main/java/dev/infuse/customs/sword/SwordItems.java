package dev.infuse.customs.sword;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.Settings;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Builds sword items and recognises them again. */
public final class SwordItems {

    private static final net.kyori.adventure.text.format.TextColor DESCRIPTION = net.kyori.adventure.text.format.TextColor.color(0xC8C8D0);

    /** Vanilla ids, so the tooltip shows "13 Attack Damage" the same way vanilla weapons do. */
    private static final NamespacedKey BASE_ATTACK_DAMAGE = NamespacedKey.minecraft("base_attack_damage");
    private static final NamespacedKey BASE_ATTACK_SPEED = NamespacedKey.minecraft("base_attack_speed");
    private static final int LORE_WIDTH = 42;

    private final CustomsPlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey revisionKey;
    private final NamespacedKey forgerKey;

    /** Keys used before the plugin was renamed to Customs; items carrying them are migrated. */
    @SuppressWarnings("deprecation")
    private static final NamespacedKey LEGACY_TYPE = new NamespacedKey("opswords", "sword");
    @SuppressWarnings("deprecation")
    private static final NamespacedKey LEGACY_REVISION = new NamespacedKey("opswords", "revision");
    @SuppressWarnings("deprecation")
    private static final NamespacedKey LEGACY_FORGER = new NamespacedKey("opswords", "forged_by");

    public SwordItems(CustomsPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "sword");
        this.revisionKey = new NamespacedKey(plugin, "revision");
        this.forgerKey = new NamespacedKey(plugin, "forged_by");
    }

    public ItemStack create(SwordType type) {
        ItemStack item = ItemStack.of(type.baseItem());
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
            applyStats(meta, type);
        });
        return item;
    }

    /** A crafted sword: like {@link #create} but signed with the crafter's name. */
    public ItemStack createForged(SwordType type, String forgerName) {
        ItemStack item = ItemStack.of(type.baseItem());
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
            meta.getPersistentDataContainer().set(forgerKey, PersistentDataType.STRING, forgerName);
            applyStats(meta, type);
        });
        return item;
    }

    public @Nullable SwordType typeOf(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        var pdc = item.getPersistentDataContainer();
        String id = pdc.get(typeKey, PersistentDataType.STRING);
        if (id == null) {
            id = pdc.get(LEGACY_TYPE, PersistentDataType.STRING); // made before the rename to Customs
        }
        return SwordType.byId(id);
    }

    public boolean isSword(@Nullable ItemStack item) {
        return typeOf(item) != null;
    }

    public @Nullable SwordType heldType(Player player) {
        return typeOf(player.getInventory().getItemInMainHand());
    }

    /** True if the player carries a sword of this type anywhere in their inventory. */
    public boolean carries(Player player, SwordType type) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (typeOf(stack) == type) {
                return true;
            }
        }
        return false;
    }

    /** True if the item was built with older settings and should be refreshed. */
    public boolean isOutdated(ItemStack item, SwordType type) {
        if (item.getPersistentDataContainer().has(LEGACY_TYPE)) {
            return true;
        }
        Integer rev = item.getPersistentDataContainer().get(revisionKey, PersistentDataType.INTEGER);
        return rev == null || rev != plugin.settings().revision(type);
    }

    /**
     * Re-applies name, lore, model and stats from the current config. Enchantments,
     * custom names and trims are kept.
     */
    public void refresh(ItemStack item, SwordType type) {
        item.editMeta(meta -> applyStats(meta, type));
    }

    private void applyStats(ItemMeta meta, SwordType type) {
        Settings settings = plugin.settings();
        var pdc = meta.getPersistentDataContainer();
        String legacyForger = pdc.get(LEGACY_FORGER, PersistentDataType.STRING);
        if (legacyForger != null && !pdc.has(forgerKey)) {
            pdc.set(forgerKey, PersistentDataType.STRING, legacyForger);
        }
        pdc.remove(LEGACY_TYPE);
        pdc.remove(LEGACY_REVISION);
        pdc.remove(LEGACY_FORGER);
        Settings.Sword s = settings.sword(type);

        meta.itemName(Text.sword(type)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(lore(type, s, meta.getPersistentDataContainer().get(forgerKey, PersistentDataType.STRING)));
        meta.setItemModel(type.itemModel());
        meta.setRarity(ItemRarity.EPIC);
        meta.setUnbreakable(settings.unbreakable);

        meta.setAttributeModifiers(null);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                BASE_ATTACK_DAMAGE, s.attackDamage() - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                BASE_ATTACK_SPEED, s.attackSpeed() - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        plugin.abilities().get(type).addAttributes(meta);
        // The tooltip shows its own compact stat line instead of vanilla's attribute block.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id()); // migrates old ids
        meta.getPersistentDataContainer().set(revisionKey, PersistentDataType.INTEGER, settings.revision(type));
    }

    private List<Component> lore(SwordType type, Settings.Sword s, @Nullable String forger) {
        List<Component> lore = new ArrayList<>();
        lore.add(Text.caps(type.title(), type.accent()).append(Text.caps("  ·  legendary", NamedTextColor.DARK_GRAY)));
        if (forger != null) {
            lore.add(Text.caps("forged by ", NamedTextColor.DARK_GRAY).append(Text.plain(forger, NamedTextColor.GRAY)));
        }
        lore.add(Component.empty());
        for (String line : Text.wrap(type.bio(), LORE_WIDTH)) {
            lore.add(Text.plain(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
        lore.add(Component.empty());

        section(lore, type, Component.text(Text.smallCaps("Passive")), type.passive().name(), type.passive().description(), null);
        section(lore, type, Component.text(Text.smallCaps("On Hit")), type.onHit().name(), type.onHit().description(), null);
        for (AbilitySlot slot : AbilitySlot.values()) {
            AbilityInfo info = type.ability(slot);
            Component label = slot == AbilitySlot.SECONDARY && plugin.settings().trigger != Settings.Trigger.RIGHT_CLICK
                    ? Component.text(Text.smallCaps("Sneak") + " + ").append(AbilitySlot.PRIMARY.label(plugin.settings().trigger))
                    : slot.label(plugin.settings().trigger);
            section(lore, type, label, info.name(), info.description(), s.cooldown(slot) + "s");
        }
        lore.add(Component.empty());
        Component stats = Text.plain("⚔ ", type.color())
                .append(Text.plain(trim(s.attackDamage()), NamedTextColor.WHITE))
                .append(Text.caps(" damage", NamedTextColor.GRAY))
                .append(Text.plain("   ⏱ ", type.color()))
                .append(Text.plain(trim(s.attackSpeed()), NamedTextColor.WHITE))
                .append(Text.caps(" speed", NamedTextColor.GRAY));
        if (plugin.settings().unbreakable) {
            stats = stats.append(Text.plain("   ∞ ", type.color())).append(Text.caps("unbreakable", NamedTextColor.GRAY));
        }
        lore.add(stats);
        return lore;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private void section(List<Component> lore, SwordType type, Component label, String name, String description,
                         @Nullable String cooldown) {
        Component header = Text.plain("▸ ", type.color())
                .append(label.color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .append(Text.plain(": ", NamedTextColor.GRAY))
                .append(Text.plain(name, type.color()).decoration(TextDecoration.BOLD, true));
        if (cooldown != null) {
            header = header.append(Text.plain("  ⌛ " + cooldown, NamedTextColor.DARK_GRAY));
        }
        lore.add(header);
        for (String line : Text.wrap(description, LORE_WIDTH)) {
            lore.add(Text.plain("   " + line, DESCRIPTION));
        }
    }

    public NamespacedKey typeKey() {
        return typeKey;
    }
}
