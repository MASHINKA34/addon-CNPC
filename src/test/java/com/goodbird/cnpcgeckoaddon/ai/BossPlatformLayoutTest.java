package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How much a burning platform sows on one repaint and where, checked without a world.
 *
 * <p>None of it throws when it is wrong: a fill counted off the wrong area is a platform that
 * is invisible at eight by eight or a snowstorm at sixteen, a pillar on the rim rather than
 * inside it rises out of the block next door, and a ceiling that cuts a pillar short instead
 * of thinning it is a pillar the party reads as lower than it is.</p>
 */
class BossPlatformLayoutTest {

    private static final double EPSILON = 1.0E-9D;
    private static final AABB EIGHT_BY_EIGHT = new AABB(10, 64, -20, 18, 66, -12);

    @Test
    @DisplayName("the fill is the density over the floor, and grows by the ramp over the fuse")
    void theFillGrowsWithTheArea() {
        // Six per ten square blocks over sixty-four of them is thirty-eight, plus two hundred
        // per cent by the end: three times as many.
        assertEquals(38, BossPlatformScheduler.fillPoints(6, 64.0D, 200, 0.0F));
        assertEquals(77, BossPlatformScheduler.fillPoints(6, 64.0D, 200, 0.5F));
        assertEquals(115, BossPlatformScheduler.fillPoints(6, 64.0D, 200, 1.0F));
        assertEquals(6, BossPlatformScheduler.fillPoints(6, 9.5D, 200, 0.0F), "five point seven is rounded, not cut");
        assertEquals(1, BossPlatformScheduler.fillPoints(6, 1.0D, 200, 0.0F), "the smallest platform still gets its one");
        assertEquals(0, BossPlatformScheduler.fillPoints(0, 64.0D, 200, 1.0F), "a density of nought is no fill at all");
        assertEquals(38, BossPlatformScheduler.fillPoints(6, 64.0D, 0, 1.0F), "no ramp is the same fill to the end");
    }

    @Test
    @DisplayName("the fill counts anything off the fuse as its start")
    void theFillClampsItsProgress() {
        int atStart = BossPlatformScheduler.fillPoints(6, 64.0D, 200, 0.0F);
        assertEquals(atStart, BossPlatformScheduler.fillPoints(6, 64.0D, 200, BossTelegraphPaint.NO_END));
        assertEquals(atStart, BossPlatformScheduler.fillPoints(6, 64.0D, 200, -3.0F));
        assertEquals(BossPlatformScheduler.fillPoints(6, 64.0D, 200, 1.0F),
                BossPlatformScheduler.fillPoints(6, 64.0D, 200, 7.0F));
    }

    @Test
    @DisplayName("the fill is held under its ceiling, however big the floor or thick the density")
    void theFillHasACeiling() {
        // Sixteen by sixteen at the default density is a hundred and fifty-four before the ceiling.
        assertEquals(BossPlatformScheduler.MAX_FILL_POINTS,
                BossPlatformScheduler.fillPoints(6, 256.0D, 200, 0.0F));
        assertEquals(BossPlatformScheduler.MAX_FILL_POINTS,
                BossPlatformScheduler.fillPoints(60, 10_000.0D, 500, 1.0F));
        assertEquals(128, BossPlatformScheduler.MAX_FILL_POINTS);
    }

    @Test
    @DisplayName("the pillars stand half a block inside the four corners, walked the way the outline is")
    void thePillarsStandInsideTheCorners() {
        Vec3[] corners = BossPlatformScheduler.pillarCorners(EIGHT_BY_EIGHT, 64.0D);
        assertEquals(4, corners.length);
        assertVec(10.5D, 64.0D, -19.5D, corners[0]);
        assertVec(17.5D, 64.0D, -19.5D, corners[1]);
        assertVec(17.5D, 64.0D, -12.5D, corners[2]);
        assertVec(10.5D, 64.0D, -12.5D, corners[3]);
    }

    @Test
    @DisplayName("a platform too narrow for the inset folds its pillars onto its middle rather than outside it")
    void aNarrowPlatformKeepsItsPillarsInside() {
        Vec3[] corners = BossPlatformScheduler.pillarCorners(new AABB(0, 64, 0, 1, 66, 8), 64.0D);
        for (Vec3 corner : corners) {
            assertEquals(0.5D, corner.x, EPSILON, "a one block wide box has one column of pillars");
        }
        assertEquals(0.5D, corners[0].z, EPSILON);
        assertEquals(7.5D, corners[2].z, EPSILON);
    }

    @Test
    @DisplayName("a pillar is one particle every half block up to its height, the topmost on it")
    void aPillarIsSpacedByHalfBlocks() {
        assertArrayEquals(new double[]{0.5D, 1.0D, 1.5D, 2.0D}, BossPlatformScheduler.pillarHeights(2.0D), EPSILON);
        assertArrayEquals(new double[]{0.4D, 0.8D}, BossPlatformScheduler.pillarHeights(0.8D), EPSILON);
        assertArrayEquals(new double[]{0.3D}, BossPlatformScheduler.pillarHeights(0.3D), EPSILON,
                "a pillar shorter than a step still shows its top");
        assertEquals(0, BossPlatformScheduler.pillarHeights(0.0D).length, "no height is no pillar at all");
        assertEquals(0, BossPlatformScheduler.pillarHeights(-1.0D).length);
    }

    @Test
    @DisplayName("four pillars together never cost more than their ceiling, and keep their height under it")
    void thePillarsHaveACeiling() {
        int perPillar = BossPlatformScheduler.MAX_PILLAR_POINTS / 4;
        // Eight blocks at half a block a step is exactly the ceiling, the tallest the editor offers.
        assertEquals(perPillar, BossPlatformScheduler.pillarHeights(8.0D).length);
        double[] tall = BossPlatformScheduler.pillarHeights(20.0D);
        assertEquals(perPillar, tall.length, "past the ceiling the pillar is thinned, not cut");
        assertEquals(20.0D, tall[tall.length - 1], EPSILON, "and still reaches its top");
        assertEquals(1.25D, tall[0], EPSILON);
        assertEquals(64, BossPlatformScheduler.MAX_PILLAR_POINTS);
    }

    private static void assertVec(double x, double y, double z, Vec3 actual) {
        assertEquals(x, actual.x, EPSILON, "x of " + actual);
        assertEquals(y, actual.y, EPSILON, "y of " + actual);
        assertEquals(z, actual.z, EPSILON, "z of " + actual);
    }
}
