package dev.infuse.customs.manager;

import dev.infuse.customs.sword.AbilitySlot;
import dev.infuse.customs.sword.SwordType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player ability cooldowns, tracked per sword type and slot. Kept across relogs until restart. */
public final class CooldownManager {

    /** A cooldown that has finished and hasn't been announced yet. */
    public record Ready(SwordType type, AbilitySlot slot) {
    }

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
                .computeIfAbsent(type, k -> new long[AbilitySlot.values().length * 2]);
        long[] times = cooldowns.get(player).get(type);
        times[slot.ordinal()] = System.currentTimeMillis() + seconds * 1000L;
        times[AbilitySlot.values().length + slot.ordinal()] = seconds * 1000L;
    }

    /** How much of the cooldown has passed, from 0 (just used) to 1 (ready). */
    public double progress(UUID player, SwordType type, AbilitySlot slot) {
        long left = remaining(player, type, slot);
        if (left <= 0) {
            return 1;
        }
        long total = cooldowns.get(player).get(type)[AbilitySlot.values().length + slot.ordinal()];
        return total <= 0 ? 1 : Math.clamp(1 - (double) left / total, 0, 1);
    }

    /**
     * Returns cooldowns of this player that have run out since the last call and forgets them,
     * so each one is reported exactly once.
     */
    public List<Ready> pollReady(UUID player) {
        Map<SwordType, long[]> byType = cooldowns.get(player);
        if (byType == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<Ready> ready = new ArrayList<>(0);
        byType.entrySet().removeIf(entry -> {
            long[] until = entry.getValue();
            boolean active = false;
            for (AbilitySlot slot : AbilitySlot.values()) {
                long t = until[slot.ordinal()];
                if (t == 0) {
                    continue;
                }
                if (t <= now) {
                    ready.add(new Ready(entry.getKey(), slot));
                    until[slot.ordinal()] = 0;
                } else {
                    active = true;
                }
            }
            return !active;
        });
        if (byType.isEmpty()) {
            cooldowns.remove(player);
        }
        return ready;
    }

    /** Makes one ability ready again. */
    public void clear(UUID player, SwordType type, AbilitySlot slot) {
        Map<SwordType, long[]> byType = cooldowns.get(player);
        if (byType != null && byType.get(type) != null) {
            byType.get(type)[slot.ordinal()] = 0;
        }
    }

    public void reset(UUID player) {
        cooldowns.remove(player);
    }

    public void resetAll() {
        cooldowns.clear();
    }
}
