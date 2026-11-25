package dev.lambro.blade.gui;

import dev.lambro.blade.LambroPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static dev.lambro.blade.util.Text.*;

/** Base chest menu with the blue/black frame. */
public abstract class Menu implements InventoryHolder {

    protected final LambroPlugin plugin;
    protected final Player viewer;
    private final Inventory inventory;

    protected Menu(LambroPlugin plugin, Player viewer, int rows, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, mm(title));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    protected abstract void draw();

    public abstract void click(int slot, ClickType type);

    public void render() {
        inventory.clear();
        frame();
        draw();
    }

    private void frame() {
        int size = inventory.getSize();
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack blue = pane(Material.BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            boolean edge = row == 0 || row == size / 9 - 1 || col == 0 || col == 8;
            boolean corner = (row == 0 || row == size / 9 - 1) && (col == 0 || col == 8);
            inventory.setItem(i, corner ? blue : edge ? black : null);
        }
        // accent a few edge slots blue for a cleaner rhythm
        inventory.setItem(4, blue);
        inventory.setItem(size - 5, blue);
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected static ItemStack pane(Material m) {
        ItemStack it = ItemStack.of(m);
        it.editMeta(meta -> {
            meta.itemName(Component.empty());
            meta.setHideTooltip(true);
        });
        return it;
    }

    protected static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = ItemStack.of(m);
        it.editMeta(meta -> {
            meta.itemName(mm(name));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(mm(s));
            meta.lore(l);
            meta.addItemFlags(ItemFlag.values());
        });
        return it;
    }

    protected void click() {
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    protected void deny() {
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    protected static String header(String s) {
        return "<bold>" + GRADIENT + tc(s) + "</gradient></bold>";
    }

    protected static String label(String k, String v) {
        return "<" + STEEL + ">" + tc(k) + " <" + SHADOW + ">· <" + WHITE + ">" + v;
    }

    protected static String hint(String key, String action) {
        return "<" + BLUE + ">" + tc(key) + " <" + SHADOW + ">» <" + STEEL + ">" + tc(action);
    }

    protected static String toggle(boolean on) {
        return on ? "<" + CYAN + ">● " + tc("enabled") : "<" + SHADOW + ">○ " + tc("disabled");
    }
}
