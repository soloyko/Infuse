package me.infuse.craft;

import me.infuse.InfusePlugin;
import me.infuse.effect.EffectType;
import me.infuse.util.Msg;
import me.infuse.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Brewing stands open a crafting table; effect crafts are limited, broadcast, and the first starts a ritual. */
public final class CraftingListener implements Listener {
    private final InfusePlugin plugin;
    private final Map<UUID, Location> openStands = new HashMap<>();

    public CraftingListener(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean replaceBrewing() {
        return plugin.getConfig().getBoolean("brewing.replace-with-crafting", true);
    }

    private boolean requireStand() {
        return replaceBrewing() && plugin.getConfig().getBoolean("crafting.require-brewing-stand", true);
    }

    private int maxCrafts() {
        return plugin.getConfig().getInt("crafting.max-crafts", 4);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (!replaceBrewing() || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.BREWING_STAND) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && e.getItem() != null) return; // allow placing blocks against it
        e.setCancelled(true);
        Location loc = block.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.openWorkbench(loc, true) != null) openStands.put(p.getUniqueId(), loc);
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        openStands.remove(e.getPlayer().getUniqueId());
    }

    private EffectType effectOf(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) return null;
        return plugin.recipes().effectFor(keyed.getKey());
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        EffectType type = effectOf(e.getRecipe());
        if (type == null) return;
        if (!(e.getView().getPlayer() instanceof Player p)) return;
        CraftingInventory inv = e.getInventory();
        if (requireStand() && !openStands.containsKey(p.getUniqueId())) {
            inv.setResult(null);
            return;
        }
        int count = plugin.data().craftCount(type);
        if (count >= maxCrafts()) {
            inv.setResult(null);
            return;
        }
        inv.setResult(plugin.items().create(type, count == 0));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent e) {
        EffectType type = effectOf(e.getRecipe());
        if (type == null) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (requireStand() && !openStands.containsKey(p.getUniqueId())) return;
        int count = plugin.data().craftCount(type);
        if (count >= maxCrafts()) {
            Msg.send(p, "<red>The " + type.displayName() + " effect can't be crafted anymore.");
            return;
        }
        CraftingInventory inv = e.getInventory();
        if (inv.getResult() == null) return;
        consume(inv);
        plugin.data().setCraftCount(type, count + 1);

        Location where = openStands.getOrDefault(p.getUniqueId(),
                inv.getLocation() != null ? inv.getLocation() : p.getLocation());
        if (count == 0) {
            p.closeInventory();
            plugin.rituals().begin(type, where);
        } else {
            ItemStack item = plugin.items().create(type, false);
            for (ItemStack left : p.getInventory().addItem(item).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            if (plugin.getConfig().getBoolean("crafting.broadcast-coordinates", true)) {
                Msg.broadcast("<white>" + Msg.esc(p.getName()) + "</white><gray> crafted the </gray><"
                        + Msg.hex(type.rgb()) + ">" + type.displayName() + " Effect</" + Msg.hex(type.rgb())
                        + "><gray> at </gray><white>" + Util.coords(where) + "</white><gray>. ("
                        + (count + 1) + "/" + maxCrafts() + ")</gray>");
            }
        }
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    private static void consume(CraftingInventory inv) {
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            if (it == null || it.getType().isAir()) continue;
            Material remainder = it.getType().getCraftingRemainingItem();
            if (it.getAmount() > 1) {
                it.setAmount(it.getAmount() - 1);
            } else {
                matrix[i] = remainder == null ? null : new ItemStack(remainder);
            }
        }
        inv.setMatrix(matrix);
        inv.setResult(null);
    }
}
