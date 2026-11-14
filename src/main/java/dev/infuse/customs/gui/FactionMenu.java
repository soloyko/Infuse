package dev.infuse.customs.gui;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.arc.Factions;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Arc II's factions at a glance: a column per faction with its banner and points, its leader's
 * head (and weapon) and its members' heads.
 */
public final class FactionMenu extends Menu {

    /** Banner slot of each faction's column; the leader goes below it, then members. */
    private static final int[] COLUMNS = {11, 13, 15};

    public FactionMenu(CustomsPlugin plugin, Player viewer) {
        super(6, Text.mm("<gradient:#4DA3FF:#E0A030:#D83A3A><bold>The Factions</bold></gradient>"));
        Factions factions = plugin.arcs().factions();
        Factions.Faction mine = factions.of(viewer);
        Factions.Faction[] all = Factions.Faction.values();
        for (int i = 0; i < all.length; i++) {
            Factions.Faction f = all[i];
            int col = COLUMNS[i];
            int score = factions.score(f);
            List<UUID> roster = factions.roster(f);
            List<Component> lore = new ArrayList<>();
            lore.add(Text.caps(score + (score == 1 ? " point" : " points") + " · " + roster.size() + " sworn", NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Text.plain("Blessing: " + f.blessing, f.color));
            for (String line : Text.wrap(f.blessingText, 34)) {
                lore.add(Text.plain(line, NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Text.plain("Leader carries: ", NamedTextColor.GRAY).append(Text.sword(f.weapon)));
            if (f == mine) {
                lore.add(Component.empty());
                lore.add(Text.plain("◆ Your faction", NamedTextColor.GREEN));
            }
            ItemStack banner = button(f.banner, f.label().decorate(TextDecoration.BOLD), lore);
            banner.editMeta(meta -> meta.setEnchantmentGlintOverride(f == mine));
            set(col, banner, null);

            UUID leader = factions.leader(f);
            if (leader != null) {
                ItemStack head = head(leader, Text.plain("★ ", f.color).append(Text.plain(name(leader), NamedTextColor.WHITE))
                        .decorate(TextDecoration.BOLD), List.of(Text.caps("leader of the " + f.title, f.color),
                        Text.plain(Bukkit.getPlayer(leader) != null ? "Online" : "Offline", Bukkit.getPlayer(leader) != null
                                ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
                set(col + 9, head, null);
            }
            // members in the two rows below the leader; past two, the second slot says how many more
            List<UUID> members = new ArrayList<>(roster);
            members.remove(leader);
            for (int k = 0; k < Math.min(2, members.size()); k++) {
                int slot = col + 9 * (2 + k);
                if (k == 1 && members.size() > 2) {
                    set(slot, button(Material.PAPER, Text.plain("+" + (members.size() - 1) + " more", f.color), List.of()), null);
                    break;
                }
                UUID member = members.get(k);
                boolean online = Bukkit.getPlayer(member) != null;
                set(slot, head(member, Text.plain(name(member), f.color), List.of(Text.caps("sworn", NamedTextColor.GRAY),
                        Text.plain(online ? "Online" : "Offline", online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))), null);
            }
        }
        List<Component> you = new ArrayList<>();
        you.add(mine == null ? Text.plain("You are unsworn.", NamedTextColor.GRAY)
                : Text.plain(factions.isLeader(viewer) ? "You lead the " : "You are sworn to the ", NamedTextColor.GRAY).append(mine.label()));
        you.add(Component.empty());
        you.add(Text.plain("Kills score 1 point; a leader is worth more.", NamedTextColor.GRAY));
        you.add(Text.plain("Fall to a faction's weapon while unsworn", NamedTextColor.GRAY));
        you.add(Text.plain("and you join that faction.", NamedTextColor.GRAY));
        set(INFO, button(Material.WRITABLE_BOOK, Text.plain("Faction War", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), you), null);
        closeButton();
    }

    private static ItemStack head(UUID id, Component name, List<Component> lore) {
        ItemStack item = button(Material.PLAYER_HEAD, name, lore);
        item.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(id)));
        return item;
    }

    private static String name(UUID id) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(id);
        return p.getName() == null ? "someone" : p.getName();
    }
}
