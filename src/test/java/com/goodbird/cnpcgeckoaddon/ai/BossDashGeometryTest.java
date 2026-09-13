package com.goodbird.cnpcgeckoaddon.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry the dash is run by, checked without a world.
 *
 * <p>Getting any of it slightly wrong throws nothing: a lane cut at the wrong spot resets the
 * fight on the boss' own terms or stops it short of the warning it drew, and the only symptom
 * is a mechanic that reads as sloppy.</p>
 */
class BossDashGeometryTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a run from home is cut at the leash in whichever direction it goes")
    void aRunFromHomeIsCutAtTheLeash() {
        assertEquals(10.0D, BossDashRuntime.leashReach(0.0D, 0.0D, 1.0D, 0.0D, 10.0D), EPSILON);
        assertEquals(10.0D, BossDashRuntime.leashReach(0.0D, 0.0D, 0.0D, -1.0D, 10.0D), EPSILON);
    }

    @Test
    @DisplayName("a run from off centre reaches the edge its own line meets")
    void aRunOffCentreReachesItsOwnEdge() {
        assertEquals(6.0D, BossDashRuntime.leashReach(4.0D, 0.0D, 1.0D, 0.0D, 10.0D), EPSILON,
                "four blocks east of home, running east, the edge is six away");
        assertEquals(14.0D, BossDashRuntime.leashReach(4.0D, 0.0D, -1.0D, 0.0D, 10.0D), EPSILON,
                "running west it crosses home and reaches the far edge");
        // On the circle and aimed through its centre: the whole diameter lies ahead.
        assertEquals(10.0D, BossDashRuntime.leashReach(3.0D, 4.0D, -0.6D, -0.8D, 5.0D), EPSILON);
    }

    @Test
    @DisplayName("a boss outside its leash may only run back in")
    void outsideTheLeashOnlyInward() {
        assertEquals(0.0D, BossDashRuntime.leashReach(15.0D, 0.0D, 1.0D, 0.0D, 10.0D), EPSILON,
                "a lane pointing further out has no step inside the leash");
        assertEquals(25.0D, BossDashRuntime.leashReach(15.0D, 0.0D, -1.0D, 0.0D, 10.0D), EPSILON,
                "a lane pointing back in runs to the far edge");
        assertEquals(0.0D, BossDashRuntime.leashReach(0.0D, 15.0D, 1.0D, 0.0D, 10.0D), EPSILON,
                "a lane that passes the circle by never enters it");
    }

    @Test
    @DisplayName("a leash with no room left allows no run at all")
    void noRoomNoRun() {
        assertEquals(0.0D, BossDashRuntime.leashReach(0.0D, 0.0D, 1.0D, 0.0D, 0.0D), EPSILON);
        assertEquals(0.0D, BossDashRuntime.leashReach(0.0D, 0.0D, 1.0D, 0.0D, -2.0D), EPSILON);
    }

    /** A lane two blocks wide running east from the origin. */
    private static final BossDashRuntime.Lane EAST = new BossDashRuntime.Lane(0.0D, 0.0D, 1.0D, 0.0D, 1.0D);

    @Test
    @DisplayName("along and across are read off the committed line, from where the run started")
    void alongAndAcrossFollowTheLine() {
        assertEquals(5.0D, EAST.along(5.0D, 3.0D), EPSILON);
        assertEquals(-2.0D, EAST.along(-2.0D, 0.0D), EPSILON, "behind the start is negative");
        // Looking east down the lane, north (-z) is on the left, and the left side is positive.
        assertEquals(-3.0D, EAST.across(5.0D, 3.0D), EPSILON);
        assertEquals(3.0D, EAST.across(5.0D, -3.0D), EPSILON);

        double diagonal = Math.sqrt(0.5D);
        BossDashRuntime.Lane northEast = new BossDashRuntime.Lane(10.0D, 10.0D, diagonal, -diagonal, 1.0D);
        assertEquals(Math.sqrt(8.0D), northEast.along(12.0D, 8.0D), EPSILON, "two steps each way is down the line");
        assertEquals(0.0D, northEast.across(12.0D, 8.0D), EPSILON, "and exactly on it");
    }

    @Test
    @DisplayName("somebody standing in the stretch just run is met, measured body to body")
    void aBodyInTheStretchIsMet() {
        // The run covered 3 to 4 this tick; boss and victim are 0.6 wide each, so they meet 0.6 apart.
        assertTrue(EAST.covers(3.0D, 4.0D, 0.6D, 64.0D, 67.0D, 4.5D, 0.0D, 64.0D, 65.8D),
                "half a block ahead of the boss' middle is inside its reach");
        assertTrue(EAST.covers(3.0D, 4.0D, 0.6D, 64.0D, 67.0D, 3.5D, 0.9D, 64.0D, 65.8D),
                "inside the lane's width counts, off the line or not");
        assertFalse(EAST.covers(3.0D, 4.0D, 0.6D, 64.0D, 67.0D, 4.7D, 0.0D, 64.0D, 65.8D),
                "further ahead than the two bodies reach has not been met yet");
        assertFalse(EAST.covers(3.0D, 4.0D, 0.6D, 64.0D, 67.0D, 2.3D, 0.0D, 64.0D, 65.8D),
                "behind the stretch was the last tick's business");
    }

    @Test
    @DisplayName("the lane's width is what the warning drew, not the boss' own body")
    void theWidthIsTheLane() {
        assertTrue(EAST.covers(0.0D, 1.0D, 0.6D, 64.0D, 67.0D, 0.5D, 1.0D, 64.0D, 65.8D),
                "standing on the corridor's edge is standing in it");
        assertFalse(EAST.covers(0.0D, 1.0D, 0.6D, 64.0D, 67.0D, 0.5D, 1.2D, 64.0D, 65.8D),
                "a step outside the drawn edge is the dodge");
        assertFalse(EAST.covers(0.0D, 1.0D, 0.6D, 64.0D, 67.0D, 0.5D, -1.2D, 64.0D, 65.8D),
                "on either side");
    }

    @Test
    @DisplayName("the lane is as tall as its band, from the boss' feet up")
    void theHeightIsTheBand() {
        assertTrue(EAST.covers(0.0D, 1.0D, 0.6D, 64.0D, 67.0D, 0.5D, 0.0D, 63.0D, 64.8D),
                "a victim a block down whose head is in the band is met");
        assertFalse(EAST.covers(0.0D, 1.0D, 0.6D, 64.0D, 67.0D, 0.5D, 0.0D, 62.0D, 63.8D),
                "two blocks down the run passes over them");
        assertFalse(EAST.covers(0.0D, 1.0D, 0.6D, 64.0D, 67.0D, 0.5D, 0.0D, 67.0D, 68.8D),
                "standing on the band's top the run passes under them");
    }

    @Test
    @DisplayName("the safety net is twice the lane's time at full speed, and a second")
    void theSafetyNetScalesWithTheLane() {
        assertEquals(50, BossDashRuntime.timeoutTicks(12, 8), "twelve blocks at 0.8 a tick is fifteen ticks");
        assertEquals(64 * 10 / 2 * 2 + 20, BossDashRuntime.timeoutTicks(64, 2), "the slowest longest run");
        assertEquals(22, BossDashRuntime.timeoutTicks(2, 30), "rounded up, never down to nothing");
    }

    @Test
    @DisplayName("a run straight through a chain crosses it where it passes")
    void aRunThroughAChainCrossesIt() {
        // Running east from 0 to 2, through a chain strung north to south at x = 1.
        assertEquals(0.5D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D), EPSILON);
        assertEquals(0.25D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 1.0D, -0.5D, 1.0D, 1.5D), EPSILON,
                "the fraction is measured along the chain, from its first end");
        // Diagonal both ways, crossing at (1, 1).
        assertEquals(0.5D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 2.0D, 0.0D, 2.0D, 2.0D, 0.0D), EPSILON);
    }

    @Test
    @DisplayName("which way the run or the chain is walked does not change whether they cross")
    void crossingDoesNotDependOnDirection() {
        assertEquals(0.5D, BossDashRuntime.crossing(2.0D, 0.0D, 0.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D), EPSILON,
                "running west through the same chain");
        assertEquals(0.75D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 1.0D, 1.5D, 1.0D, -0.5D), EPSILON,
                "the chain walked from its other end");
    }

    @Test
    @DisplayName("a run that stops short of a chain, or passes its end, does not cross it")
    void shortOrPastTheEndDoesNotCross() {
        assertTrue(Double.isNaN(BossDashRuntime.crossing(0.0D, 0.0D, 0.9D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D)),
                "stopped a tenth of a block before the chain");
        assertTrue(Double.isNaN(BossDashRuntime.crossing(1.1D, 0.0D, 3.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D)),
                "the stretch started past it: that was the last tick");
        assertTrue(Double.isNaN(BossDashRuntime.crossing(0.0D, 2.0D, 2.0D, 2.0D, 1.0D, -1.0D, 1.0D, 1.0D)),
                "running by beyond the end of the chain, past the victim holding it");
    }

    @Test
    @DisplayName("touching the chain counts; running along it or standing still does not")
    void endsCountParallelDoesNot() {
        assertEquals(0.5D, BossDashRuntime.crossing(0.0D, 0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D), EPSILON,
                "a run that stops exactly on the chain has met it");
        assertEquals(1.0D, BossDashRuntime.crossing(0.0D, 1.0D, 2.0D, 1.0D, 1.0D, -1.0D, 1.0D, 1.0D), EPSILON,
                "clipping the very end of it counts too");
        assertTrue(Double.isNaN(BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 0.0D, 0.5D, 2.0D, 0.5D)),
                "a run beside a chain and parallel to it never crosses it");
        assertTrue(Double.isNaN(BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, -1.0D, 0.0D, 3.0D, 0.0D)),
                "nor does one running right along it");
        assertTrue(Double.isNaN(BossDashRuntime.crossing(1.0D, 0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D)),
                "a tick without movement crosses nothing, even standing on the chain");
    }

    @Test
    @DisplayName("a wall is being against something without getting anywhere")
    void aWallIsNoProgressAgainstSomething() {
        assertTrue(BossDashRuntime.stoppedByWall(true, 0.0D, 0.8D), "pressed against it and not moving");
        assertTrue(BossDashRuntime.stoppedByWall(true, 0.1D, 0.8D), "a crawl along its face is still the wall");
        assertFalse(BossDashRuntime.stoppedByWall(true, 0.5D, 0.8D), "scraping past a corner at speed is not");
        assertFalse(BossDashRuntime.stoppedByWall(false, 0.0D, 0.8D), "no collision, whatever held it up, is no wall");
    }
}
