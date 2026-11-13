package dev.infuse.customs.gui;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.arc.Orders;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Arc II: pick one of the three orders. */
public final class OrderMenu extends Menu {

    private static final int[] SLOTS = {20, 22, 24};

    public OrderMenu(CustomsPlugin plugin) {
        super(5, Text.mm("<gradient:#F5B841:#B46BFF><bold>War of the Three Orders</bold></gradient>"));
        Orders orders = plugin.arcs().orders();
        Orders.Order[] all = Orders.Order.values();
        for (int i = 0; i < all.length; i++) {
            Orders.Order order = all[i];
            List<Component> lore = new ArrayList<>();
            lore.add(Text.caps(orders.count(order) + " sworn · " + orders.score(order) + (orders.score(order) == 1 ? " kill" : " kills"), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(Text.plain("Blessing: " + order.blessing, order.color));
            lore.add(Text.plain(order.blessingText, NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Text.plain("Relic: " + order.relicName + " · " + order.power, order.color));
            lore.add(Text.plain(order.powerText, NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Text.plain("Click to swear yourself to it", NamedTextColor.YELLOW));
            ItemStack banner = button(order.banner, order.label().decorate(TextDecoration.BOLD), lore);
            set(SLOTS[i], banner, player -> {
                String refusal = orders.refuse(player, order);
                if (refusal != null) {
                    Text.send(player, Text.plain(refusal, NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                orders.join(player, order, false);
            });
        }
        set(40, button(org.bukkit.Material.BARRIER, Text.plain("Close", NamedTextColor.RED), List.of()),
                org.bukkit.entity.Player::closeInventory);
    }
}
