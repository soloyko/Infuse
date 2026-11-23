package dev.blackclover.smp.grimoire;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Authoritative state machine for the 15 unique grimoires. Pure logic, no server calls. */
public final class GrimoireRegistry {

    private final Map<GrimoireType, GrimoireState> states = new EnumMap<>(GrimoireType.class);

    public GrimoireRegistry() {
        for (GrimoireType type : GrimoireType.values()) {
            states.put(type, home(type, UUID.randomUUID()));
        }
    }

    private static GrimoireState home(GrimoireType type, UUID instance) {
        GrimoireStatus status = type.craftable() ? GrimoireStatus.LIBRARY : GrimoireStatus.ADMIN_POOL;
        return new GrimoireState(type, status, instance, null, null, null);
    }

    public GrimoireState get(GrimoireType type) {
        return states.get(type);
    }

    public Collection<GrimoireState> all() {
        return Collections.unmodifiableCollection(states.values());
    }

    public Optional<GrimoireType> heldBy(UUID player) {
        for (GrimoireState s : states.values()) {
            if (s.status() == GrimoireStatus.HELD && player.equals(s.holder())) {
                return Optional.of(s.type());
            }
        }
        return Optional.empty();
    }

    public UUID craft(GrimoireType type, UUID player) {
        GrimoireState s = states.get(type);
        if (!type.craftable() || s.status() != GrimoireStatus.LIBRARY) {
            throw new IllegalStateException(type + " is not waiting at its library");
        }
        return takeFromHome(s, player);
    }

    public UUID giveFromPool(GrimoireType type, UUID player) {
        GrimoireState s = states.get(type);
        if (s.status() != GrimoireStatus.ADMIN_POOL) {
            throw new IllegalStateException(type + " is not in the admin pool");
        }
        return takeFromHome(s, player);
    }

    private UUID takeFromHome(GrimoireState s, UUID player) {
        requireEmptyHanded(player);
        UUID instance = UUID.randomUUID();
        states.put(s.type(), new GrimoireState(s.type(), GrimoireStatus.HELD, instance, player, null, null));
        return instance;
    }

    private void requireEmptyHanded(UUID player) {
        if (heldBy(player).isPresent()) {
            throw new IllegalStateException(player + " already carries a grimoire");
        }
    }

    public void markHeld(GrimoireType type, UUID player) {
        GrimoireState s = states.get(type);
        if (player.equals(s.holder()) && s.status() == GrimoireStatus.HELD) {
            return;
        }
        requireEmptyHanded(player);
        states.put(type, new GrimoireState(type, GrimoireStatus.HELD, s.instance(), player, null, null));
    }

    public void markGround(GrimoireType type, UUID itemEntity, BlockLoc loc) {
        GrimoireState s = states.get(type);
        states.put(type, new GrimoireState(type, GrimoireStatus.GROUND, s.instance(), null, itemEntity, loc));
    }

    /** Sends the grimoire back to its library or the admin pool with a fresh instance id. */
    public UUID returnHome(GrimoireType type) {
        UUID instance = UUID.randomUUID();
        states.put(type, home(type, instance));
        return instance;
    }

    public boolean isCurrent(GrimoireType type, UUID instance) {
        return instance != null && instance.equals(states.get(type).instance());
    }

    /** Used when loading saved data. */
    public void restore(GrimoireState state) {
        states.put(state.type(), state);
    }
}
