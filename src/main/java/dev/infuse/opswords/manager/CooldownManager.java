package dev.infuse.opswords.manager;

import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player ability cooldowns, tracked per sword type and slot. Kept across relogs until restart. */
public final class CooldownManager {

    private final Map<UUID, Map<SwordType, long[]>> cooldowns = new HashMap<>();

    /** Milliseconds left, 0 if ready. */
    public long remaining(UUID player, SwordType type, AbilitySlot slot) {
        Map<SwordType, long[]> byType = cooldowns.get(player);
        if (byType == null) {
            return 0;
        }
        long[] until = byType.get(type);
        if (until == null) {
            return 0;
        }
        return Math.max(0, until[slot.ordinal()] - System.currentTimeMillis());
    }

    public void start(UUID player, SwordType type, AbilitySlot slot, int seconds) {
        if (seconds <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(player, k -> new EnumMap<>(SwordType.class))
                .computeIfAbsent(type, k -> new long[AbilitySlot.values().length])[slot.ordinal()] =
                System.currentTimeMillis() + seconds * 1000L;
    }

    public void reset(UUID player) {
        cooldowns.remove(player);
    }

    public void resetAll() {
        cooldowns.clear();
    }
}
