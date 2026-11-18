package me.infuse.command;

import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.effect.EffectType;
import me.infuse.effect.Equipped;
import me.infuse.effect.ItemFactory;
import me.infuse.util.Msg;
import me.infuse.util.Style;
import me.infuse.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The effects menu (/infuses and /infuse gui). Everyone can browse; players with
 * infuse.gui.take can click an effect to take it.
 */
public final class InfuseGui implements Listener {
    private static final int TOGGLE_SLOT = 49;
    private static final int INFO_SLOT = 45;
    private final InfusePlugin plugin;

    public InfuseGui(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private boolean augmented;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player p) {
        open(p, false);
    }

    private void open(Player p, boolean augmented) {
        Holder holder = new Holder();
        holder.augmented = augmented;
        Inventory inv = Bukkit.createInventory(holder, 54, Msg.get("gui-title"));
        holder.inventory = inv;
        fill(inv, p, augmented);
        p.openInventory(inv);
    }

    private void fill(Inventory inv, Player p, boolean augmented) {
        inv.clear();
        Material[] border = {Material.MAGENTA_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE};
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inv.setItem(i, pane(border[(row + col) % 2]));
            }
        }
        List<EffectType> shown = new ArrayList<>();
        for (EffectType t : EffectType.values()) if (t.enabled()) shown.add(t);
        int[] slots = layout(shown.size());
        for (int i = 0; i < slots.length; i++) inv.setItem(slots[i], plugin.items().create(shown.get(i), augmented));
        inv.setItem(TOGGLE_SLOT, button(augmented ? Material.NETHER_STAR : Material.GLASS_BOTTLE,
                Style.colored(Style.caps(augmented ? "Showing: Augmented" : "Showing: Regular"), 0xD11FD1),
                List.of(Component.text(Style.caps("Click to switch"), NamedTextColor.GRAY))));
        inv.setItem(INFO_SLOT, info(p));
    }

    /**
     * Slots for {@code n} effects inside the border (rows 1-4, 7 per row). Full rows fill left to
     * right; the last row is centred (skipping the middle column when its count is even).
     */
    static int[] layout(int n) {
        n = Math.min(n, 28);
        int[] out = new int[n];
        int idx = 0;
        int rows = (n + 6) / 7;
        for (int r = 0; r < rows; r++) {
            int inRow = Math.min(7, n - r * 7);
            int base = (r + 1) * 9;
            List<Integer> cols = new ArrayList<>();
            if (inRow == 7) {
                for (int c = 1; c <= 7; c++) cols.add(c);
            } else {
                int start = 4 - inRow / 2;
                for (int c = 0; c < inRow; c++) {
                    int col = start + c;
                    if (inRow % 2 == 0 && col >= 4) col++;
                    cols.add(col);
                }
            }
            for (int c : cols) out[idx++] = base + c;
        }
        return out;
    }

    private ItemStack info(Player p) {
        PlayerData d = plugin.data().get(p);
        List<Component> lore = new ArrayList<>();
        for (int slot = 0; slot < 2; slot++) {
            Equipped e = d.get(slot);
            Component label = Component.text(Style.caps(slot == 0 ? "Primary: " : "Secondary: "), NamedTextColor.GRAY);
            if (e == null) {
                lore.add(label.append(Component.text(Style.caps("empty"), NamedTextColor.DARK_GRAY)));
            } else {
                long rem = d.cooldownRemaining(slot);
                lore.add(label.append(ItemFactory.name(e.type(), e.augmented()))
                        .append(rem > 0 ? Component.text("  " + Util.time(rem), NamedTextColor.RED)
                                : Component.text("  " + Style.caps("ready"), NamedTextColor.GREEN)));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text(Style.caps(d.offhandControls() ? "Controls: Offhand (F / Sneak+F)" : "Controls: /lspark /rspark"),
                NamedTextColor.DARK_GRAY));
        return button(Material.BOOK, Style.colored(Style.caps("Your Effects"), 0x7F3CFF), lore);
    }

    private static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack button(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getSlot() == TOGGLE_SLOT) {
            holder.augmented = !holder.augmented;
            fill(holder.inventory, p, holder.augmented);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, holder.augmented ? 1.4f : 1f);
            return;
        }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || plugin.items().read(clicked) == null) return;
        if (!p.hasPermission("infuse.gui.take")) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.2f);
            return;
        }
        for (ItemStack left : p.getInventory().addItem(clicked.clone()).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
