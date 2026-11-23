package dev.blackclover.smp.magic;

import dev.blackclover.smp.grimoire.GrimoireType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Which spell each player has selected. Resets when they switch grimoire; clamps after a level-down. */
public final class SpellSelection {

    private record Choice(GrimoireType type, int index) {
    }

    private final Map<UUID, Choice> choices = new HashMap<>();

    public int index(UUID player, GrimoireType type, int unlocked) {
        Choice c = choices.get(player);
        if (c == null || c.type() != type || unlocked <= 0) {
            return 0;
        }
        return Math.min(c.index(), unlocked - 1);
    }

    public int next(UUID player, GrimoireType type, int unlocked) {
        int next = unlocked <= 0 ? 0 : (index(player, type, unlocked) + 1) % unlocked;
        choices.put(player, new Choice(type, next));
        return next;
    }
}
