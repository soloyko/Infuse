package dev.blackclover.smp.magic;

/** A spell: identity, balance numbers (overridable in config) and its action. */
public record Spell(String id, String name, int unlockLevel, double cost, double cooldownSeconds, double damage,
                    SpellAction action) {

    public Spell withStats(double cost, double cooldownSeconds, double damage) {
        return new Spell(id, name, unlockLevel, cost, cooldownSeconds, damage, action);
    }
}
