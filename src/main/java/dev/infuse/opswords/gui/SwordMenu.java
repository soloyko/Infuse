package dev.infuse.opswords.gui;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Admin menu: click a sword to get a copy. */
public final class SwordMenu extends Menu {

    /** Three centred rows of five. */
    static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    static final int ROWS = 5;

    public SwordMenu(OPSwordsPlugin plugin) {
        super(ROWS, Text.mm("<gradient:#ECBA44:#C4415B><bold>OP Swords</bold></gradient>"));
        SwordType[] types = SwordType.values();
        for (int i = 0; i < types.length && i < SLOTS.length; i++) {
            SwordType type = types[i];
            set(SLOTS[i], plugin.items().create(type), player -> give(plugin, player, type));
        }
    }

    private static void give(OPSwordsPlugin plugin, Player player, SwordType type) {
        if (!player.hasPermission("opswords.admin")) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(plugin.items().create(type));
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        Text.send(player, Component.text("You received ").color(Text.GRAY)
                .append(Text.sword(type))
                .append(Component.text(".", Text.GRAY)));
    }
}
