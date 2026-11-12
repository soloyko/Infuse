package dev.infuse.opswords.sword;

/** The two activated abilities every sword has. */
public enum AbilitySlot {
    /** Right-click. */
    PRIMARY("Right-Click", "RC"),
    /** Sneak + right-click. */
    SECONDARY("Sneak + Right-Click", "⇧RC");

    private final String trigger;
    private final String shortTrigger;

    AbilitySlot(String trigger, String shortTrigger) {
        this.trigger = trigger;
        this.shortTrigger = shortTrigger;
    }

    public String trigger() {
        return trigger;
    }

    public String shortTrigger() {
        return shortTrigger;
    }

    public String configKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
