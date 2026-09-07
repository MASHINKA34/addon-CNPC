package com.goodbird.cnpcgeckoaddon.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The closed-form arc the leap is aimed with, checked against the motion it stands for.
 *
 * <p>The runtime does not simulate the jump before making it. It solves for the launch speed
 * that reaches the configured height, and for how long the boss will be in the air, and then
 * pushes once - which is the only way the warning ring on the floor can be a promise. Those
 * formulas are three lines of algebra over vanilla's own fall constants, and being slightly
 * wrong does not throw: the boss simply lands somewhere near where it said it would, over
 * and over, and the mechanic reads as sloppy rather than broken.</p>
 *
 * <p>So the arc is stepped here the way the game steps it - {@code v = (v - gravity) * drag},
 * one tick at a time - and the algebra has to agree with the result.</p>
 */
class BossLeapPhysicsTest {

    /** The same two numbers the runtime solves over, restated so a change to either shows up. */
    private static final double GRAVITY = 0.08D;
    private static final double DRAG = 0.98D;

    @Test
    @DisplayName("terminal speed is the fall speed vanilla's constants settle at")
    void terminalSpeedMatchesTheSteadyState() {
        double terminal = BossLeapRuntime.terminalSpeed();
        // Stepping the fall from rest for long enough has to converge on exactly that.
        double speed = 0.0D;
        for (int tick = 0; tick < 2000; tick++) {
            speed = (speed - GRAVITY) * DRAG;
        }
        assertEquals(-terminal, speed, 1.0E-6D,
                "the closed form and the stepped fall disagree about terminal speed");
    }

    @Test
    @DisplayName("the peak of the arc matches the height the same push actually reaches")
    void peakHeightMatchesTheSteppedRise() {
        for (double launch : new double[]{0.5D, 1.0D, 1.5D, 2.0D, 3.0D, 4.0D, 5.0D}) {
            double stepped = 0.0D;
            double speed = launch;
            int ticks = 0;
            while (speed > 0.0D) {
                stepped += speed;
                speed = (speed - GRAVITY) * DRAG;
                ticks++;
            }
            double solved = BossLeapRuntime.peakHeight(launch);
            // The stepped rise lands on a whole tick and the closed form does not, so they
            // differ by less than the last step taken - never by more.
            assertTrue(Math.abs(solved - stepped) <= launch,
                    "launch " + launch + ": solved " + solved + " vs stepped " + stepped);
            assertEquals(ticks, BossLeapRuntime.riseTicks(launch), 1.0D,
                    "launch " + launch + ": the rise is solved to the wrong number of ticks");
        }
    }

    @Test
    @DisplayName("the launch speed solved for a height actually reaches that height")
    void speedForHeightInvertsPeakHeight() {
        for (double height = 1.0D; height <= 20.0D; height += 0.5D) {
            double speed = BossLeapRuntime.speedForHeight(height);
            double reached = BossLeapRuntime.peakHeight(speed);
            assertEquals(height, reached, 0.05D,
                    "asking for " + height + " blocks gave a push that reaches " + reached);
        }
    }

    @Test
    @DisplayName("a height past what a push can reach is answered with the fastest push there is")
    void impossibleHeightIsAnsweredWithTheCeiling() {
        double speed = BossLeapRuntime.speedForHeight(10_000.0D);
        assertTrue(speed > 4.9D && speed <= 5.0D,
                "an unreachable height has to come back at the speed ceiling, not past it: " + speed);
    }

    @Test
    @DisplayName("the fall time matches the drop it is solved for")
    void fallTicksMatchesTheSteppedFall() {
        for (double drop : new double[]{1.0D, 4.0D, 10.0D, 30.0D, 100.0D}) {
            double solved = BossLeapRuntime.fallTicks(drop);
            double fallen = 0.0D;
            double speed = 0.0D;
            int ticks = 0;
            while (fallen < drop) {
                speed = (speed - GRAVITY) * DRAG;
                fallen -= speed;
                ticks++;
            }
            assertEquals(ticks, solved, 1.5D,
                    "a drop of " + drop + " takes " + ticks + " ticks but was solved as " + solved);
        }
    }

    @Test
    @DisplayName("higher asks mean harder pushes and longer flights, without exception")
    void theSolversAreMonotonic() {
        double previousSpeed = 0.0D;
        for (double height = 0.5D; height <= 20.0D; height += 0.5D) {
            double speed = BossLeapRuntime.speedForHeight(height);
            assertTrue(speed >= previousSpeed,
                    "asking for more height gave a softer push at " + height);
            previousSpeed = speed;
        }
        double previousFall = 0.0D;
        for (double drop = 1.0D; drop <= 60.0D; drop += 1.0D) {
            double ticks = BossLeapRuntime.fallTicks(drop);
            assertTrue(ticks >= previousFall, "a longer drop took less time at " + drop);
            previousFall = ticks;
        }
    }

    @Test
    @DisplayName("a push of nothing rises for no time and reaches nowhere")
    void aZeroPushDoesNothing() {
        assertEquals(0.0D, BossLeapRuntime.riseTicks(0.0D), 1.0E-9D);
        assertEquals(0.0D, BossLeapRuntime.peakHeight(0.0D), 1.0E-9D);
    }

    @Test
    @DisplayName("no drop at all still costs a tick, and never a negative one")
    void aZeroDropCostsAtMostOneTick() {
        // The fall is solved over a continuous curve rather than tick by tick, and that curve
        // crosses zero a shade under one tick rather than at it. Worth pinning down: the
        // flight time it feeds is what the horizontal speed is divided by, so a value that
        // came back negative or zero would send the boss off at the speed ceiling.
        double ticks = BossLeapRuntime.fallTicks(0.0D);
        assertTrue(ticks >= 0.0D, "a drop of nothing came back as a negative flight time: " + ticks);
        assertTrue(ticks <= 1.0001D, "a drop of nothing was solved as " + ticks + " ticks");
    }
}
