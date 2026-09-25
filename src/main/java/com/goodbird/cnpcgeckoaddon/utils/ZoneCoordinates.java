package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * A block picked in the world turned into the numbers a zone keeps, and those numbers turned
 * back into blocks.
 *
 * <p>Every spot and box a boss keeps is written one of two ways: as the block itself, or as its
 * offset from an anchor - the block the boss activated on, which on the client is the npc's own
 * block, the one every "use my position" button has always measured from. Both directions go
 * through here so a spot picked with the mouse, a spot typed in and a spot drawn in the world
 * agree to the block, and so that going from one to the other and back never loses one.</p>
 */
public final class ZoneCoordinates {

    private ZoneCoordinates() {
    }

    /**
     * The numbers a block is kept as.
     *
     * @param world  the block in the world
     * @param fixed  whether the zone keeps fixed blocks rather than offsets
     * @param anchor what an offset is measured from
     */
    public static BlockPos toStored(BlockPos world, boolean fixed, BlockPos anchor) {
        return fixed ? world.immutable() : fixedToOffset(world, anchor);
    }

    /** The block a set of kept numbers stands for. */
    public static BlockPos toWorld(int x, int y, int z, boolean fixed, BlockPos anchor) {
        BlockPos stored = new BlockPos(x, y, z);
        return fixed ? stored : offsetToFixed(stored, anchor);
    }

    /** An offset from the anchor as the block it lands on. */
    public static BlockPos offsetToFixed(BlockPos offset, BlockPos anchor) {
        return new BlockPos(anchor.getX() + offset.getX(), anchor.getY() + offset.getY(),
                anchor.getZ() + offset.getZ());
    }

    /** A block as its offset from the anchor. */
    public static BlockPos fixedToOffset(BlockPos fixed, BlockPos anchor) {
        return new BlockPos(fixed.getX() - anchor.getX(), fixed.getY() - anchor.getY(),
                fixed.getZ() - anchor.getZ());
    }

    /**
     * The box two kept corners cover in the world: either order, both corner blocks inside, the
     * way the runtime reads a platform's or a vent's box.
     */
    public static AABB box(int x1, int y1, int z1, int x2, int y2, int z2, boolean fixed, BlockPos anchor) {
        return blockBox(toWorld(x1, y1, z1, fixed, anchor), toWorld(x2, y2, z2, fixed, anchor));
    }

    /** Two blocks as the box that holds both of them and everything between. */
    public static AABB blockBox(BlockPos a, BlockPos b) {
        // The upper AABB bounds are exclusive, so one more takes in every block of the far corner.
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1.0D, Math.max(a.getY(), b.getY()) + 1.0D,
                Math.max(a.getZ(), b.getZ()) + 1.0D);
    }

    /**
     * A box cut to a dimension's build height, the way the runtime cuts every box it acts in.
     *
     * @return the box, or null when the build height leaves nothing of it
     */
    public static AABB clampToBuildHeight(AABB box, int minBuildHeight, int maxBuildHeight) {
        double minY = Math.max(box.minY, minBuildHeight);
        double maxY = Math.min(box.maxY, maxBuildHeight);
        if (minY >= maxY) {
            return null;
        }
        return new AABB(box.minX, minY, box.minZ, box.maxX, maxY, box.maxZ);
    }
}
