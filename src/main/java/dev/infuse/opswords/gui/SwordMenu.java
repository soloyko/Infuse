package dev.infuse.opswords.gui;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Admin menu: click a sword to get a copy. */
public final class SwordMenu implements InventoryHolder {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private final OPSwordsPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, SwordType> bySlot = new HashMap<>();

    public SwordMenu(OPSwordsPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 36,
                Text.mm("<gradient:#ECBA44:#C4415B><bold>OP Swords</bold></gradient>"));
        ItemStack filler = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        SwordType[] types = SwordType.values();
        for (int i = 0; i < types.length && i < SLOTS.length; i++) {
            inventory.setItem(SLOTS[i], plugin.items().create(types[i]));
            bySlot.put(SLOTS[i], types[i]);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    private void click(Player player, int slot) {
        SwordType type = bySlot.get(slot);
        if (type == null || !player.hasPermission("opswords.admin")) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(plugin.items().create(type));
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        Text.send(player, Component.text("You received the ").color(Text.GRAY)
                .append(Text.gradient(type.emoji() + " " + type.displayName(), type.color(), type.accent()))
                .append(Component.text(".", Text.GRAY)));
    }

    /** Handles clicks for every open {@link SwordMenu}. */
    public static final class Listener implements org.bukkit.event.Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder(false) instanceof SwordMenu menu)) {
                return;
            }
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getClickedInventory() == menu.inventory) {
                menu.click(player, event.getSlot());
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof SwordMenu) {
                event.setCancelled(true);
            }
        }
    }
}
