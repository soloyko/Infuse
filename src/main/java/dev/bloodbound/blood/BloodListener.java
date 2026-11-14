package dev.bloodbound.blood;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.core.BloodRules;
import dev.bloodbound.event.EventListener;
import dev.bloodbound.event.EventType;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import static dev.bloodbound.config.Messages.text;

/** Turns kills and deaths during a session into blood gains, shatters and losses. */
public final class BloodListener implements Listener {
    private final BloodboundPlugin plugin;

    public BloodListener(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.sessions().isActive()) return;
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        BloodRules rules = plugin.settings().rules();
        int victimBlood = plugin.players().get(victim).blood();

        if (killer == null || killer.equals(victim)) {
            plugin.blood().remove(victim, rules.environmentalLoss(victimBlood));
            if (plugin.events().isBountyHolder(victim.getUniqueId())) plugin.events().reassignBounty(victim);
            return;
        }

        // The bounty always passes to the killer, even on a repeat kill.
        plugin.events().onBountyKilled(victim, killer);
        if (plugin.events().current().orElse(null) == EventType.LAST_STAND) EventListener.fullHeal(killer);

        if (!plugin.killCooldowns().tryRecord(killer.getUniqueId(), victim.getUniqueId())) {
            plugin.messages().send(killer, "blood.repeat-kill", text("player", victim.getName()));
            return;
        }

        int reward = rules.killReward(plugin.blood().tierOf(killer), plugin.blood().tierOf(victim),
                plugin.events().bloodMultiplier());
        BloodRules.Shatter shatter = rules.shatter(victimBlood);

        plugin.blood().remove(victim, shatter.lost());
        plugin.blood().add(killer, reward + shatter.killerGain(), "blood.reason-kill");
        if (shatter.killerGain() > 0) {
            plugin.messages().send(killer, "blood.shattered", text("player", victim.getName()),
                    text("amount", shatter.killerGain()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        if (!plugin.sessions().isActive()) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !(entity instanceof Enemy)) return;
        Player killer = entity.getKiller();
        if (killer == null) return;
        plugin.blood().add(killer, plugin.settings().rules().mobReward(plugin.events().bloodMultiplier()), "blood.reason-mob");
    }
}
