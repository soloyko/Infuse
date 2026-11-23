package dev.blackclover.smp.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tracks who last hit whom, for kill credit and combat-log detection. */
public final class CombatTracker {

    private record Hit(UUID attacker, long time) {
    }

    private final long tagMillis;
    private final Map<UUID, Hit> lastHit = new HashMap<>();
    private final Map<UUID, Long> lastCombat = new HashMap<>();

    public CombatTracker(long tagMillis) {
        this.tagMillis = tagMillis;
    }

    public void recordHit(UUID attacker, UUID victim, long now) {
        lastHit.put(victim, new Hit(attacker, now));
        lastCombat.put(attacker, now);
        lastCombat.put(victim, now);
    }

    public Optional<UUID> lastAttacker(UUID victim, long now) {
        Hit hit = lastHit.get(victim);
        if (hit == null || now - hit.time() > tagMillis) {
            return Optional.empty();
        }
        return Optional.of(hit.attacker());
    }

    public boolean inCombat(UUID player, long now) {
        Long t = lastCombat.get(player);
        return t != null && now - t <= tagMillis;
    }

    public void clear(UUID player) {
        lastHit.remove(player);
        lastCombat.remove(player);
    }
}
