package dev.infuse.customs.listener;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.manager.ForgeRegistry;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/** Crafting rules and dropped-item protection. */
public final class ItemListener implements Listener {

    private final CustomsPlugin plugin;

    public ItemListener(CustomsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- dropped swords ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        protect(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        protect(event.getItemDrop());
    }

    private void protect(Item item) {
        if (!plugin.settings().protectDroppedSwords || !plugin.items().isSword(item.getItemStack())) {
            return;
        }
        item.setUnlimitedLifetime(true);
        item.setInvulnerable(true);
        item.setGlowing(true);
    }

    // ---- crafting ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        SwordType type = recipe instanceof Keyed keyed ? plugin.recipes().typeOf(keyed.getKey()) : null;
        if (type == null) {
            // Never let a Customs weapon be used up in another recipe (e.g. the vanilla repair recipe,
            // which would turn it back into a plain netherite sword).
            for (ItemStack stack : event.getInventory().getMatrix()) {
                if (plugin.items().isSword(stack)) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
            return;
        }
        Player crafter = event.getView().getPlayer() instanceof Player p ? p : null;
        boolean allowed = crafter != null && plugin.settings().craftingEnabled && plugin.settings().sword(type).enabled()
                && crafter.hasPermission("customs.craft")
                && !(plugin.settings().uniqueSwords && plugin.forges().isForged(type));
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (plugin.items().isSword(stack)) {
                allowed = false; // don't let one weapon be eaten to craft another
                break;
            }
        }
        event.getInventory().setResult(allowed ? plugin.items().createForged(type, crafter.getName()) : null);
    }

    /** Legendary mode: if two players race to craft the same sword, only the first one gets it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftCheck(CraftItemEvent event) {
        SwordType type = event.getRecipe() instanceof Keyed keyed ? plugin.recipes().typeOf(keyed.getKey()) : null;
        if (type != null && plugin.settings().uniqueSwords && plugin.forges().isForged(type)) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            ForgeRegistry.Forged forged = plugin.forges().get(type);
            Text.send(event.getWhoClicked(), "<red>" + type.swordName() + " has already been forged"
                    + (forged != null ? " by " + forged.name() : "") + ".");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SwordType type = event.getRecipe() instanceof Keyed keyed ? plugin.recipes().typeOf(keyed.getKey()) : null;
        if (type == null || event.getCurrentItem() == null || event.getCurrentItem().isEmpty()) {
            return;
        }
        if (plugin.settings().uniqueSwords) {
            plugin.forges().record(type, player);
            plugin.altars().refreshAll(); // altars holding it now show who forged it
        }
        plugin.recipes().announce(player, type, event.getCurrentItem());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("customs.craft")) {
            player.discoverRecipes(plugin.recipes().keys());
        }
        upgrade(player.getInventory());
        upgrade(player.getEnderChest());
    }

    /** Chests and other containers holding older weapons are brought up to date when opened. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof dev.infuse.customs.gui.Menu)) {
            upgrade(event.getInventory());
        }
    }

    /** Refreshes outdated weapons (new stats, lore and models) and old altar items in place. */
    private void upgrade(org.bukkit.inventory.Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            SwordType type = plugin.items().typeOf(stack);
            if (type != null && plugin.items().isOutdated(stack, type)) {
                plugin.items().refresh(stack, type);
                changed = true;
            } else if (type == null && plugin.altars().isOutdatedAltarItem(stack)) {
                contents[i] = plugin.altars().createAltarItem(stack.getAmount());
                changed = true;
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }
}
