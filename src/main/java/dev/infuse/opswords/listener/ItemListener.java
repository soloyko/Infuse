package dev.infuse.opswords.listener;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.manager.ForgeRegistry;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
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

    private final OPSwordsPlugin plugin;

    public ItemListener(OPSwordsPlugin plugin) {
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
            // Never let an OP sword be used up in another recipe (e.g. the vanilla repair recipe,
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
                && crafter.hasPermission("opswords.craft")
                && !(plugin.settings().uniqueSwords && plugin.forges().isForged(type));
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (plugin.items().isSword(stack)) {
                allowed = false; // don't let one OP sword be eaten to craft another
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
        if (player.hasPermission("opswords.craft")) {
            player.discoverRecipes(plugin.recipes().keys());
        }
    }
}
