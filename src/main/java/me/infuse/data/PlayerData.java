package me.infuse.data;

import me.infuse.effect.EffectType;
import me.infuse.effect.Equipped;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerData {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;

    private final UUID id;
    private final Equipped[] slots = new Equipped[2];
    private final long[] cooldownEnd = new long[2];
    private final long[] cooldownTotal = new long[2];
    private final long[] activeEnd = new long[2];
    private final boolean[] readyPinged = {true, true};
    private final long[] readyFlashUntil = new long[2];
    private boolean offhandControls = true;
    private final java.util.Set<UUID> trusted = new java.util.LinkedHashSet<>();

    // Runtime-only state
    private final Map<EffectType, Long> sparkActiveUntil = new EnumMap<>(EffectType.class);
    private int hits;
    private int takenHits;
    private long oozingSlimesAt;
    private float airFall;
    private long hitProcAt;
    private int speedStacks;
    private long lastHitAt;
    private long featherSlamUntil;
    private long lastSaturationBonus;

    public PlayerData(UUID id) {
        this.id = id;
    }

    public UUID id() { return id; }

    public Equipped get(int slot) { return slots[slot]; }
    public void set(int slot, Equipped e) { slots[slot] = e; }
    public boolean isEmpty() { return slots[0] == null && slots[1] == null; }

    public void swap() {
        swap(slots); swap(cooldownEnd); swap(cooldownTotal); swap(activeEnd);
    }

    private static void swap(Object[] a) { Object t = a[0]; a[0] = a[1]; a[1] = t; }
    private static void swap(long[] a) { long t = a[0]; a[0] = a[1]; a[1] = t; }

    /** True if an equipped effect grants this effect's passive (Apophis grants Fire, Emerald and Heart). */
    public boolean hasPassive(EffectType type) {
        for (Equipped e : slots) {
            if (e != null && e.type().enabled() && type.enabled() && e.type().grants().contains(type)) return true;
        }
        return false;
    }

    public boolean hasEquipped(EffectType type) {
        for (Equipped e : slots) {
            if (e != null && e.type() == type) return true;
        }
        return false;
    }

    public boolean isSparkActive(EffectType type) {
        Long until = sparkActiveUntil.get(type);
        return until != null && until > System.currentTimeMillis();
    }

    public long sparkActiveUntil(EffectType type) {
        Long until = sparkActiveUntil.get(type);
        return until == null ? 0 : until;
    }

    public void setSparkActive(EffectType type, long untilMillis) { sparkActiveUntil.put(type, untilMillis); }
    public void clearSparks() { sparkActiveUntil.clear(); }

    public long cooldownRemaining(int slot) { return Math.max(0, cooldownEnd[slot] - System.currentTimeMillis()); }
    public long cooldownTotal(int slot) { return cooldownTotal[slot]; }
    public void setCooldown(int slot, long totalMillis) {
        setCooldown(slot, totalMillis, 0);
    }

    /** Starts a cooldown that begins after {@code delayMillis} (the spark's active duration). */
    public void setCooldown(int slot, long totalMillis, long delayMillis) {
        cooldownTotal[slot] = totalMillis;
        cooldownEnd[slot] = System.currentTimeMillis() + Math.max(0, delayMillis) + totalMillis;
        readyPinged[slot] = false;
    }

    /** True exactly once when a slot's cooldown finishes. */
    public boolean consumeReady(int slot) {
        if (readyPinged[slot] || cooldownRemaining(slot) > 0) return false;
        readyPinged[slot] = true;
        readyFlashUntil[slot] = System.currentTimeMillis() + 960;
        return true;
    }

    /** Brief glow on the HUD icon right after a cooldown finishes. */
    public boolean isReadyFlash(int slot) {
        long left = readyFlashUntil[slot] - System.currentTimeMillis();
        return left > 0 && (left / 240) % 2 == 0;
    }
    public void resetCooldowns() {
        cooldownEnd[0] = cooldownEnd[1] = 0;
        activeEnd[0] = activeEnd[1] = 0;
    }
    public long cooldownEndRaw(int slot) { return cooldownEnd[slot]; }
    public void setCooldownRaw(int slot, long end, long total) { cooldownEnd[slot] = end; cooldownTotal[slot] = total; }

    public boolean isSlotActive(int slot) { return activeEnd[slot] > System.currentTimeMillis(); }
    public void setSlotActive(int slot, long untilMillis) { activeEnd[slot] = untilMillis; }

    public java.util.Set<UUID> trusted() { return trusted; }
    public boolean trusts(UUID other) { return trusted.contains(other); }

    public boolean offhandControls() { return offhandControls; }
    public void setOffhandControls(boolean v) { offhandControls = v; }

    public int takenHits() { return takenHits; }
    public void setTakenHits(int v) { takenHits = v; }
    public long oozingSlimesAt() { return oozingSlimesAt; }
    public void setOozingSlimesAt(long t) { oozingSlimesAt = t; }
    public float airFall() { return airFall; }
    public void setAirFall(float f) { airFall = f; }
    public int hits() { return hits; }
    public void setHits(int hits) { this.hits = hits; }
    public long hitProcAt() { return hitProcAt; }
    public void setHitProcAt(long t) { hitProcAt = t; }

    public int speedStacks() { return speedStacks; }
    public void setSpeedStacks(int s) { speedStacks = s; }
    public long lastHitAt() { return lastHitAt; }
    public void setLastHitAt(long t) { lastHitAt = t; }

    public long featherSlamUntil() { return featherSlamUntil; }
    public void setFeatherSlamUntil(long t) { featherSlamUntil = t; }

    public long lastSaturationBonus() { return lastSaturationBonus; }
    public void setLastSaturationBonus(long t) { lastSaturationBonus = t; }
}
