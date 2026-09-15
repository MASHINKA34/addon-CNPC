package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry a storm travels by, checked without a world.
 *
 * <p>None of it throws when it is wrong: a storm that comes off a wall at the wrong angle, a
 * cross with two arms down the same line or a spiral that winds past its range simply reads
 * as a storm that wanders, and the only symptom is a mechanic that looks sloppy.</p>
 */
class BossHurricanePathTest {

    private static final double EPSILON = 1.0E-6D;
    private static final Vec3 CENTRE = new Vec3(10.0D, 64.0D, -5.0D);
    private static final Vec3 EAST = new Vec3(1.0D, 0.0D, 0.0D);

    @Test
    @DisplayName("a wall along X turns the X of the heading round and leaves Z alone, and the other way about")
    void reflectionTurnsOneComponentRound() {
        Vec3 heading = new Vec3(0.3D, 0.0D, -0.4D);
        Vec3 offX = BossHurricanePath.reflect(heading, true, false);
        assertEquals(-0.3D, offX.x, EPSILON);
        assertEquals(-0.4D, offX.z, EPSILON);
        Vec3 offZ = BossHurricanePath.reflect(heading, false, true);
        assertEquals(0.3D, offZ.x, EPSILON);
        assertEquals(0.4D, offZ.z, EPSILON);
        Vec3 corner = BossHurricanePath.reflect(heading, true, true);
        assertEquals(-0.3D, corner.x, EPSILON);
        assertEquals(0.4D, corner.z, EPSILON);
    }

    @Test
    @DisplayName("each wall costs one bounce, and the wall after the last one ends the storm")
    void bouncesAreCounted() {
        BossHurricanePath.Course course = new BossHurricanePath.Course(new Vec3(0.4D, 0.0D, 0.0D), 2);
        assertTrue(course.bounce(true, false), "the first wall is bounced off");
        assertEquals(-0.4D, course.velocity().x, EPSILON);
        assertEquals(1, course.bounces());
        assertTrue(course.bounce(true, false), "and the second");
        assertEquals(0.4D, course.velocity().x, EPSILON);
        assertEquals(0, course.bounces());
        assertFalse(course.bounce(false, true), "the third is one too many");
        assertEquals(0.4D, course.velocity().x, EPSILON, "a storm that is over is not turned round");
        assertFalse(new BossHurricanePath.Course(EAST, 0).bounce(true, false),
                "with no bounces at all the first wall is the end");
    }

    @Test
    @DisplayName("a cross along the axes is the world's four, starting from the one nearest the gaze")
    void crossAlongTheAxes() {
        List<Vec3> axes = BossHurricanePath.launchAxes(BossPhaseData.HURRICANE_MODE_CROSS,
                new Vec3(0.2D, 0.0D, 0.98D));
        assertEquals(4, axes.size());
        assertEquals(1.0D, axes.get(0).z, EPSILON, "the gaze leans south, so south leads");
        for (Vec3 axis : axes) {
            assertEquals(1.0D, axis.length(), EPSILON, "every arm is a unit heading");
            assertTrue(Math.abs(axis.x) < EPSILON || Math.abs(axis.z) < EPSILON,
                    "and lies along a world axis: " + axis);
        }
        assertOpposite(axes.get(0), axes.get(2));
        assertOpposite(axes.get(1), axes.get(3));
        assertEquals(0.0D, axes.get(0).dot(axes.get(1)), EPSILON, "neighbours are square to each other");
    }

    @Test
    @DisplayName("a cross along the diagonals is the four at forty-five degrees")
    void crossAlongTheDiagonals() {
        List<Vec3> axes = BossHurricanePath.launchAxes(BossPhaseData.HURRICANE_MODE_DIAGONAL, EAST);
        assertEquals(4, axes.size());
        for (Vec3 axis : axes) {
            assertEquals(1.0D, axis.length(), EPSILON);
            assertEquals(Math.abs(axis.x), Math.abs(axis.z), EPSILON, "each arm sits at forty-five degrees: " + axis);
        }
        assertTrue(axes.get(0).x > 0.0D, "east leans toward the two eastern diagonals");
        assertOpposite(axes.get(0), axes.get(2));
        assertOpposite(axes.get(1), axes.get(3));
    }

    @Test
    @DisplayName("a straight launch keeps the one committed heading")
    void aStraightLaunchIsTheCommittedAxis() {
        Vec3 committed = new Vec3(0.6D, 0.0D, 0.8D);
        assertEquals(List.of(committed),
                BossHurricanePath.launchAxes(BossPhaseData.HURRICANE_MODE_STRAIGHT, committed));
    }

    @Test
    @DisplayName("a spiral's angle and radius grow every tick, and the radius stops at the range")
    void theSpiralWindsOutToItsRange() {
        double radius = 0.0D;
        double angle = BossHurricanePath.spiralStartAngle(0.0D, 0, 3);
        for (int tick = 1; tick <= 100; tick++) {
            double next = BossHurricanePath.spiralRadius(radius, 0.2D, 16.0D);
            assertTrue(next >= radius, "the circle never shrinks");
            assertTrue(next <= 16.0D + EPSILON, "and never passes the range");
            radius = next;
            angle += 12.0D;
        }
        assertEquals(16.0D, radius, EPSILON, "a hundred ticks of growth is well past sixteen blocks");
        assertEquals(1200.0D, angle, EPSILON);
        Vec3 out = BossHurricanePath.orbitPoint(CENTRE, radius, 90.0D);
        assertEquals(CENTRE.x, out.x, EPSILON);
        assertEquals(CENTRE.z + 16.0D, out.z, EPSILON, "ninety degrees is due south of the centre");
        assertEquals(CENTRE.y, out.y, EPSILON);
        // Three storms share the circle evenly, the first at the gaze.
        assertEquals(120.0D, BossHurricanePath.spiralStartAngle(0.0D, 1, 3), EPSILON);
        assertEquals(240.0D + 30.0D, BossHurricanePath.spiralStartAngle(30.0D, 2, 3), EPSILON);
    }

    @Test
    @DisplayName("a typhoon's random points all fall inside the range, and a seed makes them repeatable")
    void typhoonPointsFallInsideTheRange() {
        RandomSource random = RandomSource.create(81L);
        double farthest = 0.0D;
        for (int i = 0; i < 200; i++) {
            Vec3 point = BossHurricanePath.randomPointWithin(random, CENTRE, 16.0D);
            double distance = Math.hypot(point.x - CENTRE.x, point.z - CENTRE.z);
            assertTrue(distance <= 16.0D + EPSILON, "a point landed outside the range: " + point);
            assertEquals(CENTRE.y, point.y, EPSILON, "points are laid at the centre's height");
            farthest = Math.max(farthest, distance);
        }
        assertTrue(farthest > 8.0D, "the points spread over the whole disc rather than bunching in the middle");
        Vec3 first = BossHurricanePath.randomPointWithin(RandomSource.create(7L), CENTRE, 16.0D);
        Vec3 again = BossHurricanePath.randomPointWithin(RandomSource.create(7L), CENTRE, 16.0D);
        assertEquals(first, again, "the same seed lays the same point");
    }

    @Test
    @DisplayName("a point seen through the wall is as far along the reflected heading as it was along the old one")
    void aMirroredTargetKeepsItsDistance() {
        Vec3 pos = new Vec3(4.0D, 64.0D, 2.0D);
        Vec3 target = new Vec3(9.0D, 64.0D, 8.0D);
        Vec3 mirrored = BossHurricanePath.mirrorTarget(pos, target, true, false);
        assertEquals(-1.0D, mirrored.x, EPSILON);
        assertEquals(8.0D, mirrored.z, EPSILON);
        assertEquals(pos.distanceTo(target), pos.distanceTo(mirrored), EPSILON);
        Vec3 step = BossHurricanePath.stepToward(pos, mirrored, 0.5D);
        assertEquals(0.5D, step.length(), EPSILON, "a step is the speed long while the point is farther than that");
        assertTrue(step.x < 0.0D, "and heads away from the wall");
        Vec3 last = BossHurricanePath.stepToward(pos, new Vec3(4.2D, 64.0D, 2.0D), 0.5D);
        assertEquals(0.2D, last.length(), EPSILON, "the last step is exactly what is left");
        assertTrue(BossHurricanePath.arrived(new Vec3(4.2D, 64.0D, 2.0D), new Vec3(4.2D, 64.0D, 2.0D), 1.0E-6D));
        assertFalse(BossHurricanePath.arrived(pos, target, 0.5D));
    }

    private static void assertOpposite(Vec3 one, Vec3 other) {
        assertEquals(-one.x, other.x, EPSILON);
        assertEquals(-one.z, other.z, EPSILON);
    }
}
