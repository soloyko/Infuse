package dev.blackclover.smp.mage;

import dev.blackclover.smp.grimoire.GrimoireType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Per-player progression: XP is stored separately for every grimoire the player has wielded. */
public final class PlayerData {

    private final UUID id;
    private String name;
    private final Map<GrimoireType, Integer> xp = new EnumMap<>(GrimoireType.class);
    private long lastPenaltyMillis = Long.MIN_VALUE / 2;
    private long lastSeenMillis;

    public PlayerData(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int xp(GrimoireType type) {
        return xp.getOrDefault(type, 0);
    }

    public void setXp(GrimoireType type, int value) {
        xp.put(type, Math.max(0, value));
    }

    public Map<GrimoireType, Integer> xpView() {
        return Collections.unmodifiableMap(xp);
    }

    public long lastPenaltyMillis() {
        return lastPenaltyMillis;
    }

    public void setLastPenaltyMillis(long lastPenaltyMillis) {
        this.lastPenaltyMillis = lastPenaltyMillis;
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    public void setLastSeenMillis(long lastSeenMillis) {
        this.lastSeenMillis = lastSeenMillis;
    }

    public boolean canBePenalized(long now, long cooldownMillis) {
        return now - lastPenaltyMillis >= cooldownMillis;
    }
}
