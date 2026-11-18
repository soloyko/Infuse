package dev.bloodbound.weapon;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.config.Messages;
import dev.bloodbound.core.Tiers;
import dev.bloodbound.data.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.bloodbound.config.Messages.text;

/** Creates, identifies and refreshes Bloodbound weapon items. */
public final class WeaponItems {
    /** Data stored on a weapon item. */
    public record WeaponTag(UUID owner, WeaponType type, int version) {}

    private final BloodboundPlugin plugin;

    public WeaponItems(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(PlayerData data) {
        WeaponType type = data.type();
        ItemStack item = new ItemStack(type.material());
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(plugin.keys().weaponOwner, PersistentDataType.STRING, data.id().toString());
            pdc.set(plugin.keys().weaponType, PersistentDataType.STRING, type.key());
            pdc.set(plugin.keys().weaponVersion, PersistentDataType.INTEGER, data.weaponVersion());
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.setEnchantmentGlintOverride(true);
            if (type == WeaponType.TEMPEST_TRIDENT) meta.addEnchant(Enchantment.LOYALTY, 3, true);
            applyText(meta, data);
        });
        return item;
    }

    public Optional<WeaponTag> read(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String owner = pdc.get(plugin.keys().weaponOwner, PersistentDataType.STRING);
        String type = pdc.get(plugin.keys().weaponType, PersistentDataType.STRING);
        Integer version = pdc.get(plugin.keys().weaponVersion, PersistentDataType.INTEGER);
        if (owner == null || type == null || version == null) return Optional.empty();
        Optional<WeaponType> weaponType = WeaponType.fromKey(type);
        if (weaponType.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new WeaponTag(UUID.fromString(owner), weaponType.get(), version));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isWeapon(ItemStack item) {
        return read(item).isPresent();
    }

    /** True if the item is the current, valid weapon of this player. */
    public boolean isValidFor(ItemStack item, PlayerData data) {
        return read(item).map(tag -> tag.owner().equals(data.id())
                && tag.type() == data.type()
                && tag.version() == data.weaponVersion()).orElse(false);
    }

    /** The player's valid weapon in their main hand, if any. */
    public Optional<ItemStack> heldWeapon(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return isValidFor(hand, plugin.players().get(player)) ? Optional.of(hand) : Optional.empty();
    }

    public boolean hasValidWeapon(Player player) {
        PlayerData data = plugin.players().get(player);
        for (ItemStack item : player.getInventory().getContents()) {
            if (isValidFor(item, data)) return true;
        }
        return false;
    }

    /** Removes every copy of this player's own weapons (valid or stale) from their inventory. */
    public void removeOwnWeapons(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (read(item).map(t -> t.owner().equals(player.getUniqueId())).orElse(false)) inv.setItem(i, null);
        }
    }

    /** Rewrites the lore of the player's valid weapons (after a tier or blood change). */
    public void refresh(Player player) {
        PlayerData data = plugin.players().get(player);
        for (ItemStack item : player.getInventory().getContents()) {
            if (isValidFor(item, data)) item.editMeta(meta -> applyText(meta, data));
        }
    }

    public Component displayName(WeaponType type) {
        return plugin.messages().item("weapon.types." + type.key() + ".name");
    }

    private void applyText(ItemMeta meta, PlayerData data) {
        Messages m = plugin.messages();
        Tiers tiers = plugin.settings().tiers();
        WeaponType type = data.type();
        String base = "weapon.types." + type.key() + ".";
        meta.customName(displayName(type));

        List<Component> lore = new ArrayList<>();
        lore.add(m.item(base + "description"));
        lore.add(Component.empty());
        lore.add(m.item("weapon.lore-tier",
                text("tier", tiers.tierFor(data.blood())), text("max", tiers.maxTier()), text("blood", data.blood())));
        lore.add(m.item("weapon.lore-passive", text("name", m.raw(base + "passive"))));
        lore.add(m.item("weapon.lore-active", text("name", m.raw(base + "active")),
                text("tier", plugin.settings().activeTier())));
        lore.add(m.item("weapon.lore-ultimate", text("name", m.raw(base + "ultimate")),
                text("tier", plugin.settings().ultimateTier())));
        lore.add(Component.empty());
        lore.add(m.item("weapon.lore-owner", text("owner", data.name())));
        lore.add(m.item("weapon.lore-session"));
        meta.lore(lore);
    }

}
