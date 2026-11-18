package dev.infuse.ultimate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * One entry per plugin in the Infuse Ultimate suite.
 *
 * <p>InfuseCore owns no game logic - it only discovers which of these plugins are present on the
 * server (by their {@code plugin.yml} name) and presents them as one hub. Everything else lives in
 * the module plugins themselves, exactly as it was built and tested.
 */
public enum Module {
    CUSTOMS(
            "Customs",
            "Customs",
            "customs",
            Material.NETHERITE_SWORD,
            "53 legendary weapons",
            List.of(
                    "Fifty-three 3D-modelled weapons across five arcs,",
                    "each forged at a stone altar with its own passive,",
                    "on-hit effect and two activated abilities.")),

    BLOODBOUND(
            "Bloodbound",
            "Bloodbound",
            "bloodbound",
            Material.REDSTONE,
            "Blood-forged PvP weapons",
            List.of(
                    "Altar-crafted weapon kits that grow stronger from",
                    "the blood of your kills during admin-run sessions.",
                    "Five kits, each with a signature ability.")),

    INFUSE(
            "Infuse SMP",
            "Infuse",
            "infuse",
            Material.EXPERIENCE_BOTTLE,
            "Infusable effects & sparks",
            List.of(
                    "Drink effect bottles for passives and activated",
                    "sparks, shown on a HUD above your hotbar.",
                    "Includes the /trust ally system.")),

    LIMITLESS(
            "Limitless Arsenal",
            "LimitlessEnchantments",
            "coolability",
            Material.END_CRYSTAL,
            "24 mythic relics",
            List.of(
                    "Energy-cast relic weapons with their own altars.",
                    "Cast with Sneak + Right-click, switch relics with",
                    "Sneak + F. Twenty-four relics in all."));

    private final String displayName;
    private final String pluginName;
    private final String command;
    private final Material icon;
    private final String tagline;
    private final List<String> description;

    Module(String displayName, String pluginName, String command, Material icon,
           String tagline, List<String> description) {
        this.displayName = displayName;
        this.pluginName = pluginName;
        this.command = command;
        this.icon = icon;
        this.tagline = tagline;
        this.description = description;
    }

    public String displayName() { return displayName; }

    public String pluginName() { return pluginName; }

    /** The command a player runs to open this module (no leading slash). */
    public String command() { return command; }

    public Material icon() { return icon; }

    public String tagline() { return tagline; }

    public List<String> description() { return description; }

    /** The live plugin on this server, or {@code null} if it is not installed. */
    public Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin(pluginName);
    }

    public boolean installed() {
        Plugin plugin = plugin();
        return plugin != null && plugin.isEnabled();
    }

    /** The installed version, or {@code "-"} when the module is absent. */
    public String version() {
        Plugin plugin = plugin();
        return plugin == null ? "-" : plugin.getPluginMeta().getVersion();
    }
}
