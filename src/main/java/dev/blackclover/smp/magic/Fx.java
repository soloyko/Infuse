package dev.blackclover.smp.magic;

/** Spell toolkit. Flags let listeners tell spell damage and permission probes apart from melee. */
public final class Fx {

    /** True while a PvP-permission probe event is being fired. */
    public static boolean probing;
    /** True while spell damage is being applied. */
    public static boolean spellDamage;
}
