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
    private boolean altarRegistered;

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
            ShapedRecipe shaped = build(type.id(), new NamespacedKey(plugin, type.id() + "_sword"), plugin.items().create(type), recipe);
            if (shaped != null && Bukkit.addRecipe(shaped, false)) {
                registered.put(shaped.getKey(), type);
            }
        }
        if (settings.altarsEnabled && settings.altarRecipe.enabled()) {
            ShapedRecipe altar = build("the weapon altar", altarKey(), plugin.altars().createAltarItem(1), settings.altarRecipe);
            if (altar != null && Bukkit.addRecipe(altar, false)) {
                altarRegistered = true;
            }
        }
        Bukkit.updateRecipes();
    }

    private NamespacedKey altarKey() {
        return new NamespacedKey(plugin, "weapon_altar");
    }

    private @Nullable ShapedRecipe build(String label, NamespacedKey key, org.bukkit.inventory.ItemStack result, Settings.Recipe recipe) {
        List<String> shape = recipe.shape();
        if (shape.isEmpty() || shape.size() > 3) {
            warn(label, "shape must have 1 to 3 rows");
            return null;
        }
        int width = shape.get(0).length();
        for (String row : shape) {
            if (row.isEmpty() || row.length() > 3 || row.length() != width) {
                warn(label, "every row must have the same length (1 to 3 characters)");
                return null;
            }
            for (char c : row.toCharArray()) {
                if (c != ' ' && !recipe.ingredients().containsKey(c)) {
                    warn(label, "'" + c + "' is used in the shape but has no ingredient");
                    return null;
                }
            }
        }
        ShapedRecipe shaped = new ShapedRecipe(key, result);
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

    private void warn(String label, String problem) {
        plugin.getLogger().warning("Recipe for " + label + " skipped: " + problem + ".");
    }

    public void unregisterAll() {
        if (altarRegistered) {
            Bukkit.removeRecipe(altarKey(), false);
            altarRegistered = false;
        }
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
        Set<NamespacedKey> keys = new java.util.HashSet<>(registered.keySet());
        if (altarRegistered) {
            keys.add(altarKey());
        }
        return keys;
    }
}
