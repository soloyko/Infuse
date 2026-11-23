package dev.blackclover.smp.magic;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.spells.AllSpells;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AllSpellsTest {

    static final Set<GrimoireType> BATCH_ONE = EnumSet.of(GrimoireType.FIRE, GrimoireType.WATER, GrimoireType.WIND,
            GrimoireType.LIGHTNING, GrimoireType.EARTH, GrimoireType.PLANT, GrimoireType.LIGHT);

    @Test
    void batchOneTypesHaveFourSpells() {
        SpellRegistry r = new SpellRegistry();
        AllSpells.register(r);
        for (GrimoireType type : BATCH_ONE) {
            List<Spell> spells = r.spells(type);
            assertEquals(4, spells.size(), type + " spell count");
            String prefix = type.name().toLowerCase(Locale.ROOT) + "_";
            for (Spell s : spells) {
                assertTrue(s.id().startsWith(prefix), s.id() + " should start with " + prefix);
                assertTrue(s.cooldownSeconds() > 0, s.id() + " cooldown");
                assertNotNull(s.action());
            }
            assertTrue(spells.get(3).cost() >= 80, type + " final spell should be expensive");
        }
    }

    @Test
    void allFifteenGrimoiresRegistered() {
        SpellRegistry r = new SpellRegistry();
        AllSpells.register(r);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (GrimoireType type : GrimoireType.values()) {
            List<Spell> spells = r.spells(type);
            assertEquals(4, spells.size(), type + " spell count");
            int[] levels = {1, 5, 10, 20};
            for (int i = 0; i < 4; i++) {
                assertEquals(levels[i], spells.get(i).unlockLevel());
                assertTrue(ids.add(spells.get(i).id()), "duplicate id " + spells.get(i).id());
            }
        }
        assertEquals(60, ids.size());
    }
}
