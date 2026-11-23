package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.magic.SpellRegistry;

/** Registers every grimoire's spells. */
public final class AllSpells {

    private AllSpells() {
    }

    public static void register(SpellRegistry r) {
        FireSpells.register(r);
        WaterSpells.register(r);
        WindSpells.register(r);
        LightningSpells.register(r);
        EarthSpells.register(r);
        PlantSpells.register(r);
        LightSpells.register(r);
    }
}
