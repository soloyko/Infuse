package dev.blackclover.smp.magic;

import dev.blackclover.smp.grimoire.GrimoireType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellRegistryTest {

    private static Spell spell(String id, int unlock) {
        return new Spell(id, id, unlock, Spell.Role.ATTACK, 10, 2, 5, "test spell", ctx -> true);
    }

    private static Spell[] four(String prefix) {
        return new Spell[]{spell(prefix + "_a", 1), spell(prefix + "_b", 5), spell(prefix + "_c", 10), spell(prefix + "_d", 20)};
    }

    @Test
    void needsExactlyFourSpells() {
        SpellRegistry r = new SpellRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> r.register(GrimoireType.FIRE, spell("a", 1), spell("b", 5), spell("c", 10)));
    }

    @Test
    void unlockLevelsMustBeStandard() {
        SpellRegistry r = new SpellRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> r.register(GrimoireType.FIRE, spell("a", 1), spell("b", 4), spell("c", 10), spell("d", 20)));
    }

    @Test
    void idsAreUnique() {
        SpellRegistry r = new SpellRegistry();
        r.register(GrimoireType.FIRE, four("x"));
        assertThrows(IllegalArgumentException.class, () -> r.register(GrimoireType.WATER, four("x")));
    }

    @Test
    void unlockedByLevel() {
        SpellRegistry r = new SpellRegistry();
        r.register(GrimoireType.FIRE, four("fire"));
        assertEquals(1, r.unlocked(GrimoireType.FIRE, 1).size());
        assertEquals(2, r.unlocked(GrimoireType.FIRE, 9).size());
        assertEquals(3, r.unlocked(GrimoireType.FIRE, 10).size());
        assertEquals(4, r.unlocked(GrimoireType.FIRE, 20).size());
        assertEquals("fire_b", r.spells(GrimoireType.FIRE).get(1).id());
        assertTrue(r.spells(GrimoireType.WATER).isEmpty());
    }

    @Test
    void configOverridesAndWritesDefaults() {
        SpellRegistry r = new SpellRegistry();
        r.register(GrimoireType.FIRE, four("fire"));
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("spells.fire_a.cost", 50.0);
        cfg.set("spells.fire_a.cooldown", 0.5);
        r.applyConfig(cfg);
        Spell a = r.spells(GrimoireType.FIRE).get(0);
        assertEquals(50.0, a.cost(), 1e-9);
        assertEquals(0.5, a.cooldownSeconds(), 1e-9);
        assertEquals(5.0, a.damage(), 1e-9);
        assertEquals(10.0, cfg.getDouble("spells.fire_b.cost"), 1e-9);
        assertEquals(5.0, cfg.getDouble("spells.fire_d.damage"), 1e-9);
    }
}
