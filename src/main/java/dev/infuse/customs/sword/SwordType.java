package dev.infuse.customs.sword;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * The weapons: fifteen swords and thirteen other weapons. The id is the item model name in the resource pack
 * ({@code customs:<id>}) and the key in config.yml; the name is the sword's legendary name.
 */
public enum SwordType {
    DRAGON("dragon", "Wyrmsbane", "Dragon Sword", "🐉", 0xD8344A, 0xF5C451,
            "Forged to slay the last elder dragon, it drank the beast's soul instead. When you swing it, something old and furious breathes with you.",
            new AbilityInfo("Dragon Scales", "Resistance I and +50% knockback resistance while held."),
            new AbilityInfo("Dragon's Fury", "25% chance to scorch the target for +4 bonus damage."),
            new AbilityInfo("Dragon Breath", "Exhale a cone of dragonfire that shreds everything in front of you."),
            new AbilityInfo("Wyrm Dive", "Soar into the sky, then crash down in a shockwave. No fall damage.")),

    PHOENIX("phoenix", "Cinderwing", "Phoenix Sword", "🔥", 0xF28A1E, 0xFFE27A,
            "A feather of the first phoenix, beaten into a blade over a fire that never went out. Whoever carries it rises from their own ashes.",
            new AbilityInfo("Rebirth", "Fire Resistance while held. Once every 5 minutes, a killing blow revives you instead."),
            new AbilityInfo("Searing Edge", "Ignites targets. 15% chance to combust, burning everything nearby."),
            new AbilityInfo("Ember Volley", "Three exploding fireballs that set the ground they pass over on fire for a few seconds."),
            new AbilityInfo("Firestorm", "Unleash a ring of fire that scorches and blasts back everything around you.")),

    FROST("frost", "Everwinter", "Frost Sword", "❄", 0x9BE3F2, 0xFFFFFF,
            "Carved from ice that has not melted since the world was young. The wounds it leaves never quite stop freezing.",
            new AbilityInfo("Frostguard", "+4 armor, +2 toughness and immunity to freezing while held."),
            new AbilityInfo("Frostbite", "Slows and freezes the target."),
            new AbilityInfo("Glacial Spike", "Fire an ice spike that impales and encases the first enemy hit."),
            new AbilityInfo("Blizzard", "Summon a 6 second blizzard around you that freezes and batters enemies.")),

    STORM("storm", "Stormcaller", "Storm Sword", "⚡", 0x4DB2FF, 0xF5C842,
            "It hung on the highest peak for a thousand years, struck by every storm that passed. In the end, the storms began to answer it.",
            new AbilityInfo("Tailwind", "+25% movement speed and immunity to lightning while held."),
            new AbilityInfo("Static Charge", "12% chance to call lightning on the target for +5 damage."),
            new AbilityInfo("Thunder Strike", "Call a lightning bolt down wherever you're looking."),
            new AbilityInfo("Chain Lightning", "Lightning arcs between up to 6 nearby enemies.")),

    TIDE("tide", "Tidebreaker", "Tide Sword", "🌊", 0x2EC4B6, 0xA8FFF3,
            "Lost with a drowned kingdom and given back by the sea. It still carries the weight of the deep, and the deep still follows it.",
            new AbilityInfo("Ocean's Blessing", "Conduit Power and Dolphin's Grace while held."),
            new AbilityInfo("Undertow", "20% chance to drag the target towards you and slow it."),
            new AbilityInfo("Riptide Surge", "Surge forward like a riptide, slamming through every enemy in your path."),
            new AbilityInfo("Tidal Wave", "Send a crashing wave forward that sweeps enemies away.")),

    VERDANT("verdant", "Elderbloom", "Verdant Sword", "🌿", 0x2FBF71, 0xE8C64A,
            "Grown, not forged, from the heartwood of the world's oldest tree. Wherever it fights, roots break through the ground.",
            new AbilityInfo("Verdant Life", "Regeneration I while held. Mobs you slay drop double XP."),
            new AbilityInfo("Thorned Edge", "25% chance to poison the target."),
            new AbilityInfo("Nature's Grasp", "Roots erupt around you, damaging and rooting enemies in place."),
            new AbilityInfo("Verdant Bloom", "Heal, gain Absorption and cleanse all negative effects. Shared with teammates.")),

    PLAGUE("plague", "Pestilence", "Plague Sword", "☣", 0x8DC63F, 0x4A5A3A,
            "Quenched in the blight that emptied a hundred villages. Every scratch it leaves festers, and every festering wound spreads.",
            new AbilityInfo("Plaguebearer", "Immune to poison, wither, hunger and nausea while held. The plague you spread even rots the undead."),
            new AbilityInfo("Infection", "Infects the target with plague. Striking an infected target withers it for +2 damage."),
            new AbilityInfo("Miasma", "Hurl a lingering plague cloud that infects and weakens enemies inside."),
            new AbilityInfo("Pandemic", "Every infected enemy nearby erupts. The deeper the infection, the bigger the burst.")),

    CRIMSON("crimson", "Sanguine", "Crimson Sword", "❤", 0xE0245E, 0x7A0F2A,
            "A duelist's needle that learned to drink. It gives every drop it draws back to the hand that holds it.",
            new AbilityInfo("Blood Reserve", "+3 max hearts while held."),
            new AbilityInfo("Bloodthirst", "Heal 15% of damage dealt. 25% chance to make the target bleed."),
            new AbilityInfo("Blood Crescent", "Launch a crimson slash that cuts through every enemy in its path."),
            new AbilityInfo("Crimson Harvest", "Rip the life out of all nearby enemies and heal from it.")),

    SOLAR("solar", "Solaris", "Solar Sword", "☀", 0xF2C94C, 0xFFF6C9,
            "Blessed at dawn by the last of the sun-priests and kept in light ever since. The dead burn at the mere sight of it.",
            new AbilityInfo("Radiance", "Haste II while held. Nearby enemies are revealed with Glowing."),
            new AbilityInfo("Sunfire", "Ignites targets. Undead take +50% damage."),
            new AbilityInfo("Solar Beam", "Channel a beam of sunlight for 1.5 seconds that follows your aim, burning everything it sweeps across."),
            new AbilityInfo("Supernova", "Charge up, then explode in a blinding blast of solar fire.")),

    VOID("void", "Voidreaver", "Void Sword", "🌌", 0xA64DFF, 0x3A1A5C,
            "Pulled from the empty space between the stars. Light bends around its edge and never comes back out.",
            new AbilityInfo("Void Walker", "Night Vision while held. The void can't claim you while you carry this blade."),
            new AbilityInfo("Void Drain", "20% chance to wither the target and steal 2 health."),
            new AbilityInfo("Void Step", "Blink up to 15 blocks forward, tearing at anything where you land."),
            new AbilityInfo("Singularity", "Open a black hole that drags enemies in, then implodes.")),

    COSMIC("cosmic", "Starfall", "Cosmic Sword", "✨", 0xC54DFF, 0x4DE8FF,
            "Hammered from the core of a fallen star, still warm after ten thousand years. Gravity itself kneels to whoever holds it.",
            new AbilityInfo("Zero-G", "Lower gravity, higher jumps and no fall damage while held."),
            new AbilityInfo("Starstrike", "20% chance to launch the target into orbit with Levitation."),
            new AbilityInfo("Gravity Pulse", "Hurl nearby enemies into the sky, then slam them back down."),
            new AbilityInfo("Meteor Shower", "Rain meteors down on the area you're looking at.")),

    CHRONO("chrono", "Aeon", "Chrono Sword", "⌛", 0x7FE0D0, 0xE8C35A,
            "Its smith worked on it for a thousand years, then went back and finished it in an afternoon. Time moves however its wielder prefers.",
            new AbilityInfo("Quickened", "+25% attack speed while held."),
            new AbilityInfo("Time Lock", "15% chance to freeze the target in time for a moment."),
            new AbilityInfo("Stasis Field", "Freeze time in an area: enemies crawl and projectiles stop in mid-air."),
            new AbilityInfo("Rewind", "Return to where you were 4 seconds ago, with the health you had then.")),

    SHADOW("shadow", "Umbra", "Shadow Sword", "🌑", 0x7B6BE0, 0x1B1B3A,
            "The blade of a watcher who was never once seen. It is darkest exactly where you are not looking.",
            new AbilityInfo("Umbral Cloak", "Invisible while sneaking, plus faster movement while held."),
            new AbilityInfo("Backstab", "+60% damage when striking from behind. 20% chance to blind."),
            new AbilityInfo("Shadow Strike", "Teleport behind the enemy you're looking at and strike."),
            new AbilityInfo("Shadow Veil", "Vanish completely (armor too) for 6s. Your next hit deals +8 damage.")),

    SOUL("soul", "Gravecaller", "Soul Sword", "💀", 0x6FD8F0, 0x1F3A4A,
            "A lich's blade, still humming with every soul it ever took. Some of them are eager to hunt for you.",
            new AbilityInfo("Soul Siphon", "Every kill heals 4 health and grants Absorption."),
            new AbilityInfo("Soul Rend", "20% chance to tear at the target's soul for +4 damage."),
            new AbilityInfo("Spectral Hounds", "Summon three spectral hounds that hunt your target for 15 seconds."),
            new AbilityInfo("Wail of the Damned", "Wither, weaken and scatter enemies around you; six screaming skulls then hunt them down.")),

    ECHO("echo", "Deepwhisper", "Echo Sword", "🔊", 0x22C2C7, 0x0B2A33,
            "Grown in the sculk beneath the Deep Dark, where silence is a weapon. It hears your heartbeat before you even swing.",
            new AbilityInfo("Child of the Deep", "Immune to Darkness. Sculk sensors and shriekers ignore you while held."),
            new AbilityInfo("Resonance", "20% chance to engulf the target in darkness."),
            new AbilityInfo("Sonic Boom", "Charge, then release a warden's sonic boom that pierces every enemy in a line."),
            new AbilityInfo("Shriek", "A sculk shriek darkens and reveals every enemy around you, and quickens you.")),

    // ---- weapons that aren't swords ----

    HAMMER("hammer", "Earthshaker", "Titan Hammer", "⚒", 0xE8822A, 0x6B6B6B, Material.MACE,
            "Forged in a dead volcano by giants who wanted mountains to kneel. Strike the ground with it, and the ground remembers.",
            new AbilityInfo("Unshakable", "Immune to knockback and +3 armor while held. Keeps the mace's smash attack."),
            new AbilityInfo("Quake", "20% chance to send out a shockwave that stuns everything around the target."),
            new AbilityInfo("Seismic Slam", "Slam the ground: a ring of erupting earth launches everything around you."),
            new AbilityInfo("Earthen Bulwark", "Raise stone pillars around you. Resistance II, and attackers are hurt and thrown back.")),

    SCYTHE("scythe", "Requiem", "Reaper's Scythe", "⚰", 0x2BD9A8, 0x0E3B33, Material.NETHERITE_SWORD,
            "The ninth abyss sings through its curved blade. Every soul it reaps adds another voice to the choir.",
            new AbilityInfo("Death's Embrace", "Kills refresh Reap and grant a burst of speed."),
            new AbilityInfo("Rend", "+50% damage against targets below 35% health."),
            new AbilityInfo("Reap", "A wide sweeping arc that cuts every enemy in front of you and heals you for each."),
            new AbilityInfo("Final Harvest", "A giant spectral scythe falls on your target, hitting harder the more health it has lost.")),

    SPEAR("spear", "Skypiercer", "Celestial Spear", "♆", 0xE9C46A, 0xC0392B, Material.NETHERITE_SPEAR,
            "Hurled from the heavens to end a war between gods, it fell for an age. It has been searching for a worthy arm ever since.",
            new AbilityInfo("Long Reach", "+1.5 blocks of attack reach while held. Keeps the spear's charge attack."),
            new AbilityInfo("Impale", "20% chance to pin the target in place for +3 damage."),
            new AbilityInfo("Javelin", "Hurl a spectral spear that pierces every enemy in its path, then returns to you."),
            new AbilityInfo("Phalanx", "Spears erupt from the ground in a line ahead, skewering and launching enemies.")),

    CHAKRAM("chakram", "Zephyr", "Wind Chakram", "☸", 0x4CC38A, 0xE8C64A, Material.NETHERITE_SWORD,
            "A royal war-disc blessed by all four winds. Thrown true, it always finds its way home.",
            new AbilityInfo("Windborne", "Double jump while held. No fall damage after a double jump."),
            new AbilityInfo("Gust", "20% chance to blast the target away with a burst of wind."),
            new AbilityInfo("Halo Throw", "Throw the chakram: it slices through enemies on the way out and on the way back."),
            new AbilityInfo("Cyclone", "Three spectral chakrams orbit you, shredding enemies and deflecting arrows.")),

    AXE("axe", "Ragnarök", "Berserker Axe", "ᛉ", 0xC77DFF, 0x5A1E6E, Material.NETHERITE_AXE,
            "The axe that will end the world, carved with runes of doom. It grows hungrier the closer its wielder comes to death.",
            new AbilityInfo("Bloodlust", "Up to +50% melee damage the lower your health. Disables shields like any axe."),
            new AbilityInfo("Cleave", "Every hit also strikes enemies beside the target for 40% of the damage."),
            new AbilityInfo("Whirlwind", "Spin with the axe for 2.5 seconds, carving through everything around you."),
            new AbilityInfo("Warcry", "A giant spectral axe crashes down ahead. You gain Strength II and Resistance; enemies are weakened and slowed.")),

    STAFF("staff", "Arcanum", "Arcane Staff", "⚚", 0xB57EDC, 0xF3D9FF, Material.NETHERITE_SWORD,
            "Grown from a single amethyst that hummed with magic for ten thousand years. It still hums, and now it answers.",
            new AbilityInfo("Arcane Ward", "A 2-heart magic ward that recharges 12 seconds after it breaks."),
            new AbilityInfo("Arcane Spark", "25% chance for a spark to leap to another nearby enemy."),
            new AbilityInfo("Arcane Missiles", "Fire five homing amethyst missiles at the enemies in front of you."),
            new AbilityInfo("Crystal Nova", "Rings of amethyst crystals burst from the ground around you, launching enemies.")),

    TRIDENT("trident", "Leviathan", "Abyssal Trident", "Ψ", 0x2EC4C4, 0x0B4F6C, Material.TRIDENT,
            "Raised from the trench where the old sea god sleeps. Wherever it is thrown, the storm follows.",
            new AbilityInfo("Stormborn", "Always returns when thrown, and calls down lightning where it hits, in any weather. Water Breathing while held."),
            new AbilityInfo("Undertow", "20% chance to drag the target under: +3 damage and Slowness III."),
            new AbilityInfo("Trident Rain", "Five spectral tridents fall on the target area, each bursting into a wave."),
            new AbilityInfo("Maelstrom", "A whirlpool drags enemies around and in for four seconds, then erupts as a geyser.")),

    SHURIKEN("shuriken", "Nightbloom", "Amethyst Shuriken", "✴", 0xE040FB, 0x7B1FA2, Material.NETHERITE_SWORD,
            "Four petals of living amethyst, folded by a clan that was never seen. It blooms only in the dark, and never twice in the same place.",
            new AbilityInfo("Light Feet", "Speed I and Jump Boost I while held."),
            new AbilityInfo("Petal Cut", "25% chance to fling a spare shuriken at another nearby enemy."),
            new AbilityInfo("Star Fan", "Throw five spinning shuriken in a fan; each one ricochets to a second enemy."),
            new AbilityInfo("Thousand Cuts", "Flash between up to six nearby enemies, cutting each one, then return to where you started.")),

    HALBERD("halberd", "Jadewarden", "Jade Halberd", "⚜", 0x3FBF7F, 0xE8C35A, Material.NETHERITE_SPEAR,
            "Carried by the emperor's last guard, who held the gate alone for a hundred days. The jade remembers every enemy that fell before it.",
            new AbilityInfo("Imperial Guard", "+1 block of reach and +4 armor while held. Keeps the spear's charge attack."),
            new AbilityInfo("Jade Cleave", "25% chance to sweep everything in front of you for half the damage."),
            new AbilityInfo("Dragon Sweep", "Spin the halberd in a full circle, cutting and hurling back everything around you."),
            new AbilityInfo("Jade Dragon", "Release a jade serpent that winds forward through your enemies, biting and tossing them.")),

    WARGLAIVE("warglaive", "Eclipse", "Lunar Warglaive", "☾", 0x8FA8FF, 0x2B2F6B, Material.NETHERITE_SWORD,
            "Forged on the one night the moon swallowed the sun. It has been hungry for light ever since.",
            new AbilityInfo("Moonlit", "Night Vision while held. Melee hits deal +25% damage at night or in the dark."),
            new AbilityInfo("Crescent Echo", "20% chance for a spectral crescent to hit the target again a moment later."),
            new AbilityInfo("Lunar Dash", "Dash forward through your enemies, leaving a trail of silver crescents."),
            new AbilityInfo("Moonfall", "A giant crescent moon falls on the target area and shatters.")),

    SAWBLADE("sawblade", "Ripsaw", "Cyber Sawblade", "⚙", 0xFF7A1A, 0x29E0FF, Material.NETHERITE_SWORD,
            "A prototype from a future that never happened. Nobody remembers who built it, but everyone remembers the noise.",
            new AbilityInfo("Overclock", "Hits in quick succession build heat. At 5 heat your next hit vents for +6 damage."),
            new AbilityInfo("Arc Spark", "25% chance for electricity to jump to two nearby enemies."),
            new AbilityInfo("Grinder", "Rev the blade for two seconds, shredding everything in front of you."),
            new AbilityInfo("EMP Overload", "Release an electromagnetic pulse: enemies are stunned and revealed, and you speed up.")),

    RAPIER("rapier", "Valiance", "Royal Rapier", "♛", 0xF5E6B8, 0xD4A017, Material.NETHERITE_SWORD,
            "The queen's own blade, which never lost a duel. It asks only that you strike first, and strike true.",
            new AbilityInfo("Finesse", "+25% attack speed and +10% movement speed while held."),
            new AbilityInfo("Precision", "+50% damage against enemies at full health."),
            new AbilityInfo("Flèche", "Lunge forward and run the first enemy in your path through."),
            new AbilityInfo("Riposte", "Take a parry stance: the next attack against you is blocked and answered with a flurry of thrusts.")),

    KHOPESH("khopesh", "Sandreaver", "Pharaoh's Khopesh", "☥", 0xE3B341, 0x1FA3A3, Material.NETHERITE_SWORD,
            "Buried with a pharaoh who refused to stay buried. When it is raised, the desert rises with it.",
            new AbilityInfo("Sun-Blessed", "Regeneration I in daylight while held."),
            new AbilityInfo("Pharaoh's Curse", "20% chance to curse the target: Weakness, Slowness and +20% damage from you for 5 seconds."),
            new AbilityInfo("Sandstorm", "Send a sand tornado forward that lifts, blinds and grinds enemies along its path."),
            new AbilityInfo("Tomb of Sands", "Seal nearby enemies in sandstone tombs, then collapse them."));

    public static final String MODEL_NAMESPACE = "customs";

    /** Ids from older versions, so swords players already own keep working. */
    private static final Map<String, SwordType> LEGACY_IDS = Map.of("inferno", PHOENIX, "emerald", VERDANT);

    private final String id;
    private final Material base;
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
        this(id, swordName, title, emoji, color, accent, null, bio, passive, onHit, primary, secondary);
    }

    SwordType(String id, String swordName, String title, String emoji, int color, int accent, Material base, String bio,
              AbilityInfo passive, AbilityInfo onHit, AbilityInfo primary, AbilityInfo secondary) {
        this.id = id;
        this.base = base;
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

    /** The vanilla item it is built on (decides vanilla behaviour such as the mace smash or spear lunge). */
    public Material baseItem() {
        return base == null ? Material.NETHERITE_SWORD : base;
    }

    /** The element of this weapon's magic: decides how casting its abilities looks. */
    public dev.infuse.customs.ability.Fx3D.Element element() {
        return switch (this) {
            case DRAGON, PHOENIX, SOLAR -> dev.infuse.customs.ability.Fx3D.Element.FIRE;
            case FROST -> dev.infuse.customs.ability.Fx3D.Element.FROST;
            case STORM, SAWBLADE -> dev.infuse.customs.ability.Fx3D.Element.STORM;
            case TIDE, TRIDENT -> dev.infuse.customs.ability.Fx3D.Element.WATER;
            case VERDANT, PLAGUE -> dev.infuse.customs.ability.Fx3D.Element.NATURE;
            case VOID, COSMIC, CHRONO, STAFF, WARGLAIVE, KHOPESH -> dev.infuse.customs.ability.Fx3D.Element.ARCANE;
            case SHADOW, SOUL, ECHO, SCYTHE -> dev.infuse.customs.ability.Fx3D.Element.SHADOW;
            case CRIMSON, AXE -> dev.infuse.customs.ability.Fx3D.Element.BLOOD;
            default -> dev.infuse.customs.ability.Fx3D.Element.MARTIAL;
        };
    }

    /** How melee hits with this weapon are drawn. */
    public dev.infuse.customs.ability.Fx3D.MeleeStyle meleeStyle() {
        return switch (this) {
            case SCYTHE, AXE, WARGLAIVE, KHOPESH -> dev.infuse.customs.ability.Fx3D.MeleeStyle.HEAVY;
            case SPEAR, RAPIER, HALBERD, TRIDENT -> dev.infuse.customs.ability.Fx3D.MeleeStyle.THRUST;
            case HAMMER -> dev.infuse.customs.ability.Fx3D.MeleeStyle.IMPACT;
            case CHAKRAM, SHURIKEN, SAWBLADE -> dev.infuse.customs.ability.Fx3D.MeleeStyle.SPIN;
            default -> dev.infuse.customs.ability.Fx3D.MeleeStyle.SLASH;
        };
    }

    /** True for the 15 swords, false for the other weapons. */
    public boolean isSword() {
        return base == null;
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
            case HAMMER -> "crushed into the earth";
            case SCYTHE -> "reaped";
            case SPEAR -> "impaled";
            case CHAKRAM -> "carried off by the wind";
            case AXE -> "cleaved in two";
            case TRIDENT -> "skewered by the tide";
            case SHURIKEN -> "cut to ribbons";
            case HALBERD -> "felled at the gate";
            case WARGLAIVE -> "eclipsed";
            case SAWBLADE -> "shredded";
            case RAPIER -> "outdueled";
            case KHOPESH -> "buried in the sands";
            case STAFF -> "unmade by arcane fire";
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
