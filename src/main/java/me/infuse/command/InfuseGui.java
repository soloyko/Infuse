package me.infuse.command;

import me.infuse.InfusePlugin;
import me.infuse.effect.EffectType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** /infuse gui — preview every effect; players with infuse.gui.take can take them. */
public final class InfuseGui implements Listener {
    private final InfusePlugin plugin;

    public InfuseGui(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 36, Component.text("Infuse Effects"));
        holder.inventory = inv;
        EffectType[] types = EffectType.values();
        for (int i = 0; i < types.length; i++) {
            inv.setItem(i, plugin.items().create(types[i], false));
            inv.setItem(18 + i, plugin.items().create(types[i], true));
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (!p.hasPermission("infuse.gui.take")) return;
        for (ItemStack left : p.getInventory().addItem(clicked.clone()).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
