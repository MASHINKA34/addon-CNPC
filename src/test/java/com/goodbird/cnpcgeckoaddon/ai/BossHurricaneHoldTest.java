package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sums a storm holds a victim by, checked without a world.
 *
 * <p>The client runs the same sums, so a mistake here is not a crash but a player shivering
 * between two places, a lift that overshoots or a hold that never lets go.</p>
 */
class BossHurricaneHoldTest {

    private static final double EPSILON = 1.0E-6D;

    @Test
    @DisplayName("the lift goes from where the victim was caught to the carrying height over its ticks")
    void theLiftTakesItsTicks() {
        assertEquals(64.0D, BossHurricaneHold.liftY(64.0D, 68.0D, 100L, 110L, 100L), EPSILON, "caught: on the floor");
        assertEquals(66.0D, BossHurricaneHold.liftY(64.0D, 68.0D, 100L, 110L, 105L), EPSILON, "halfway up at half the ticks");
        assertEquals(68.0D, BossHurricaneHold.liftY(64.0D, 68.0D, 100L, 110L, 110L), EPSILON, "up at the last tick");
        assertEquals(68.0D, BossHurricaneHold.liftY(64.0D, 68.0D, 100L, 110L, 500L), EPSILON, "and stays up after it");
        assertEquals(68.0D, BossHurricaneHold.liftY(64.0D, 68.0D, 100L, 100L, 100L), EPSILON, "a lift of no ticks is up at once");
        assertEquals(64.0D, BossHurricaneHold.liftY(64.0D, 68.0D, 100L, 110L, 90L), EPSILON, "and nothing before the catch");
    }

    @Test
    @DisplayName("the orbit is drawn round the eye from the angle, and moves with the eye")
    void theOrbitFollowsTheEye() {
        Vec3 east = BossHurricaneHold.orbitPoint(10.0D, 70.0D, -5.0D, 2.0D, 0.0D);
        assertEquals(12.0D, east.x, EPSILON);
        assertEquals(70.0D, east.y, EPSILON);
        assertEquals(-5.0D, east.z, EPSILON);
        Vec3 south = BossHurricaneHold.orbitPoint(10.0D, 70.0D, -5.0D, 2.0D, 90.0D);
        assertEquals(10.0D, south.x, EPSILON);
        assertEquals(-3.0D, south.z, EPSILON);
        Vec3 moved = BossHurricaneHold.orbitPoint(13.0D, 70.0D, -1.0D, 2.0D, 90.0D);
        assertEquals(13.0D, moved.x, EPSILON, "the eye moved three east, so did the point");
        assertEquals(1.0D, moved.z, EPSILON, "and four south");
        assertEquals(0.0D, BossHurricaneHold.startAngle(new Vec3(10.0D, 64.0D, -5.0D), 14.0D, -5.0D), EPSILON,
                "caught due east of the eye, the ride starts there");
        assertEquals(90.0D, BossHurricaneHold.startAngle(new Vec3(10.0D, 64.0D, -5.0D), 10.0D, 1.0D), EPSILON);
        Vec3 none = BossHurricaneHold.orbitPoint(10.0D, 70.0D, -5.0D, 0.0D, 123.0D);
        assertEquals(10.0D, none.x, EPSILON, "no orbit is the eye itself");
        assertEquals(-5.0D, none.z, EPSILON);
    }

    @Test
    @DisplayName("the angle and the view turn by the spin each tick, and neither runs away")
    void theSpinTurnsTheRideAndTheView() {
        double angle = 350.0D;
        angle = BossHurricaneHold.advanceAngle(angle, 15.0F);
        assertEquals(5.0D, angle, EPSILON, "past a full turn the angle starts over");
        assertEquals(20.0D, BossHurricaneHold.advanceAngle(angle, 15.0F), EPSILON);
        assertEquals(-170.0F, BossHurricaneHold.spinYaw(175.0F, 15.0F), 1.0E-4F, "a yaw is kept inside the half turn either way");
        assertEquals(30.0F, BossHurricaneHold.spinYaw(15.0F, 15.0F), 1.0E-4F);
        assertEquals(15.0F, BossHurricaneHold.spinYaw(15.0F, 0.0F), 1.0E-4F, "no spin leaves the view alone");
    }

    @Test
    @DisplayName("the hold ends on its ticks, and whoever was let go is not taken again inside the grace")
    void theExitAndTheGrace() {
        long caughtAt = 1000L;
        long endsAt = caughtAt + 60L;
        assertFalse(BossHurricaneHold.isOver(endsAt, endsAt - 1L), "one tick short is still held");
        assertTrue(BossHurricaneHold.isOver(endsAt, endsAt), "and the sixtieth tick is the exit");
        long until = BossHurricaneHold.graceUntil(endsAt, 40);
        assertTrue(BossHurricaneHold.inGrace(until, endsAt), "let go, and out of reach at once");
        assertTrue(BossHurricaneHold.inGrace(until, until - 1L), "for the whole grace");
        assertFalse(BossHurricaneHold.inGrace(until, until), "and fair game the tick it runs out");
        assertFalse(BossHurricaneHold.inGrace(null, endsAt), "somebody never let go has no grace to be in");
        assertFalse(BossHurricaneHold.inGrace(BossHurricaneHold.graceUntil(endsAt, 0), endsAt),
                "a grace of nought is none at all");
    }

    @Test
    @DisplayName("a storm takes no more than its victims")
    void theVictimCeiling() {
        assertTrue(BossHurricaneHold.hasRoom(0, 1));
        assertTrue(BossHurricaneHold.hasRoom(3, 4));
        assertFalse(BossHurricaneHold.hasRoom(4, 4), "full");
        assertFalse(BossHurricaneHold.hasRoom(1, 1), "one victim per storm is one");
    }
}
