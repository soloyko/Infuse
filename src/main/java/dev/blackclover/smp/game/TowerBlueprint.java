package dev.blackclover.smp.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The grimoire tower: a round, weathered stone wizard tower with four library floors, a spiral staircase
 * around a glowing central pillar, a balcony ring, an altar room at the very top and a tall spire.
 * Coordinates are relative to the tower's centre at ground level (dy = 0). The door faces south (+z).
 * Pure data, so it can be unit-tested and previewed without a server.
 */
public final class TowerBlueprint {

    public static final int HALF_WIDTH = 7;
    public static final int ROOF_TOP = 56;
    /** Floor heights of the library levels; the altar room floor is TOP_FLOOR. */
    private static final int[] FLOORS = {0, 8, 16, 24};
    private static final int TOP_FLOOR = 32;
    private static final int ROOM_CEILING = 41;
    private static final double INNER = 4.6;   // interior radius
    private static final double OUTER = 5.6;   // outer wall radius
    private static final double[] SPIRAL_RADII = {3.0, 3.9};
    private static final double STEP_DEGREES = 22.5;
    private static final double START_DEGREES = -90;   // first and last steps at the north side

    public record Block(int dx, int dy, int dz, String state) {
    }

    private static final Set<String> SPIRAL = new HashSet<>();
    private static final List<Block> BLOCKS = build();

    private TowerBlueprint() {
    }

    public static List<Block> blocks() {
        return BLOCKS;
    }

    /** Altar (lectern) position: centre of the top room. */
    public static int[] altar() {
        return new int[]{0, TOP_FLOOR + 1, 0};
    }

    public static boolean isSpiral(int dx, int dy, int dz) {
        return SPIRAL.contains(dx + "," + dy + "," + dz);
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

        boolean has(int dx, int dy, int dz) {
            return map.containsKey(dx + "," + dy + "," + dz);
        }
    }

    private static double r(int dx, int dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean inPillar(int dx, int dz) {
        return Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
    }

    /** Deterministic weathering so the stone looks aged, not noisy. */
    private static String stone(int dx, int dy, int dz) {
        int h = Math.floorMod(dx * 73856093 ^ dy * 19349663 ^ dz * 83492791, 100);
        if (h < 9) {
            return "mossy_stone_bricks";
        }
        if (h < 17) {
            return "cracked_stone_bricks";
        }
        return "stone_bricks";
    }

    private static String facingToward(double x, double z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? "east" : "west";
        }
        return z > 0 ? "south" : "north";
    }

    private static int[] cellAt(double degrees, double radius) {
        double a = Math.toRadians(degrees);
        return new int[]{(int) Math.round(Math.cos(a) * radius), (int) Math.round(Math.sin(a) * radius)};
    }

    private static List<Block> build() {
        Plan p = new Plan();

        // ---- plinth, foundations ring and floors
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
            for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
                double rr = r(dx, dz);
                if (rr <= 6.9) {
                    p.put(dx, 0, dz, rr > OUTER ? "polished_andesite" : rr > INNER ? "stone_bricks"
                            : (Math.floorMod(dx + dz, 2) == 0 ? "spruce_planks" : "dark_oak_planks"));
                }
            }
        }
        for (int floor : FLOORS) {
            if (floor == 0) {
                continue;
            }
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double rr = r(dx, dz);
                    if (rr <= INNER && !inPillar(dx, dz)) {
                        p.put(dx, floor, dz, rr > 3.5 ? "spruce_planks" : "dark_oak_planks");
                    }
                }
            }
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (r(dx, dz) <= INNER) {
                    p.put(dx, TOP_FLOOR, dz, r(dx, dz) <= 1.6 ? "chiseled_deepslate" : "polished_deepslate");
                    p.put(dx, ROOM_CEILING, dz, "dark_oak_planks");
                }
            }
        }

        // ---- outer wall with dark bands at each floor, weathered stone between
        for (int dy = 1; dy < ROOM_CEILING; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    double rr = r(dx, dz);
                    if (rr > INNER && rr <= OUTER) {
                        boolean band = dy % 8 == 0;
                        p.put(dx, dy, dz, band ? "polished_blackstone_bricks" : stone(dx, dy, dz));
                    }
                }
            }
        }

        // ---- buttresses at the four diagonals
        for (int deg = 45; deg < 360; deg += 90) {
            for (int dy = 1; dy <= 9; dy++) {
                double reach = dy <= 5 ? 6.7 : 6.2;
                int[] c = cellAt(deg, reach);
                p.put(c[0], dy, c[1], dy == 9 || (dy == 5 && reach > 6.5)
                        ? "stone_brick_stairs[facing=" + facingToward(-c[0], -c[1]) + ",half=bottom]" : stone(c[0], dy, c[1]));
            }
        }

        // ---- central pillar with glowing green cores
        for (int dy = 1; dy < TOP_FLOOR; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean core = dx == 0 && dz == 0;
                    boolean glow = dy % 8 == 4 && !(dx == 0 && dz == 0) && (dx == 0 || dz == 0);
                    p.put(dx, dy, dz, glow ? "verdant_froglight[axis=y]" : core ? "polished_blackstone"
                            : (Math.abs(dx) == 1 && Math.abs(dz) == 1 ? "stripped_dark_oak_log[axis=y]" : "polished_blackstone_bricks"));
                }
            }
        }

        // ---- windows: tall stained glass on every library floor, rotated each level
        for (int level = 0; level < FLOORS.length; level++) {
            int base = FLOORS[level];
            for (int k = 0; k < 4; k++) {
                double deg = k * 90 + (level % 2 == 0 ? 45 : 0);
                if (level == 0 && Math.abs(deg - 90) < 1) {
                    continue; // the door is here
                }
                int[] c = cellAt(deg, 5.1);
                for (int dy = base + 3; dy <= base + 5; dy++) {
                    p.put(c[0], dy, c[1], dy == base + 5 ? "lime_stained_glass" : "green_stained_glass");
                }
            }
        }

        // ---- bookshelves lining every library floor
        for (int base : FLOORS) {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double rr = r(dx, dz);
                    if (rr > 3.9 && rr <= INNER) {
                        for (int dy = base + 1; dy <= base + 3; dy++) {
                            p.put(dx, dy, dz, (dy == base + 2 && Math.floorMod(dx * 3 + dz, 5) == 0) ? "chiseled_bookshelf[facing="
                                    + facingToward(-dx, -dz) + "]" : "bookshelf");
                        }
                    }
                }
            }
            // hanging lanterns under the next floor
            int ceiling = base + 8;
            for (int deg = 0; deg < 360; deg += 90) {
                int[] c = cellAt(deg + 45, 2.4);
                if (ceiling < TOP_FLOOR + 1) {
                    p.put(c[0], ceiling - 1, c[1], "lantern[hanging=true]");
                }
            }
        }

        // ---- entrance: arched door in the south wall, steps out to the path
        for (int dz = 3; dz <= 6; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 1; dy <= 3; dy++) {
                    p.clear(dx, dy, dz);
                }
            }
        }
        p.clear(0, 4, 5);
        p.put(-1, 4, 5, "stone_brick_stairs[facing=west,half=top]");
        p.put(1, 4, 5, "stone_brick_stairs[facing=east,half=top]");
        p.put(0, 5, 5, "chiseled_stone_bricks");
        for (int dx = -2; dx <= 2; dx++) {
            p.put(dx, 0, 7, "stone_brick_stairs[facing=north,half=bottom]");
        }
        for (int dz = 3; dz <= 6; dz++) {
            p.put(0, 1, dz, "red_carpet");
        }
        for (int side : new int[]{-2, 2}) {
            p.put(side, 1, 6, "dark_oak_fence");
            p.put(side, 2, 6, "lantern[hanging=false]");
        }

        // ---- spiral staircase from the ground floor to the altar room
        for (int h = 1; h <= TOP_FLOOR; h++) {
            double deg = START_DEGREES + h * STEP_DEGREES;
            for (double radius : SPIRAL_RADII) {
                int[] c = cellAt(deg, radius);
                if (inPillar(c[0], c[1])) {
                    continue;
                }
                double tangent = Math.toRadians(deg + 90);
                String facing = facingToward(Math.cos(tangent), Math.sin(tangent));
                // carve headroom (floors, shelves, lanterns) above the step
                for (int up = 1; up <= 3; up++) {
                    p.clear(c[0], h + up, c[1]);
                    SPIRAL.remove(c[0] + "," + (h + up) + "," + c[1]);
                }
                p.put(c[0], h, c[1], h == TOP_FLOOR ? "polished_deepslate" : "spruce_stairs[facing=" + facing + ",half=bottom]");
                SPIRAL.add(c[0] + "," + h + "," + c[1]);
            }
        }

        // ---- altar room: tall windows, doors to the balcony, candles round the altar
        for (int k = 0; k < 8; k++) {
            double deg = k * 45;
            int[] c = cellAt(deg, 5.1);
            boolean door = k % 2 == 1;
            if (door) {
                for (int dy = TOP_FLOOR + 1; dy <= TOP_FLOOR + 3; dy++) {
                    p.clear(c[0], dy, c[1]);
                }
                p.put(c[0], TOP_FLOOR + 4, c[1], "chiseled_stone_bricks");
            } else {
                for (int dy = TOP_FLOOR + 3; dy <= TOP_FLOOR + 6; dy++) {
                    p.put(c[0], dy, c[1], dy == TOP_FLOOR + 6 ? "lime_stained_glass" : "glass");
                }
            }
        }
        int[] a = altar();
        p.put(a[0], a[1], a[2], "lectern[facing=south]");
        for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, -2}}) {
            p.put(d[0], TOP_FLOOR + 1, d[1], "candle[candles=4,lit=true]");
        }
        for (int deg = 0; deg < 360; deg += 90) {
            int[] c = cellAt(deg + 45, 3.0);
            p.put(c[0], ROOM_CEILING - 1, c[1], "soul_lantern[hanging=true]");
        }
        p.put(0, ROOM_CEILING - 1, 0, "iron_chain[axis=y]");
        p.put(0, ROOM_CEILING - 2, 0, "lantern[hanging=true]");

        // ---- balcony ring with a low parapet, corbels underneath and lanterns at the diagonals
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
            for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
                double rr = r(dx, dz);
                if (rr > OUTER && rr <= 6.9) {
                    p.put(dx, TOP_FLOOR, dz, "polished_blackstone_brick_slab[type=top]");
                    if (rr > 6.2) {
                        p.put(dx, TOP_FLOOR + 1, dz, "stone_brick_slab[type=bottom]");
                    }
                    if (rr <= 6.3) {
                        p.put(dx, TOP_FLOOR - 1, dz, "polished_blackstone_brick_stairs[facing=" + facingToward(-dx, -dz) + ",half=top]");
                    }
                }
            }
        }
        for (int deg = 0; deg < 360; deg += 90) {
            int[] c = cellAt(deg, 6.5);
            p.put(c[0], TOP_FLOOR + 1, c[1], "polished_blackstone_wall");
            p.put(c[0], TOP_FLOOR + 2, c[1], "lantern[hanging=false]");
        }

        // ---- crown and spire
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double rr = r(dx, dz);
                if (rr > INNER && rr <= OUTER) {
                    p.put(dx, ROOM_CEILING, dz, "polished_blackstone_bricks");
                }
            }
        }
        int spireBase = ROOM_CEILING + 1;
        int spireHeight = ROOF_TOP - 2 - spireBase;
        for (int dy = spireBase; dy <= ROOF_TOP - 2; dy++) {
            double t = (dy - spireBase) / (double) spireHeight;
            double rad = 6.9 * (1 - t);
            for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
                for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
                    double rr = r(dx, dz);
                    if (rr <= rad && rr > rad - 1.3) {
                        p.put(dx, dy, dz, Math.floorMod(dx * 7 + dz * 3 + dy, 9) == 0 ? "cracked_deepslate_tiles" : "deepslate_tiles");
                    }
                }
            }
            if (rad < 0.8) {
                p.put(0, dy, 0, "deepslate_tiles");
            }
        }
        // spire eaves: a ring of stairs flaring out at the base
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
            for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
                double rr = r(dx, dz);
                if (rr > 6.0 && rr <= 7.0) {
                    p.put(dx, spireBase, dz, "deepslate_tile_stairs[facing=" + facingToward(-dx, -dz) + ",half=bottom]");
                }
            }
        }
        p.put(0, ROOF_TOP - 1, 0, "gold_block");
        p.put(0, ROOF_TOP, 0, "lightning_rod[facing=up]");

        return Collections.unmodifiableList(new ArrayList<>(p.map.values()));
    }
}
