package dev.blackclover.smp.game;

import dev.blackclover.smp.grimoire.GrimoireType;

/** A built library: its altar block and the protected bounding box. */
public record Library(GrimoireType type, String world, int altarX, int altarY, int altarZ,
                      int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public boolean contains(String w, int x, int y, int z) {
        return world.equals(w) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean isAltar(String w, int x, int y, int z) {
        return world.equals(w) && x == altarX && y == altarY && z == altarZ;
    }
}
