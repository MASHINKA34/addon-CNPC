package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block picked in the world against the numbers a zone keeps for it: an offset from the anchor
 * or the block itself, there and back. An error of one here puts a summon point, a platform or a
 * totem a block away from where the builder clicked, which nobody notices until the fight.
 */
class ZoneCoordinateTest {

    private static final List<BlockPos> ANCHORS = List.of(
            BlockPos.ZERO,
            new BlockPos(100, 64, -200),
            new BlockPos(-12345, -40, 67890),
            new BlockPos(29_000_000, 300, -29_000_000));

    private static final List<BlockPos> SPOTS = List.of(
            BlockPos.ZERO,
            new BlockPos(3, 1, -7),
            new BlockPos(-100, 70, 250),
            new BlockPos(-29_000_000, -64, 29_000_000));

    @Test
    @DisplayName("a fixed zone keeps the block itself, whatever the anchor")
    void fixedKeepsTheBlock() {
        for (BlockPos anchor : ANCHORS) {
            for (BlockPos spot : SPOTS) {
                assertEquals(spot, ZoneCoordinates.toStored(spot, true, anchor));
                assertEquals(spot, ZoneCoordinates.toWorld(spot.getX(), spot.getY(), spot.getZ(), true, anchor));
            }
        }
    }

    @Test
    @DisplayName("an offset zone keeps the distance from the anchor")
    void offsetKeepsTheDistance() {
        BlockPos anchor = new BlockPos(100, 64, -200);
        assertEquals(new BlockPos(5, -1, 10), ZoneCoordinates.toStored(new BlockPos(105, 63, -190), false, anchor));
        assertEquals(new BlockPos(105, 63, -190), ZoneCoordinates.toWorld(5, -1, 10, false, anchor));
        assertEquals(BlockPos.ZERO, ZoneCoordinates.toStored(anchor, false, anchor),
                "the anchor's own block is no offset at all");
    }

    @Test
    @DisplayName("offset to fixed and back, and fixed to offset and back, loses nothing")
    void roundTripsAreLossless() {
        for (BlockPos anchor : ANCHORS) {
            for (BlockPos spot : SPOTS) {
                BlockPos offset = ZoneCoordinates.fixedToOffset(spot, anchor);
                assertEquals(spot, ZoneCoordinates.offsetToFixed(offset, anchor), "fixed -> offset -> fixed");
                BlockPos fixed = ZoneCoordinates.offsetToFixed(spot, anchor);
                assertEquals(spot, ZoneCoordinates.fixedToOffset(fixed, anchor), "offset -> fixed -> offset");
                for (boolean fixedMode : new boolean[]{true, false}) {
                    BlockPos stored = ZoneCoordinates.toStored(spot, fixedMode, anchor);
                    assertEquals(spot, ZoneCoordinates.toWorld(stored.getX(), stored.getY(), stored.getZ(),
                            fixedMode, anchor), "stored and read back in the same mode");
                }
            }
        }
    }

    @Test
    @DisplayName("a zone switched between the modes through the anchor still covers the same blocks")
    void switchingModesKeepsTheBlocks() {
        BlockPos anchor = new BlockPos(-40, 70, 15);
        BlockPos corner1 = new BlockPos(-50, 64, 10);
        BlockPos corner2 = new BlockPos(-30, 72, 25);
        AABB asFixed = ZoneCoordinates.box(corner1.getX(), corner1.getY(), corner1.getZ(),
                corner2.getX(), corner2.getY(), corner2.getZ(), true, anchor);
        BlockPos offset1 = ZoneCoordinates.fixedToOffset(corner1, anchor);
        BlockPos offset2 = ZoneCoordinates.fixedToOffset(corner2, anchor);
        AABB asOffset = ZoneCoordinates.box(offset1.getX(), offset1.getY(), offset1.getZ(),
                offset2.getX(), offset2.getY(), offset2.getZ(), false, anchor);
        assertEquals(asFixed, asOffset);
    }

    @Test
    @DisplayName("a box holds both corner blocks whole, in either order")
    void boxIsInclusive() {
        AABB box = ZoneCoordinates.blockBox(new BlockPos(10, 70, 10), new BlockPos(0, 64, 0));
        assertEquals(new AABB(0, 64, 0, 11, 71, 11), box);
        assertEquals(box, ZoneCoordinates.blockBox(new BlockPos(0, 64, 0), new BlockPos(10, 70, 10)));
        assertTrue(box.contains(new Vec3(10.999D, 70.999D, 10.999D)), "the far corner block is inside");
        assertFalse(box.contains(new Vec3(11.0D, 65.0D, 5.0D)), "one step past it is not");
        assertEquals(new AABB(3, 3, 3, 4, 4, 4), ZoneCoordinates.blockBox(new BlockPos(3, 3, 3), new BlockPos(3, 3, 3)),
                "one block is a one-block box");
    }

    @Test
    @DisplayName("a box is cut to the build height, and one wholly outside it is nothing")
    void boxIsCutToTheBuildHeight() {
        AABB tall = ZoneCoordinates.blockBox(new BlockPos(0, -100, 0), new BlockPos(1, 400, 1));
        assertEquals(new AABB(0, -64, 0, 2, 320, 2), ZoneCoordinates.clampToBuildHeight(tall, -64, 320));
        AABB above = ZoneCoordinates.blockBox(new BlockPos(0, 330, 0), new BlockPos(1, 340, 1));
        assertNull(ZoneCoordinates.clampToBuildHeight(above, -64, 320));
    }
}
