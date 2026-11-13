package dev.infuse.opswords.gui;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.Settings;
import dev.infuse.opswords.manager.ForgeRegistry;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
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
    public static Menu overview(OPSwordsPlugin plugin) {
        Menu menu = new Menu(Menu.ROWS, Text.mm("<gradient:#ECBA44:#C4415B><bold>OP Swords</bold></gradient> <dark_gray>· Recipes")) {
        };
        menu.info(plugin, "Click a weapon to see its recipe.");
        for (SwordType type : SwordType.values()) {
            ItemStack icon = plugin.items().create(type);
            List<Component> lore = new ArrayList<>(icon.lore() == null ? List.of() : icon.lore());
            lore.add(Component.empty());
            Component status = status(plugin, type);
            if (status != Component.empty()) {
                lore.add(status);
            }
            lore.add(Text.plain("Click to view the recipe", NamedTextColor.YELLOW));
            icon.lore(lore);
            menu.set(Menu.slotOf(type), icon, player -> recipe(plugin, type).open(player));
        }
        if (plugin.settings().altarsEnabled) {
            ItemStack altar = plugin.altars().createAltarItem(1);
            List<Component> lore = new ArrayList<>(altar.lore() == null ? List.of() : altar.lore());
            lore.add(Component.empty());
            lore.add(Text.plain("Click to view the recipe", NamedTextColor.YELLOW));
            altar.lore(lore);
            menu.set(Menu.ALTAR, altar, player -> altar(plugin).open(player));
        }
        menu.closeButton();
        return menu;
    }

    /** The crafting grid for one weapon. */
    public static Menu recipe(OPSwordsPlugin plugin, SwordType type) {
        Settings settings = plugin.settings();
        Settings.Sword sword = settings.sword(type);
        boolean craftable = settings.craftingEnabled && sword.enabled() && sword.recipe().enabled();
        return grid(plugin, Text.sword(type).append(Component.text(" · Recipe", NamedTextColor.DARK_GRAY)),
                sword.recipe(), craftable, status(plugin, type), plugin.items().create(type));
    }

    /** The crafting grid for the weapon altar. */
    public static Menu altar(OPSwordsPlugin plugin) {
        Settings settings = plugin.settings();
        boolean craftable = settings.craftingEnabled && settings.altarsEnabled && settings.altarRecipe.enabled();
        return grid(plugin, Text.mm("<gold>⛩ Weapon Altar</gold> <dark_gray>· Recipe"), settings.altarRecipe, craftable,
                Text.plain("Place it, then right-click it with a weapon.", NamedTextColor.GRAY),
                plugin.altars().createAltarItem(1));
    }

    private static Menu grid(OPSwordsPlugin plugin, Component title, Settings.Recipe recipe, boolean craftable,
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

    private static Component status(OPSwordsPlugin plugin, SwordType type) {
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
