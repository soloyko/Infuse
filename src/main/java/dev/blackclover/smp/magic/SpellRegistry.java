package dev.blackclover.smp.magic;

import dev.blackclover.smp.core.SpellUnlocks;
import dev.blackclover.smp.grimoire.GrimoireType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The four spells of every grimoire, with config overrides applied. */
public final class SpellRegistry {

    private final Map<GrimoireType, List<Spell>> base = new EnumMap<>(GrimoireType.class);
    private final Map<GrimoireType, List<Spell>> effective = new EnumMap<>(GrimoireType.class);
    private final Set<String> ids = new HashSet<>();

    public void register(GrimoireType type, Spell... spells) {
        if (spells.length != SpellUnlocks.slots()) {
            throw new IllegalArgumentException(type + " needs exactly " + SpellUnlocks.slots() + " spells");
        }
        for (int i = 0; i < spells.length; i++) {
            if (spells[i].unlockLevel() != SpellUnlocks.levelForSlot(i)) {
                throw new IllegalArgumentException(spells[i].id() + " must unlock at level " + SpellUnlocks.levelForSlot(i));
            }
            if (ids.contains(spells[i].id())) {
                throw new IllegalArgumentException("duplicate spell id " + spells[i].id());
            }
        }
        for (Spell s : spells) {
            ids.add(s.id());
        }
        base.put(type, List.of(spells));
        effective.put(type, List.of(spells));
    }

    public List<Spell> spells(GrimoireType type) {
        return effective.getOrDefault(type, List.of());
    }

    public List<Spell> unlocked(GrimoireType type, int level) {
        List<Spell> all = spells(type);
        return all.subList(0, Math.min(all.size(), SpellUnlocks.unlockedCount(level)));
    }

    /** Reads {@code spells.<id>.cost/cooldown/damage}; missing values are written back with defaults. */
    public void applyConfig(ConfigurationSection root) {
        for (Map.Entry<GrimoireType, List<Spell>> e : base.entrySet()) {
            List<Spell> out = new ArrayList<>();
            for (Spell s : e.getValue()) {
                String p = "spells." + s.id() + ".";
                out.add(s.withStats(read(root, p + "cost", s.cost()), read(root, p + "cooldown", s.cooldownSeconds()),
                        read(root, p + "damage", s.damage())));
            }
            effective.put(e.getKey(), List.copyOf(out));
        }
    }

    private static double read(ConfigurationSection root, String path, double def) {
        if (!root.isSet(path)) {
            root.set(path, def);
            return def;
        }
        return root.getDouble(path, def);
    }
}
