package com.goodbird.cnpcgeckoaddon.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
