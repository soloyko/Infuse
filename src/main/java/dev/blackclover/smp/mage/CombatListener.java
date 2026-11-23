package dev.blackclover.smp.mage;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.game.GameService;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.util.Msg;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Grace-period PvP blocking, kill credit, death handling and combat logging. */
public final class CombatListener implements Listener {

    private final BlackCloverPlugin plugin;
    private final MageService mage;
    private final GrimoireService grimoires;
    private final GameService game;
    private Consumer<Player> onDemonDeath = p -> { };

    public CombatListener(BlackCloverPlugin plugin, MageService mage, GrimoireService grimoires, GameService game) {
        this.plugin = plugin;
        this.mage = mage;
        this.grimoires = grimoires;
        this.game = game;
    }

    public void setOnDemonDeath(Consumer<Player> onDemonDeath) {
        this.onDemonDeath = onDemonDeath;
    }

    /** The player responsible for this damage, if any. */
    public static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            return p;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) {
            return p;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGracePvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !game.isGrace()) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker != null && !attacker.equals(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (Fx.probing || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker != null && !attacker.equals(victim)) {
            mage.combat().recordHit(attacker.getUniqueId(), victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        UUID id = victim.getUniqueId();
        long now = System.currentTimeMillis();
        boolean grace = game.isGrace();
        boolean isDemon = id.equals(plugin.demonRef().get());
        Optional<GrimoireType> carried = grimoires.carried(victim);
        int victimLevel = carried.map(t -> mage.level(id, t)).orElse(0);

        event.getDrops().removeIf(grimoires.items()::isGrimoire);
        if (carried.isPresent()) {
            GrimoireType type = carried.get();
            if (grace && !isDemon) {
                if (!event.getKeepInventory()) {
                    for (ItemStack stack : victim.getInventory().getContents()) {
                        if (grimoires.items().typeOf(stack).orElse(null) == type && grimoires.isLive(stack)) {
                            event.getItemsToKeep().add(stack);
                            break;
                        }
                    }
                }
            } else {
                if (event.getKeepInventory()) {
                    grimoires.removeAll(victim, type);
                }
                grimoires.dropAt(type, victim.getLocation());
            }
        }

        if (!grace) {
            mage.data(id).recordDeath();
        }
        Player killer = mage.combat().lastAttacker(id, now).map(plugin.getServer()::getPlayer).orElse(victim.getKiller());
        if (killer != null && !killer.equals(victim) && !grace) {
            event.deathMessage(killFeed(killer, victim, carried.orElse(null), victimLevel));
            boolean allies = plugin.squads().allies(killer, victim);
            if (!allies && mage.onKill(killer, victim, carried.orElse(null), victimLevel)) {
                mage.data(killer.getUniqueId()).recordKill();
                plugin.squads().onKill(killer, victim);
                if (isDemon) {
                    plugin.squads().onDemonSlain(killer);
                }
            }
            if (id.equals(plugin.game().wizardKing())) {
                Msg.broadcast("<gold>♛</gold> <gray>The Wizard King <white><v></white> was slain by <white><k></white>!",
                        Msg.arg("k", killer.getName()), Msg.arg("v", victim.getName()));
            }
        }
        carried.ifPresent(type -> mage.applyDeathPenalty(victim, type, grace));
        if (isDemon) {
            onDemonDeath.accept(victim);
        }
        mage.combat().clear(id);
        plugin.saveAsync();
    }

    /** "☠ Victim [Fire 7] was slain by Killer [Water 9]" */
    private net.kyori.adventure.text.Component killFeed(Player killer, Player victim, GrimoireType victimType, int victimLevel) {
        Optional<GrimoireType> killerType = grimoires.carried(killer);
        return Msg.mm("<dark_red>☠</dark_red> <white><v></white>" + badge(victimType, victimLevel)
                        + " <gray>was slain by</gray> <white><k></white>"
                        + badge(killerType.orElse(null), killerType.map(t -> mage.level(killer.getUniqueId(), t)).orElse(0)),
                Msg.arg("v", victim.getName()), Msg.arg("k", killer.getName()));
    }

    private static String badge(GrimoireType type, int level) {
        if (type == null) {
            return "";
        }
        String c = Msg.hex(type.rgb());
        return " <dark_gray>[<" + c + ">" + type.displayName() + " " + level + "</" + c + ">]</dark_gray>";
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (!plugin.settings().combatLogKills() || game.isGrace() || p.isDead()
                || !mage.combat().inCombat(p.getUniqueId(), System.currentTimeMillis())) {
            return;
        }
        Msg.broadcast("<white><name></white> <red>logged out during combat and was killed.", Msg.arg("name", p.getName()));
        p.setHealth(0);
    }
}
