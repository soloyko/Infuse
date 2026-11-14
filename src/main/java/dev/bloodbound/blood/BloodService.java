package dev.bloodbound.blood;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.config.Messages;
import dev.bloodbound.core.Tiers;
import dev.bloodbound.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import static dev.bloodbound.config.Messages.text;

/** The only place blood changes. Handles tier-up/down feedback and weapon lore refresh. */
public final class BloodService {
    private final BloodboundPlugin plugin;

    public BloodService(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    public void add(Player player, int amount, String reasonKey) {
        if (amount <= 0) return;
        PlayerData data = plugin.players().get(player);
        int before = data.blood();
        data.blood(before + amount);
        plugin.players().markDirty();
        Messages m = plugin.messages();
        plugin.abilities().pauseBar(player, 1500);
        m.actionBar(player, "blood.gained", text("amount", amount), text("reason", m.raw(reasonKey)));
        afterChange(player, before, data.blood());
    }

    public void remove(Player player, int amount) {
        if (amount <= 0) return;
        PlayerData data = plugin.players().get(player);
        int before = data.blood();
        data.blood(before - amount);
        plugin.players().markDirty();
        plugin.messages().send(player, "blood.lost", text("amount", before - data.blood()));
        afterChange(player, before, data.blood());
    }

    public void set(PlayerData data, int amount) {
        int before = data.blood();
        data.blood(amount);
        plugin.players().markDirty();
        Player online = Bukkit.getPlayer(data.id());
        if (online != null) afterChange(online, before, data.blood());
    }

    public int tierOf(Player player) {
        return plugin.settings().tiers().tierFor(plugin.players().get(player).blood());
    }

    private void afterChange(Player player, int before, int after) {
        Tiers tiers = plugin.settings().tiers();
        int oldTier = tiers.tierFor(before), newTier = tiers.tierFor(after);
        Messages m = plugin.messages();
        if (newTier > oldTier) {
            m.title(player, "blood.tier-up-title", "blood.tier-up-subtitle", text("tier", newTier));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            if (newTier == tiers.maxTier()) {
                m.send(Bukkit.getServer(), "blood.max-tier-broadcast", text("player", player.getName()));
            }
        } else if (newTier < oldTier) {
            m.send(player, "blood.tier-down", text("tier", newTier));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 0.8f, 0.8f);
        }
        plugin.weapons().refresh(player);
    }
}
