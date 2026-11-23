package dev.blackclover.smp.magic;

/** What a spell does. Return true if it was cast (mana and cooldown are then charged). */
@FunctionalInterface
public interface SpellAction {
    boolean cast(SpellContext ctx);
}
