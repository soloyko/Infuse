package me.infuse.craft;

import me.infuse.InfusePlugin;
import me.infuse.effect.EffectType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads effect recipes from recipes.yml and registers them as shaped recipes. */
public final class RecipeManager {
    private final InfusePlugin plugin;
    private final Map<NamespacedKey, EffectType> recipes = new HashMap<>();

    public RecipeManager(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        unregister();
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) plugin.saveResource("recipes.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("recipes");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                register(id, sec);
            } catch (Exception ex) {
                plugin.getLogger().warning("Invalid recipe '" + id + "' in recipes.yml: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + recipes.size() + " effect recipes.");
    }

    private void register(String id, ConfigurationSection sec) {
        EffectType type = EffectType.fromId(sec.getString("effect", id));
        if (type == null) throw new IllegalArgumentException("unknown effect " + sec.getString("effect"));
        if (!type.enabled() || !sec.getBoolean("enabled", true)) return;
        List<String> shape = sec.getStringList("shape");
        if (shape.isEmpty() || shape.size() > 3) throw new IllegalArgumentException("shape must have 1-3 rows");
        NamespacedKey key = new NamespacedKey(plugin, "recipe_" + id.toLowerCase());
        ShapedRecipe recipe = new ShapedRecipe(key, plugin.items().create(type, false));
        recipe.shape(shape.toArray(new String[0]));
        ConfigurationSection ing = sec.getConfigurationSection("ingredients");
        if (ing == null) throw new IllegalArgumentException("missing ingredients");
        for (String symbol : ing.getKeys(false)) {
            if (symbol.length() != 1) throw new IllegalArgumentException("ingredient key must be one character: " + symbol);
            List<Material> mats = new ArrayList<>();
            if (ing.isList(symbol)) {
                for (String s : ing.getStringList(symbol)) mats.add(material(s));
            } else {
                mats.add(material(ing.getString(symbol)));
            }
            recipe.setIngredient(symbol.charAt(0), new RecipeChoice.MaterialChoice(mats));
        }
        Bukkit.addRecipe(recipe, true);
        recipes.put(key, type);
    }

    private static Material material(String name) {
        Material m = name == null ? null : Material.matchMaterial(name);
        if (m == null || !m.isItem()) throw new IllegalArgumentException("unknown item " + name);
        return m;
    }

    public void unregister() {
        for (NamespacedKey key : recipes.keySet()) Bukkit.removeRecipe(key, true);
        recipes.clear();
    }

    public EffectType effectFor(NamespacedKey key) {
        return recipes.get(key);
    }
}
