package me.infuse.effect;

import net.kyori.adventure.text.format.TextColor;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every effect, in the same order as the rows of the resource pack's icon sheet
 * (assets/infuse/textures/icons/effects.png and hit_tracker.png).
 */
public enum EffectType {
    EMERALD("Emerald", 0x3EE05A, false,
            List.of("Looting 5", "Luck 10", "1.5x EXP", "Enchanting table always on level 30"),
            List.of("Hero of the Village 255", "3x EXP")),
    ENDER("Ender", 0xC43BD6, false,
            List.of("All nearby players have glowing", "Dragon's breath shoots fireballs that curse players"),
            List.of("Teleport to cursor within a 15 block radius", "Instantly kills any mob and curses players")),
    FEATHER("Feather", 0x5FD14A, true,
            List.of("No fall damage", "Every 10 hits it summons a wind charge", "Wind charges shoot faster"),
            List.of("Levitate high in the air and when you come down your attack deals 6 hearts")),
    FIRE("Fire", 0xFF7A1A, true,
            List.of("Fire Resistance (2s every 1s)", "Full charged bow shots set arrows on fire", "Swim in lava",
                    "In lava, no fall damage", "Every 10 hits sets target on fire for 5s"),
            List.of("Set surrounding enemies on fire (5 blocks)")),
    FROST("Frost", 0x7FF2FF, true,
            List.of("Speed 3 on ice and snow", "Freeze player every 10 hits", "Swim in powder snow",
                    "Frozen enemies can't use wind charges"),
            List.of("Reduce enemies jump strength and freeze them every hit")),
    HASTE("Haste", 0xFFB02E, false,
            List.of("Fortune 5 + Efficiency 10 + Unbreaking 10 on pickaxes", "Halved shield cooldown when stunned"),
            List.of("Attack faster")),
    HEART("Heart", 0xFF3D7A, true,
            List.of("+5 Hearts", "All food gives absorption hearts", "Egaps give +10 absorption hearts",
                    "See players health every 10 hits"),
            List.of("Heal players to 20 hearts instantly")),
    INVISIBILITY("Invisibility", 0xB05CFF, false,
            List.of("Permanent Invisibility", "Full bow shot blinds the target for 5s", "Mobs cannot target you"),
            List.of("Creates a 5x5 hollow circle of black dust",
                    "Allies become fully invisible, enemies get blindness")),
    OCEAN("Ocean", 0x4A7DFF, false,
            List.of("Swim faster", "Breathe underwater", "Make everyone around you start drowning",
                    "Tridents pull players"),
            List.of("Creates a Whirlhole")),
    REGENERATION("Regeneration", 0xFF5A5A, false,
            List.of("Every hit grants Regeneration 2 for 1s", "Healing gives 3 extra saturation bars"),
            List.of("Apply lifesteal effect")),
    SPEED("Speed", 0x5FF2FF, false,
            List.of("Speed 1", "Increase speed level by 1 after each hit", "Ranged weapons charge 1.5x faster",
                    "Enemy invincibility frames are halved"),
            List.of("Speed Dash")),
    STRENGTH("Strength", 0xD41E1E, false,
            List.of("Double Damage to all mobs", "Disable shields for 10 seconds", "Ranged weapons pierce shields"),
            List.of("Activate auto-crit for 15 seconds")),
    THUNDER("Thunder", 0xFFE23A, false,
            List.of("Chain lightning", "Trident Strikes Lightning"),
            List.of("Strike enemies with lightning and make a thunderstorm")),
    APOPHIS("Apophis", 0xD11FD1, true,
            List.of("Combine Fire, Emerald and Heart's effects", "Have a custom nametag"),
            List.of("Activate Fire, Emerald and Heart's sparks", "Upon hitting a player blind their screen")),
    THIEF("Thief", 0xE02424, false,
            List.of("Hidden from the tablist"),
            List.of("Vanish from nearby players", "Your icon shows how long your cover lasts"));

    private final String displayName;
    private final int rgb;
    private final boolean usesHitTracker;
    private final List<String> passives;
    private final List<String> sparks;

    EffectType(String displayName, int rgb, boolean usesHitTracker, List<String> passives, List<String> sparks) {
        this.displayName = displayName;
        this.rgb = rgb;
        this.usesHitTracker = usesHitTracker;
        this.passives = passives;
        this.sparks = sparks;
    }

    public String displayName() { return displayName; }
    public int rgb() { return rgb; }
    public TextColor color() { return TextColor.color(rgb); }
    public boolean usesHitTracker() { return usesHitTracker; }
    public List<String> passives() { return passives; }
    public List<String> sparks() { return sparks; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** Row of this effect on the resource pack icon sheets. */
    public int iconIndex() { return ordinal(); }

    /** The effects whose passives/sparks this effect grants (Apophis bundles Fire, Emerald and Heart). */
    public Set<EffectType> grants() {
        if (this == APOPHIS) return EnumSet.of(APOPHIS, FIRE, EMERALD, HEART);
        return EnumSet.of(this);
    }

    public static EffectType fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
