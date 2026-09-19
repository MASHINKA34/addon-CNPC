package com.goodbird.cnpcgeckoaddon.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of the platform a rift is fought on, worked out without a world.
 *
 * <p>Every position is relative to the platform's centre block - the floor block right under
 * the spot the taken players land on - so the same shape can be laid at any slot of the rift
 * dimension, and checked by a test that has no level to lay it in.</p>
 *
 * <p>Two layers of floor, a wall standing on the floor's outer ring, lights set into the floor
 * on a grid and, when asked for, a roof over the whole disc. The centre block is always plain
 * floor: whether it holds the floor block is how a slot tells that its platform is already
 * there.</p>
 */
public final class BossRiftPlatform {

    /** How far above the wall's top the roof sits. */
    public static final int ROOF_GAP = 8;

    /** What a block of the platform is made of: which of the three configured blocks it takes. */
    public enum Part {
        /** The walking surface, level with the centre block. */
        FLOOR,
        /** The layer right under the floor, so a broken floor block is not a hole into the void. */
        SUBFLOOR,
        /** A light set into the floor in place of a floor block. */
        LIGHT,
        /** The wall standing on the floor's outer ring. */
        WALL,
        /** The lid over the whole disc. */
        ROOF
    }

    /** One block of the platform, relative to the centre block. */
    public record Placement(int dx, int dy, int dz, Part part) {
    }

    private BossRiftPlatform() {
    }

    /**
     * Every block of a platform, floor first and roof last: the order they are set in, so the
     * floor is there before anything is stood on it.
     *
     * @param radius       the disc's radius in blocks; a cell counts when its centre is inside it
     * @param wallHeight   how many blocks the wall rises above the floor; none for no wall
     * @param lightSpacing the grid the lights are set on; none for no lights
     * @param roof         whether a roof is laid {@link #ROOF_GAP} blocks over the wall's top
     */
    public static List<Placement> placements(int radius, int wallHeight, int lightSpacing, boolean roof) {
        int r = Math.max(0, radius);
        List<Placement> placements = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!inDisc(dx, dz, r)) {
                    continue;
                }
                boolean light = isLight(dx, dz, r, lightSpacing);
                placements.add(new Placement(dx, 0, dz, light ? Part.LIGHT : Part.FLOOR));
                placements.add(new Placement(dx, -1, dz, Part.SUBFLOOR));
            }
        }
        if (wallHeight > 0) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!isRim(dx, dz, r)) {
                        continue;
                    }
                    for (int dy = 1; dy <= wallHeight; dy++) {
                        placements.add(new Placement(dx, dy, dz, Part.WALL));
                    }
                }
            }
        }
        if (roof) {
            int roofY = roofHeight(wallHeight);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (inDisc(dx, dz, r)) {
                        placements.add(new Placement(dx, roofY, dz, Part.ROOF));
                    }
                }
            }
        }
        return placements;
    }

    /** Whether a cell belongs to the disc: its centre within the radius of the centre block's. */
    public static boolean inDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * Whether a cell of the disc is on its outer ring: one of its four neighbours is outside the
     * disc. The wall stands here, so it is always on floor and always closes the ring.
     */
    public static boolean isRim(int dx, int dz, int radius) {
        return inDisc(dx, dz, radius)
                && (!inDisc(dx + 1, dz, radius) || !inDisc(dx - 1, dz, radius)
                || !inDisc(dx, dz + 1, radius) || !inDisc(dx, dz - 1, radius));
    }

    /**
     * Whether a floor cell holds a light: on the grid, off the rim the wall covers, and never
     * the centre block, which has to stay the floor block the slot is recognised by.
     */
    public static boolean isLight(int dx, int dz, int radius, int spacing) {
        if (spacing <= 0 || dx == 0 && dz == 0 || isRim(dx, dz, radius)) {
            return false;
        }
        return Math.floorMod(dx, spacing) == 0 && Math.floorMod(dz, spacing) == 0;
    }

    /** The roof's height over the floor. */
    public static int roofHeight(int wallHeight) {
        return Math.max(0, wallHeight) + ROOF_GAP;
    }
}
