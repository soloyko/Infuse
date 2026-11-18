package me.infuse.effect;

/** An effect sitting in one of a player's two slots. */
public record Equipped(EffectType type, boolean augmented) {
}
