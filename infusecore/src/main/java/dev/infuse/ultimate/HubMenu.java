package dev.infuse.ultimate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The {@code /iu} chest menu: one icon per module, plus a resource-pack tile. */
public final class HubMenu implements Listener {

    /** Marks an inventory as ours so clicks in other chests are ignored. */
    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final int SIZE = 27;
    // Four modules spread across the middle row.
    private static final int[] MODULE_SLOTS = {10, 12, 14, 16};
    private static final int PACK_SLOT = 22;

    private final InfuseCorePlugin plugin;

    public HubMenu(InfuseCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), SIZE,
                Component.text("Infuse Ultimate", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));

        ItemStack filler = pane();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        Module[] modules = Module.values();
        for (int i = 0; i < modules.length && i < MODULE_SLOTS.length; i++) {
            inv.setItem(MODULE_SLOTS[i], icon(modules[i]));
        }
        inv.setItem(PACK_SLOT, packIcon());

        player.openInventory(inv);
    }

    private ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack icon(Module module) {
        boolean on = module.installed();
        ItemStack item = new ItemStack(on ? module.icon() : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(module.displayName(),
                        on ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(module.tagline(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        for (String line : module.description()) {
            lore.add(Component.text(line, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        if (on) {
            lore.add(Component.text("Installed", NamedTextColor.GREEN)
                    .append(Component.text("  v" + module.version(), NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to open /" + module.command(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(Component.text("Not installed on this server.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack packIcon() {
        ItemStack item = new ItemStack(Material.PAINTING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Resource Pack", NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        boolean configured = plugin.getConfig().getBoolean("resource-pack.enabled")
                && !plugin.getConfig().getString("resource-pack.url", "").isBlank();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("3D weapon models and the effect HUD.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (configured) {
            lore.add(Component.text("Click to (re)apply the pack.", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Not configured by the server.", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null
                    || !(event.getClickedInventory().getHolder() instanceof Holder)) {
                return;
            }
            int slot = event.getSlot();
            Module[] modules = Module.values();
            for (int i = 0; i < modules.length && i < MODULE_SLOTS.length; i++) {
                if (slot == MODULE_SLOTS[i]) {
                    Module module = modules[i];
                    if (module.installed()) {
                        player.closeInventory();
                        player.performCommand(module.command());
                    } else {
                        player.sendMessage(Component.text(module.displayName()
                                + " is not installed on this server.", NamedTextColor.RED));
                    }
                    return;
                }
            }
            if (slot == PACK_SLOT) {
                player.closeInventory();
                player.performCommand("iu pack");
            }
        }
    }
}
