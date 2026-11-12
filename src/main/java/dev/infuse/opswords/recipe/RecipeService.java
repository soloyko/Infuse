package dev.infuse.opswords.recipe;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.Settings;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registers the configurable shaped recipes. */
public final class RecipeService {

    private final OPSwordsPlugin plugin;
    private final Map<NamespacedKey, SwordType> registered = new HashMap<>();

    public RecipeService(OPSwordsPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        Settings settings = plugin.settings();
        if (!settings.craftingEnabled) {
            return;
        }
        for (SwordType type : SwordType.values()) {
            Settings.Sword sword = settings.sword(type);
            Settings.Recipe recipe = sword.recipe();
            if (!sword.enabled() || !recipe.enabled()) {
                continue;
            }
            ShapedRecipe shaped = build(type, recipe);
            if (shaped != null && Bukkit.addRecipe(shaped, false)) {
                registered.put(shaped.getKey(), type);
            }
        }
        Bukkit.updateRecipes();
    }

    private @Nullable ShapedRecipe build(SwordType type, Settings.Recipe recipe) {
        List<String> shape = recipe.shape();
        if (shape.isEmpty() || shape.size() > 3) {
            warn(type, "shape must have 1 to 3 rows");
            return null;
        }
        int width = shape.get(0).length();
        for (String row : shape) {
            if (row.isEmpty() || row.length() > 3 || row.length() != width) {
                warn(type, "every row must have the same length (1 to 3 characters)");
                return null;
            }
            for (char c : row.toCharArray()) {
                if (c != ' ' && !recipe.ingredients().containsKey(c)) {
                    warn(type, "'" + c + "' is used in the shape but has no ingredient");
                    return null;
                }
            }
        }
        NamespacedKey key = new NamespacedKey(plugin, type.id() + "_sword");
        ShapedRecipe shaped = new ShapedRecipe(key, plugin.items().create(type));
        shaped.shape(shape.toArray(new String[0]));
        String joined = String.join("", shape);
        for (Map.Entry<Character, Material> ing : recipe.ingredients().entrySet()) {
            if (joined.indexOf(ing.getKey()) >= 0) {
                shaped.setIngredient(ing.getKey(), ing.getValue());
            }
        }
        shaped.setGroup("opswords");
        shaped.setCategory(CraftingBookCategory.EQUIPMENT);
        return shaped;
    }

    private void warn(SwordType type, String problem) {
        plugin.getLogger().warning("Recipe for " + type.id() + " skipped: " + problem + ".");
    }

    public void unregisterAll() {
        if (registered.isEmpty()) {
            return;
        }
        for (NamespacedKey key : new ArrayList<>(registered.keySet())) {
            Bukkit.removeRecipe(key, false);
        }
        registered.clear();
        if (!Bukkit.isStopping()) {
            Bukkit.updateRecipes();
        }
    }

    public @Nullable SwordType typeOf(NamespacedKey key) {
        return registered.get(key);
    }

    public Set<NamespacedKey> keys() {
        return Set.copyOf(registered.keySet());
    }
}
