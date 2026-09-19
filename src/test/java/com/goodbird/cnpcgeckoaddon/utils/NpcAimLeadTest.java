package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a shot is pointed: ahead of a runner by the right amount, at a stander exactly, and off
 * the aim by no more than the fan allows.
 */
class NpcAimLeadTest {

    private static final double EPSILON = 1.0E-9D;
    private static final Vec3 MUZZLE = new Vec3(0.0D, 1.6D, 0.0D);
    /** Ten blocks down the z axis, at the same height. */
    private static final Vec3 TARGET = new Vec3(0.0D, 1.6D, 10.0D);

    @Test
    @DisplayName("a target standing still is aimed at where it stands")
    void aStillTargetIsItself() {
        assertSame(TARGET, NpcAimLead.aimPoint(MUZZLE, TARGET, Vec3.ZERO, 1.0D, 100));
        assertSame(TARGET, NpcAimLead.aimPoint(MUZZLE, TARGET, null, 1.0D, 100));
    }

    @Test
    @DisplayName("no lead at all aims at the target whatever it is doing")
    void noLeadIsNoShift() {
        assertSame(TARGET, NpcAimLead.aimPoint(MUZZLE, TARGET, new Vec3(0.3D, 0.0D, 0.0D), 1.0D, 0));
    }

    @Test
    @DisplayName("a runner is led by its travel over the shot's flight")
    void aRunnerIsLedByItsTravel() {
        // Ten blocks at one block a tick is ten ticks in the air, three tenths a tick sideways.
        Vec3 aim = NpcAimLead.aimPoint(MUZZLE, TARGET, new Vec3(0.3D, 0.0D, 0.0D), 1.0D, 100);
        assertEquals(3.0D, aim.x, EPSILON);
        assertEquals(TARGET.y, aim.y, EPSILON);
        assertEquals(TARGET.z, aim.z, EPSILON);
    }

    @Test
    @DisplayName("half the lead is half the shift, and a faster shot needs less of it")
    void theLeadScalesWithThePercentAndTheSpeed() {
        Vec3 half = NpcAimLead.aimPoint(MUZZLE, TARGET, new Vec3(0.3D, 0.0D, 0.0D), 1.0D, 50);
        assertEquals(1.5D, half.x, EPSILON);
        Vec3 fast = NpcAimLead.aimPoint(MUZZLE, TARGET, new Vec3(0.3D, 0.0D, 0.0D), 2.0D, 100);
        assertEquals(1.5D, fast.x, EPSILON, "twice the speed is half the flight");
    }

    @Test
    @DisplayName("a lead past a hundred per cent, or a speed of nought, changes nothing it should not")
    void theEdgesAreHeld() {
        Vec3 over = NpcAimLead.aimPoint(MUZZLE, TARGET, new Vec3(0.3D, 0.0D, 0.0D), 1.0D, 500);
        assertEquals(3.0D, over.x, EPSILON, "clamped to a hundred");
        assertSame(TARGET, NpcAimLead.aimPoint(MUZZLE, TARGET, new Vec3(0.3D, 0.0D, 0.0D), 0.0D, 100),
                "a shot that does not move never arrives, so there is nothing to lead");
    }

    @Test
    @DisplayName("no spread hands the direction straight back")
    void noSpreadIsTheSameDirection() {
        Vec3 direction = new Vec3(0.0D, 0.0D, 10.0D);
        assertSame(direction, NpcAimLead.spread(direction, 0, RandomSource.create(1L)));
    }

    @Test
    @DisplayName("every spread shot stays inside the fan and keeps its length")
    void theSpreadStaysInsideTheFan() {
        RandomSource random = RandomSource.create(20260919L);
        Vec3 direction = new Vec3(3.0D, 1.0D, 7.0D);
        double widest = 0.0D;
        for (int i = 0; i < 2000; i++) {
            Vec3 spread = NpcAimLead.spread(direction, 20, random);
            assertEquals(direction.length(), spread.length(), 1.0E-6D, "the speed is not the fan's to change");
            double angle = Math.toDegrees(Math.acos(Math.min(1.0D,
                    direction.dot(spread) / (direction.length() * spread.length()))));
            widest = Math.max(widest, angle);
            // Half the fan of yaw and a quarter of it of pitch, which together stay inside the fan.
            assertTrue(angle <= 20.0D + 1.0E-6D, "shot " + i + " strayed " + angle + " degrees");
        }
        assertTrue(widest > 5.0D, "two thousand draws never opened the fan: " + widest);
    }

    @Test
    @DisplayName("the same seed draws the same fan")
    void theFanIsSeeded() {
        Vec3 direction = new Vec3(0.0D, 0.0D, 10.0D);
        Vec3 first = NpcAimLead.spread(direction, 30, RandomSource.create(7L));
        Vec3 again = NpcAimLead.spread(direction, 30, RandomSource.create(7L));
        assertEquals(first, again);
    }
}
