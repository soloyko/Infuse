package dev.blackclover.smp.magic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Right-click can fire twice (block + air packets); allow one action per window. */
public final class CastDebounce {

    private final long millis;
    private final Map<UUID, Long> last = new HashMap<>();

    public CastDebounce(long millis) {
        this.millis = millis;
    }

    public boolean tryAcquire(UUID player, long now) {
        Long prev = last.get(player);
        if (prev != null && now - prev < millis) {
            return false;
        }
        last.put(player, now);
        return true;
    }
}
