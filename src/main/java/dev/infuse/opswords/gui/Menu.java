package dev.infuse.opswords.gui;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A read-only, framed chest menu whose slots can have click actions. */
public abstract class Menu implements InventoryHolder {

    static final int ROWS = 6;
    /** Swords: three centred rows of five. */
    static final int[] SWORD_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    /** The other weapons: one row, split around the centre. */
    static final int[] WEAPON_SLOTS = {37, 38, 39, 41, 42, 43};
    static final int INFO = 4;
    static final int BACK = 45;
    static final int ALTAR = 48;
    static final int CLOSE = 49;

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        ItemStack inner = pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemStack frame = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            boolean edge = row == 0 || row == rows - 1 || col == 0 || col == 8;
            inventory.setItem(i, edge ? frame : inner);
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = ItemStack.of(material);
        pane.editMeta(meta -> meta.setHideTooltip(true));
        return pane;
    }

    protected void set(int slot, ItemStack item, Consumer<Player> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            actions.put(slot, onClick);
        } else {
            actions.remove(slot);
        }
    }

    /** Where a weapon goes in the overview layout. */
    static int slotOf(SwordType type) {
        int swordIndex = 0;
        int weaponIndex = 0;
        for (SwordType t : SwordType.values()) {
            if (t == type) {
                return t.isSword() ? SWORD_SLOTS[swordIndex] : WEAPON_SLOTS[weaponIndex];
            }
            if (t.isSword()) {
                swordIndex++;
            } else {
                weaponIndex++;
            }
        }
        throw new IllegalStateException();
    }

    static ItemStack button(Material material, Component name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.itemName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    /** The book in the header: what this is and how to use the weapons. */
    protected void info(OPSwordsPlugin plugin, String subtitle) {
        var trigger = plugin.settings().trigger;
        set(INFO, button(Material.ENCHANTED_BOOK,
                Text.gradient("⚔ OP Swords", TextColor.color(0xECBA44), TextColor.color(0xC4415B)).decorate(TextDecoration.BOLD),
                List.of(Text.plain(subtitle, NamedTextColor.GRAY),
                        Component.empty(),
                        Text.plain("15 legendary swords and 6 other weapons.", NamedTextColor.WHITE),
                        Component.empty(),
                        AbilitySlot.PRIMARY.label(trigger).color(NamedTextColor.YELLOW)
                                .append(Text.plain("  first ability", NamedTextColor.GRAY)),
                        AbilitySlot.SECONDARY.label(trigger).color(NamedTextColor.YELLOW)
                                .append(Text.plain("  second ability", NamedTextColor.GRAY)))), null);
    }

    protected void closeButton() {
        set(CLOSE, button(Material.BARRIER, Text.plain("Close", NamedTextColor.RED), List.of()), Player::closeInventory);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Routes clicks for every open {@link Menu}; menus can never be taken from or put into. */
    public static final class Listener implements org.bukkit.event.Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
                return;
            }
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == menu.inventory) {
                Consumer<Player> action = menu.actions.get(event.getSlot());
                if (action != null) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    action.accept(player);
                }
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof Menu) {
                event.setCancelled(true);
            }
        }
    }
}
