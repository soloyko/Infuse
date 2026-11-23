package dev.blackclover.smp.gui;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.command.AdminActions;
import dev.blackclover.smp.game.Library;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The /bcadmin chest menus. */
public final class AdminMenus {

    private final BlackCloverPlugin plugin;
    private final AdminActions actions;

    public AdminMenus(BlackCloverPlugin plugin, AdminActions actions) {
        this.plugin = plugin;
        this.actions = actions;
    }

    /** Menu icon; text is set in small caps. Put player names in {@code <name>} via {@code args} to keep their case. */
    static ItemStack icon(Material material, String name, String... lore) {
        return icon(material, new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0], name, lore);
    }

    static ItemStack icon(Material material, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] args,
                          String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.mm(Msg.sc(name), args).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            lines.add(Msg.mm(Msg.sc(l), args).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static String esc(String s) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(s);
    }

    private static Component title(String text) {
        return Msg.mm(Msg.sc(text));
    }

    private ItemStack back() {
        return icon(Material.ARROW, "<gray>Back");
    }

    // ---------------------------------------------------------------- main

    public void openMain(Player admin) {
        new Menu(3, title("<dark_gray>Black Clover — Admin"))
                .set(11, icon(Material.PLAYER_HEAD, "<yellow><b>Players", "<gray>Possess, give Anti-Magic,", "<gray>take grimoires, set levels"),
                        e -> openPlayers(admin))
                .set(13, icon(Material.ENCHANTED_BOOK, "<light_purple><b>Grimoires", "<gray>Where all 15 grimoires are"),
                        e -> openGrimoires(admin))
                .set(15, icon(Material.CLOCK, "<green><b>Game", "<gray>Grace, possession, reset"),
                        e -> openGame(admin))
                .open(admin);
    }

    // ---------------------------------------------------------------- players

    public void openPlayers(Player admin) {
        Menu menu = new Menu(6, title("<dark_gray>Players"));
        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < Math.min(45, online.size()); i++) {
            Player target = online.get(i);
            menu.set(i, head(target), e -> openPlayer(admin, target.getUniqueId()));
        }
        menu.set(49, back(), e -> openMain(admin)).open(admin);
    }

    private ItemStack head(Player target) {
        Optional<GrimoireType> carried = plugin.grimoires().carried(target);
        String grimoire = carried.map(t -> "<gray>Grimoire: <white>" + t.displayName() + " <gray>Lv <white>"
                + plugin.mage().level(target.getUniqueId(), t)).orElse("<dark_gray>No grimoire");
        boolean demon = target.getUniqueId().equals(plugin.demonRef().get());
        ItemStack item = icon(Material.PLAYER_HEAD, new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.arg("name", target.getName())},
                (demon ? "<dark_red>☠ " : "<yellow>") + "<name>",
                grimoire, demon ? "<dark_red>Demon Possessed" : "", "<dark_gray>Click to manage");
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        item.setItemMeta(meta);
        return item;
    }

    public void openPlayer(Player admin, UUID targetId) {
        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null) {
            Msg.send(admin, "<red>That player went offline.");
            openPlayers(admin);
            return;
        }
        GrimoireState anti = plugin.registry().get(GrimoireType.ANTI_MAGIC);
        Menu menu = new Menu(3, Msg.mm(Msg.sc("<dark_gray>Manage ") + "<name>", Msg.arg("name", target.getName())));
        menu.set(4, head(target), null)
                .set(10, icon(Material.WITHER_SKELETON_SKULL, "<dark_red><b>Possess", "<gray>Make them the Demon Possessed.",
                        "<gray>They get the Five-Leaf Grimoire at level 20."), e -> {
                    actions.possess(admin, target);
                    openPlayer(admin, targetId);
                })
                .set(11, icon(Material.NETHER_STAR, "<white><b>Give Anti-Magic",
                        anti.status() == GrimoireStatus.ADMIN_POOL ? "<green>Available" : "<red>" + esc(actions.describe(anti)).replace(" by ", " by "),
                        "<gray>Their current grimoire returns home."), e -> {
                    actions.giveAntiMagic(admin, target);
                    openPlayer(admin, targetId);
                })
                .set(12, icon(Material.HOPPER, "<gold><b>Take grimoire", "<gray>Sends their grimoire home."), e -> {
                    actions.take(admin, targetId, target.getName());
                    openPlayer(admin, targetId);
                })
                .set(14, icon(Material.LIME_DYE, "<green><b>Level +1"), e -> {
                    actions.setLevel(admin, target, plugin.mage().carriedLevel(target) + 1);
                    openPlayer(admin, targetId);
                })
                .set(15, icon(Material.RED_DYE, "<red><b>Level −1"), e -> {
                    actions.setLevel(admin, target, plugin.mage().carriedLevel(target) - 1);
                    openPlayer(admin, targetId);
                })
                .set(16, icon(Material.LAVA_BUCKET, "<dark_red><b>Clear XP", "<gray>On the grimoire they carry."), e -> {
                    actions.clearXp(admin, target);
                    openPlayer(admin, targetId);
                })
                .set(22, back(), e -> openPlayers(admin))
                .open(admin);
    }

    // ---------------------------------------------------------------- grimoires

    public void openGrimoires(Player admin) {
        Menu menu = new Menu(3, title("<dark_gray>Grimoires"));
        GrimoireType[] types = GrimoireType.values();
        for (int i = 0; i < types.length; i++) {
            GrimoireType type = types[i];
            GrimoireState s = plugin.registry().get(type);
            ItemStack bookIcon = icon(s.status() == GrimoireStatus.HELD ? Material.ENCHANTED_BOOK : Material.BOOK,
                    new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.arg("where", actions.describe(s))},
                    "<" + Msg.hex(type.rgb()) + "><b>" + type.displayName() + " Grimoire",
                    "<gray><where>", "", "<dark_gray>Click: teleport to it", "<dark_gray>Shift-click: return it home");
            ItemMeta bookMeta = bookIcon.getItemMeta();
            dev.blackclover.smp.pack.PackItems.tag(bookMeta, dev.blackclover.smp.pack.PackModels.grimoireKey(type));
            bookIcon.setItemMeta(bookMeta);
            menu.set(i, bookIcon,
                    e -> {
                        if (e.isShiftClick()) {
                            plugin.grimoires().returnHome(type, "returned by admin " + admin.getName());
                            Msg.send(admin, "<green><type> grimoire returned home.", Msg.arg("type", type.displayName()));
                            openGrimoires(admin);
                        } else {
                            teleportTo(admin, type);
                        }
                    });
        }
        menu.set(22, back(), e -> openMain(admin)).open(admin);
    }

    private void teleportTo(Player admin, GrimoireType type) {
        GrimoireState s = plugin.registry().get(type);
        Location dest = null;
        switch (s.status()) {
            case HELD -> {
                Player holder = plugin.getServer().getPlayer(s.holder());
                if (holder != null) {
                    dest = holder.getLocation();
                }
            }
            case GROUND -> {
                org.bukkit.entity.Entity item = s.itemEntity() == null ? null : plugin.getServer().getEntity(s.itemEntity());
                World w = s.ground() == null ? null : plugin.getServer().getWorld(s.ground().world());
                if (item != null) {
                    dest = item.getLocation().add(0, 0.5, 0);
                } else if (w != null) {
                    dest = new Location(w, s.ground().x(), s.ground().y() + 0.5, s.ground().z());
                }
            }
            case LIBRARY -> {
                Library lib = plugin.game().libraries().get(type);
                World w = lib == null ? null : plugin.getServer().getWorld(lib.world());
                if (w != null) {
                    dest = new Location(w, lib.altarX() + 0.5, lib.altarY(), lib.altarZ() + 2.5);
                }
            }
            case ADMIN_POOL -> {
            }
        }
        if (dest == null) {
            Msg.send(admin, "<red>Can't teleport: the grimoire is <s>.", Msg.arg("s", actions.describe(s)));
            return;
        }
        admin.closeInventory();
        admin.teleport(dest);
    }

    // ---------------------------------------------------------------- game

    public void openGame(Player admin) {
        String demon = plugin.demon().demon().map(id -> plugin.mage().data(id).name()).orElse("none");
        long grace = plugin.gameService().graceRemainingMillis() / 1000;
        new Menu(3, title("<dark_gray>Game"))
                .set(4, icon(Material.PAPER, new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.arg("demon", demon)},
                        "<white><b>Status",
                        "<gray>Started: <white>" + plugin.game().isStarted(),
                        "<gray>Border: <white>" + plugin.game().borderSize(),
                        "<gray>Libraries: <white>" + plugin.game().libraries().size() + "/13",
                        "<gray>Grace left: <white>" + (grace > 0 ? grace + "s" : "none"),
                        "<gray>Demon Possessed: <white><demon>"), null)
                .set(11, icon(Material.CLOCK, "<green><b>End grace now"), e -> {
                    actions.endGrace(admin);
                    openGame(admin);
                })
                .set(13, icon(Material.WITHER_ROSE, "<dark_red><b>End possession", "<gray>Five-Leaf returns to the admin pool."), e -> {
                    actions.unpossess(admin);
                    openGame(admin);
                })
                .set(15, icon(Material.TNT, "<red><b>Reset game", "<gray>Returns every grimoire, clears levels", "<gray>and lets /start run again."),
                        e -> openResetConfirm(admin))
                .set(22, back(), e -> openMain(admin))
                .open(admin);
    }

    public void openResetConfirm(Player admin) {
        Menu menu = new Menu(3, title("<dark_red>Reset the whole game?"));
        for (int slot : Arrays.asList(10, 11, 12)) {
            menu.set(slot, icon(Material.LIME_CONCRETE, "<green><b>Yes, reset everything"), e -> {
                admin.closeInventory();
                actions.reset(admin);
            });
        }
        for (int slot : Arrays.asList(14, 15, 16)) {
            menu.set(slot, icon(Material.RED_CONCRETE, "<red><b>Cancel"), e -> openGame(admin));
        }
        menu.open(admin);
    }
}
