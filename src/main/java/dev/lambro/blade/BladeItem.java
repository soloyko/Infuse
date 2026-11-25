package dev.lambro.blade;

import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.Trigger;
import dev.lambro.blade.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

import static dev.lambro.blade.util.Text.*;

/** Creates, identifies and re-renders the Blade of Lambro item. */
@SuppressWarnings("UnstableApiUsage")
public final class BladeItem {

    private final LambroPlugin plugin;
    private final NamespacedKey bladeKey;
    private final NamespacedKey killsKey;

    public BladeItem(LambroPlugin plugin) {
        this.plugin = plugin;
        this.bladeKey = new NamespacedKey(plugin, "blade");
        this.killsKey = new NamespacedKey(plugin, "kills");
    }

    public ItemStack create() {
        ItemStack item = ItemStack.of(Material.NETHERITE_SWORD);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(bladeKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(killsKey, PersistentDataType.INTEGER, 0);
        });
        render(item);
        return item;
    }

    public boolean isBlade(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) return false;
        return item.getPersistentDataContainer().has(bladeKey, PersistentDataType.BYTE);
    }

    public boolean isHolding(Player player) {
        return isBlade(player.getInventory().getItemInMainHand());
    }

    public int kills(ItemStack item) {
        if (!isBlade(item)) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.getOrDefault(killsKey, PersistentDataType.INTEGER, 0);
    }

    public void setKills(ItemStack item, int kills) {
        item.editPersistentDataContainer(pdc -> pdc.set(killsKey, PersistentDataType.INTEGER, Math.max(0, kills)));
        render(item);
    }

    public int killsPerUnlock() {
        return Math.max(1, plugin.getConfig().getInt("general.kills-per-unlock", 3));
    }

    /** Number of tiers unlocked for a kill count. */
    public int tiers(int kills) {
        return Math.min(plugin.abilities().all().size(), kills / killsPerUnlock());
    }

    public boolean isUnlocked(ItemStack item, Ability ability) {
        return tiers(kills(item)) >= ability.tier();
    }

    /** Rebuilds name, model and lore from the current kill count + config. */
    public void render(ItemStack item) {
        int kills = kills(item);
        int per = killsPerUnlock();
        List<Ability> abilities = plugin.abilities().all();
        int unlocked = tiers(kills);
        boolean maxed = unlocked >= abilities.size();


        List<Component> lore = new ArrayList<>();
        lore.add(mm("<" + STEEL + ">" + tc("mythic blade") + " <" + SHADOW + ">◆ <" + STEEL + ">" + tc("tide & void")));
        lore.add(Component.empty());
        lore.add(mm("<" + SHADOW + ">“<" + STEEL + ">" + tc("forged where the abyss swallows the storm.") + "<" + SHADOW + ">”"));
        lore.add(Component.empty());

        // Kill progress
        if (maxed) {
            lore.add(mm("<" + CYAN + ">⚔ <" + WHITE + ">" + kills + " <" + STEEL + ">" + tc("kills") + "  <" + SHADOW + ">│  "
                    + GRADIENT + tc("fully awakened") + "</gradient>"));
        } else {
            int into = kills - unlocked * per;
            int left = per - into;
            lore.add(mm("<" + CYAN + ">⚔ <" + WHITE + ">" + kills + " <" + STEEL + ">" + tc("kills") + "  <" + SHADOW + ">│  "
                    + bar(into / (double) per, 10, BLUE, SHADOW) + "  <" + STEEL + ">" + left + " " + tc("to awaken")));
        }
        lore.add(Component.empty());
        lore.add(mm(GRADIENT + tc("powers") + "</gradient> <" + SHADOW + ">" + unlocked + "/" + abilities.size()));

        for (Ability a : abilities) {
            boolean open = unlocked >= a.tier();
            String kind = a.trigger() == Trigger.PASSIVE ? tc("passive") : tc(a.trigger().label());
            if (!a.enabled()) {
                lore.add(mm(" <" + SHADOW + ">✕ <st>" + tc(a.name()) + "</st> <" + SHADOW + ">· " + tc("disabled")));
            } else if (open) {
                String bullet = a.isPassive() ? "◆" : "✦";
                lore.add(mm(" <" + CYAN + ">" + bullet + " <" + WHITE + ">" + tc(a.name()) + " <" + SHADOW + ">· <" + BLUE + ">" + kind));
                lore.add(mm("   <" + STEEL + ">" + tc(a.summary())));
            } else {
                int need = a.tier() * per;
                lore.add(mm(" <" + SHADOW + ">◇ <obf>" + a.name().replaceAll("[^ ]", "x") + "</obf> <" + SHADOW + ">· "
                        + tc(need + " kills")));
            }
        }

        lore.add(Component.empty());
        lore.add(mm("<" + STEEL + ">" + tc("damage") + " <" + WHITE + ">8  <" + SHADOW + ">│  <" + STEEL + ">" + tc("speed") + " <" + WHITE + ">1.6"
                + (plugin.getConfig().getBoolean("general.unbreakable", true) ? "  <" + SHADOW + ">│  <" + STEEL + ">" + tc("unbreakable") : "")));

        item.editMeta(meta -> {
            meta.itemName(mm(plugin.getConfig().getString("general.display-name",
                    "<bold>" + GRADIENT + "Blade of Lambro</gradient></bold>")));
            meta.lore(lore);
            meta.setUnbreakable(plugin.getConfig().getBoolean("general.unbreakable", true));
        });

        // Data components last, so the meta write above can never clobber them.
        String modelKey = plugin.getConfig().getString("general.item-model", "minecraft:blade_of_lambro");
        if (modelKey != null && !modelKey.isBlank()) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(modelKey));
        } else {
            item.resetData(DataComponentTypes.ITEM_MODEL);
        }
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE)
                .build());
    }
}
