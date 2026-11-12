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

/** Recipe book: an overview of all swords, and a crafting grid view for each one. */
public final class RecipeMenu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private RecipeMenu() {
    }

    /** All ten swords; click one to see how to craft it. */
    public static Menu overview(OPSwordsPlugin plugin) {
        Menu menu = new Menu(4, Text.mm("<gradient:#ECBA44:#C4415B><bold>OP Swords</bold></gradient> <dark_gray>Recipes")) {
        };
        SwordType[] types = SwordType.values();
        for (int i = 0; i < types.length && i < SwordMenu.SLOTS.length; i++) {
            SwordType type = types[i];
            ItemStack icon = plugin.items().create(type);
            List<Component> lore = new ArrayList<>(icon.lore() == null ? List.of() : icon.lore());
            Component status = status(plugin, type);
            if (status != Component.empty()) {
                lore.add(status);
            }
            lore.add(Text.plain("Click to view the recipe", NamedTextColor.YELLOW));
            icon.lore(lore);
            menu.set(SwordMenu.SLOTS[i], icon, player -> recipe(plugin, type).open(player));
        }
        return menu;
    }

    /** The crafting grid for one sword. */
    public static Menu recipe(OPSwordsPlugin plugin, SwordType type) {
        Menu menu = new Menu(5, Text.gradient(type.emoji() + " " + type.displayName(), type.color(), type.accent())
                .append(Component.text(" Recipe", NamedTextColor.DARK_GRAY))) {
        };
        Settings settings = plugin.settings();
        Settings.Sword sword = settings.sword(type);
        Settings.Recipe recipe = sword.recipe();
        boolean craftable = settings.craftingEnabled && sword.enabled() && recipe.enabled();

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

        ItemStack arrow = ItemStack.of(craftable ? Material.SPECTRAL_ARROW : Material.BARRIER);
        arrow.editMeta(meta -> {
            meta.itemName(craftable
                    ? Text.plain("Craft in a crafting table", NamedTextColor.GREEN)
                    : Text.plain("Can't be crafted on this server", NamedTextColor.RED));
            Component status = status(plugin, type);
            if (status != Component.empty()) {
                meta.lore(List.of(status));
            }
        });
        menu.set(23, arrow, null);
        menu.set(25, plugin.items().create(type), null);

        ItemStack back = ItemStack.of(Material.ARROW);
        back.editMeta(meta -> meta.itemName(Text.plain("← Back", NamedTextColor.GRAY)));
        menu.set(36, back, player -> overview(plugin).open(player));
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
