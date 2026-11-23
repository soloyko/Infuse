package dev.blackclover.smp.grimoire;

import java.util.UUID;

/**
 * Where a grimoire is. {@code instance} changes every time the grimoire returns home, so any
 * older item copy can be recognised as stale and deleted.
 */
public record GrimoireState(GrimoireType type, GrimoireStatus status, UUID instance,
                            UUID holder, UUID itemEntity, BlockLoc ground) {
}
