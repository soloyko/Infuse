package dev.infuse.opswords.gui;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Admin armory: click a weapon (or the altar) to get one. */
public final class SwordMenu extends Menu {

    public SwordMenu(OPSwordsPlugin plugin) {
        super(ROWS, Text.mm("<gradient:#ECBA44:#C4415B><bold>OP Swords</bold></gradient> <dark_gray>· Armory"));
        info(plugin, "Click a weapon to take a copy.");
        for (SwordType type : SwordType.values()) {
            ItemStack icon = plugin.items().create(type);
            List<Component> lore = new ArrayList<>(icon.lore() == null ? List.of() : icon.lore());
            lore.add(Component.empty());
            lore.add(Text.plain("Click to take one", NamedTextColor.YELLOW));
            icon.lore(lore);
            set(slotOf(type), icon, player -> give(plugin, player, plugin.items().create(type), Text.sword(type)));
        }
        if (plugin.settings().altarsEnabled) {
            ItemStack altar = plugin.altars().createAltarItem(1);
            set(ALTAR, altar, player -> give(plugin, player, plugin.altars().createAltarItem(1),
                    Text.plain("a Weapon Altar", NamedTextColor.GOLD)));
        }
        closeButton();
    }

    private static void give(OPSwordsPlugin plugin, Player player, ItemStack item, Component name) {
        if (!player.hasPermission("opswords.admin")) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        Text.send(player, Component.text("You received ", NamedTextColor.GRAY).append(name)
                .append(Component.text(".", NamedTextColor.GRAY)));
    }
}
