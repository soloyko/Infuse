package dev.blackclover.smp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The grimoire library: a long timber-framed hall. Coordinates are relative to the hall centre at floor
 * level (dy = 0 is the floor). The entrance faces south (+z); the altar dais is at the north end.
 * Pure data, so it can be unit-tested and previewed without a server.
 */
public final class LibraryBlueprint {

    /** Walls sit at |dx| == 6 and |dz| == HALL_HALF_LENGTH. */
    public static final int HALL_HALF_LENGTH = 15;
    public static final int HALF_WIDTH = 7;      // roof overhang
    public static final int HALF_LENGTH = 19;    // overhang + entrance porch
    public static final int ROOF_TOP = 18;       // bell tower spire
    private static final int RIDGE = 14;
    private static final int WALL = 6;
    private static final int[] BAYS = {-15, -10, -5, 0, 5, 10, 15};

    public record Block(int dx, int dy, int dz, String state) {
    }

    private static final List<Block> BLOCKS = build();

    private LibraryBlueprint() {
    }

    public static List<Block> blocks() {
        return BLOCKS;
    }

    /** Altar (lectern) position. */
    public static int[] altar() {
        return new int[]{0, 2, -12};
    }

    // ------------------------------------------------------------------------------------------ building

    private static final class Plan {
        final Map<String, Block> map = new LinkedHashMap<>();

        void put(int dx, int dy, int dz, String state) {
            map.put(dx + "," + dy + "," + dz, new Block(dx, dy, dz, "minecraft:" + state));
        }

        void clear(int dx, int dy, int dz) {
            map.remove(dx + "," + dy + "," + dz);
        }
    }

    private static boolean isBayPillar(int dz) {
        for (int b : BAYS) {
            if (b == dz) {
                return true;
            }
        }
        return false;
    }

    private static List<Block> build() {
        Plan p = new Plan();
        int L = HALL_HALF_LENGTH;

        // plinth and floor
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
            for (int dz = -(L + 1); dz <= L + 1; dz++) {
                boolean inside = Math.abs(dx) < WALL && Math.abs(dz) < L;
                boolean wall = Math.abs(dx) <= WALL && Math.abs(dz) <= L && !inside;
                p.put(dx, 0, dz, inside ? (Math.abs(dx) == 5 ? "polished_deepslate" : "dark_oak_planks")
                        : wall ? "stone_bricks" : "polished_andesite");
            }
        }

        // walls: log pillars, stone base, white plaster, tall windows, beam on top
        for (int dx = -WALL; dx <= WALL; dx++) {
            for (int dz = -L; dz <= L; dz++) {
                boolean longWall = Math.abs(dx) == WALL;
                boolean shortWall = Math.abs(dz) == L;
                if (!longWall && !shortWall) {
                    continue;
                }
                boolean pillar = (longWall && isBayPillar(dz)) || (shortWall && (Math.abs(dx) == 2 || Math.abs(dx) == WALL));
                for (int dy = 1; dy <= 6; dy++) {
                    String state;
                    if (pillar) {
                        state = "stripped_dark_oak_log[axis=y]";
                    } else if (dy == 6) {
                        state = "stripped_dark_oak_log[axis=" + (longWall ? "z" : "x") + "]";
                    } else if (dy <= 2) {
                        state = dy == 1 && (dx + dz) % 3 == 0 ? "mossy_stone_bricks" : "stone_bricks";
                    } else {
                        state = "calcite";
                    }
                    p.put(dx, dy, dz, state);
                }
                // wall plate under the roof
                p.put(dx, 7, dz, "dark_oak_planks");
            }
        }
        // windows: two panes wide in the middle of every bay on the long walls
        for (int bay = 0; bay < BAYS.length - 1; bay++) {
            for (int dz : new int[]{BAYS[bay] + 2, BAYS[bay] + 3}) {
                for (int side : new int[]{-WALL, WALL}) {
                    for (int dy = 3; dy <= 5; dy++) {
                        p.put(side, dy, dz, dy == 5 ? "light_gray_stained_glass" : "glass");
                    }
                }
            }
        }

        // entrance: an arched doorway in the south wall
        for (int dy = 1; dy <= 4; dy++) {
            p.clear(0, dy, L);
        }
        for (int dy = 1; dy <= 3; dy++) {
            p.clear(-1, dy, L);
            p.clear(1, dy, L);
        }
        p.put(-1, 4, L, "stone_brick_stairs[facing=west,half=top]");
        p.put(1, 4, L, "stone_brick_stairs[facing=east,half=top]");
        p.put(0, 5, L, "chiseled_stone_bricks");

        // clover window behind the altar (north wall)
        String[][] clover = {
            {null, "lime_stained_glass", null},
            {"lime_stained_glass", "yellow_stained_glass", "lime_stained_glass"},
            {null, "green_stained_glass", null},
            {null, "green_stained_glass", null},
        };
        for (int row = 0; row < clover.length; row++) {
            for (int col = 0; col < 3; col++) {
                if (clover[row][col] != null) {
                    p.put(col - 1, 6 - row, -L, clover[row][col]);
                }
            }
        }

        // gables
        for (int k = 1; k <= 6; k++) {
            for (int dx = -(WALL - k); dx <= WALL - k; dx++) {
                p.put(dx, 7 + k, -L, "dark_oak_planks");
                p.put(dx, 7 + k, L, "dark_oak_planks");
            }
        }
        // clover window in the front gable
        p.put(0, 12, L, "lime_stained_glass");
        p.put(-1, 11, L, "green_stained_glass");
        p.put(1, 11, L, "green_stained_glass");
        p.put(0, 11, L, "yellow_stained_glass");
        p.put(0, 10, L, "green_stained_glass");

        // roof: deepslate tile gable with a ridge
        for (int k = 0; k <= 6; k++) {
            int x = HALF_WIDTH - k;
            for (int dz = -(L + 1); dz <= L + 1; dz++) {
                p.put(x, 7 + k, dz, "deepslate_tile_stairs[facing=west,half=bottom]");
                p.put(-x, 7 + k, dz, "deepslate_tile_stairs[facing=east,half=bottom]");
            }
        }
        for (int dz = -(L + 1); dz <= L + 1; dz++) {
            p.put(0, RIDGE, dz, "deepslate_tile_slab[type=bottom]");
        }

        // bell tower on the ridge
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.put(dx, RIDGE, dz, "dark_oak_planks");
                boolean corner = dx != 0 && dz != 0;
                if (corner) {
                    p.put(dx, RIDGE + 1, dz, "stripped_dark_oak_log[axis=y]");
                    p.put(dx, RIDGE + 2, dz, "stripped_dark_oak_log[axis=y]");
                }
                p.put(dx, RIDGE + 3, dz, dx == 0 && dz == 0 ? "deepslate_tiles" : "deepslate_tile_slab[type=bottom]");
            }
        }
        p.put(0, RIDGE + 2, 0, "bell[attachment=ceiling,facing=south]");
        p.put(0, ROOF_TOP, 0, "lightning_rod[facing=up]");

        // exposed trusses with hanging lanterns
        for (int dz : new int[]{-10, -5, 0, 5, 10}) {
            for (int dx = -5; dx <= 5; dx++) {
                p.put(dx, 7, dz, "stripped_dark_oak_log[axis=x]");
            }
            for (int dy = 8; dy <= 11; dy++) {
                p.put(0, dy, dz, "stripped_dark_oak_log[axis=y]");
            }
            p.put(-3, 6, dz, "lantern[hanging=true]");
            p.put(3, 6, dz, "lantern[hanging=true]");
        }

        // chandeliers between the trusses
        for (int dz : new int[]{-7, 3}) {
            for (int dy = 10; dy <= 13; dy++) {
                p.put(0, dy, dz, "iron_chain[axis=y]");
            }
            p.put(0, 9, dz, "dark_oak_fence");
            for (int[] arm : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                p.put(arm[0], 9, dz + arm[1], "dark_oak_fence");
                p.put(arm[0], 8, dz + arm[1], "lantern[hanging=true]");
            }
        }

        // red carpet runner from the door to the dais
        for (int dz = -9; dz <= L - 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                p.put(dx, 1, dz, "red_carpet");
            }
        }

        // bookshelf stacks in every bay, both sides, with slab caps; shelves line the walls between
        for (int dz : new int[]{-8, -3, 2, 7, 12}) {
            for (int side : new int[]{-1, 1}) {
                for (int dx = 3; dx <= 5; dx++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        p.put(side * dx, dy, dz, "bookshelf");
                    }
                    p.put(side * dx, 4, dz, "dark_oak_slab[type=bottom]");
                }
                p.put(side * 3, 5, dz, "candle[candles=2,lit=true]");
            }
        }
        for (int dz = -9; dz <= L - 2; dz++) {
            if (Math.floorMod(dz + 8, 5) == 0) {
                continue;
            }
            for (int side : new int[]{-5, 5}) {
                p.put(side, 1, dz, "bookshelf");
                p.put(side, 2, dz, "bookshelf");
            }
        }

        // the dais at the north end
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -(L - 1); dz <= -11; dz++) {
                p.put(dx, 1, dz, "polished_deepslate");
            }
        }
        for (int dx = -5; dx <= 5; dx++) {
            p.put(dx, 1, -10, "polished_deepslate_stairs[facing=north,half=bottom]");
        }
        int[] a = altar();
        p.put(a[0], a[1], a[2], "lectern[facing=south]");
        p.put(-2, 2, a[2], "candle[candles=3,lit=true]");
        p.put(2, 2, a[2], "candle[candles=3,lit=true]");
        for (int side : new int[]{-4, 4}) {
            p.put(side, 2, -13, "dark_oak_fence");
            p.put(side, 3, -13, "soul_lantern[hanging=false]");
        }
        for (int dx = -5; dx <= 5; dx++) {
            if (Math.abs(dx) <= 1) {
                continue;
            }
            for (int dy = 2; dy <= 4; dy++) {
                p.put(dx, dy, -(L - 1), "bookshelf");
            }
        }

        // covered entrance porch: log pillars, a small gable roof, a lantern and steps
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = L + 1; dz <= L + 3; dz++) {
                p.put(dx, 0, dz, Math.abs(dx) <= 1 ? "polished_andesite" : "stone_bricks");
            }
            p.put(dx, 0, L + 4, "stone_brick_stairs[facing=north,half=bottom]");
        }
        for (int side : new int[]{-3, 3}) {
            for (int dy = 1; dy <= 4; dy++) {
                p.put(side, dy, L + 3, "stripped_dark_oak_log[axis=y]");
            }
            for (int dz = L + 1; dz <= L + 3; dz++) {
                p.put(side, 5, dz, "stripped_dark_oak_log[axis=z]");
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            p.put(dx, 5, L + 3, "stripped_dark_oak_log[axis=x]");
            for (int dz = L + 1; dz <= L + 2; dz++) {
                p.put(dx, 5, dz, "dark_oak_planks");
            }
        }
        p.put(0, 4, L + 2, "lantern[hanging=true]");
        for (int k = 0; k <= 3; k++) {
            int x = 4 - k;
            for (int dz = L + 1; dz <= L + 4; dz++) {
                p.put(x, 6 + k, dz, "deepslate_tile_stairs[facing=west,half=bottom]");
                p.put(-x, 6 + k, dz, "deepslate_tile_stairs[facing=east,half=bottom]");
            }
            for (int dx = -(x - 1); dx <= x - 1; dx++) {
                p.put(dx, 6 + k, L + 3, "dark_oak_planks");
            }
        }
        for (int dz = L + 1; dz <= L + 4; dz++) {
            p.put(0, 10, dz, "deepslate_tile_slab[type=bottom]");
        }

        return Collections.unmodifiableList(new ArrayList<>(p.map.values()));
    }
}
