package dev.infuse.opswords.sword;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The ten OP swords. The id doubles as the item model name in the resource pack
 * ({@code op_swords:<id>}) and as the key in config.yml.
 */
public enum SwordType {
    DRAGON("dragon", "Dragon Sword", "🐉", 0x5FC460, 0xD4AF37,
            new AbilityInfo("Dragon Scales", "Resistance I and +50% knockback resistance while held."),
            new AbilityInfo("Dragon's Fury", "25% chance to scorch the target for +4 bonus damage."),
            new AbilityInfo("Dragon Breath", "Exhale a cone of dragonfire that shreds everything in front of you."),
            new AbilityInfo("Wyrm Dive", "Soar into the sky, then crash down in a shockwave. No fall damage.")),

    VOID("void", "Void Sword", "🌌", 0x845ECC, 0x2B1B4A,
            new AbilityInfo("Void Walker", "Night Vision while held. The void can't claim you while you carry this blade."),
            new AbilityInfo("Void Drain", "20% chance to wither the target and steal 2 health."),
            new AbilityInfo("Void Step", "Blink up to 15 blocks forward, tearing at anything where you land."),
            new AbilityInfo("Singularity", "Open a black hole that drags enemies in, then implodes.")),

    INFERNO("inferno", "Inferno Sword", "🔥", 0xDC6A1D, 0xFFD24A,
            new AbilityInfo("Heart of Fire", "Fire Resistance while held."),
            new AbilityInfo("Searing Edge", "Ignites targets. 15% chance to combust, burning everything nearby."),
            new AbilityInfo("Fireball Barrage", "Hurl a spread of three exploding fireballs."),
            new AbilityInfo("Inferno Nova", "Unleash a ring of fire that scorches and blasts back all around you.")),

    FROST("frost", "Frost Sword", "❄", 0x93D7EF, 0xFFFFFF,
            new AbilityInfo("Frostguard", "+4 armor, +2 toughness and immunity to freezing while held."),
            new AbilityInfo("Frostbite", "Slows and freezes the target."),
            new AbilityInfo("Glacial Spike", "Fire an ice spike that impales and encases the first enemy hit."),
            new AbilityInfo("Blizzard", "Summon a 6 second blizzard around you that freezes and batters enemies.")),

    STORM("storm", "Storm Sword", "⚡", 0x6B92D2, 0xE8F4FF,
            new AbilityInfo("Tailwind", "+25% movement speed and immunity to lightning while held."),
            new AbilityInfo("Static Charge", "12% chance to call lightning on the target for +5 damage."),
            new AbilityInfo("Thunder Strike", "Call a lightning bolt down wherever you're looking."),
            new AbilityInfo("Chain Lightning", "Lightning arcs between up to 6 nearby enemies.")),

    EMERALD("emerald", "Emerald Sword", "💚", 0x49C186, 0xB8F5D0,
            new AbilityInfo("Verdant Life", "Regeneration I while held. Mobs you slay drop double XP."),
            new AbilityInfo("Thorned Edge", "25% chance to poison the target."),
            new AbilityInfo("Nature's Grasp", "Roots erupt around you, damaging and rooting enemies in place."),
            new AbilityInfo("Verdant Bloom", "Heal, gain Absorption and cleanse all negative effects. Shared with teammates.")),

    CRIMSON("crimson", "Crimson Sword", "❤", 0xC4415B, 0x5C0A1A,
            new AbilityInfo("Blood Reserve", "+3 max hearts while held."),
            new AbilityInfo("Bloodthirst", "Heal 15% of damage dealt. 25% chance to make the target bleed."),
            new AbilityInfo("Blood Crescent", "Launch a crimson slash that cuts through every enemy in its path."),
            new AbilityInfo("Crimson Harvest", "Rip the life out of all nearby enemies and heal from it.")),

    SOLAR("solar", "Solar Sword", "☀", 0xECBA44, 0xFFF4C2,
            new AbilityInfo("Radiance", "Haste II while held. Nearby enemies are revealed with Glowing."),
            new AbilityInfo("Sunfire", "Ignites targets. Undead take +50% damage."),
            new AbilityInfo("Solar Beam", "Fire a piercing beam of sunlight that burns everything in a line."),
            new AbilityInfo("Supernova", "Charge up, then explode in a blinding blast of solar fire.")),

    SHADOW("shadow", "Shadow Sword", "🌑", 0x6D6B84, 0x1A1A24,
            new AbilityInfo("Umbral Cloak", "Invisible while sneaking, plus faster movement while held."),
            new AbilityInfo("Backstab", "+60% damage when striking from behind. 20% chance to blind."),
            new AbilityInfo("Shadow Strike", "Teleport behind the enemy you're looking at and strike."),
            new AbilityInfo("Shadow Veil", "Vanish completely (armor too) for 6s. Your next hit deals +8 damage.")),

    COSMIC("cosmic", "Cosmic Sword", "✨", 0x6C60C4, 0xF2C9FF,
            new AbilityInfo("Zero-G", "Lower gravity, higher jumps and no fall damage while held."),
            new AbilityInfo("Starstrike", "20% chance to launch the target into orbit with Levitation."),
            new AbilityInfo("Gravity Pulse", "Hurl nearby enemies into the sky, then slam them back down."),
            new AbilityInfo("Meteor Shower", "Rain meteors down on the area you're looking at."));

    public static final String MODEL_NAMESPACE = "op_swords";

    private final String id;
    private final String displayName;
    private final String emoji;
    private final TextColor color;
    private final TextColor accent;
    private final AbilityInfo passive;
    private final AbilityInfo onHit;
    private final AbilityInfo primary;
    private final AbilityInfo secondary;

    SwordType(String id, String displayName, String emoji, int color, int accent,
              AbilityInfo passive, AbilityInfo onHit, AbilityInfo primary, AbilityInfo secondary) {
        this.id = id;
        this.displayName = displayName;
        this.emoji = emoji;
        this.color = TextColor.color(color);
        this.accent = TextColor.color(accent);
        this.passive = passive;
        this.onHit = onHit;
        this.primary = primary;
        this.secondary = secondary;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String emoji() {
        return emoji;
    }

    /** Main theme colour (matches the blade texture). */
    public TextColor color() {
        return color;
    }

    /** Secondary colour used for gradients. */
    public TextColor accent() {
        return accent;
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

    public NamespacedKey itemModel() {
        return new NamespacedKey(MODEL_NAMESPACE, id);
    }

    public static @Nullable SwordType byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String needle = id.toLowerCase(Locale.ROOT).replace("_sword", "").replace("sword", "");
        for (SwordType type : values()) {
            if (type.id.equals(needle)) {
                return type;
            }
        }
        return null;
    }
}
