package dev.infuse.opswords.sword;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * The fifteen OP swords. The id is the item model name in the resource pack
 * ({@code op_swords:<id>}) and the key in config.yml; the name is the sword's legendary name.
 */
public enum SwordType {
    DRAGON("dragon", "Wyrmsbane", "Dragon Sword", "🐉", 0xD8344A, 0xF5C451,
            "Forged to slay the last elder dragon, it drank the beast's soul instead. Its rage still burns in the steel.",
            new AbilityInfo("Dragon Scales", "Resistance I and +50% knockback resistance while held."),
            new AbilityInfo("Dragon's Fury", "25% chance to scorch the target for +4 bonus damage."),
            new AbilityInfo("Dragon Breath", "Exhale a cone of dragonfire that shreds everything in front of you."),
            new AbilityInfo("Wyrm Dive", "Soar into the sky, then crash down in a shockwave. No fall damage.")),

    PHOENIX("phoenix", "Cinderwing", "Phoenix Sword", "🔥", 0xF28A1E, 0xFFE27A,
            "A feather of the first phoenix, hammered into a blade. Those who carry it do not stay dead for long.",
            new AbilityInfo("Rebirth", "Fire Resistance while held. Once every 5 minutes, a killing blow revives you instead."),
            new AbilityInfo("Searing Edge", "Ignites targets. 15% chance to combust, burning everything nearby."),
            new AbilityInfo("Ember Volley", "Hurl a spread of three exploding fireballs."),
            new AbilityInfo("Firestorm", "Unleash a ring of fire that scorches and blasts back everything around you.")),

    FROST("frost", "Everwinter", "Frost Sword", "❄", 0x9BE3F2, 0xFFFFFF,
            "Carved from ice that has not melted since the world was young. Its touch slows even the heart.",
            new AbilityInfo("Frostguard", "+4 armor, +2 toughness and immunity to freezing while held."),
            new AbilityInfo("Frostbite", "Slows and freezes the target."),
            new AbilityInfo("Glacial Spike", "Fire an ice spike that impales and encases the first enemy hit."),
            new AbilityInfo("Blizzard", "Summon a 6 second blizzard around you that freezes and batters enemies.")),

    STORM("storm", "Stormcaller", "Storm Sword", "⚡", 0x4DB2FF, 0xF5C842,
            "Struck by a thousand bolts atop the highest peak, until the storm itself agreed to obey.",
            new AbilityInfo("Tailwind", "+25% movement speed and immunity to lightning while held."),
            new AbilityInfo("Static Charge", "12% chance to call lightning on the target for +5 damage."),
            new AbilityInfo("Thunder Strike", "Call a lightning bolt down wherever you're looking."),
            new AbilityInfo("Chain Lightning", "Lightning arcs between up to 6 nearby enemies.")),

    TIDE("tide", "Tidebreaker", "Tide Sword", "🌊", 0x2EC4B6, 0xA8FFF3,
            "Lost with a drowned kingdom and returned by the sea. It still remembers the weight of the deep.",
            new AbilityInfo("Ocean's Blessing", "Conduit Power and Dolphin's Grace while held."),
            new AbilityInfo("Undertow", "20% chance to drag the target towards you and slow it."),
            new AbilityInfo("Riptide Surge", "Surge forward like a riptide, slamming through every enemy in your path."),
            new AbilityInfo("Tidal Wave", "Send a crashing wave forward that sweeps enemies away.")),

    VERDANT("verdant", "Elderbloom", "Verdant Sword", "🌿", 0x2FBF71, 0xE8C64A,
            "Grown, not forged, from the heartwood of the world's oldest tree. Life follows wherever it goes.",
            new AbilityInfo("Verdant Life", "Regeneration I while held. Mobs you slay drop double XP."),
            new AbilityInfo("Thorned Edge", "25% chance to poison the target."),
            new AbilityInfo("Nature's Grasp", "Roots erupt around you, damaging and rooting enemies in place."),
            new AbilityInfo("Verdant Bloom", "Heal, gain Absorption and cleanse all negative effects. Shared with teammates.")),

    PLAGUE("plague", "Pestilence", "Plague Sword", "☣", 0x8DC63F, 0x4A5A3A,
            "Quenched in the blight that emptied a hundred villages. Every scratch it leaves festers.",
            new AbilityInfo("Plaguebearer", "Immune to poison, wither, hunger and nausea while held. The plague you spread even rots the undead."),
            new AbilityInfo("Infection", "Infects the target with plague. Striking an infected target withers it for +2 damage."),
            new AbilityInfo("Miasma", "Hurl a lingering plague cloud that infects and weakens enemies inside."),
            new AbilityInfo("Pandemic", "Every infected enemy nearby erupts. The deeper the infection, the bigger the burst.")),

    CRIMSON("crimson", "Sanguine", "Crimson Sword", "❤", 0xE0245E, 0x7A0F2A,
            "A duelist's needle with a thirst of its own. It returns every drop it draws to its master.",
            new AbilityInfo("Blood Reserve", "+3 max hearts while held."),
            new AbilityInfo("Bloodthirst", "Heal 15% of damage dealt. 25% chance to make the target bleed."),
            new AbilityInfo("Blood Crescent", "Launch a crimson slash that cuts through every enemy in its path."),
            new AbilityInfo("Crimson Harvest", "Rip the life out of all nearby enemies and heal from it.")),

    SOLAR("solar", "Solaris", "Solar Sword", "☀", 0xF2C94C, 0xFFF6C9,
            "Blessed at dawn by a forgotten order of sun-priests. The undead burn at the very sight of it.",
            new AbilityInfo("Radiance", "Haste II while held. Nearby enemies are revealed with Glowing."),
            new AbilityInfo("Sunfire", "Ignites targets. Undead take +50% damage."),
            new AbilityInfo("Solar Beam", "Fire a piercing beam of sunlight that burns everything in a line."),
            new AbilityInfo("Supernova", "Charge up, then explode in a blinding blast of solar fire.")),

    VOID("void", "Voidreaver", "Void Sword", "🌌", 0xA64DFF, 0x3A1A5C,
            "Pulled from the empty space between the stars. Light bends around its edge and never comes back.",
            new AbilityInfo("Void Walker", "Night Vision while held. The void can't claim you while you carry this blade."),
            new AbilityInfo("Void Drain", "20% chance to wither the target and steal 2 health."),
            new AbilityInfo("Void Step", "Blink up to 15 blocks forward, tearing at anything where you land."),
            new AbilityInfo("Singularity", "Open a black hole that drags enemies in, then implodes.")),

    COSMIC("cosmic", "Starfall", "Cosmic Sword", "✨", 0xC54DFF, 0x4DE8FF,
            "Forged in the heart of a falling star. Gravity itself kneels to whoever holds it.",
            new AbilityInfo("Zero-G", "Lower gravity, higher jumps and no fall damage while held."),
            new AbilityInfo("Starstrike", "20% chance to launch the target into orbit with Levitation."),
            new AbilityInfo("Gravity Pulse", "Hurl nearby enemies into the sky, then slam them back down."),
            new AbilityInfo("Meteor Shower", "Rain meteors down on the area you're looking at.")),

    CHRONO("chrono", "Aeon", "Chrono Sword", "⌛", 0x7FE0D0, 0xE8C35A,
            "Its maker spent a thousand years forging it, then went back in time and did it in an afternoon.",
            new AbilityInfo("Quickened", "+25% attack speed while held."),
            new AbilityInfo("Time Lock", "15% chance to freeze the target in time for a moment."),
            new AbilityInfo("Stasis Field", "Freeze time in an area: enemies crawl and projectiles stop in mid-air."),
            new AbilityInfo("Rewind", "Return to where you were 4 seconds ago, with the health you had then.")),

    SHADOW("shadow", "Umbra", "Shadow Sword", "🌑", 0x7B6BE0, 0x1B1B3A,
            "The blade of a watcher who was never seen. It is darkest exactly where you aren't looking.",
            new AbilityInfo("Umbral Cloak", "Invisible while sneaking, plus faster movement while held."),
            new AbilityInfo("Backstab", "+60% damage when striking from behind. 20% chance to blind."),
            new AbilityInfo("Shadow Strike", "Teleport behind the enemy you're looking at and strike."),
            new AbilityInfo("Shadow Veil", "Vanish completely (armor too) for 6s. Your next hit deals +8 damage.")),

    SOUL("soul", "Gravecaller", "Soul Sword", "💀", 0x6FD8F0, 0x1F3A4A,
            "A lich's blade, still humming with the souls it collected. Some of them are eager to help.",
            new AbilityInfo("Soul Siphon", "Every kill heals 4 health and grants Absorption."),
            new AbilityInfo("Soul Rend", "20% chance to tear at the target's soul for +4 damage."),
            new AbilityInfo("Spectral Hounds", "Summon three spectral hounds that hunt your target for 15 seconds."),
            new AbilityInfo("Wail of the Damned", "A wail that withers, weakens and scatters every enemy around you.")),

    ECHO("echo", "Deepwhisper", "Echo Sword", "🔊", 0x22C2C7, 0x0B2A33,
            "Grown in the sculk beneath the Deep Dark. It hears your heartbeat before you even swing.",
            new AbilityInfo("Child of the Deep", "Immune to Darkness. Sculk sensors and shriekers ignore you while held."),
            new AbilityInfo("Resonance", "20% chance to engulf the target in darkness."),
            new AbilityInfo("Sonic Boom", "Charge, then release a warden's sonic boom that pierces every enemy in a line."),
            new AbilityInfo("Shriek", "A sculk shriek darkens and reveals every enemy around you, and quickens you."));

    public static final String MODEL_NAMESPACE = "op_swords";

    /** Ids from older versions, so swords players already own keep working. */
    private static final Map<String, SwordType> LEGACY_IDS = Map.of("inferno", PHOENIX, "emerald", VERDANT);

    private final String id;
    private final String swordName;
    private final String title;
    private final String emoji;
    private final TextColor color;
    private final TextColor accent;
    private final String bio;
    private final AbilityInfo passive;
    private final AbilityInfo onHit;
    private final AbilityInfo primary;
    private final AbilityInfo secondary;

    SwordType(String id, String swordName, String title, String emoji, int color, int accent, String bio,
              AbilityInfo passive, AbilityInfo onHit, AbilityInfo primary, AbilityInfo secondary) {
        this.id = id;
        this.swordName = swordName;
        this.title = title;
        this.emoji = emoji;
        this.color = TextColor.color(color);
        this.accent = TextColor.color(accent);
        this.bio = bio;
        this.passive = passive;
        this.onHit = onHit;
        this.primary = primary;
        this.secondary = secondary;
    }

    public String id() {
        return id;
    }

    /** The sword's legendary name, e.g. "Wyrmsbane". */
    public String swordName() {
        return swordName;
    }

    /** What kind of sword it is, e.g. "Dragon Sword". */
    public String title() {
        return title;
    }

    /** Emoji + legendary name, e.g. "🐉 Wyrmsbane". */
    public String displayName() {
        return emoji + " " + swordName;
    }

    public String emoji() {
        return emoji;
    }

    /** Main theme colour (matches the model). */
    public TextColor color() {
        return color;
    }

    /** Secondary colour used for gradients. */
    public TextColor accent() {
        return accent;
    }

    /** One or two sentences of lore. */
    public String bio() {
        return bio;
    }

    public AbilityInfo passive() {
        return passive;
    }

    public AbilityInfo onHit() {
        return onHit;
    }

    public AbilityInfo ability(AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY ? primary : secondary;
    }

    /** Used in death messages: "Steve was {verb} by Alex". */
    public String deathVerb() {
        return switch (this) {
            case DRAGON -> "torn apart";
            case PHOENIX -> "reduced to ashes";
            case FROST -> "frozen solid";
            case STORM -> "struck down";
            case TIDE -> "dragged into the depths";
            case VERDANT -> "reclaimed by nature";
            case PLAGUE -> "consumed by the plague";
            case CRIMSON -> "drained dry";
            case SOLAR -> "burned to a crisp";
            case VOID -> "erased from existence";
            case COSMIC -> "sent into orbit";
            case CHRONO -> "erased from the timeline";
            case SHADOW -> "assassinated";
            case SOUL -> "claimed by the grave";
            case ECHO -> "silenced";
        };
    }

    public NamespacedKey itemModel() {
        return new NamespacedKey(MODEL_NAMESPACE, id);
    }

    /** Finds a sword by id ("dragon"), legendary name ("wyrmsbane") or an older id ("inferno"). */
    public static @Nullable SwordType byId(@Nullable String input) {
        if (input == null) {
            return null;
        }
        String needle = input.toLowerCase(Locale.ROOT).replace("_sword", "").replace("sword", "")
                .replaceAll("[^a-z0-9]", "");
        for (SwordType type : values()) {
            if (type.id.equals(needle) || type.swordName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").equals(needle)) {
                return type;
            }
        }
        return LEGACY_IDS.get(needle);
    }
}
