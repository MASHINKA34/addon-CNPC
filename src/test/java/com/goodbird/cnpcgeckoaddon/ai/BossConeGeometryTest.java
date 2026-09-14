package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sector a cone strike is judged by, checked without a world.
 *
 * <p>Getting it slightly wrong throws nothing: an angle compared as a full width where half was
 * meant doubles the fan, a length compared against a squared distance reaches a mile, and a
 * height band on the wrong side of the boss spares a whole balcony. The only symptom is a strike
 * that lands on somebody the warning never covered, or misses somebody it did.</p>
 */
class BossConeGeometryTest {

    private static final Vec3 ORIGIN = new Vec3(10.0D, 64.0D, -5.0D);
    /** Looking east. */
    private static final Vec3 EAST = new Vec3(1.0D, 0.0D, 0.0D);
    /** How tall the body judged below stands: a player's. */
    private static final double BODY = 1.8D;

    /**
     * Whether a body {@code distance} out and {@code degrees} off the east axis, its feet
     * {@code rise} above the origin, stands in the cone.
     */
    private static boolean inEastCone(double angle, double length, double height,
                                      double distance, double degrees, double rise) {
        double radians = Math.toRadians(degrees);
        double feet = ORIGIN.y + rise;
        return BossConeRuntime.inSector(ORIGIN, EAST, angle, length, height,
                ORIGIN.x + Math.cos(radians) * distance, ORIGIN.z + Math.sin(radians) * distance, feet, feet + BODY);
    }

    @Test
    @DisplayName("sixty degrees opens thirty to either side of the axis, and the edge is inside")
    void theAngleIsSplitAcrossTheAxis() {
        assertTrue(inEastCone(60, 10, 3, 5.0D, 0.0D, 0.0D), "straight down the middle");
        assertTrue(inEastCone(60, 10, 3, 5.0D, 29.0D, 0.0D));
        assertTrue(inEastCone(60, 10, 3, 5.0D, -29.0D, 0.0D), "the other side of the axis is the same");
        assertTrue(inEastCone(60, 10, 3, 5.0D, 30.0D, 0.0D), "exactly on the edge of the angle is in the cone");
        assertTrue(inEastCone(60, 10, 3, 5.0D, -30.0D, 0.0D));
        assertFalse(inEastCone(60, 10, 3, 5.0D, 31.0D, 0.0D), "just past the edge of the angle is clear");
        assertFalse(inEastCone(60, 10, 3, 5.0D, -45.0D, 0.0D), "the full sixty is not half the sector");
        assertFalse(inEastCone(60, 10, 3, 5.0D, 180.0D, 0.0D), "behind the boss is never in front of it");
    }

    @Test
    @DisplayName("the length is measured flat, and its end is inside")
    void theLengthIsFlat() {
        assertTrue(inEastCone(60, 10, 3, 9.9D, 0.0D, 0.0D));
        assertTrue(inEastCone(60, 10, 3, 10.0D, 0.0D, 0.0D), "exactly at the length is in the cone");
        assertFalse(inEastCone(60, 10, 3, 10.1D, 0.0D, 0.0D), "just past the length is clear");
        assertFalse(inEastCone(60, 10, 3, 11.0D, 0.0D, 0.0D), "eleven blocks out of a ten block cone is clear");
        // At the edge of the angle the reach is still the full length, not the axis' projection of it.
        assertTrue(inEastCone(60, 10, 3, 10.0D, 30.0D, 0.0D), "the corner of the fan is inside it");
        // A body three up and ten out is ten away on the floor plan, so height does not eat into the length.
        assertTrue(inEastCone(60, 10, 3, 10.0D, 0.0D, 3.0D));
    }

    @Test
    @DisplayName("the height band reaches as far above the boss as below it, and a body reaching into it is in it")
    void theHeightBandIsBothWays() {
        assertTrue(inEastCone(60, 10, 3, 5.0D, 0.0D, 3.0D), "feet exactly the height above are in the cone");
        assertTrue(inEastCone(60, 10, 3, 5.0D, 0.0D, -3.0D), "feet exactly the height below are in the cone");
        assertFalse(inEastCone(60, 10, 3, 5.0D, 0.0D, 3.1D), "a balcony above the band is clear");
        assertTrue(inEastCone(60, 10, 3, 5.0D, 0.0D, -3.5D), "a pit the head still pokes out of into the band is not");
        assertFalse(inEastCone(60, 10, 3, 5.0D, 0.0D, -5.0D), "a pit deep enough to swallow the whole body is clear");
    }

    @Test
    @DisplayName("a body is judged from its feet to its head, so a step up or a ledge down does not spare it")
    void theBodyCountsNotTheFeet() {
        // A height of one, the tightest band there is: what used to spare a player on a step.
        assertTrue(inEastCone(60, 10, 1, 4.0D, 0.0D, 0.5D), "on a slab");
        assertTrue(inEastCone(60, 10, 1, 4.0D, 0.0D, 1.0D), "on a step one block up, the feet on the top edge");
        assertFalse(inEastCone(60, 10, 1, 4.0D, 0.0D, 1.5D), "up on a ledge the whole body is above the band");
        assertTrue(inEastCone(60, 10, 1, 4.0D, 0.0D, -BODY), "on a ledge below with the head level with the boss' feet");
        assertTrue(inEastCone(60, 10, 1, 4.0D, 0.0D, -BODY - 1.0D), "one lower still, the head on the bottom edge");
        assertFalse(inEastCone(60, 10, 1, 4.0D, 0.0D, -BODY - 1.1D), "and below that the whole body is under the band");
        // The band on its own, apart from any fan: what the target pick reads too.
        assertTrue(BossConeRuntime.inHeightBand(64.0D, 1.0D, 62.2D, 64.0D));
        assertFalse(BossConeRuntime.inHeightBand(64.0D, 1.0D, 61.0D, 62.8D));
        assertFalse(BossConeRuntime.inHeightBand(64.0D, 1.0D, 65.1D, 66.9D));
    }

    @Test
    @DisplayName("a half circle covers everything in front of the boss and nothing behind it")
    void aHalfCircleIsTheFrontHalf() {
        assertTrue(inEastCone(180, 10, 3, 5.0D, 90.0D, 0.0D), "square to the side is the edge of a half circle");
        assertTrue(inEastCone(180, 10, 3, 5.0D, -89.0D, 0.0D));
        assertFalse(inEastCone(180, 10, 3, 5.0D, 91.0D, 0.0D), "a hair behind square is behind the boss");
        assertFalse(inEastCone(180, 10, 3, 5.0D, 180.0D, 0.0D));
    }

    @Test
    @DisplayName("somebody standing inside the boss is in its cone, whichever way it faces")
    void theCentreIsInside() {
        assertTrue(BossConeRuntime.inSector(ORIGIN, EAST, 10, 2, 1, ORIGIN.x, ORIGIN.z, ORIGIN.y, ORIGIN.y + BODY));
        assertTrue(BossConeRuntime.inSector(ORIGIN, new Vec3(0.0D, 0.0D, -1.0D), 10, 2, 1,
                ORIGIN.x, ORIGIN.z, ORIGIN.y + 0.5D, ORIGIN.y + 0.5D + BODY));
    }

    @Test
    @DisplayName("the yaw a sector is drawn at points down the same axis the cone is judged along")
    void theMarkIsTurnedTheWayTheConeIs() {
        assertEquals(0.0F, BossConeRuntime.yawOf(new Vec3(0.0D, 0.0D, 1.0D)), 1.0E-4F, "Minecraft's yaw 0 looks south");
        assertEquals(-90.0F, BossConeRuntime.yawOf(EAST), 1.0E-4F, "and -90 looks east");
        for (float yaw : new float[]{-170.0F, -45.0F, 0.0F, 30.0F, 135.0F}) {
            // The gaze the boss reads its facing axis off, turned back into the yaw the arc is drawn at.
            double radians = Math.toRadians(yaw);
            Vec3 gaze = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
            float back = BossConeRuntime.yawOf(gaze);
            double arcFacing = Math.toRadians(back + 90.0F);
            assertEquals(gaze.x, Math.cos(arcFacing), 1.0E-4D, "the arc's middle has to lie on the axis at yaw " + yaw);
            assertEquals(gaze.z, Math.sin(arcFacing), 1.0E-4D, "the arc's middle has to lie on the axis at yaw " + yaw);
        }
    }

    @Test
    @DisplayName("the cone follows whichever way its axis points")
    void theAxisTurnsTheCone() {
        double diagonal = Math.sqrt(0.5D);
        Vec3 southWest = new Vec3(-diagonal, 0.0D, diagonal);
        assertTrue(inCone(southWest, 40, ORIGIN.x - 4.0D, ORIGIN.z + 4.0D),
                "four west and four south is straight down a south-west axis");
        assertFalse(inCone(southWest, 40, ORIGIN.x + 4.0D, ORIGIN.z + 4.0D),
                "four east and four south is ninety degrees off it");
        assertFalse(inCone(southWest, 40, ORIGIN.x - 4.0D, ORIGIN.z),
                "due west is forty-five degrees off a south-west axis, past a forty degree cone's twenty");
        assertTrue(inCone(southWest, 90, ORIGIN.x - 4.0D, ORIGIN.z),
                "and exactly on the edge of a ninety degree one");
    }

    /** Whether a body standing on the origin's floor at {@code x, z} is in an eight block cone of {@code angle} along {@code axis}. */
    private static boolean inCone(Vec3 axis, double angle, double x, double z) {
        return BossConeRuntime.inSector(ORIGIN, axis, angle, 8, 2, x, z, ORIGIN.y, ORIGIN.y + BODY);
    }
}
