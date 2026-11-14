package dev.bloodbound.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Prevents farming the same victim repeatedly within a time window. */
public final class KillCooldowns {
    private record Pair(UUID killer, UUID victim) {}

    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<Pair, Long> lastKill = new HashMap<>();

    public KillCooldowns(long windowMillis, LongSupplier clock) {
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Records the kill and returns true if it counts; false if still on cooldown. */
    public boolean tryRecord(UUID killer, UUID victim) {
        long now = clock.getAsLong();
        lastKill.values().removeIf(t -> now - t > windowMillis);
        Pair key = new Pair(killer, victim);
        Long previous = lastKill.get(key);
        if (previous != null && now - previous <= windowMillis) return false;
        lastKill.put(key, now);
        return true;
    }

    public void clear() {
        lastKill.clear();
    }
}
