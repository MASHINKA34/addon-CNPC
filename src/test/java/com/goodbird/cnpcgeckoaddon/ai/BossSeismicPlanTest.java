package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossSeismicPlan.Ring;
import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sums a seismic series is made of, checked without a world.
 *
 * <p>None of it throws when it is wrong: a ring that overlaps its neighbour, a random pulse
 * that picks the same ring twice, a height check that lets a jump through - each only reads
 * in play as a boss that is oddly harsh, or oddly easy.</p>
 */
class BossSeismicPlanTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("the plan is the core circle and then rings of the width, touching, cut to the maximum")
    void ringsTouchWithNoGap() {
        assertEquals(List.of(new Ring(0, 3), new Ring(3, 5), new Ring(5, 7), new Ring(7, 9),
                new Ring(9, 11), new Ring(11, 12)), BossSeismicPlan.rings(3, 2, 0, 12));
    }

    @Test
    @DisplayName("a gap leaves that much floor untouched between one ring and the next")
    void ringsKeepTheirGap() {
        assertEquals(List.of(new Ring(0, 3), new Ring(4, 6), new Ring(7, 9), new Ring(10, 12)),
                BossSeismicPlan.rings(3, 2, 1, 12));
    }

    @Test
    @DisplayName("a plan smaller than its core is the core alone, cut to the maximum")
    void aTinyPlanIsTheCoreAlone() {
        assertEquals(List.of(new Ring(0, 2)), BossSeismicPlan.rings(3, 2, 0, 2));
        assertEquals(List.of(new Ring(0, 3)), BossSeismicPlan.rings(3, 4, 0, 3),
                "a ring whose inner edge is the maximum is not started");
    }

    @Test
    @DisplayName("going outward a series has one pulse per ring; at random it has what was asked, or that again")
    void pulseCountFollowsTheMode() {
        assertEquals(6, BossSeismicPlan.pulseCount(BossSeismicSettings.MODE_GROWING, 6, 4),
                "outward the random count is not read");
        assertEquals(4, BossSeismicPlan.pulseCount(BossSeismicSettings.MODE_RANDOM, 6, 4));
        assertEquals(6, BossSeismicPlan.pulseCount(BossSeismicSettings.MODE_RANDOM, 6, 0),
                "nought is one pulse per ring");
    }

    @Test
    @DisplayName("a random pulse takes between min and max rings, no two the same")
    void randomPulsesTakeDistinctRings() {
        List<Ring> plan = BossSeismicPlan.rings(3, 2, 0, 12);
        RandomSource random = RandomSource.create(20260915L);
        Set<Integer> counts = new HashSet<>();
        for (int pulse = 0; pulse < 200; pulse++) {
            List<Ring> picked = BossSeismicPlan.randomPulse(plan, 1, 3, true, random);
            assertTrue(picked.size() >= 1 && picked.size() <= 3, "took " + picked.size() + " rings");
            assertEquals(picked.size(), Set.copyOf(picked).size(), "a ring was taken twice in one pulse: " + picked);
            assertTrue(plan.containsAll(picked), "a ring that is not in the plan was taken");
            counts.add(picked.size());
        }
        assertEquals(Set.of(1, 2, 3), counts, "two hundred pulses should have used every count in the range");
    }

    @Test
    @DisplayName("with the core switched off a random pulse never takes the circle under the boss")
    void randomPulsesSpareTheCoreWhenTold() {
        List<Ring> plan = BossSeismicPlan.rings(3, 2, 0, 12);
        RandomSource random = RandomSource.create(7L);
        boolean coreTaken = false;
        for (int pulse = 0; pulse < 200; pulse++) {
            for (Ring ring : BossSeismicPlan.randomPulse(plan, 1, 3, false, random)) {
                assertFalse(ring.isCore(), "the core came up with the core switched off");
            }
            for (Ring ring : BossSeismicPlan.randomPulse(plan, 1, 3, true, random)) {
                coreTaken |= ring.isCore();
            }
        }
        assertTrue(coreTaken, "with the core allowed, two hundred pulses should have taken it at least once");
    }

    @Test
    @DisplayName("a random pulse gives what the plan has: never more rings than there are, none from a plan with none to give")
    void randomPulsesAreCutToThePlan() {
        RandomSource random = RandomSource.create(3L);
        List<Ring> small = BossSeismicPlan.rings(3, 2, 0, 5);
        assertEquals(2, small.size());
        for (int pulse = 0; pulse < 50; pulse++) {
            assertTrue(BossSeismicPlan.randomPulse(small, 3, 8, true, random).size() <= 2);
        }
        assertEquals(List.of(), BossSeismicPlan.randomPulse(List.of(new Ring(0, 3)), 1, 3, false, random),
                "a plan that is only the core has nothing to give with the core switched off");
        assertFalse(BossSeismicPlan.randomPulse(small, 3, 1, true, random).isEmpty(),
                "the pair is read as a range whichever way round it was typed");
    }

    @Test
    @DisplayName("a ring holds its inner edge and not its outer one")
    void ringEdges() {
        Ring ring = new Ring(3, 5);
        assertTrue(BossSeismicPlan.inRing(ring, 3.0D, 0.0D), "the inner edge is in");
        assertTrue(BossSeismicPlan.inRing(ring, 0.0D, 4.99D));
        assertFalse(BossSeismicPlan.inRing(ring, 5.0D, 0.0D), "the outer edge is the next ring's");
        assertFalse(BossSeismicPlan.inRing(ring, 2.0D, 2.0D), "2.83 out is short of the ring");
        assertTrue(BossSeismicPlan.inRing(ring, 3.0D, 3.0D), "4.24 out is in it");
        assertTrue(BossSeismicPlan.inRing(new Ring(0, 3), 0.0D, 0.0D), "the core holds its own centre");
        assertEquals(4.0D, ring.middle(), EPSILON);
    }

    @Test
    @DisplayName("the height check lets a body through only while it crosses the wave's layer, and no height is no check")
    void heightCheck() {
        double floor = 64.0D;
        assertTrue(BossSeismicPlan.withinHeight(floor, 1.0D, 64.0D, 65.8D), "standing on the floor");
        assertTrue(BossSeismicPlan.withinHeight(floor, 1.0D, 64.9D, 66.7D), "a foot off the floor still crosses the layer");
        assertFalse(BossSeismicPlan.withinHeight(floor, 1.0D, 65.2D, 67.0D), "clear of the layer at the top of a jump");
        assertTrue(BossSeismicPlan.withinHeight(floor, 1.0D, 62.5D, 63.5D), "and below the floor, inside the layer");
        assertFalse(BossSeismicPlan.withinHeight(floor, 1.0D, 61.0D, 62.9D), "or too far below it");
        assertTrue(BossSeismicPlan.withinHeight(floor, 0.0D, 90.0D, 91.8D), "no height is any height");
    }

    @Test
    @DisplayName("a slam arrives as asked: a player gets the tick of gravity the server takes off put back, a mob is handed it whole")
    void slamVelocity() {
        Vec3 run = new Vec3(0.2D, 0.5D, -0.1D);
        Vec3 mob = BossSeismicPlan.slamVelocity(run, 1.5D, false);
        assertEquals(0.2D, mob.x, EPSILON, "the run is kept");
        assertEquals(-1.5D, mob.y, EPSILON);
        assertEquals(-0.1D, mob.z, EPSILON);
        Vec3 player = BossSeismicPlan.slamVelocity(run, 1.5D, true);
        assertEquals(0.2D, player.x, EPSILON);
        assertEquals(-1.5D / 0.98D + 0.08D, player.y, EPSILON);
        // What the server's own pass leaves of it is exactly the slam asked for.
        assertEquals(-1.5D, (player.y - 0.08D) * 0.98D, EPSILON);
        assertEquals(-0.1D, player.z, EPSILON);
    }
}
