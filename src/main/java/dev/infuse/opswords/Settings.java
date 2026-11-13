package dev.infuse.opswords;

import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable snapshot of config.yml. Rebuilt on every reload. */
public final class Settings {

    public enum PackMode { SELF_HOST, URL, NONE }

    /** Which input fires abilities. */
    public enum Trigger { OFFHAND, RIGHT_CLICK, BOTH }

    public record Recipe(boolean enabled, List<String> shape, Map<Character, Material> ingredients) {
    }

    public record Sword(boolean enabled, double attackDamage, double attackSpeed,
                        int primaryCooldown, int secondaryCooldown, Recipe recipe) {
        public int cooldown(AbilitySlot slot) {
            return slot == AbilitySlot.PRIMARY ? primaryCooldown : secondaryCooldown;
        }
    }

    public final boolean unbreakable;
    public final boolean abilitiesHitPlayers;
    public final double abilityDamageMultiplier;
    public final double playerDamageMultiplier;
    public final boolean actionBarHud;
    public final boolean readySound;
    public final boolean heldAura;
    public final boolean deathMessages;
    public final boolean protectDroppedSwords;
    public final Set<String> disabledWorlds;
    public final Trigger trigger;

    public final PackMode packMode;
    public final boolean packRequired;
    public final String packPrompt;
    public final int packPort;
    public final String packBindAddress;
    public final String packPublicAddress;
    public final String packUrl;
    public final String packSha1;

    public final boolean craftingEnabled;
    public final boolean craftingAnnounce;
    public final boolean uniqueSwords;
    public final boolean altarsEnabled;
    public final Recipe altarRecipe;

    private final Map<SwordType, Sword> swords;
    private final Map<SwordType, Integer> revisions;

    private Settings(FileConfiguration c, Logger log) {
        unbreakable = c.getBoolean("settings.unbreakable", true);
        abilitiesHitPlayers = c.getBoolean("settings.abilities-hit-players", true);
        abilityDamageMultiplier = Math.max(0, c.getDouble("settings.ability-damage-multiplier", 1.0));
        playerDamageMultiplier = Math.max(0, c.getDouble("settings.ability-damage-multiplier-vs-players", 1.0));
        actionBarHud = c.getBoolean("settings.action-bar-hud", true);
        readySound = c.getBoolean("settings.ability-ready-sound", true);
        heldAura = c.getBoolean("settings.held-aura", true);
        deathMessages = c.getBoolean("settings.death-messages", true);
        protectDroppedSwords = c.getBoolean("settings.protect-dropped-swords", true);
        String triggerName = c.getString("controls.trigger", "offhand").toLowerCase(Locale.ROOT).replace('_', '-');
        trigger = switch (triggerName) {
            case "right-click", "rightclick", "click" -> Trigger.RIGHT_CLICK;
            case "both" -> Trigger.BOTH;
            default -> Trigger.OFFHAND;
        };
        Set<String> worlds = new HashSet<>();
        for (String w : c.getStringList("settings.disabled-worlds")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }
        disabledWorlds = Collections.unmodifiableSet(worlds);

        String mode = c.getString("resource-pack.mode", "self-host").toLowerCase(Locale.ROOT).replace('_', '-');
        packMode = switch (mode) {
            case "url" -> PackMode.URL;
            case "none", "off", "false" -> PackMode.NONE;
            default -> PackMode.SELF_HOST;
        };
        packRequired = c.getBoolean("resource-pack.required", false);
        packPrompt = c.getString("resource-pack.prompt", "");
        packPort = c.getInt("resource-pack.self-host.port", 8163);
        packBindAddress = c.getString("resource-pack.self-host.bind-address", "0.0.0.0");
        packPublicAddress = c.getString("resource-pack.self-host.public-address", "").trim();
        packUrl = c.getString("resource-pack.url.link", "").trim();
        packSha1 = c.getString("resource-pack.url.sha1", "").trim().toLowerCase(Locale.ROOT);

        craftingEnabled = c.getBoolean("crafting.enabled", true);
        craftingAnnounce = c.getBoolean("crafting.announce", true);
        uniqueSwords = c.getBoolean("crafting.unique-swords", false);
        altarsEnabled = c.getBoolean("altars.enabled", true);
        altarRecipe = readRecipe(c.getConfigurationSection("altars.recipe"), "the weapon altar", log);

        swords = new EnumMap<>(SwordType.class);
        revisions = new EnumMap<>(SwordType.class);
        for (SwordType type : SwordType.values()) {
            String path = "swords." + type.id();
            ConfigurationSection s = c.getConfigurationSection(path);
            if (s == null) {
                log.warning("Missing config section '" + path + "', using defaults.");
                s = c.createSection(path);
            }
            Sword sword = new Sword(
                    s.getBoolean("enabled", true),
                    s.getDouble("attack-damage", 12),
                    s.getDouble("attack-speed", 1.6),
                    Math.max(0, s.getInt("cooldowns.primary", 10)),
                    Math.max(0, s.getInt("cooldowns.secondary", 30)),
                    readRecipe(s.getConfigurationSection("recipe"), type.id(), log));
            swords.put(type, sword);
            revisions.put(type, Objects.hash(sword.attackDamage, sword.attackSpeed, sword.primaryCooldown,
                    sword.secondaryCooldown, unbreakable, trigger == Trigger.RIGHT_CLICK, ITEM_FORMAT));
        }
    }

    /** Bump when the item layout (lore, attributes) changes so old items refresh. */
    private static final int ITEM_FORMAT = 5;

    private static Recipe readRecipe(ConfigurationSection r, String label, Logger log) {
        if (r == null) {
            return new Recipe(false, List.of(), Map.of());
        }
        List<String> shape = new ArrayList<>(r.getStringList("shape"));
        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection ing = r.getConfigurationSection("ingredients");
        if (ing != null) {
            for (String key : ing.getKeys(false)) {
                if (key.length() != 1) {
                    log.warning("Recipe for " + label + ": ingredient key '" + key + "' must be one character.");
                    continue;
                }
                String name = ing.getString(key, "");
                Material mat = Material.matchMaterial(name);
                if (mat == null || !mat.isItem() || mat.isAir()) {
                    log.warning("Recipe for " + label + ": unknown item '" + name + "'.");
                    continue;
                }
                ingredients.put(key.charAt(0), mat);
            }
        }
        return new Recipe(r.getBoolean("enabled", true), List.copyOf(shape), Collections.unmodifiableMap(ingredients));
    }

    public static Settings load(FileConfiguration config, Logger log) {
        return new Settings(config, log);
    }

    public Sword sword(SwordType type) {
        return swords.get(type);
    }

    /** Changes whenever anything shown on the item changes. */
    public int revision(SwordType type) {
        return revisions.get(type);
    }
}
