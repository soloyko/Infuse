package dev.infuse.customs.gui;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.Settings;
import dev.infuse.customs.manager.ForgeRegistry;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Recipe book: an overview of every weapon, and a crafting grid view for each one. */
public final class RecipeMenu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int ARROW = 23;
    private static final int RESULT = 25;

    private RecipeMenu() {
    }

    /** All weapons and the altar; click one to see how to craft it. */
    public static Menu overview(CustomsPlugin plugin) {
        return overview(plugin, Menu.Page.I);
    }

    public static Menu overview(CustomsPlugin plugin, Menu.Page page) {
        Menu menu = new Menu(Menu.ROWS, Text.mm("<gradient:#ECBA44:#C4415B><bold>Customs</bold></gradient> <dark_gray>· Recipes")) {
        };
        menu.info(plugin, "Click a weapon to see its recipe.");
        menu.tabs(page, p -> overview(plugin, p));
        Menu.fill(page, (slot, type) -> {
            ItemStack icon = plugin.items().create(type);
            List<Component> lore = new ArrayList<>(icon.lore() == null ? List.of() : icon.lore());
            lore.add(Component.empty());
            Component status = status(plugin, type);
            if (status != Component.empty()) {
                lore.add(status);
            }
            lore.add(Text.plain("Click to view the recipe", NamedTextColor.YELLOW));
            icon.lore(lore);
            menu.set(slot, icon, player -> recipe(plugin, type).open(player));
        });
        if (plugin.settings().altarsEnabled) {
            menu.set(Menu.ALTAR, Menu.button(Material.LODESTONE,
                    Text.gradient("⛩ Weapon Altars", net.kyori.adventure.text.format.TextColor.color(0xF5D78E),
                            net.kyori.adventure.text.format.TextColor.color(0xB57EDC)),
                    List.of(Text.caps("forge without a crafting table", NamedTextColor.GOLD),
                            Component.empty(),
                            Text.plain("Find an altar holding the weapon you want", NamedTextColor.GRAY),
                            Text.plain("and right-click it with the ingredients", NamedTextColor.GRAY),
                            Text.plain("in your inventory.", NamedTextColor.GRAY))), null);
        }
        menu.closeButton();
        return menu;
    }

    /** The crafting grid for one weapon. */
    public static Menu recipe(CustomsPlugin plugin, SwordType type) {
        Settings settings = plugin.settings();
        Settings.Sword sword = settings.sword(type);
        boolean craftable = settings.craftingEnabled && sword.enabled() && sword.recipe().enabled()
                && (settings.craftingTable || settings.altarsEnabled);
        Menu menu = grid(plugin, Text.sword(type).append(Component.text(" · Recipe", NamedTextColor.DARK_GRAY)),
                sword.recipe(), craftable, status(plugin, type), plugin.items().create(type));
        if (craftable) {
            boolean table = settings.craftingTable;
            boolean altars = settings.altarsEnabled;
            List<Component> lore = new ArrayList<>();
            if (status(plugin, type) != Component.empty()) {
                lore.add(status(plugin, type));
            }
            if (altars) {
                lore.add(Text.plain(table ? "Or forge it at a weapon altar: right-click it" : "Right-click a weapon altar holding it",
                        NamedTextColor.GRAY));
                lore.add(Text.plain("with these ingredients in your inventory.", NamedTextColor.GRAY));
            }
            menu.set(ARROW, Menu.button(Material.SPECTRAL_ARROW, table
                    ? Text.plain("Craft in a crafting table", NamedTextColor.GREEN)
                    : Text.plain("Forge at a weapon altar", NamedTextColor.GREEN), lore), null);
        }
        menu.set(Menu.BACK, Menu.button(Material.ARROW, Text.plain("← Back", NamedTextColor.GRAY), List.of()),
                player -> overview(plugin, Menu.Page.of(type)).open(player));
        return menu;
    }

    private static Menu grid(CustomsPlugin plugin, Component title, Settings.Recipe recipe, boolean craftable,
                             Component status, ItemStack result) {
        Menu menu = new Menu(Menu.ROWS, title) {
        };
        for (int slot : GRID) {
            menu.set(slot, ItemStack.empty(), null);
        }
        if (craftable) {
            List<String> shape = recipe.shape();
            int rowOffset = (3 - shape.size()) / 2;
            for (int r = 0; r < shape.size(); r++) {
                String row = shape.get(r);
                int colOffset = (3 - row.length()) / 2;
                for (int c = 0; c < row.length(); c++) {
                    Material mat = recipe.ingredients().get(row.charAt(c));
                    if (mat != null) {
                        menu.set(GRID[(r + rowOffset) * 3 + c + colOffset], ItemStack.of(mat), null);
                    }
                }
            }
        }
        List<Component> arrowLore = status == Component.empty() ? List.of() : List.of(status);
        menu.set(ARROW, Menu.button(craftable ? Material.SPECTRAL_ARROW : Material.BARRIER,
                craftable ? Text.plain("Craft in a crafting table", NamedTextColor.GREEN)
                        : Text.plain("Can't be crafted on this server", NamedTextColor.RED), arrowLore), null);
        menu.set(RESULT, result, null);
        menu.set(Menu.BACK, Menu.button(Material.ARROW, Text.plain("← Back", NamedTextColor.GRAY), List.of()),
                player -> overview(plugin).open(player));
        menu.closeButton();
        return menu;
    }

    private static Component status(CustomsPlugin plugin, SwordType type) {
        Settings settings = plugin.settings();
        if (!settings.sword(type).enabled()) {
            return Text.plain("Disabled on this server", NamedTextColor.RED);
        }
        if (settings.uniqueSwords) {
            ForgeRegistry.Forged forged = plugin.forges().get(type);
            if (forged != null) {
                return Text.plain("Already forged by " + forged.name(), NamedTextColor.RED);
            }
            return Text.plain("Legendary: only one can ever be forged", NamedTextColor.GOLD);
        }
        return Component.empty();
    }
}
