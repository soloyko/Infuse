package dev.lambro.blade.gui;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static dev.lambro.blade.util.Text.*;

/** /lambro gui — overview of all powers + general settings. */
public final class MainMenu extends Menu {

    private static final int[] ABILITY_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};
    private static final int KILLS = 37, PLAYERS = 38, MOBS = 39, PASSIVE_MOBS = 40, HUD = 41, ANNOUNCE = 42, UNBREAKABLE = 43;
    private static final int GIVE = 48, PREVIEW = 4, RELOAD = 50, RESET = 46, CLOSE = 52;

    public MainMenu(LambroPlugin plugin, Player viewer) {
        super(plugin, viewer, 6, "<" + DEEP + ">" + tc("blade of lambro") + " <" + SHADOW + ">· <" + STEEL + ">" + tc("config"));
    }

    @Override
    protected void draw() {
        set(PREVIEW, plugin.blade().create());

        List<Ability> list = plugin.abilities().all();
        int per = plugin.blade().killsPerUnlock();
        for (int i = 0; i < list.size() && i < ABILITY_SLOTS.length; i++) {
            Ability a = list.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(label("tier", a.tier() + " <" + SHADOW + ">(" + a.tier() * per + " " + tc("kills") + ")"));
            lore.add(label("trigger", tc(a.trigger().label())));
            lore.add(toggle(a.enabled()));
            lore.add("");
            for (String d : a.description()) lore.add("<" + STEEL + ">" + tc(d));
            lore.add("");
            lore.add(hint("left click", "edit settings"));
            lore.add(hint("right click", a.enabled() ? "disable" : "enable"));
            ItemStack it = icon(a.icon(), "<" + (a.enabled() ? CYAN : SHADOW) + ">" + (a.isPassive() ? "◆ " : "✦ ")
                    + "<" + WHITE + ">" + tc(a.name()), lore);
            if (a.enabled()) it.editMeta(m -> m.setEnchantmentGlintOverride(true));
            set(ABILITY_SLOTS[i], it);
        }

        set(KILLS, icon(Material.EXPERIENCE_BOTTLE, header("kills per unlock"), List.of(
                label("current", String.valueOf(per)),
                label("all powers at", (per * list.size()) + " " + tc("kills")),
                "",
                hint("left / right", "+1 / -1"),
                hint("shift", "+5 / -5"))));
        set(PLAYERS, bool(Material.PLAYER_HEAD, "player kills count", "general.count-player-kills"));
        set(MOBS, bool(Material.ZOMBIE_HEAD, "mob kills count", "general.count-mob-kills"));
        set(PASSIVE_MOBS, bool(Material.WHEAT, "abilities hit passive mobs", "general.hit-passive-mobs"));
        set(HUD, bool(Material.NAME_TAG, "action bar hud", "general.action-bar"));
        set(ANNOUNCE, bool(Material.BELL, "unlock announcements", "general.announce-unlocks"));
        set(UNBREAKABLE, bool(Material.ANVIL, "unbreakable", "general.unbreakable"));

        set(RESET, icon(Material.WATER_BUCKET, header("reset held blade"), List.of(
                "<" + STEEL + ">" + tc("sets kills on the blade in"),
                "<" + STEEL + ">" + tc("your main hand back to 0."),
                "",
                hint("shift click", "confirm"))));
        set(GIVE, icon(Material.NETHERITE_SWORD, header("get the blade"), List.of(hint("click", "receive a fresh blade"))));
        set(RELOAD, icon(Material.HEART_OF_THE_SEA, header("reload config"), List.of(hint("click", "reload config.yml"))));
        set(CLOSE, icon(Material.BARRIER, "<" + STEEL + ">" + tc("close"), List.of()));
    }

    private ItemStack bool(Material m, String name, String path) {
        boolean on = plugin.getConfig().getBoolean(path);
        return icon(m, header(name), List.of(toggle(on), "", hint("click", "toggle")));
    }

    private void flip(String path) {
        plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
        saveAndRefresh();
    }

    private void saveAndRefresh() {
        plugin.saveConfig();
        plugin.refreshAllBlades();
        click();
        render();
    }

    @Override
    public void click(int slot, ClickType type) {
        for (int i = 0; i < ABILITY_SLOTS.length; i++) {
            if (ABILITY_SLOTS[i] != slot || i >= plugin.abilities().all().size()) continue;
            Ability a = plugin.abilities().all().get(i);
            if (type.isRightClick()) {
                plugin.getConfig().set(a.path("enabled"), !a.enabled());
                saveAndRefresh();
            } else {
                click();
                new AbilityMenu(plugin, viewer, a).open();
            }
            return;
        }
        switch (slot) {
            case KILLS -> {
                int step = type.isShiftClick() ? 5 : 1;
                int v = plugin.blade().killsPerUnlock() + (type.isRightClick() ? -step : step);
                plugin.getConfig().set("general.kills-per-unlock", Math.max(1, Math.min(100, v)));
                saveAndRefresh();
            }
            case PLAYERS -> flip("general.count-player-kills");
            case MOBS -> flip("general.count-mob-kills");
            case PASSIVE_MOBS -> flip("general.hit-passive-mobs");
            case HUD -> flip("general.action-bar");
            case ANNOUNCE -> flip("general.announce-unlocks");
            case UNBREAKABLE -> flip("general.unbreakable");
            case GIVE, PREVIEW -> {
                viewer.getInventory().addItem(plugin.blade().create());
                viewer.playSound(viewer.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1f);
            }
            case RESET -> {
                ItemStack held = viewer.getInventory().getItemInMainHand();
                if (!type.isShiftClick() || !plugin.blade().isBlade(held)) { deny(); return; }
                plugin.blade().setKills(held, 0);
                click();
            }
            case RELOAD -> {
                plugin.reload();
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 1.6f);
                render();
            }
            case CLOSE -> viewer.closeInventory();
            default -> {}
        }
    }
}
