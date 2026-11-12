package dev.infuse.opswords.listener;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
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
        boolean allowed = plugin.settings().craftingEnabled && plugin.settings().sword(type).enabled()
                && event.getView().getPlayer().hasPermission("opswords.craft");
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (plugin.items().isSword(stack)) {
                allowed = false; // don't let one OP sword be eaten to craft another
                break;
            }
        }
        event.getInventory().setResult(allowed ? plugin.items().create(type) : null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!plugin.settings().craftingAnnounce || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SwordType type = event.getRecipe() instanceof Keyed keyed ? plugin.recipes().typeOf(keyed.getKey()) : null;
        if (type == null || event.getCurrentItem() == null || event.getCurrentItem().isEmpty()) {
            return;
        }
        Component name = Text.gradient(type.emoji() + " " + type.displayName(), type.color(), type.accent())
                .hoverEvent(plugin.items().create(type).asHoverEvent());
        Bukkit.broadcast(Text.PREFIX
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(" has forged the ", NamedTextColor.GRAY))
                .append(name)
                .append(Component.text("!", NamedTextColor.GRAY)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("opswords.craft")) {
            player.discoverRecipes(plugin.recipes().keys());
        }
    }
}
