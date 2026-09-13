package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossDashSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** The share of its step a tick has to keep to count as a scrape: the shipped default. */
    private static final double QUARTER = 0.25D;

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
    @DisplayName("nobody behind the start of the lane is met, however close to the boss' back")
    void nothingBehindTheStartIsMet() {
        // The first stretch runs from the start; a player pressed against the boss' back is
        // within two bodies' reach of it, and still not in its path.
        assertFalse(EAST.covers(0.0D, 0.8D, 0.6D, 64.0D, 67.0D, -0.5D, 0.0D, 64.0D, 65.8D),
                "hugging the boss' back when it sets off is not standing in the lane");
        assertTrue(EAST.covers(0.0D, 0.8D, 0.6D, 64.0D, 67.0D, 0.1D, 0.0D, 64.0D, 65.8D),
                "inside the boss' front half at the start is in the lane");
        // Further down the lane the reach back is the run's own ground, covered by the last stretch.
        assertTrue(EAST.covers(3.0D, 3.8D, 0.6D, 64.0D, 67.0D, 2.5D, 0.0D, 64.0D, 65.8D));
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
    @DisplayName("a run straight through a chain crosses it where both of them pass")
    void aRunThroughAChainCrossesIt() {
        // Running east from 0 to 2, through a chain strung north to south at x = 1.
        assertCrossing(0.5D, 0.5D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D));
        assertCrossing(0.5D, 0.25D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 1.0D, -0.5D, 1.0D, 1.5D));
        assertCrossing(0.25D, 0.5D, BossDashRuntime.crossing(0.0D, 0.0D, 4.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D));
        // Diagonal both ways, crossing at (1, 1).
        assertCrossing(0.5D, 0.5D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 2.0D, 0.0D, 2.0D, 2.0D, 0.0D));
    }

    @Test
    @DisplayName("which way the run or the chain is walked only turns its own fraction round")
    void crossingDoesNotDependOnDirection() {
        assertCrossing(0.5D, 0.5D, BossDashRuntime.crossing(2.0D, 0.0D, 0.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D));
        assertCrossing(0.75D, 0.5D, BossDashRuntime.crossing(4.0D, 0.0D, 0.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D));
        assertCrossing(0.5D, 0.75D, BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 1.0D, 1.5D, 1.0D, -0.5D));
    }

    @Test
    @DisplayName("a run that stops short of a chain, or passes its end, does not cross it")
    void shortOrPastTheEndDoesNotCross() {
        assertNull(BossDashRuntime.crossing(0.0D, 0.0D, 0.9D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D),
                "stopped a tenth of a block before the chain");
        assertNull(BossDashRuntime.crossing(1.1D, 0.0D, 3.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D),
                "the stretch started past it: that was the last tick");
        assertNull(BossDashRuntime.crossing(0.0D, 2.0D, 2.0D, 2.0D, 1.0D, -1.0D, 1.0D, 1.0D),
                "running by beyond the end of the chain, past the victim holding it");
    }

    @Test
    @DisplayName("touching the chain counts; running along it or standing still does not")
    void endsCountParallelDoesNot() {
        assertCrossing(1.0D, 0.5D, BossDashRuntime.crossing(0.0D, 0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D));
        assertCrossing(0.5D, 1.0D, BossDashRuntime.crossing(0.0D, 1.0D, 2.0D, 1.0D, 1.0D, -1.0D, 1.0D, 1.0D));
        assertNull(BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, 0.0D, 0.5D, 2.0D, 0.5D),
                "a run beside a chain and parallel to it never crosses it");
        assertNull(BossDashRuntime.crossing(0.0D, 0.0D, 2.0D, 0.0D, -1.0D, 0.0D, 3.0D, 0.0D),
                "nor does one running right along it");
        assertNull(BossDashRuntime.crossing(1.0D, 0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D, 1.0D),
                "a tick without movement crosses nothing, even standing on the chain");
    }

    private static void assertCrossing(double run, double chain, BossDashRuntime.Crossing crossing) {
        assertNotNull(crossing, "the run and the chain should cross");
        assertEquals(run, crossing.run(), EPSILON, "how far along the stretch run they cross");
        assertEquals(chain, crossing.chain(), EPSILON, "how far along the chain they cross");
    }

    @Test
    @DisplayName("a wall is being against something without getting anywhere")
    void aWallIsNoProgressAgainstSomething() {
        assertTrue(BossDashRuntime.stoppedByWall(true, 0.0D, 0.8D, QUARTER), "pressed against it and not moving");
        assertTrue(BossDashRuntime.stoppedByWall(true, 0.1D, 0.8D, QUARTER),
                "a crawl along its face is still the wall");
        assertFalse(BossDashRuntime.stoppedByWall(true, 0.5D, 0.8D, QUARTER),
                "scraping past a corner at speed is not");
        assertFalse(BossDashRuntime.stoppedByWall(false, 0.0D, 0.8D, QUARTER),
                "no collision, whatever held it up, is no wall");
    }

    @Test
    @DisplayName("the share the wall is judged by is what decides a scrape from a stop")
    void theWallShareDecides() {
        // Nine tenths: all but a full-speed tick against something reads as a wall.
        assertTrue(BossDashRuntime.stoppedByWall(true, 0.5D, 0.8D, 0.9D),
                "scraping a corner counts as a wall once the share is this high");
        assertFalse(BossDashRuntime.stoppedByWall(true, 0.75D, 0.8D, 0.9D),
                "a tick that kept nearly all its step is still not a wall");
        // A hundredth: only a boss that has genuinely stopped dead counts.
        assertTrue(BossDashRuntime.stoppedByWall(true, 0.0D, 0.8D, 0.01D), "dead stop, at any share");
        assertFalse(BossDashRuntime.stoppedByWall(true, 0.1D, 0.8D, 0.01D),
                "the crawl that used to be a wall is a scrape at this share");
    }

    @Test
    @DisplayName("the tenths the settings hold are the numbers the run used to be written with")
    void theTenthsAreYesterdaysLiterals() {
        BossDashSettings dash = new BossDashSettings();
        assertEquals(1.0D, dash.getMinReach(), EPSILON, "the shortest lane worth running");
        assertEquals(0.25D, dash.getWallShare(), EPSILON);
        assertEquals(0.4D, dash.getContactSlice(), EPSILON);
        assertEquals(0.5D, dash.getMaxSteer(), EPSILON);
        assertEquals(1.0D, dash.getSweepSlack(), EPSILON);
        assertEquals(2.0D, dash.getTeleportSlack(), EPSILON);
        assertEquals(0.5D, dash.getChainHeightSlack(), EPSILON);

        // A lane the leash cuts to under the shortest one is refused; the setting moves where
        // that line is drawn, so a boss set to zero runs whatever stub is left.
        dash.setMinReachTenths(0);
        assertEquals(0.0D, dash.getMinReach(), EPSILON);
        dash.setMinReachTenths(1000);
        assertEquals(10.0D, dash.getMinReach(), EPSILON, "clamped to the longest lane a setting allows");
    }
}
