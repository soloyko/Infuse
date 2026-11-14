package dev.bloodbound.event;

import dev.bloodbound.BloodboundPlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Event rules that hook into vanilla events. Kill rewards live in BloodListener. */
public final class EventListener implements Listener {
    private final BloodboundPlugin plugin;

    public EventListener(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (plugin.events().current().orElse(null) != EventType.LAST_STAND) return;
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED || reason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (plugin.events().isBountyHolder(p.getUniqueId())) {
            p.setGlowing(false);
            plugin.events().reassignBounty(p);
        }
    }

    /** Full heal used by Last Stand when a player gets a kill. */
    public static void fullHeal(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) player.setHealth(max.getValue());
    }
}
