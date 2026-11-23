package dev.blackclover.smp.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Blocks kill XP for the same killer/victim pair within a time window. */
public final class RepeatKillTracker {

    private final long windowMillis;
    private final Map<String, Long> lastKill = new HashMap<>();

    public RepeatKillTracker(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    /** @return true if XP is allowed for this kill; the kill is recorded either way only when allowed. */
    public boolean tryRecord(UUID killer, UUID victim, long now) {
        String key = killer + ">" + victim;
        Long previous = lastKill.get(key);
        if (previous != null && now - previous < windowMillis) {
            return false;
        }
        lastKill.put(key, now);
        if (lastKill.size() > 4096) {
            lastKill.values().removeIf(t -> now - t >= windowMillis);
        }
        return true;
    }
}
