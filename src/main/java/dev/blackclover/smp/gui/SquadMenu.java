package dev.blackclover.smp.gui;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.squad.Squad;
import dev.blackclover.smp.squad.SquadRoster;
import dev.blackclover.smp.squad.SquadService;
import dev.blackclover.smp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** /squad: your squad at a glance, members, invites, colours and the squad ranking. */
public final class SquadMenu {

    private static final int[] MEMBER_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final Map<String, Material> BANNERS = Map.ofEntries(
            Map.entry("red", Material.RED_BANNER), Map.entry("gold", Material.ORANGE_BANNER),
            Map.entry("yellow", Material.YELLOW_BANNER), Map.entry("green", Material.LIME_BANNER),
            Map.entry("dark_green", Material.GREEN_BANNER), Map.entry("aqua", Material.LIGHT_BLUE_BANNER),
            Map.entry("dark_aqua", Material.CYAN_BANNER), Map.entry("blue", Material.BLUE_BANNER),
            Map.entry("light_purple", Material.MAGENTA_BANNER), Map.entry("dark_purple", Material.PURPLE_BANNER),
            Map.entry("white", Material.WHITE_BANNER), Map.entry("gray", Material.LIGHT_GRAY_BANNER));

    private final BlackCloverPlugin plugin;
    private final SquadService squads;

    public SquadMenu(BlackCloverPlugin plugin, SquadService squads) {
        this.plugin = plugin;
        this.squads = squads;
    }

    private static Component line(String miniMessage) {
        return Msg.mm(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack named(ItemStack item, String name, List<String> lore) {
        ItemMeta m = item.getItemMeta();
        m.displayName(line(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(line(s));
        }
        m.lore(l);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(m);
        return item;
    }

    private static Menu framed(int rows, String title) {
        Menu menu = new Menu(rows, Msg.mm("<dark_gray>" + Msg.sc(title)));
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.setHideTooltip(true);
        pane.setItemMeta(pm);
        for (int i = 0; i < rows * 9; i++) {
            menu.set(i, pane, null);
        }
        return menu;
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    public static Material banner(String color) {
        return BANNERS.getOrDefault(color, Material.WHITE_BANNER);
    }

    private ItemStack squadIcon(Squad s, boolean details) {
        SquadRoster roster = squads.roster();
        List<String> lore = new ArrayList<>();
        lore.add("<gold>★ " + s.stars() + "</gold> <dark_gray>" + Msg.sc("stars") + "   <gray>" + Msg.sc("rank") + " <white>#" + roster.rank(s));
        lore.add("<dark_gray>" + Msg.sc("captain") + " <white>" + squads.nameOf(s.captain()));
        lore.add("<dark_gray>" + Msg.sc("members") + " <white>" + s.size() + "</white>/" + roster.maxMembers());
        if (details) {
            int online = (int) s.members().stream().filter(id -> Bukkit.getPlayer(id) != null).count();
            lore.add("<dark_gray>" + Msg.sc("online") + " <green>" + online);
        }
        return named(new ItemStack(banner(s.color())), "<" + s.color() + "><b>" + Msg.sc(s.name()), lore);
    }

    private ItemStack head(UUID id, Squad s, boolean captainView) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        item.setItemMeta(meta);
        PlayerData pd = plugin.players().get(id);
        Player online = Bukkit.getPlayer(id);
        boolean captain = s.captain().equals(id);
        List<String> lore = new ArrayList<>();
        lore.add(captain ? "<gold>♛ " + Msg.sc("captain") : "<gray>" + Msg.sc("magic knight"));
        lore.add(online != null ? "<green>● " + Msg.sc("online") : "<dark_gray>● " + Msg.sc("offline"));
        Optional<GrimoireType> g = plugin.registry().all().stream()
                .filter(st -> id.equals(st.holder()) && st.status() == dev.blackclover.smp.grimoire.GrimoireStatus.HELD)
                .map(dev.blackclover.smp.grimoire.GrimoireState::type).findFirst();
        lore.add("");
        if (g.isPresent()) {
            lore.add("<" + Msg.hex(g.get().rgb()) + ">" + Msg.sc(g.get().displayName() + " grimoire") + "  <white>"
                    + Msg.sc("lv") + " " + plugin.mage().level(id, g.get()));
        } else {
            lore.add("<dark_gray>" + Msg.sc("no grimoire"));
        }
        if (pd != null) {
            lore.add("<dark_gray>" + Msg.sc("kills") + " <white>" + pd.kills() + "</white>  " + Msg.sc("deaths") + " <white>" + pd.deaths()
                    + "</white>");
        }
        if (captainView && !captain) {
            lore.add("");
            lore.add("<yellow>" + Msg.sc("click to manage"));
        }
        return named(item, "<" + s.color() + ">" + squads.nameOf(id), lore);
    }

    public void open(Player p) {
        Optional<Squad> mine = squads.squadOf(p);
        if (mine.isEmpty()) {
            openNoSquad(p);
        } else {
            openSquad(p, mine.get());
        }
    }

    private void openNoSquad(Player p) {
        Menu menu = framed(5, "Magic Knight Squads");
        menu.set(13, named(new ItemStack(Material.WRITABLE_BOOK), "<green><b>" + Msg.sc("Found a squad"), List.of(
                "<white>/squad create <name>", "", "<gray>Squadmates can't hurt each other,",
                "<gray>share a chat, and earn <gold>★ stars</gold>",
                "<gray>together for kills and grimoires.", "", "<gray>The squad with the most stars",
                "<gray>tops the ranking.")), null);
        List<Squad> invites = squads.roster().invitesFor(p.getUniqueId(), System.currentTimeMillis());
        int[] slots = {29, 30, 31, 32, 33};
        for (int i = 0; i < invites.size() && i < slots.length; i++) {
            Squad s = invites.get(i);
            ItemStack icon = squadIcon(s, true);
            ItemMeta m = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore());
            lore.add(Component.empty());
            lore.add(line("<green>" + Msg.sc("click to join")));
            m.lore(lore);
            icon.setItemMeta(m);
            menu.set(slots[i], icon, e -> {
                click(p);
                p.closeInventory();
                squads.join(p, s.name());
            });
        }
        if (invites.isEmpty()) {
            menu.set(31, named(new ItemStack(Material.PAPER), "<gray>" + Msg.sc("No invites"),
                    List.of("<dark_gray>A squad captain can invite you", "<dark_gray>with /squad invite.")), null);
        }
        menu.set(40, AdminMenus.icon(Material.GOLD_INGOT, "<gold><b>Squad ranking", "<gray>Click to view"), e -> {
            click(p);
            openRanking(p);
        });
        menu.open(p);
    }

    private void openSquad(Player p, Squad s) {
        boolean captain = s.captain().equals(p.getUniqueId());
        Menu menu = framed(6, s.name());
        menu.set(4, squadIcon(s, true), null);
        for (int i = 0; i < s.members().size() && i < MEMBER_SLOTS.length; i++) {
            UUID id = s.members().get(i);
            boolean manage = captain && !id.equals(p.getUniqueId());
            menu.set(MEMBER_SLOTS[i], head(id, s, captain), manage ? e -> {
                click(p);
                openMember(p, s, id);
            } : null);
        }
        boolean chat = squads.inChatMode(p);
        menu.set(46, AdminMenus.icon(chat ? Material.LIME_DYE : Material.GRAY_DYE,
                chat ? "<green><b>Squad chat: on" : "<gray><b>Squad chat: off",
                "<gray>Click to switch"), e -> {
            click(p);
            squads.toggleChat(p);
            open(p);
        });
        if (captain) {
            menu.set(48, AdminMenus.icon(Material.PLAYER_HEAD, "<green><b>Invite players",
                    "<gray>Pick an online player", "<gray>who isn't in a squad"), e -> {
                click(p);
                openInvite(p);
            });
            menu.set(50, AdminMenus.icon(banner(s.color()), "<" + s.color() + "><b>Squad colour",
                    "<gray>Click to choose"), e -> {
                click(p);
                openColors(p, s);
            });
        }
        menu.set(49, AdminMenus.icon(Material.GOLD_INGOT, "<gold><b>Squad ranking", "<gray>Click to view"), e -> {
            click(p);
            openRanking(p);
        });
        menu.set(52, AdminMenus.icon(Material.BARRIER, captain ? "<red><b>Leave or disband" : "<red><b>Leave squad",
                "<gray>Click, then confirm"), e -> {
            click(p);
            openLeave(p, s);
        });
        menu.open(p);
    }

    private void openMember(Player p, Squad s, UUID member) {
        Menu menu = framed(3, squads.nameOf(member));
        menu.set(13, head(member, s, false), null);
        menu.set(11, AdminMenus.icon(Material.GOLDEN_HELMET, "<gold><b>Make captain",
                "<gray>Hand the squad to " + squads.nameOf(member)), e -> {
            click(p);
            p.closeInventory();
            squads.transfer(p, member);
        });
        menu.set(15, AdminMenus.icon(Material.IRON_DOOR, "<red><b>Remove from squad", "<gray>Shift-click to confirm"), e -> {
            if (!e.isShiftClick()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
                return;
            }
            click(p);
            squads.kick(p, member);
            open(p);
        });
        menu.set(22, AdminMenus.icon(Material.ARROW, "<gray>Back"), e -> {
            click(p);
            open(p);
        });
        menu.open(p);
    }

    private void openInvite(Player p) {
        Menu menu = framed(6, "Invite to your squad");
        int slot = 10;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p) || squads.squadOf(other).isPresent()) {
                continue;
            }
            if (slot > 43) {
                break;
            }
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(other);
            item.setItemMeta(meta);
            named(item, "<white>" + other.getName(), List.of("<green>" + Msg.sc("click to invite")));
            menu.set(slot, item, e -> {
                click(p);
                p.closeInventory();
                squads.invite(p, other);
            });
            slot += slot % 9 == 7 ? 3 : 1;
        }
        if (slot == 10) {
            menu.set(22, AdminMenus.icon(Material.PAPER, "<gray>Nobody to invite", "<dark_gray>Everyone online is in a squad."), null);
        }
        menu.set(49, AdminMenus.icon(Material.ARROW, "<gray>Back"), e -> {
            click(p);
            open(p);
        });
        menu.open(p);
    }

    private void openColors(Player p, Squad s) {
        Menu menu = framed(3, "Squad colour");
        int[] slots = {1, 2, 3, 4, 5, 6, 7, 11, 12, 13, 14, 15};
        for (int i = 0; i < SquadRoster.COLORS.size(); i++) {
            String c = SquadRoster.COLORS.get(i);
            boolean current = c.equals(s.color());
            ItemStack icon = AdminMenus.icon(banner(c), "<" + c + "><b>" + SquadService.colorLabel(c),
                    current ? "<green>" + "✔ current" : "<gray>Click to choose");
            menu.set(slots[i], icon, e -> {
                click(p);
                squads.color(p, c);
                open(p);
            });
        }
        menu.set(22, AdminMenus.icon(Material.ARROW, "<gray>Back"), e -> {
            click(p);
            open(p);
        });
        menu.open(p);
    }

    private void openLeave(Player p, Squad s) {
        boolean captain = s.captain().equals(p.getUniqueId());
        Menu menu = framed(3, "Leave " + s.name());
        menu.set(11, AdminMenus.icon(Material.OAK_DOOR, "<red><b>Leave squad",
                captain && s.size() > 1 ? "<gray>" + squads.nameOf(s.members().get(1)) + " becomes captain" : "<gray>Leave " + s.name()), e -> {
            click(p);
            p.closeInventory();
            squads.leave(p);
        });
        if (captain) {
            menu.set(13, AdminMenus.icon(Material.TNT, "<dark_red><b>Disband squad",
                    "<gray>Everyone is removed", "<gray>and the stars are lost.", "", "<red>Shift-click to confirm"), e -> {
                if (!e.isShiftClick()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
                    return;
                }
                p.closeInventory();
                squads.disband(p);
            });
        }
        menu.set(15, AdminMenus.icon(Material.ARROW, "<gray>Cancel"), e -> {
            click(p);
            open(p);
        });
        menu.open(p);
    }

    public void openRanking(Player p) {
        Menu menu = framed(6, "Squad ranking");
        List<Squad> ranking = squads.roster().ranking();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < ranking.size() && i < slots.length; i++) {
            Squad s = ranking.get(i);
            ItemStack icon = squadIcon(s, false);
            ItemMeta m = icon.getItemMeta();
            String medal = (i == 0 ? "<gold>" : i == 1 ? "<white>" : i == 2 ? "<#cd7f32>" : "<dark_gray>") + "#" + (i + 1) + " ";
            m.displayName(line(medal + "<" + s.color() + "><b>" + Msg.sc(s.name())));
            icon.setItemMeta(m);
            menu.set(slots[i], icon, null);
        }
        if (ranking.isEmpty()) {
            menu.set(22, named(new ItemStack(Material.PAPER), "<gray>" + Msg.sc("No squads yet"), List.of("<dark_gray>/squad create <name>")), null);
        }
        menu.set(49, AdminMenus.icon(Material.ARROW, "<gray>Back"), e -> {
            click(p);
            open(p);
        });
        menu.open(p);
    }
}
