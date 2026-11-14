package dev.infuse.customs.gui;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.Settings;
import dev.infuse.customs.manager.ForgeRegistry;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-game settings for admins: every switch in config.yml that matters during play, plus a page
 * per weapon for its stats and cooldowns. Changes are written to config.yml and applied at once.
 */
public final class SettingsMenu extends Menu {

    private static final String ADMIN = "customs.admin";
    private static final int WEAPONS_BUTTON = 33;
    private static final int RELOAD_BUTTON = 34;

    private final CustomsPlugin plugin;

    public SettingsMenu(CustomsPlugin plugin) {
        super(ROWS, Text.mm("<gradient:#ECBA44:#C4415B><bold>Customs</bold></gradient> <dark_gray>· Settings"));
        this.plugin = plugin;
        render();
    }

    // ---------------------------------------------------------------- shared helpers

    static Component title(String name) {
        return Text.plain(name, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    static ItemStack toggle(Material material, String name, String description, boolean on) {
        List<Component> lore = new ArrayList<>();
        for (String line : Text.wrap(description, 34)) {
            lore.add(Text.plain(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(on ? Text.caps("✔ on", NamedTextColor.GREEN) : Text.caps("✘ off", NamedTextColor.RED));
        lore.add(Text.plain("Click to turn " + (on ? "off" : "on"), NamedTextColor.YELLOW));
        ItemStack item = button(material, title(name), lore);
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(on));
        return item;
    }

    static ItemStack number(Material material, String name, String description, String value, String step) {
        List<Component> lore = new ArrayList<>();
        for (String line : Text.wrap(description, 34)) {
            lore.add(Text.plain(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Text.caps("value ", NamedTextColor.DARK_GRAY).append(Text.plain(value, NamedTextColor.WHITE)));
        lore.add(Text.plain("Left-click +" + step + "   Right-click −" + step, NamedTextColor.YELLOW));
        lore.add(Text.plain("Shift-click for bigger steps", NamedTextColor.DARK_GRAY));
        return button(material, title(name), lore);
    }

    static String trim(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /** +step on left click, −step on right click, ×5 with shift. */
    static double delta(ClickType click, double step) {
        double d = click.isRightClick() ? -step : step;
        return click.isShiftClick() ? d * 5 : d;
    }

    static boolean allowed(Player player) {
        if (player.hasPermission(ADMIN)) {
            return true;
        }
        player.closeInventory();
        return false;
    }

    // ---------------------------------------------------------------- the main page

    private void render() {
        FileConfiguration c = plugin.getConfig();
        set(INFO, button(Material.COMPARATOR, Text.gradient("⚙ Settings", TextColor.color(0xECBA44), TextColor.color(0xC4415B))
                        .decoration(TextDecoration.BOLD, true),
                List.of(Text.caps("saved to config.yml", NamedTextColor.GRAY),
                        Component.empty(),
                        Text.plain("Changes apply as soon as you click.", NamedTextColor.WHITE),
                        Text.plain("Row 1: combat · Row 2: effects", NamedTextColor.DARK_GRAY),
                        Text.plain("Row 3: crafting and altars", NamedTextColor.DARK_GRAY))), null);

        // combat
        flag(10, Material.IRON_SWORD, "Abilities Hit Players", "Abilities can damage other players, not just mobs.",
                "settings.abilities-hit-players");
        multiplier(11, Material.BLAZE_POWDER, "Ability Damage", "Multiplies the damage of every ability.",
                "settings.ability-damage-multiplier");
        multiplier(12, Material.SHIELD, "Damage vs Players", "Extra multiplier for ability damage dealt to players, on top of Ability Damage.",
                "settings.ability-damage-multiplier-vs-players");
        trigger(13);
        flag(14, Material.ANVIL, "Unbreakable", "Weapons never lose durability.", "settings.unbreakable");
        flag(15, Material.ENDER_CHEST, "Protect Dropped Weapons", "Dropped weapons never despawn, can't be destroyed and glow.",
                "settings.protect-dropped-swords");
        flag(16, Material.SKELETON_SKULL, "Death Messages", "Custom death messages for kills with a weapon.",
                "settings.death-messages");

        // effects
        flag(19, Material.CLOCK, "Cooldown HUD", "Shows both abilities and their cooldowns on the action bar.",
                "settings.action-bar-hud");
        flag(20, Material.BELL, "Ready Chime", "A chime when an ability comes off cooldown.", "settings.ability-ready-sound");
        flag(21, Material.GLOWSTONE_DUST, "Held Aura", "Faint themed particles around a held weapon (others see them).",
                "settings.held-aura");
        flag(22, Material.FEATHER, "Hit Effects", "A glowing slash, thrust or impact on every melee hit.", "settings.melee-slashes");
        flag(23, Material.BLAZE_ROD, "Cast Effects", "Using an ability shows the weapon's element around you.", "settings.cast-circles");
        flag(24, Material.FIREWORK_STAR, "Kill Effects", "Kills end in a burst of light that fits the weapon.", "settings.kill-effects");
        flag(25, Material.NAME_TAG, "Damage Numbers", "Hits show how much damage they dealt above the target.",
                "settings.damage-numbers");

        // crafting and altars
        flag(28, Material.SMITHING_TABLE, "Crafting", "Weapons can be crafted at all (crafting table or altar).", "crafting.enabled");
        flag(29, Material.CRAFTING_TABLE, "Crafting Table Recipes", "Weapons can be crafted in a crafting table. Off: altars only.",
                "crafting.crafting-table");
        flag(30, Material.GOAT_HORN, "Forge Announcements", "Tell the server when someone forges a weapon.", "crafting.announce");
        flag(31, Material.NETHER_STAR, "Legendary Mode", "Each weapon can only be forged once per server.", "crafting.unique-swords");
        boolean altars = c.getBoolean("altars.enabled", true);
        ItemStack altarToggle = toggle(Material.LODESTONE, "Stone Altars", "Admins can place altars and players can forge at them.", altars);
        altarToggle.editMeta(meta -> meta.setItemModel(new NamespacedKey("customs", "altar")));
        setClick(32, altarToggle, (p, click) -> change(p, "altars.enabled", !c.getBoolean("altars.enabled", true)));

        ItemStack weapons = button(Material.NETHERITE_SWORD, title("Weapons"),
                List.of(Text.plain("Turn weapons on or off and change", NamedTextColor.GRAY),
                        Text.plain("their damage, speed and cooldowns.", NamedTextColor.GRAY),
                        Component.empty(),
                        Text.plain("Click to open", NamedTextColor.YELLOW)));
        weapons.editMeta(meta -> meta.setItemModel(SwordType.DRAGON.itemModel()));
        set(WEAPONS_BUTTON, weapons, p -> new WeaponSettingsMenu(plugin, Page.I).open(p));
        set(RELOAD_BUTTON, button(Material.REDSTONE, title("Reload config.yml"),
                List.of(Text.plain("Re-read config.yml from disk after", NamedTextColor.GRAY),
                        Text.plain("editing it by hand.", NamedTextColor.GRAY),
                        Component.empty(),
                        Text.plain("Click to reload", NamedTextColor.YELLOW))), p -> {
            if (allowed(p)) {
                plugin.reload();
                render();
                Text.send(p, "<green>Reloaded config.yml.");
            }
        });
        arcButton(39);
        sessionButton(41);
        set(BACK, button(Material.ARROW, Text.plain("← Armory", NamedTextColor.GRAY), List.of()), p -> new SwordMenu(plugin).open(p));
        closeButton();
    }

    /** The server's arc: shows where it is; click to move on, shift + right-click to end the arcs. */
    private void arcButton(int slot) {
        dev.infuse.customs.sword.Arc current = plugin.arcs().current();
        dev.infuse.customs.sword.Arc next = current == null ? dev.infuse.customs.sword.Arc.I : current.next();
        List<Component> lore = new java.util.ArrayList<>();
        if (current == null) {
            lore.add(Text.plain("No arc is running: everything", NamedTextColor.GRAY));
            lore.add(Text.plain("can be forged, no twists.", NamedTextColor.GRAY));
        } else {
            lore.add(Text.plain("Now: " + current.label() + " · " + current.title(), current.color()));
            lore.add(Text.plain(current.hasTwist() ? "Twist: " + current.twist() + " (in sessions)" : "No twist", NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        if (next != null) {
            lore.add(Text.plain(current == null ? "Click to start Arc I" : "Click to begin " + next.label() + " · " + next.title(),
                    NamedTextColor.YELLOW));
        } else {
            lore.add(Text.plain("This is the last arc.", NamedTextColor.GRAY));
        }
        if (current != null) {
            lore.add(Text.plain("Shift + right-click to end the arcs", NamedTextColor.RED));
        }
        ItemStack item = button(Material.END_CRYSTAL, title("Server Arc"), lore);
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(current != null));
        setClick(slot, item, (p, click) -> {
            if (!allowed(p)) {
                return;
            }
            if (click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                if (plugin.arcs().current() != null) {
                    plugin.arcs().end();
                }
            } else if (next != null) {
                plugin.arcs().begin(next);
            }
            render();
        });
    }

    /** The session: click to start one (until ended), shift + right-click to end it. */
    private void sessionButton(int slot) {
        var arcs = plugin.arcs();
        boolean running = arcs.sessionActive();
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(arcs.sessionLine());
        dev.infuse.customs.sword.Arc current = arcs.current();
        if (current != null && current.hasTwist()) {
            lore.add(Text.plain(current.twist() + " runs during sessions.", NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        if (running) {
            lore.add(Text.plain("Shift + right-click to end the session", NamedTextColor.RED));
        } else {
            lore.add(Text.plain("Click to start a session", NamedTextColor.YELLOW));
            lore.add(Text.plain("(/customs session start <minutes> for a timed one)", NamedTextColor.DARK_GRAY));
        }
        ItemStack item = button(running ? Material.LIME_CANDLE : Material.CANDLE, title("Session"), lore);
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(running));
        setClick(slot, item, (p, click) -> {
            if (!allowed(p)) {
                return;
            }
            if (running && click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                arcs.endSession();
            } else if (!running) {
                arcs.startSession(plugin.getConfig().getInt("arcs.session-minutes", 0));
            }
            render();
        });
    }

    private void flag(int slot, Material icon, String name, String description, String path) {
        boolean on = plugin.getConfig().getBoolean(path, true);
        setClick(slot, toggle(icon, name, description, on), (p, click) -> change(p, path, !on));
    }

    private void multiplier(int slot, Material icon, String name, String description, String path) {
        double value = plugin.getConfig().getDouble(path, 1.0);
        setClick(slot, number(icon, name, description, "×" + trim(value), "0.1"), (p, click) -> {
            double next = Math.max(0, Math.min(5, Math.round((value + delta(click, 0.1)) * 100) / 100.0));
            change(p, path, next);
        });
    }

    private void trigger(int slot) {
        String current = plugin.getConfig().getString("controls.trigger", "offhand").toLowerCase(Locale.ROOT);
        String[] modes = {"offhand", "right-click", "both"};
        String[] labels = {"Swap-hand key (F)", "Right-click", "Both"};
        List<Component> lore = new ArrayList<>();
        lore.add(Text.plain("What fires a weapon's abilities.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        int index = 0;
        for (int i = 0; i < modes.length; i++) {
            boolean active = modes[i].equals(current);
            if (active) {
                index = i;
            }
            lore.add(Text.plain((active ? "▶ " : "   ") + labels[i], active ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(Text.plain("Click to switch", NamedTextColor.YELLOW));
        String next = modes[(index + 1) % modes.length];
        setClick(slot, button(Material.LEVER, title("Ability Controls"), lore), (p, click) -> change(p, "controls.trigger", next));
    }

    private void change(Player player, String path, Object value) {
        if (!allowed(player)) {
            return;
        }
        plugin.getConfig().set(path, value);
        plugin.applySettings();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
        render();
    }

    // ---------------------------------------------------------------- weapons

    /** Every weapon: left-click turns it on or off, right-click edits its stats. */
    static final class WeaponSettingsMenu extends Menu {

        private final CustomsPlugin plugin;
        private final Page page;

        WeaponSettingsMenu(CustomsPlugin plugin, Page page) {
            super(ROWS, Text.mm("<gradient:#ECBA44:#C4415B><bold>Customs</bold></gradient> <dark_gray>· Weapons"));
            this.plugin = plugin;
            this.page = page;
            render();
        }

        private void render() {
            set(INFO, button(Material.WRITABLE_BOOK, title("Weapon Settings"),
                    List.of(Text.plain("Left-click: turn a weapon on or off", NamedTextColor.YELLOW),
                            Text.plain("Right-click: damage, speed, cooldowns", NamedTextColor.YELLOW))), null);
            tabs(page, p -> new WeaponSettingsMenu(plugin, p));
            fill(page, (slot, type) -> {
                Settings.Sword s = plugin.settings().sword(type);
                ItemStack icon = plugin.items().create(type);
                List<Component> lore = new ArrayList<>();
                lore.add(s.enabled() ? Text.caps("✔ enabled", NamedTextColor.GREEN) : Text.caps("✘ disabled", NamedTextColor.RED));
                lore.add(Text.plain("⚔ " + trim(s.attackDamage()) + "  ⏱ " + trim(s.attackSpeed()), NamedTextColor.GRAY));
                lore.add(Text.plain(type.ability(dev.infuse.customs.sword.AbilitySlot.PRIMARY).name() + ": " + s.cooldown(dev.infuse.customs.sword.AbilitySlot.PRIMARY) + "s", NamedTextColor.GRAY));
                lore.add(Text.plain(type.ability(dev.infuse.customs.sword.AbilitySlot.SECONDARY).name() + ": " + s.cooldown(dev.infuse.customs.sword.AbilitySlot.SECONDARY) + "s",
                        NamedTextColor.GRAY));
                lore.add(Component.empty());
                lore.add(Text.plain("Left-click to turn " + (s.enabled() ? "off" : "on"), NamedTextColor.YELLOW));
                lore.add(Text.plain("Right-click to edit", NamedTextColor.YELLOW));
                icon.editMeta(meta -> {
                    meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
                    meta.setEnchantmentGlintOverride(false);
                    if (!s.enabled()) {
                        meta.itemName(Text.plain("✘ ", NamedTextColor.RED).append(Text.plain(type.swordName(), NamedTextColor.DARK_GRAY)));
                    }
                });
                setClick(slot, icon, (p, click) -> {
                    if (!allowed(p)) {
                        return;
                    }
                    if (click.isRightClick()) {
                        new WeaponEditMenu(plugin, type).open(p);
                        return;
                    }
                    plugin.getConfig().set("swords." + type.id() + ".enabled", !s.enabled());
                    plugin.applySettings();
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, s.enabled() ? 0.8f : 1.6f);
                    render();
                });
            });
            set(BACK, button(Material.ARROW, Text.plain("← Settings", NamedTextColor.GRAY), List.of()), p -> new SettingsMenu(plugin).open(p));
            closeButton();
        }
    }

    /** One weapon's stats, cooldowns and recipe. */
    static final class WeaponEditMenu extends Menu {

        private final CustomsPlugin plugin;
        private final SwordType type;

        WeaponEditMenu(CustomsPlugin plugin, SwordType type) {
            super(ROWS, Text.sword(type).append(Component.text(" · Settings", NamedTextColor.DARK_GRAY)));
            this.plugin = plugin;
            this.type = type;
            render();
        }

        private String path(String key) {
            return "swords." + type.id() + "." + key;
        }

        private void render() {
            FileConfiguration c = plugin.getConfig();
            Settings.Sword s = plugin.settings().sword(type);
            set(13, plugin.items().create(type), null);
            setClick(28, toggle(s.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, "Enabled",
                    "Off: the weapon does nothing special and can't be crafted.", s.enabled()), (p, click) -> change(p, path("enabled"), !s.enabled()));
            setClick(29, number(Material.IRON_SWORD, "Attack Damage", "Melee damage per hit (a netherite sword does 8).",
                    trim(s.attackDamage()), "0.5"), (p, click) -> change(p, path("attack-damage"),
                    Math.max(1, Math.min(50, s.attackDamage() + delta(click, 0.5)))));
            setClick(30, number(Material.FEATHER, "Attack Speed", "Attacks per second (a netherite sword is 1.6).",
                    trim(s.attackSpeed()), "0.1"), (p, click) -> change(p, path("attack-speed"),
                    Math.max(0.1, Math.min(4, Math.round((s.attackSpeed() + delta(click, 0.1)) * 100) / 100.0))));
            int primary = s.cooldown(dev.infuse.customs.sword.AbilitySlot.PRIMARY);
            int secondary = s.cooldown(dev.infuse.customs.sword.AbilitySlot.SECONDARY);
            setClick(31, number(Material.CLOCK, type.ability(dev.infuse.customs.sword.AbilitySlot.PRIMARY).name(), "Cooldown of the first ability, in seconds.",
                    primary + "s", "1s"), (p, click) -> change(p, path("cooldowns.primary"),
                    Math.max(0, Math.min(600, primary + (int) delta(click, 1)))));
            setClick(32, number(Material.RECOVERY_COMPASS, type.ability(dev.infuse.customs.sword.AbilitySlot.SECONDARY).name(), "Cooldown of the second ability, in seconds.",
                    secondary + "s", "1s"), (p, click) -> change(p, path("cooldowns.secondary"),
                    Math.max(0, Math.min(600, secondary + (int) delta(click, 1)))));
            boolean recipe = c.getBoolean(path("recipe.enabled"), true);
            setClick(33, toggle(Material.CRAFTING_TABLE, "Recipe", "Whether this weapon can be crafted or forged.", recipe),
                    (p, click) -> change(p, path("recipe.enabled"), !recipe));
            ForgeRegistry.Forged forged = plugin.forges().get(type);
            if (plugin.settings().uniqueSwords && forged != null) {
                set(34, button(Material.NETHER_STAR, title("Allow Forging Again"),
                        List.of(Text.plain("Forged by " + forged.name() + ".", NamedTextColor.GRAY),
                                Text.plain("Legendary mode: let it be forged once more.", NamedTextColor.GRAY),
                                Component.empty(),
                                Text.plain("Click to reset", NamedTextColor.YELLOW))), p -> {
                    if (allowed(p) && plugin.forges().clear(type)) {
                        plugin.altars().refreshAll();
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
                        render();
                    }
                });
            } else {
                set(34, button(Material.GRAY_STAINED_GLASS_PANE, Text.plain("Not forged yet", NamedTextColor.DARK_GRAY), List.of()), null);
            }
            set(BACK, button(Material.ARROW, Text.plain("← Weapons", NamedTextColor.GRAY), List.of()),
                    p -> new WeaponSettingsMenu(plugin, Page.of(type)).open(p));
            closeButton();
        }

        private void change(Player player, String key, Object value) {
            if (!allowed(player)) {
                return;
            }
            plugin.getConfig().set(key, value);
            plugin.applySettings();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
            render();
        }
    }
}
