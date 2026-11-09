package me.infuse.effect;

import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.util.Fx;
import me.infuse.util.Msg;
import me.infuse.util.Style;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * Effects grow stronger with use. Each slot earns Resonance XP (sparks, 10th-hit procs, kills);
 * every level cuts that effect's spark cooldown. XP is stored on the bottle when drained or dropped.
 */
public final class Resonance {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private final InfusePlugin plugin;

    public Resonance(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("resonance.enabled", true);
    }

    /** XP needed to reach each level; index 0 is level I (always 0). */
    private List<Integer> thresholds() {
        List<Integer> t = plugin.getConfig().getIntegerList("resonance.levels");
        return t.isEmpty() ? List.of(0, 150, 450) : t;
    }

    public int level(int xp) {
        List<Integer> t = thresholds();
        int level = 1;
        for (int i = 1; i < t.size(); i++) if (xp >= t.get(i)) level = i + 1;
        return level;
    }

    public int maxLevel() { return thresholds().size(); }

    /** XP needed for the next level, or -1 at max level. */
    public int nextThreshold(int xp) {
        int lvl = level(xp);
        List<Integer> t = thresholds();
        return lvl >= t.size() ? -1 : t.get(lvl);
    }

    public static String roman(int level) {
        return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }

    public double cooldownMultiplier(int xp) {
        if (!enabled()) return 1;
        double per = plugin.getConfig().getDouble("resonance.cooldown-reduction-per-level", 0.1);
        return Math.max(0.1, 1 - per * (level(xp) - 1));
    }

    /** Adds XP to a slot and celebrates level-ups. */
    public void gain(Player p, PlayerData d, int slot, int amount) {
        if (!enabled() || amount <= 0 || d.get(slot) == null) return;
        int before = level(d.xp(slot));
        d.setXp(slot, d.xp(slot) + amount);
        int after = level(d.xp(slot));
        if (after <= before) return;
        Equipped e = d.get(slot);
        var effect = Msg.comp("effect", ItemFactory.name(e.type(), e.augmented()));
        var lvl = Msg.text("level", roman(after));
        Msg.send(p, "resonance-levelup", effect, lvl);
        p.showTitle(Title.title(Msg.get("resonance-title", effect, lvl), Msg.get("resonance-subtitle", effect, lvl),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(500))));
        Fx.firework(p.getLocation().add(0, 2.4, 0), e.type().rgb(), after >= maxLevel());
        Fx.helix(p, e.type().rgb(), 30, 1.0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f + after * 0.15f);
    }

    /** Gain for every equipped slot whose effect matches the filter (null = all). */
    public void gainAll(Player p, PlayerData d, int amount) {
        for (int slot = 0; slot < 2; slot++) gain(p, d, slot, amount);
    }

    public int reward(String key, int def) {
        return plugin.getConfig().getInt("resonance.xp." + key, def);
    }

    public String label(int xp) {
        int lvl = level(xp);
        int next = nextThreshold(xp);
        return Style.caps("Resonance ") + roman(lvl) + (next < 0 ? " (MAX)" : " (" + xp + "/" + next + ")");
    }
}
