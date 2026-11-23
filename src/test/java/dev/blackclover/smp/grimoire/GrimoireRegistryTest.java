package dev.blackclover.smp.grimoire;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GrimoireRegistryTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final BlockLoc loc = new BlockLoc("world", 1, 64, 2);

    @Test
    void initialStatuses() {
        GrimoireRegistry r = new GrimoireRegistry();
        assertEquals(GrimoireStatus.LIBRARY, r.get(GrimoireType.FIRE).status());
        assertEquals(GrimoireStatus.ADMIN_POOL, r.get(GrimoireType.ANTI_MAGIC).status());
        assertEquals(GrimoireStatus.ADMIN_POOL, r.get(GrimoireType.FIVE_LEAF).status());
        assertEquals(13, GrimoireType.craftables().size());
        assertEquals(15, r.all().size());
    }

    @Test
    void craftThenHeld() {
        GrimoireRegistry r = new GrimoireRegistry();
        UUID instance = r.craft(GrimoireType.FIRE, a);
        assertEquals(GrimoireStatus.HELD, r.get(GrimoireType.FIRE).status());
        assertEquals(a, r.get(GrimoireType.FIRE).holder());
        assertEquals(Optional.of(GrimoireType.FIRE), r.heldBy(a));
        assertTrue(r.isCurrent(GrimoireType.FIRE, instance));
        assertThrows(IllegalStateException.class, () -> r.craft(GrimoireType.FIRE, b));
        assertThrows(IllegalStateException.class, () -> r.craft(GrimoireType.WATER, a));
    }

    @Test
    void dropAndPickup() {
        GrimoireRegistry r = new GrimoireRegistry();
        r.craft(GrimoireType.FIRE, a);
        UUID entity = UUID.randomUUID();
        r.markGround(GrimoireType.FIRE, entity, loc);
        GrimoireState s = r.get(GrimoireType.FIRE);
        assertEquals(GrimoireStatus.GROUND, s.status());
        assertEquals(entity, s.itemEntity());
        assertEquals(loc, s.ground());
        assertTrue(r.heldBy(a).isEmpty());
        r.markHeld(GrimoireType.FIRE, b);
        assertEquals(b, r.get(GrimoireType.FIRE).holder());
        assertNull(r.get(GrimoireType.FIRE).itemEntity());
    }

    @Test
    void staleInstanceIsInvalidAfterReturnHome() {
        GrimoireRegistry r = new GrimoireRegistry();
        UUID instance = r.craft(GrimoireType.FIRE, a);
        UUID fresh = r.returnHome(GrimoireType.FIRE);
        assertFalse(r.isCurrent(GrimoireType.FIRE, instance));
        assertTrue(r.isCurrent(GrimoireType.FIRE, fresh));
        assertEquals(GrimoireStatus.LIBRARY, r.get(GrimoireType.FIRE).status());
        assertNull(r.get(GrimoireType.FIRE).holder());
    }

    @Test
    void returnHomeFromGroundGoesToLibraryOrPool() {
        GrimoireRegistry r = new GrimoireRegistry();
        r.giveFromPool(GrimoireType.FIVE_LEAF, a);
        r.markGround(GrimoireType.FIVE_LEAF, UUID.randomUUID(), loc);
        r.returnHome(GrimoireType.FIVE_LEAF);
        assertEquals(GrimoireStatus.ADMIN_POOL, r.get(GrimoireType.FIVE_LEAF).status());
        assertNull(r.get(GrimoireType.FIVE_LEAF).ground());

        r.craft(GrimoireType.FIRE, b);
        r.markGround(GrimoireType.FIRE, UUID.randomUUID(), loc);
        r.returnHome(GrimoireType.FIRE);
        assertEquals(GrimoireStatus.LIBRARY, r.get(GrimoireType.FIRE).status());
    }

    @Test
    void giveFromPoolRequiresPool() {
        GrimoireRegistry r = new GrimoireRegistry();
        r.giveFromPool(GrimoireType.ANTI_MAGIC, a);
        assertEquals(Optional.of(GrimoireType.ANTI_MAGIC), r.heldBy(a));
        assertThrows(IllegalStateException.class, () -> r.giveFromPool(GrimoireType.ANTI_MAGIC, b));
        assertThrows(IllegalStateException.class, () -> r.giveFromPool(GrimoireType.FIRE, b));
    }

    @Test
    void cannotPickUpSecondGrimoire() {
        GrimoireRegistry r = new GrimoireRegistry();
        r.craft(GrimoireType.FIRE, a);
        r.craft(GrimoireType.WATER, b);
        r.markGround(GrimoireType.WATER, UUID.randomUUID(), loc);
        assertThrows(IllegalStateException.class, () -> r.markHeld(GrimoireType.WATER, a));
    }
}
