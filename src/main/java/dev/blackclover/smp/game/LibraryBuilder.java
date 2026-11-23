package dev.blackclover.smp.game;

import dev.blackclover.smp.core.LibraryPlacer;
import dev.blackclover.smp.grimoire.GrimoireType;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Lantern;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Builds a small library with a lectern altar, centred in one chunk. */
public final class LibraryBuilder {

    /** Half-width of the cleared footprint (11x11). Fits inside one chunk when centred at +8. */
    private static final int HALF = 5;
    private static final int CLEAR_HEIGHT = 9;
    private static final int MAX_FOUNDATION = 8;

    /**
     * @param force build even over liquid / bad terrain (used after repeated rejections)
     * @return the library, or empty when the spot is unsuitable and {@code force} is false
     */
    public CompletableFuture<Optional<Library>> build(World world, GrimoireType type, LibraryPlacer.Point point, boolean force) {
        int cx = Math.floorDiv(point.x(), 16);
        int cz = Math.floorDiv(point.z(), 16);
        return world.getChunkAtAsync(cx, cz, true)
                .thenApply(chunk -> buildLoaded(world, type, cx * 16 + 8, cz * 16 + 8, force));
    }

    private Optional<Library> buildLoaded(World world, GrimoireType type, int x, int z, boolean force) {
        int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!force && !suitable(world, x, z, floorY)) {
            return Optional.empty();
        }
        floorY = Math.max(floorY, world.getMinHeight() + 1);
        floorY = Math.min(floorY, world.getMaxHeight() - CLEAR_HEIGHT - 2);

        // Clear the space above and lay foundations below.
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    set(world, x + dx, floorY + dy, z + dz, Material.AIR);
                }
                for (int dy = 1; dy <= MAX_FOUNDATION; dy++) {
                    Block below = world.getBlockAt(x + dx, floorY - dy, z + dz);
                    if (below.getType().isSolid()) {
                        break;
                    }
                    below.setType(Material.STONE_BRICKS, false);
                }
                boolean inside = Math.abs(dx) <= 3 && Math.abs(dz) <= 3;
                boolean centre = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                Material floor = centre ? Material.CHISELED_STONE_BRICKS : inside ? Material.DARK_OAK_PLANKS : Material.STONE_BRICKS;
                set(world, x + dx, floorY, z + dz, floor);
            }
        }

        // Walls: 9x9 ring of bookshelves with log corners, a plank band and glass windows.
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean ring = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                if (!ring) {
                    continue;
                }
                boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 4;
                boolean middle = dx == 0 || dz == 0;
                for (int dy = 1; dy <= 4; dy++) {
                    Material m;
                    if (corner) {
                        m = Material.STRIPPED_DARK_OAK_LOG;
                    } else if (dy == 4) {
                        m = Material.DARK_OAK_PLANKS;
                    } else if (middle && dy >= 2) {
                        m = Material.GLASS;
                    } else {
                        m = Material.BOOKSHELF;
                    }
                    set(world, x + dx, floorY + dy, z + dz, m);
                }
            }
        }
        // Doorway on the south side.
        set(world, x, floorY + 1, z + 4, Material.AIR);
        set(world, x, floorY + 2, z + 4, Material.AIR);
        set(world, x, floorY + 3, z + 4, Material.AIR);

        // Roof: flat plank layer with a raised centre.
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                set(world, x + dx, floorY + 5, z + dz, Material.DEEPSLATE_TILES);
                if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) {
                    set(world, x + dx, floorY + 6, z + dz, Material.DARK_OAK_PLANKS);
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    set(world, x + dx, floorY + 7, z + dz, Material.DEEPSLATE_TILES);
                }
            }
        }

        // Decoration: corner shelves and hanging lanterns.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                set(world, x + 3 * sx, floorY + 1, z + 3 * sz, Material.BOOKSHELF);
                set(world, x + 3 * sx, floorY + 2, z + 3 * sz, Material.BOOKSHELF);
                Block lantern = world.getBlockAt(x + 2 * sx, floorY + 4, z + 2 * sz);
                Lantern data = (Lantern) Material.LANTERN.createBlockData();
                data.setHanging(true);
                lantern.setBlockData(data, false);
            }
        }

        // The altar.
        set(world, x, floorY + 1, z, Material.LECTERN);

        return Optional.of(new Library(type, world.getName(), x, floorY + 1, z,
                x - HALF, floorY - MAX_FOUNDATION, z - HALF, x + HALF, floorY + CLEAR_HEIGHT, z + HALF));
    }

    private static boolean suitable(World world, int x, int z, int floorY) {
        if (floorY < world.getMinHeight() + 2 || floorY > world.getMaxHeight() - CLEAR_HEIGHT - 2) {
            return false;
        }
        int[][] samples = {{0, 0}, {-HALF, -HALF}, {HALF, -HALF}, {-HALF, HALF}, {HALF, HALF}};
        for (int[] s : samples) {
            int y = world.getHighestBlockYAt(x + s[0], z + s[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block top = world.getBlockAt(x + s[0], y, z + s[1]);
            if (top.isLiquid() || top.getType() == Material.ICE || top.getType() == Material.PACKED_ICE
                    || Math.abs(y - floorY) > 6) {
                return false;
            }
        }
        return true;
    }

    private static void set(World world, int x, int y, int z, Material m) {
        world.getBlockAt(x, y, z).setType(m, false);
    }
}
