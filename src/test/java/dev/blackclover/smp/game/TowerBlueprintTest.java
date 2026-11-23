package dev.blackclover.smp.game;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TowerBlueprintTest {

    private static final List<TowerBlueprint.Block> BLOCKS = TowerBlueprint.blocks();
    private static final Map<String, String> AT = new HashMap<>();

    static {
        for (TowerBlueprint.Block b : BLOCKS) {
            AT.put(b.dx() + "," + b.dy() + "," + b.dz(), b.state());
        }
    }

    private static String at(int dx, int dy, int dz) {
        return AT.get(dx + "," + dy + "," + dz);
    }

    @Test
    void altarIsALecternAtTheTop() {
        int[] a = TowerBlueprint.altar();
        assertEquals("minecraft:lectern[facing=south]", at(a[0], a[1], a[2]));
        assertTrue(a[1] >= 30, "the altar should be at the top of a tall tower");
        assertNotNull(at(a[0], a[1] - 1, a[2]), "altar needs something under it");
    }

    @Test
    void tallTowerInsideOneChunk() {
        assertTrue(TowerBlueprint.ROOF_TOP >= 50, "tower should be tall");
        assertTrue(TowerBlueprint.HALF_WIDTH * 2 + 1 <= 16);
        for (TowerBlueprint.Block b : BLOCKS) {
            assertTrue(Math.abs(b.dx()) <= TowerBlueprint.HALF_WIDTH && Math.abs(b.dz()) <= TowerBlueprint.HALF_WIDTH, "too wide: " + b);
            assertTrue(b.dy() >= 0 && b.dy() <= TowerBlueprint.ROOF_TOP, "too tall: " + b);
        }
    }

    @Test
    void doorwayIsOpen() {
        for (int dy = 1; dy <= 3; dy++) {
            for (int dz = 4; dz <= 5; dz++) {
                String b = at(0, dy, dz);
                assertTrue(b == null || b.endsWith("_carpet"), "doorway blocked at dy " + dy + ", dz " + dz + ": " + b);
            }
        }
    }

    @Test
    void everyStairStepHasHeadroom() {
        int steps = 0;
        for (TowerBlueprint.Block b : BLOCKS) {
            if (!b.state().contains("_stairs") || !TowerBlueprint.isSpiral(b.dx(), b.dy(), b.dz())) {
                continue;
            }
            steps++;
            assertNull(at(b.dx(), b.dy() + 1, b.dz()), "no headroom above step " + b);
            assertNull(at(b.dx(), b.dy() + 2, b.dz()), "no headroom above step " + b);
        }
        assertTrue(steps >= 30, "spiral too short: " + steps);
    }

    @Test
    void spiralReachesTheTopFloor() {
        int top = TowerBlueprint.altar()[1] - 1;
        boolean reaches = BLOCKS.stream().anyMatch(b -> b.dy() >= top - 1 && TowerBlueprint.isSpiral(b.dx(), b.dy(), b.dz()));
        assertTrue(reaches);
    }

    @Test
    void floorBesideTheAltarToStandOn() {
        int[] a = TowerBlueprint.altar();
        for (int dz = 2; dz <= 3; dz++) {
            assertNotNull(at(a[0], a[1] - 1, a[2] + dz), "no floor at dz " + dz);
            assertNull(at(a[0], a[1], a[2] + dz), "blocked at dz " + dz);
            assertNull(at(a[0], a[1] + 1, a[2] + dz), "no headroom at dz " + dz);
        }
    }

    @Test
    void everyBlockIsARealMaterial() throws Exception {
        for (String id : BLOCKS.stream().map(b -> b.state().replaceAll("\\[.*", "")).collect(Collectors.toSet())) {
            assertNotNull(Material.matchMaterial(id), "unknown block " + id);
        }
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/tower-blueprint.txt"),
                BLOCKS.stream().map(b -> b.dx() + " " + b.dy() + " " + b.dz() + " " + b.state()).collect(Collectors.joining("\n")));
    }
}
