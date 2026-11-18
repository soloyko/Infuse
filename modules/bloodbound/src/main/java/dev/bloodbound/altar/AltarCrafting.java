package dev.bloodbound.altar;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.data.PlayerData;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

import static dev.bloodbound.config.Messages.comp;
import static dev.bloodbound.config.Messages.text;

/** Pays a recipe from the player's inventory and hands out a weapon. */
public final class AltarCrafting {
    public enum Result { OK, NOT_IN_SESSION, ALREADY_OWNS, MISSING_ITEMS }

    private final BloodboundPlugin plugin;

    public AltarCrafting(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    public Result tryCraft(Player player, WeaponType type) {
        if (!plugin.sessions().isActive()) return Result.NOT_IN_SESSION;
        PlayerData data = plugin.players().get(player);
        if (data.type() == type && plugin.weapons().hasValidWeapon(player)) return Result.ALREADY_OWNS;

        Map<Material, Integer> recipe = plugin.settings().weapon(type).recipe();
        for (Map.Entry<Material, Integer> e : recipe.entrySet()) {
            if (count(player, e.getKey(), plugin) < e.getValue()) return Result.MISSING_ITEMS;
        }
        for (Map.Entry<Material, Integer> e : recipe.entrySet()) take(player, e.getKey(), e.getValue());

        plugin.weapons().removeOwnWeapons(player);
        data.type(type);
        data.weaponVersion(data.weaponVersion() + 1);
        plugin.players().markDirty();
        ItemStack weapon = plugin.weapons().create(data);
        player.getInventory().addItem(weapon).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 0, 20), 1.6f));
        plugin.messages().send(player, "altar.forged", comp("weapon", plugin.weapons().displayName(type)));
        if (plugin.settings().announceForges()) {
            plugin.messages().send(Bukkit.getServer(), "altar.forged-broadcast",
                    text("player", player.getName()), comp("weapon", plugin.weapons().displayName(type)));
        }
        return Result.OK;
    }

    /** Counts usable items of a material (Bloodbound weapons never count as ingredients). */
    static int count(Player player, Material material, BloodboundPlugin plugin) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material && !plugin.weapons().isWeapon(item)) total += item.getAmount();
        }
        return total;
    }

    private void take(Player player, Material material, int amount) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material || plugin.weapons().isWeapon(item)) continue;
            int used = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - used);
            left -= used;
            contents[i] = item.getAmount() <= 0 ? null : item;
        }
        inv.setStorageContents(contents);
    }
}
