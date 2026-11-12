package dev.infuse.opswords.listener;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.Settings;
import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Fires sword abilities. By default the swap-hand key (F) fires them (Sneak + F for the second
 * one); controls.trigger can switch to right-click or allow both.
 */
public final class AbilityListener implements Listener {

    private final OPSwordsPlugin plugin;

    public AbilityListener(OPSwordsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.settings().trigger == Settings.Trigger.OFFHAND || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        SwordType type = plugin.items().typeOf(event.getItem());
        if (type == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && !player.isSneaking() && isInteractable(clicked)) {
            return; // let players open doors, chests, etc. without firing an ability
        }
        plugin.abilities().activate(player, type, player.isSneaking() ? AbilitySlot.SECONDARY : AbilitySlot.PRIMARY);
    }

    /** The swap-hand key (F) fires abilities instead of swapping: F = primary, Sneak + F = secondary. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.settings().trigger == Settings.Trigger.RIGHT_CLICK) {
            return;
        }
        Player player = event.getPlayer();
        SwordType type = plugin.items().heldType(player); // the swap hasn't happened yet
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        plugin.abilities().activate(player, type, player.isSneaking() ? AbilitySlot.SECONDARY : AbilitySlot.PRIMARY);
    }

    @SuppressWarnings("deprecation")
    private static boolean isInteractable(Block block) {
        return block.getType().isInteractable();
    }
}
