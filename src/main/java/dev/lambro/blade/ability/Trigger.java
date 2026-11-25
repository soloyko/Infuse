package dev.lambro.blade.ability;

public enum Trigger {
    PASSIVE("Passive"),
    RIGHT_CLICK("Right Click"),
    SNEAK_RIGHT_CLICK("Sneak + Right Click"),
    SWAP("Swap Hands [F]"),
    SNEAK_SWAP("Sneak + Swap [F]");

    private final String label;

    Trigger(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
