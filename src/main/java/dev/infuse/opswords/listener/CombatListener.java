package dev.infuse.opswords.listener;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Melee procs and the damage immunities granted by swords. */
public final class CombatListener implements Listener {

    private final OPSwordsPlugin plugin;

    public CombatListener(OPSwordsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        // Real sword swings only: no sweep hits, projectiles or ability damage.
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || plugin.combat().isAbilityDamage()) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        SwordType type = plugin.items().heldType(player);
        if (type == null || !plugin.canUse(player, type)) {
            return;
        }
        plugin.abilities().get(type).onMelee(player, target, event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        switch (event.getCause()) {
            case FALL -> {
                if (plugin.status().isFallProtected(player)) {
                    event.setCancelled(true);
                }
            }
            case VOID -> {
                if (plugin.items().carries(player, SwordType.VOID) && plugin.canUse(player, SwordType.VOID)
                        && player.getLocation().getY() < player.getWorld().getMinHeight()) {
                    event.setCancelled(true);
                    rescueFromVoid(player);
                }
            }
            case FREEZE -> {
                if (holding(player, SwordType.FROST)) {
                    event.setCancelled(true);
                }
            }
            case LIGHTNING -> {
                if (holding(player, SwordType.STORM)) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }

    private boolean holding(Player player, SwordType type) {
        return plugin.items().heldType(player) == type && plugin.canUse(player, type);
    }

    private void rescueFromVoid(Player player) {
        Location target = plugin.status().safeSpot(player);
        if (target == null) {
            World world = player.getWorld();
            target = world.getSpawnLocation();
            target.setY(world.getHighestBlockYAt(target) + 1);
        }
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        Location destination = target;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setFallDistance(0);
            player.setVelocity(player.getVelocity().zero());
            player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
            plugin.status().protectFromFall(player, 40);
            Fx.particle(destination.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 80, 0.5, 0.1);
            Fx.sound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
            Text.send(player, "<#845ECC>The void spits you back out.");
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        plugin.status().forget(victim);
        if (!plugin.settings().deathMessages || event.deathMessage() == null
                || !(event.getDamageSource().getCausingEntity() instanceof Player killer) || killer == victim) {
            return;
        }
        SwordType type = plugin.items().heldType(killer);
        if (type == null) {
            return;
        }
        ItemStack sword = killer.getInventory().getItemInMainHand();
        Component swordName = Text.gradient(type.emoji() + " " + type.displayName(), type.color(), type.accent())
                .hoverEvent(sword.asHoverEvent());
        event.deathMessage(Component.text()
                .append(victim.displayName())
                .append(Component.text(" was " + type.deathVerb() + " by ", NamedTextColor.GRAY))
                .append(killer.displayName())
                .append(Component.text(" wielding the ", NamedTextColor.GRAY))
                .append(swordName)
                .build());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.passives().clear(event.getPlayer());
        plugin.status().forget(event.getPlayer());
    }
}
