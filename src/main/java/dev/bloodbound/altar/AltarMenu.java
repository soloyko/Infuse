package dev.bloodbound.altar;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.config.Messages;
import dev.bloodbound.core.Tiers;
import dev.bloodbound.data.PlayerData;
import dev.bloodbound.weapon.WeaponType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.bloodbound.config.Messages.comp;
import static dev.bloodbound.config.Messages.text;

/** The forge menu opened by right-clicking an altar during a session. */
public final class AltarMenu implements InventoryHolder {
    private static final int[] WEAPON_SLOTS = {11, 12, 13, 14, 15};
    private static final int STATS_SLOT = 22;

    private final BloodboundPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;

    public AltarMenu(BloodboundPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 27, plugin.messages().get("altar.menu-title"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    /** The weapon type shown in a raw slot, or null. */
    public WeaponType typeAt(int rawSlot) {
        for (int i = 0; i < WEAPON_SLOTS.length; i++) {
            if (WEAPON_SLOTS[i] == rawSlot) return WeaponType.values()[i];
        }
        return null;
    }

    public void render() {
        ItemStack filler = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        filler.editMeta(meta -> {
            meta.customName(Component.empty());
            meta.setHideTooltip(true);
        });
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        WeaponType[] types = WeaponType.values();
        for (int i = 0; i < types.length; i++) inventory.setItem(WEAPON_SLOTS[i], icon(types[i]));
        inventory.setItem(STATS_SLOT, statsIcon());
    }

    private ItemStack icon(WeaponType type) {
        Messages m = plugin.messages();
        PlayerData data = plugin.players().get(viewer);
        String base = "weapon.types." + type.key() + ".";
        ItemStack item = new ItemStack(type.material());
        item.editMeta(meta -> {
            meta.customName(m.item(base + "name"));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            List<Component> lore = new ArrayList<>();
            lore.add(m.item(base + "description"));
            lore.add(Component.empty());
            lore.add(m.item("weapon.lore-passive", text("name", m.raw(base + "passive"))));
            lore.add(m.item("weapon.lore-active", text("name", m.raw(base + "active")),
                    text("tier", plugin.settings().activeTier())));
            lore.add(m.item("weapon.lore-ultimate", text("name", m.raw(base + "ultimate")),
                    text("tier", plugin.settings().ultimateTier())));
            lore.add(Component.empty());
            lore.add(m.item("altar.menu-cost"));
            for (Map.Entry<Material, Integer> e : plugin.settings().weapon(type).recipe().entrySet()) {
                boolean has = AltarCrafting.count(viewer, e.getKey(), plugin) >= e.getValue();
                lore.add(m.item("altar.menu-cost-line",
                        comp("mark", m.get(has ? "altar.menu-have" : "altar.menu-missing")),
                        text("amount", e.getValue()),
                        comp("item", Component.translatable(e.getKey().translationKey()))));
            }
            lore.add(Component.empty());
            boolean owns = data.type() == type && plugin.weapons().hasValidWeapon(viewer);
            if (owns) {
                lore.add(m.item("altar.menu-owned"));
                meta.setEnchantmentGlintOverride(true);
            } else if (data.type() != null) {
                lore.add(m.item("altar.menu-click-recraft"));
            } else {
                lore.add(m.item("altar.menu-click-forge"));
            }
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack statsIcon() {
        Messages m = plugin.messages();
        PlayerData data = plugin.players().get(viewer);
        Tiers tiers = plugin.settings().tiers();
        ItemStack item = new ItemStack(Material.REDSTONE);
        item.editMeta(meta -> {
            meta.customName(m.item("altar.menu-stats-name"));
            Component weapon = data.type() == null ? m.get("stats.no-weapon") : plugin.weapons().displayName(data.type());
            meta.lore(m.itemLines("altar.menu-stats-lore",
                    comp("weapon", weapon),
                    text("tier", tiers.tierFor(data.blood())),
                    text("max", tiers.maxTier()),
                    text("blood", data.blood())));
        });
        return item;
    }
}
