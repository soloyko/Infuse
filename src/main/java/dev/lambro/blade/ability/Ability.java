package dev.lambro.blade.ability;

import dev.lambro.blade.LambroPlugin;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the eight powers of the blade. Tier N unlocks at N * kills-per-unlock kills.
 * Passives override the hook methods; actives implement {@link #activate(Player)}.
 */
public abstract class Ability {

    protected final LambroPlugin plugin;
    private final String id;
    private final String name;
    private final int tier;
    private final Trigger trigger;
    private final Material icon;
    private final String summary;
    private final List<String> description;
    private final List<SettingDef> settings = new ArrayList<>();

    protected Ability(LambroPlugin plugin, String id, String name, int tier, Trigger trigger, Material icon,
                      String summary, List<String> description) {
        this.plugin = plugin;
        this.id = id;
        this.name = name;
        this.tier = tier;
        this.trigger = trigger;
        this.icon = icon;
        this.summary = summary;
        this.description = description;
    }

    protected final void setting(SettingDef def) {
        settings.add(def);
    }

    // ---- identity -------------------------------------------------------

    public String id() { return id; }
    public String name() { return name; }
    public int tier() { return tier; }
    public Trigger trigger() { return trigger; }
    public Material icon() { return icon; }
    public String summary() { return summary; }
    public List<String> description() { return description; }
    public List<SettingDef> settings() { return settings; }
    public boolean isPassive() { return trigger == Trigger.PASSIVE; }

    // ---- config ---------------------------------------------------------

    public String path(String key) {
        return "abilities." + id + "." + key;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean(path("enabled"), true);
    }

    public double get(String key) {
        for (SettingDef s : settings) {
            if (s.key().equals(key)) return plugin.getConfig().getDouble(path(key), s.def());
        }
        throw new IllegalArgumentException("Unknown setting " + key + " for " + id);
    }

    public int getInt(String key) {
        return (int) Math.round(get(key));
    }

    /** Cooldown in seconds, or 0 if this ability has none. */
    public double cooldown() {
        for (SettingDef s : settings) if (s.key().equals("cooldown")) return get("cooldown");
        return 0;
    }

    // ---- hooks (only called while the blade is held & ability unlocked) --

    /** Active abilities: return true if it fired (cooldown is then applied). */
    public boolean activate(Player player) { return false; }

    /** Melee hit, before damage is final — may modify the event damage. */
    public void onMeleeHit(Player player, LivingEntity target, EntityDamageByEntityEvent event) {}

    /** Melee hit, after all plugins ran — read-only (final damage). */
    public void onMeleeHitAfter(Player player, LivingEntity target, EntityDamageByEntityEvent event) {}

    /** The holder took damage. */
    public void onDamaged(Player player, EntityDamageEvent event) {}

    /** The holder killed something with the blade. */
    public void onKill(Player player, LivingEntity victim) {}

    /** Called every 2 ticks while held. */
    public void onTick(Player player, long tick) {}

    /** Plugin disable / reload cleanup. */
    public void shutdown() {}
}
