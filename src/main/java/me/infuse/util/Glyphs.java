package me.infuse.util;

import me.infuse.effect.EffectType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Maps effect state to the font characters defined by the resource pack.
 * <p>
 * effects.json: 4 rows per effect (regular, augmented, regular-active, augmented-active); rows 60-71 hold
 * extra icons (Weaving, Oozing, Resistance) made by tools/make_icons.py, ready for new effects
 * by 21 columns (cooldown frames, 0 = ready, 20 = darkest). Row k, column c is the character
 * {@code ((0xE0 + c) << 8) | low(k)} with {@code low(k) = {0x00,0x80,0x40,0xC0}[k % 4] + k / 4 + 1}.
 * <p>
 * hit_tracker.json: 15 rows (one per effect) by 14 columns (0-10 = hit count, 11-13 = break animation).
 * Row r, column c is the character {@code (c << 12) | (r + 1)}.
 */
public final class Glyphs {
    public static final Key EFFECTS_FONT = Key.key("infuse", "effects");
    public static final Key HIT_FONT = Key.key("infuse", "hit_tracker");
    public static final Key CUSTOM_FONT = Key.key("infuse", "custom");
    public static final char APOPHIS_COVER = '';
    public static final int FRAMES = 21;

    private static final int[] SUB = {0x00, 0x80, 0x40, 0xC0};

    private Glyphs() {}

    public static char effect(EffectType type, boolean augmented, boolean active, int frame) {
        int variant = (augmented ? 1 : 0) + (active ? 2 : 0);
        int k = type.iconIndex() * 4 + variant;
        int low = SUB[k % 4] + k / 4 + 1;
        int col = Math.max(0, Math.min(FRAMES - 1, frame));
        return (char) (((0xE0 + col) << 8) | low);
    }

    public static char hitTracker(EffectType type, int column) {
        int col = Math.max(0, Math.min(13, column));
        return (char) ((col << 12) | (type.iconIndex() + 1));
    }

    public static Component effectComponent(EffectType type, boolean augmented, boolean active, int frame) {
        return Component.text(String.valueOf(effect(type, augmented, active, frame)))
                .font(EFFECTS_FONT).color(NamedTextColor.WHITE);
    }

    public static Component hitComponent(EffectType type, int column) {
        return Component.text(String.valueOf(hitTracker(type, column))).font(HIT_FONT).color(NamedTextColor.WHITE);
    }

    public static Component apophisCover() {
        return Component.text(String.valueOf(APOPHIS_COVER)).font(CUSTOM_FONT).color(NamedTextColor.WHITE);
    }
}
