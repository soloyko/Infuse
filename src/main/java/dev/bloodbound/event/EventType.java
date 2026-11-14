package dev.bloodbound.event;

import net.kyori.adventure.bossbar.BossBar;

import java.util.Locale;
import java.util.Optional;

/** Admin-triggered session events. Names and descriptions live in messages.yml. */
public enum EventType {
    BLOOD_MOON(BossBar.Color.RED),
    BOUNTY(BossBar.Color.YELLOW),
    FRENZY(BossBar.Color.PINK),
    LAST_STAND(BossBar.Color.WHITE),
    ALTAR_SURGE(BossBar.Color.PURPLE);

    private final BossBar.Color color;

    EventType(BossBar.Color color) {
        this.color = color;
    }

    public BossBar.Color color() {
        return color;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<EventType> fromKey(String key) {
        if (key == null) return Optional.empty();
        String normalized = key.toUpperCase(Locale.ROOT).replace('-', '_');
        for (EventType t : values()) if (t.name().equals(normalized)) return Optional.of(t);
        return Optional.empty();
    }
}
