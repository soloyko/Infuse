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

class LibraryBlueprintTest {

    private static final List<LibraryBlueprint.Block> BLOCKS = LibraryBlueprint.blocks();
    private static final Map<String, String> AT = new HashMap<>();

    static {
        for (LibraryBlueprint.Block b : BLOCKS) {
            AT.put(b.dx() + "," + b.dy() + "," + b.dz(), b.state());
        }
    }

    private static String at(int dx, int dy, int dz) {
        return AT.get(dx + "," + dy + "," + dz);
    }

    @Test
    void altarIsALecternOnTheDais() {
        int[] a = LibraryBlueprint.altar();
        assertEquals("minecraft:lectern[facing=south]", at(a[0], a[1], a[2]));
        assertTrue(at(a[0], a[1] - 1, a[2]).startsWith("minecraft:polished_deepslate"), at(a[0], a[1] - 1, a[2]));
        assertTrue(a[2] < -8, "altar should sit at the far (north) end");
    }

    @Test
    void doorwayIsOpen() {
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertNull(at(dx, dy, LibraryBlueprint.HALL_HALF_LENGTH), "door blocked at " + dx + "," + dy);
            }
        }
    }

    @Test
    void longHallThatFitsOneChunkWide() {
        assertTrue(LibraryBlueprint.HALL_HALF_LENGTH * 2 + 1 >= 30);
        assertTrue(LibraryBlueprint.HALF_WIDTH * 2 + 1 <= 16);
        for (LibraryBlueprint.Block b : BLOCKS) {
            assertTrue(Math.abs(b.dx()) <= LibraryBlueprint.HALF_WIDTH, "too wide: " + b);
            assertTrue(Math.abs(b.dz()) <= LibraryBlueprint.HALF_LENGTH, "too long: " + b);
            assertTrue(b.dy() >= 0 && b.dy() <= LibraryBlueprint.ROOF_TOP, "too tall: " + b);
        }
    }

    @Test
    void roofIsSymmetric() {
        for (LibraryBlueprint.Block b : BLOCKS) {
            if (b.state().startsWith("minecraft:deepslate_tile_stairs") && b.state().contains("facing=west")) {
                String mirror = at(-b.dx(), b.dy(), b.dz());
                assertNotNull(mirror, "no mirror for " + b);
                assertTrue(mirror.contains("facing=east"), "mirror of " + b + " is " + mirror);
            }
        }
    }

    @Test
    void floorCoversTheWholeInterior() {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -(LibraryBlueprint.HALL_HALF_LENGTH - 1); dz <= LibraryBlueprint.HALL_HALF_LENGTH - 1; dz++) {
                assertNotNull(at(dx, 0, dz), "hole in floor at " + dx + "," + dz);
            }
        }
    }

    @Test
    void everyBlockIsARealMaterial() throws Exception {
        for (String id : BLOCKS.stream().map(b -> b.state().replaceAll("\\[.*", "")).collect(Collectors.toSet())) {
            assertNotNull(Material.matchMaterial(id), "unknown block " + id);
        }
        // dump for the isometric preview renderer
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/library-blueprint.txt"),
                BLOCKS.stream().map(b -> b.dx() + " " + b.dy() + " " + b.dz() + " " + b.state()).collect(Collectors.joining("\n")));
    }
}
