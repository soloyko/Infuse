package dev.infuse.customs.sword;

import dev.infuse.customs.Settings;
import net.kyori.adventure.text.Component;

import java.util.Locale;

/** The two activated abilities every sword has. */
public enum AbilitySlot {
    /** Swap-hand key (or right-click, depending on controls.trigger). */
    PRIMARY,
    /** Sneak + the same key. */
    SECONDARY;

    /** How to fire this ability, e.g. "[F]" / "Sneak + [F]". Uses the player's own key binding. */
    public Component label(Settings.Trigger trigger) {
        Component key = key(trigger);
        return this == PRIMARY ? key : Component.text("Sneak + ").append(key);
    }

    /** Compact form for the action bar, e.g. "[F]" / "⇧[F]". */
    public Component shortLabel(Settings.Trigger trigger) {
        Component key = trigger == Settings.Trigger.RIGHT_CLICK ? Component.text("RC") : bracketed("key.swapOffhand");
        return this == PRIMARY ? key : Component.text("⇧").append(key);
    }

    private static Component key(Settings.Trigger trigger) {
        return trigger == Settings.Trigger.RIGHT_CLICK ? Component.text("Right-Click") : bracketed("key.swapOffhand");
    }

    /** "[F]", where F is whatever the player has bound to the action. */
    private static Component bracketed(String keybind) {
        return Component.text("[").append(Component.keybind(keybind)).append(Component.text("]"));
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
