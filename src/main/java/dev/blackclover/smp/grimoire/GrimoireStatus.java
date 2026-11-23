package dev.blackclover.smp.grimoire;

public enum GrimoireStatus {
    /** Waiting at its library altar (craftable grimoires). */
    LIBRARY,
    /** Available for admins to hand out (Anti-Magic, Five-Leaf). */
    ADMIN_POOL,
    /** Carried by a player (online or offline). */
    HELD,
    /** Lying on the ground as an item entity. */
    GROUND
}
