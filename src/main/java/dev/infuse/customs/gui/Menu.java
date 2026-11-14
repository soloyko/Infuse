package dev.infuse.customs.gui;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.sword.AbilitySlot;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
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
    static final int INFO = 0;
    /** The arc tabs sit in the middle of the header, from this slot on. */
    static final int FIRST_TAB = 2;
    static final int BACK = 45;
    static final int ALTAR = 48;
    static final int CLOSE = 49;

    private final Inventory inventory;
    private final Map<Integer, java.util.function.BiConsumer<Player, org.bukkit.event.inventory.ClickType>> actions = new HashMap<>();

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
        setClick(slot, item, onClick == null ? null : (p, c) -> onClick.accept(p));
    }

    /** A slot whose action also gets the kind of click (left, right, shift...). */
    protected void setClick(int slot, ItemStack item, java.util.function.BiConsumer<Player, org.bukkit.event.inventory.ClickType> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            actions.put(slot, onClick);
        } else {
            actions.remove(slot);
        }
    }

    /** The tabs of the overview menus: one per arc, and the factions' weapons. */
    public enum Page {
        I, II, III, IV, V, FACTIONS;

        /** The arc this tab shows, or null for the factions' tab. */
        public dev.infuse.customs.sword.@org.jetbrains.annotations.Nullable Arc arc() {
            return this == FACTIONS ? null : dev.infuse.customs.sword.Arc.values()[ordinal()];
        }

        public static Page of(SwordType type) {
            return type.arc() == null ? FACTIONS : values()[type.arc().ordinal()];
        }

        List<SwordType> types() {
            return this == FACTIONS ? List.of(SwordType.HEROSWORD, SwordType.MJOLNIR, SwordType.GODSPEAR) : arc().members();
        }
    }

    /**
     * Slots for {@code count} items inside the frame: centred rows of up to {@code perRow}, the
     * rows centred vertically. A row with an even number of items leaves its middle slot free so
     * it stays symmetric.
     */
    static int[] layout(int count, int perRow) {
        int rows = Math.max(1, (count + perRow - 1) / perRow);
        int firstRow = 1 + Math.max(0, (4 - rows) / 2);
        int[] slots = new int[count];
        int i = 0;
        for (int r = 0; r < rows && i < count; r++) {
            int n = Math.min(perRow, count - i);
            int width = (n % 2 == 0) ? n + 1 : n; // even rows skip the centre column
            int startCol = 1 + (7 - width) / 2;
            int middle = startCol + width / 2;
            for (int c = startCol; c < startCol + width && i < count; c++) {
                if (n % 2 == 0 && c == middle) {
                    continue;
                }
                slots[i++] = (firstRow + r) * 9 + c;
            }
        }
        return slots;
    }

    /** Lays the page's weapons out; {@code place} puts each one in its slot. */
    static void fill(Page page, java.util.function.BiConsumer<Integer, SwordType> place) {
        List<SwordType> types = page.types();
        int[] slots = layout(types.size(), 5);
        for (int i = 0; i < types.size(); i++) {
            place.accept(slots[i], types.get(i));
        }
    }

    /** Which arc the server is in (set by the plugin), for the tabs' lock markers. */
    public static java.util.function.Supplier<dev.infuse.customs.sword.Arc> arcState;

    /** The arc tabs in the header; clicking one opens {@code open.apply(page)}. */
    protected void tabs(Page current, java.util.function.Function<Page, Menu> open) {
        for (Page page : Page.values()) {
            boolean active = page == current;
            dev.infuse.customs.sword.Arc arc = page.arc();
            List<Component> lore = new java.util.ArrayList<>();
            Component name;
            SwordType icon;
            if (arc == null) {
                lore.add(Text.caps("arc ii · faction war", net.kyori.adventure.text.format.TextColor.color(0xF2C94C)));
                lore.add(Text.plain("Carried by the faction leaders", NamedTextColor.GRAY));
                lore.add(Text.plain("during sessions; never forged.", NamedTextColor.GRAY));
                name = Text.plain("Factions", active ? net.kyori.adventure.text.format.TextColor.color(0xF2C94C) : NamedTextColor.GRAY);
                icon = SwordType.HEROSWORD;
            } else {
                lore.add(Text.caps(arc.title(), arc.color()));
                lore.add(Text.plain(arc.hasTwist() ? "Twist: " + arc.twist() + " (in sessions)" : "No twist", NamedTextColor.GRAY));
                dev.infuse.customs.sword.Arc running = arcState == null ? null : arcState.get();
                if (running != null) {
                    lore.add(running == arc ? Text.plain("◆ The current arc", NamedTextColor.GREEN)
                            : arc.ordinal() < running.ordinal() ? Text.plain("✔ Completed: its weapons can be forged", NamedTextColor.DARK_GREEN)
                            : Text.plain("🔒 Locked until it begins", NamedTextColor.RED));
                }
                name = Text.plain(arc.label(), active ? arc.color() : NamedTextColor.GRAY)
                        .append(Text.plain(" · " + arc.title(), active ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));
                icon = arc.members().get(0);
            }
            lore.add(Component.empty());
            for (SwordType type : page.types()) {
                lore.add(Text.plain(type.emoji() + " " + type.swordName(), type.color()));
            }
            lore.add(Component.empty());
            lore.add(active ? Text.plain("Showing", NamedTextColor.GREEN) : Text.plain("Click to show", NamedTextColor.YELLOW));
            ItemStack tab = button(Material.PAPER,
                    (active ? Text.plain("▶ ", NamedTextColor.GOLD) : Component.empty()).append(name).decoration(TextDecoration.BOLD, active),
                    lore);
            tab.editMeta(meta -> {
                meta.setItemModel(icon.itemModel());
                meta.setEnchantmentGlintOverride(active);
            });
            set(FIRST_TAB + page.ordinal(), tab, active ? null : player -> open.apply(page).open(player));
        }
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
    protected void info(CustomsPlugin plugin, String subtitle) {
        var trigger = plugin.settings().trigger;
        set(INFO, button(Material.ENCHANTED_BOOK,
                Text.gradient("⚔ Customs", TextColor.color(0xECBA44), TextColor.color(0xC4415B)).decorate(TextDecoration.BOLD),
                List.of(Text.caps(subtitle, NamedTextColor.GRAY),
                        Component.empty(),
                        Text.plain("45 legendary weapons in five arcs.", NamedTextColor.WHITE),
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
                var action = menu.actions.get(event.getSlot());
                if (action != null) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    action.accept(player, event.getClick());
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
