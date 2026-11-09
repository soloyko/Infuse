package me.infuse.util;

import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived status flags applied to any entity (frozen, cursed, no-fall...). */
public final class Status {
    private final Map<UUID, Long> frozen = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cursed = new ConcurrentHashMap<>();
    private final Map<UUID, Long> jumpDebuff = new ConcurrentHashMap<>();
    private final Map<UUID, Long> noFall = new ConcurrentHashMap<>();
    private final Map<UUID, Long> silenced = new ConcurrentHashMap<>();

    public void silence(Entity e, long millis) { extend(silenced, e, millis); }
    public long silencedRemaining(Entity e) {
        Long until = silenced.get(e.getUniqueId());
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void freeze(Entity e, long millis) { extend(frozen, e, millis); }
    public boolean isFrozen(Entity e) { return active(frozen, e); }

    public void curse(Entity e, long millis) { extend(cursed, e, millis); }
    public boolean isCursed(Entity e) { return active(cursed, e); }

    public void jumpDebuff(Entity e, long millis) { extend(jumpDebuff, e, millis); }
    public Map<UUID, Long> jumpDebuffs() { return jumpDebuff; }

    public void noFall(Entity e, long millis) { extend(noFall, e, millis); }
    public boolean hasNoFall(Entity e) { return active(noFall, e); }

    private static void extend(Map<UUID, Long> map, Entity e, long millis) {
        long until = System.currentTimeMillis() + millis;
        map.merge(e.getUniqueId(), until, Math::max);
    }

    private static boolean active(Map<UUID, Long> map, Entity e) {
        Long until = map.get(e.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            map.remove(e.getUniqueId());
            return false;
        }
        return true;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        frozen.values().removeIf(t -> t <= now);
        cursed.values().removeIf(t -> t <= now);
        noFall.values().removeIf(t -> t <= now);
        silenced.values().removeIf(t -> t <= now);
    }
}
