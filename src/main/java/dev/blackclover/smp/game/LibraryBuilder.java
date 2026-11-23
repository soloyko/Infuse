package dev.blackclover.smp.game;

import dev.blackclover.smp.core.LibraryPlacer;
import dev.blackclover.smp.grimoire.GrimoireType;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Builds a {@link TowerBlueprint} wizard tower in the world. A tower fits inside a single chunk. */
public final class LibraryBuilder {

    private static final int MAX_FOUNDATION = 12;
    private static final int MAX_SLOPE = 6;
    private final Map<String, BlockData> dataCache = new HashMap<>();

    /**
     * @param force build even over liquid / rough terrain (used after repeated rejections)
     * @return the library, or empty when the spot is unsuitable and {@code force} is false
     */
    public CompletableFuture<Optional<Library>> build(World world, GrimoireType type, LibraryPlacer.Point point, boolean force) {
        int x = Math.floorDiv(point.x(), 16) * 16 + 8;
        int z = Math.floorDiv(point.z(), 16) * 16 + 8;
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int cx = Math.floorDiv(x - TowerBlueprint.HALF_WIDTH, 16); cx <= Math.floorDiv(x + TowerBlueprint.HALF_WIDTH, 16); cx++) {
            for (int cz = Math.floorDiv(z - TowerBlueprint.HALF_WIDTH, 16); cz <= Math.floorDiv(z + TowerBlueprint.HALF_WIDTH, 16); cz++) {
                loads.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenApply(v -> buildLoaded(world, type, x, z, force));
    }

    private BlockData data(String state) {
        return dataCache.computeIfAbsent(state, Bukkit::createBlockData);
    }

    private Optional<Library> buildLoaded(World world, GrimoireType type, int x, int z, boolean force) {
        int floorY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (!force && !suitable(world, x, z, floorY)) {
            return Optional.empty();
        }
        int top = TowerBlueprint.ROOF_TOP + 2;
        floorY = Math.max(floorY, world.getMinHeight() + 1);
        floorY = Math.min(floorY, world.getMaxHeight() - top - 1);
        int hw = TowerBlueprint.HALF_WIDTH;
        int hl = TowerBlueprint.HALF_WIDTH;

        // clear the site
        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hl; dz <= hl; dz++) {
                for (int dy = 1; dy <= top; dy++) {
                    world.getBlockAt(x + dx, floorY + dy, z + dz).setType(Material.AIR, false);
                }
            }
        }
        // foundations under every floor block, down to solid ground
        for (TowerBlueprint.Block b : TowerBlueprint.blocks()) {
            if (b.dy() != 0) {
                continue;
            }
            for (int dy = 1; dy <= MAX_FOUNDATION; dy++) {
                Block below = world.getBlockAt(x + b.dx(), floorY - dy, z + b.dz());
                if (below.getType().isSolid() && !below.isLiquid()) {
                    break;
                }
                below.setType(dy == 1 ? Material.STONE_BRICKS : Material.COBBLESTONE, false);
            }
        }
        // the tower itself
        for (TowerBlueprint.Block b : TowerBlueprint.blocks()) {
            world.getBlockAt(x + b.dx(), floorY + b.dy(), z + b.dz()).setBlockData(data(b.state()), false);
        }

        int[] a = TowerBlueprint.altar();
        return Optional.of(new Library(type, world.getName(), x + a[0], floorY + a[1], z + a[2],
                x - hw, floorY - MAX_FOUNDATION, z - hl, x + hw, floorY + top, z + hl));
    }

    /** Flat-ish dry land across the whole footprint. */
    private static boolean suitable(World world, int x, int z, int floorY) {
        if (floorY < world.getMinHeight() + 2 || floorY > world.getMaxHeight() - TowerBlueprint.ROOF_TOP - 4) {
            return false;
        }
        for (int dx = -TowerBlueprint.HALF_WIDTH; dx <= TowerBlueprint.HALF_WIDTH; dx += 7) {
            for (int dz = -TowerBlueprint.HALF_WIDTH; dz <= TowerBlueprint.HALF_WIDTH; dz += 5) {
                int y = world.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Block topBlock = world.getBlockAt(x + dx, y, z + dz);
                if (topBlock.isLiquid() || topBlock.getType() == Material.ICE || topBlock.getType() == Material.PACKED_ICE
                        || Math.abs(y - floorY) > MAX_SLOPE) {
                    return false;
                }
            }
        }
        return true;
    }
}
