package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The box the aggro zone is judged by, checked without a world.
 *
 * <p>With the zone as the only way in, this box decides whether an archer is in the fight and
 * whether their arrow lands at all, so an edge that is off by one block reads as the zone
 * being broken: somebody standing on the corner block they typed in gets refused.</p>
 */
class BossAggroZoneBoundsTest {

    private static final int MIN_BUILD = -64;
    private static final int MAX_BUILD = 320;

    @Test
    @DisplayName("both corner blocks are inside, whichever corner was typed first")
    void cornerBlocksAreInsideInEitherOrder() {
        AABB forward = bounds(0, 64, 0, 10, 70, 10);
        AABB backward = bounds(10, 70, 10, 0, 64, 0);
        assertEquals(forward, backward, "swapping the corners must describe the same box");
        for (AABB zone : new AABB[]{forward, backward}) {
            assertTrue(zone.contains(new Vec3(0.0D, 64.0D, 0.0D)),
                    "the low face of the first corner block is inside");
            assertTrue(zone.contains(new Vec3(10.999D, 70.999D, 10.999D)),
                    "the far edge of the second corner block is still inside");
            assertTrue(zone.contains(new Vec3(5.5D, 67.0D, 5.5D)));
        }
    }

    @Test
    @DisplayName("corners mixed per axis make the same box")
    void cornersMixedPerAxis() {
        assertEquals(bounds(0, 64, 0, 10, 70, 10), bounds(10, 64, 0, 0, 70, 10));
        assertEquals(bounds(0, 64, 0, 10, 70, 10), bounds(0, 70, 10, 10, 64, 0));
    }

    @Test
    @DisplayName("a step past any face is outside")
    void pastEveryFaceIsOutside() {
        AABB zone = bounds(0, 64, 0, 10, 70, 10);
        assertFalse(zone.contains(new Vec3(11.0D, 65.0D, 5.0D)), "past the far x face");
        assertFalse(zone.contains(new Vec3(-0.001D, 65.0D, 5.0D)), "short of the near x face");
        assertFalse(zone.contains(new Vec3(5.0D, 71.0D, 5.0D)), "on top of the top block is above the box");
        assertFalse(zone.contains(new Vec3(5.0D, 63.999D, 5.0D)), "under the floor block");
        assertFalse(zone.contains(new Vec3(5.0D, 65.0D, 11.0D)), "past the far z face");
        assertFalse(zone.contains(new Vec3(5.0D, 65.0D, -0.001D)), "short of the near z face");
    }

    @Test
    @DisplayName("negative corners count blocks the way positions fall into them")
    void negativeCorners() {
        AABB zone = bounds(-10, 60, -10, -1, 62, -1);
        assertTrue(zone.contains(new Vec3(-0.5D, 61.0D, -0.5D)), "x = -0.5 stands on block -1, a corner");
        assertTrue(zone.contains(new Vec3(-10.0D, 60.0D, -10.0D)));
        assertFalse(zone.contains(new Vec3(0.0D, 61.0D, -5.0D)), "x = 0 is block 0, past the corner");
        assertFalse(zone.contains(new Vec3(-10.001D, 61.0D, -5.0D)));
    }

    @Test
    @DisplayName("a zone of one block holds exactly that block")
    void singleBlockZone() {
        AABB zone = bounds(3, 64, 3, 3, 64, 3);
        assertTrue(zone.contains(new Vec3(3.5D, 64.2D, 3.5D)));
        assertFalse(zone.contains(new Vec3(4.0D, 64.2D, 3.5D)));
        assertFalse(zone.contains(new Vec3(3.5D, 65.0D, 3.5D)));
    }

    @Test
    @DisplayName("Y is cut to the build height, and a zone wholly outside it is no zone")
    void buildHeightClampsY() {
        AABB tall = bounds(0, -100, 0, 4, 400, 4);
        assertNotNull(tall);
        assertEquals(MIN_BUILD, tall.minY, 1.0E-9D);
        assertEquals(MAX_BUILD, tall.maxY, 1.0E-9D, "the top build block is the last one inside");
        assertTrue(tall.contains(new Vec3(1.0D, 319.5D, 1.0D)));
        assertFalse(tall.contains(new Vec3(1.0D, 320.0D, 1.0D)));

        assertNull(bounds(0, 400, 0, 4, 500, 4), "a box above the world has nobody in it");
        assertNull(bounds(0, -200, 0, 4, -100, 4), "a box under the world has nobody in it");
    }

    private static AABB bounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        TeleportPathData data = new TeleportPathData();
        data.setAggroZoneEnabled(true);
        data.setAggroZoneCorner1(x1, y1, z1);
        data.setAggroZoneCorner2(x2, y2, z2);
        return BossTargetingRuntime.zoneBounds(data, MIN_BUILD, MAX_BUILD);
    }
}
