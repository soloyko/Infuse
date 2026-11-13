package dev.infuse.customs.listener;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
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

    private final CustomsPlugin plugin;

    public CombatListener(CustomsPlugin plugin) {
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
        if (plugin.settings().meleeSlashes && !event.isCancelled()) {
            plugin.fx().melee(player.getEyeLocation(), player.getEyeLocation().getDirection(), Combat.center(target),
                    type.color().value(), type.accent().value(), type.meleeStyle());
            if (event.isCritical()) {
                // a crit lands harder: a second, brighter cut across the first and a flare
                org.bukkit.Location at = Combat.center(target);
                int accent = dev.infuse.customs.ability.Fx3D.bright(type.accent().value());
                plugin.fx().cut(at, player.getEyeLocation().getDirection(), Math.PI / 2 + 0.5, 0.5, accent, 1.6f, 3);
                plugin.fx().burst(at, dev.infuse.customs.ability.Fx3D.bright(type.color().value()), 1.6f);
                Fx.particle(at, Particle.CRIT, 14, 0.3, 0.25);
            }
        }
    }

    /** Damage numbers for hits with a Customs weapon or its abilities. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitNumber(EntityDamageByEntityEvent event) {
        if (!plugin.settings().damageNumbers || !(event.getEntity() instanceof LivingEntity target)
                || !(event.getDamageSource().getCausingEntity() instanceof Player attacker) || attacker == target) {
            return;
        }
        double dealt = event.getFinalDamage();
        if (dealt < 0.05 || target instanceof org.bukkit.entity.ArmorStand) {
            return;
        }
        SwordType type = plugin.items().heldType(attacker);
        if (type == null) {
            return;
        }
        boolean ability = plugin.combat().isAbilityDamage();
        boolean crit = !ability && event.isCritical();
        plugin.damageNumbers().show(attacker, target, dealt, type.color().value(), crit, ability);
    }

    /** Kills with a Customs weapon (melee or ability) end in a burst of light in its colour. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(org.bukkit.event.entity.EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !plugin.settings().killEffects) {
            return;
        }
        SwordType type = plugin.items().heldType(killer);
        if (type == null) {
            return;
        }
        int rgb = type.color().value();
        int accent = dev.infuse.customs.ability.Fx3D.bright(type.accent().value());
        org.bukkit.Location feet = event.getEntity().getLocation();
        org.bukkit.Location at = Combat.center(event.getEntity());
        var fx = plugin.fx();
        fx.burst(at, rgb, 2.2f);
        fx.flash(at, accent, 1.6f);
        fx.shockwave(feet, rgb, 2.2f, 7);
        // the essence leaving the body, in the weapon's colour
        fx.drift(at, new org.bukkit.util.Vector(0, 2.4, 0), rgb, 0.9f, 22);
        killFlourish(type, feet, at);
    }

    /** A finishing touch that fits the weapon. */
    private void killFlourish(SwordType type, org.bukkit.Location feet, org.bukkit.Location at) {
        var fx = plugin.fx();
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        switch (type) {
            case DRAGON, PHOENIX, HAMMER -> {
                Fx.particle(at, Particle.FLAME, 30, 0.35, 0.08);
                Fx.particle(at, Particle.LAVA, 4, 0.3, 0);
            }
            case FROST -> {
                Fx.particle(at, Particle.BLOCK, 40, 0.4, 0.15, org.bukkit.Material.PACKED_ICE.createBlockData());
                Fx.particle(at, Particle.SNOWFLAKE, 25, 0.5, 0.05);
            }
            case STORM, SAWBLADE -> {
                Fx.particle(at, Particle.ELECTRIC_SPARK, 40, 0.4, 0.3);
                fx.beam(at.clone().add(0, 6, 0), feet, type.accent().value(), 0.4f, 4);
            }
            case TIDE, TRIDENT -> Fx.particle(at, Particle.SPLASH, 60, 0.5, 0.2);
            case VERDANT -> {
                Fx.particle(at, Particle.HAPPY_VILLAGER, 20, 0.5, 0);
                Fx.particle(at, Particle.CHERRY_LEAVES, 10, 0.4, 0);
            }
            case PLAGUE -> Fx.particle(at, Particle.ITEM_SLIME, 25, 0.4, 0.1);
            case CRIMSON -> Fx.particle(at, Particle.DUST, 40, 0.4, 0, Fx.dust(0x8A0A1E, 1.6f));
            case SOLAR -> fx.beam(feet, feet.clone().add(0, 8, 0), 0xFFE08A, 0.9f, 8);
            case VOID, COSMIC -> {
                Fx.particle(at, Particle.REVERSE_PORTAL, 50, 0.3, 0.2);
                fx.flash(at, 0x14062A, 2.4f);
            }
            case CHRONO -> fx.castCircle(feet, type.color().value(), 1.6f);
            case SHADOW, WARGLAIVE -> Fx.particle(at, Particle.LARGE_SMOKE, 25, 0.3, 0.03);
            case SOUL, SCYTHE, ECHO -> {
                for (int i = 0; i < 3; i++) {
                    fx.drift(at, new org.bukkit.util.Vector(r.nextDouble(-1, 1), 2 + r.nextDouble(), r.nextDouble(-1, 1)),
                            type.accent().value(), 0.5f, 18 + i * 4);
                }
                Fx.particle(at, Particle.SOUL, 15, 0.3, 0.05);
            }
            case SPEAR, RAPIER, HALBERD -> Fx.particle(at, Particle.CRIT, 30, 0.35, 0.3);
            case CHAKRAM -> Fx.particle(at, Particle.SMALL_GUST, 12, 0.4, 0.05);
            case AXE -> Fx.particle(at, Particle.DUST, 30, 0.4, 0, Fx.dust(0xFF2A4A, 1.5f));
            case STAFF, SHURIKEN -> Fx.particle(at, Particle.BLOCK, 30, 0.4, 0.1, org.bukkit.Material.AMETHYST_BLOCK.createBlockData());
            case KHOPESH -> Fx.particle(at, Particle.BLOCK, 50, 0.5, 0.15, org.bukkit.Material.SAND.createBlockData());
            case KATANA -> {
                fx.cut(at, new org.bukkit.util.Vector(1, 0, 0), 0.4, 0.2, 0xE6E9F0, 2.4f, 2);
                Fx.particle(at, Particle.DUST, 30, 0.4, 0, Fx.dust(0xD32F2F, 1.4f));
            }
            case NEEDLE -> Fx.particle(at, Particle.DUST, 40, 0.5, 0, Fx.dust(0xF5C518, 1.0f));
            case CANDLE, CLAYMORE -> {
                Fx.particle(at, Particle.FLAME, 25, 0.4, 0.08);
                Fx.particle(at, Particle.LARGE_SMOKE, 8, 0.3, 0.03);
            }
            case MIRROR -> Fx.particle(at, Particle.BLOCK, 40, 0.4, 0.15, org.bukkit.Material.GLASS.createBlockData());
            case CUTLASS -> {
                Fx.particle(at, Particle.SPLASH, 40, 0.5, 0.2);
                Fx.particle(at, Particle.WAX_OFF, 12, 0.4, 0.2);
            }
            case CLAWS -> Fx.particle(at, Particle.SOUL_FIRE_FLAME, 20, 0.4, 0.05);
            case DAGGERS -> Fx.particle(at, Particle.WITCH, 40, 0.4, 0.1);
            case MACE -> fx.beam(feet.clone().add(0, 8, 0), feet, 0xFFF8E1, 0.9f, 8);
            case GLAIVE -> Fx.particle(at, Particle.CHERRY_LEAVES, 30, 0.6, 0.05);
            case MAUL, MANTIS -> Fx.particle(at, Particle.ELECTRIC_SPARK, 40, 0.4, 0.3);
        }
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
        Component swordName = Text.sword(type)
                .hoverEvent(sword.asHoverEvent());
        event.deathMessage(Component.text()
                .append(victim.displayName())
                .append(Component.text(" was " + type.deathVerb() + " by ", NamedTextColor.GRAY))
                .append(killer.displayName())
                .append(Component.text(" wielding ", NamedTextColor.GRAY))
                .append(swordName)
                .build());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.passives().clear(event.getPlayer());
        plugin.status().forget(event.getPlayer());
    }
}
