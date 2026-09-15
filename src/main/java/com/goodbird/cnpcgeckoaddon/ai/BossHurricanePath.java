package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry a storm travels by, with no world in it.
 *
 * <p>Everything here is a sum: which way a storm goes after a wall, where the four storms of a
 * cross set off, how a spiral winds out, where a typhoon's next point lies. Kept apart from the
 * scheduler that moves the storms so the sums can be checked without a level, since none of
 * them throws when it is wrong - a storm that comes off a wall at the wrong angle simply reads
 * as a storm that wanders.</p>
 */
final class BossHurricanePath {

    private static final double HALF_ROOT_TWO = Math.sqrt(0.5D);

    /** The four ways a cross along the world's axes goes, in turning order. */
    private static final Vec3[] AXES = {
            new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D),
            new Vec3(-1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, -1.0D)
    };

    /** And the four ways a cross along the diagonals goes. */
    private static final Vec3[] DIAGONALS = {
            new Vec3(HALF_ROOT_TWO, 0.0D, HALF_ROOT_TWO), new Vec3(-HALF_ROOT_TWO, 0.0D, HALF_ROOT_TWO),
            new Vec3(-HALF_ROOT_TWO, 0.0D, -HALF_ROOT_TWO), new Vec3(HALF_ROOT_TWO, 0.0D, -HALF_ROOT_TWO)
    };

    private BossHurricanePath() {
    }

    /**
     * A storm's heading and the walls it has left in it.
     *
     * <p>A wall met along X sends the storm back along X and leaves its Z alone, and the other
     * way round; a corner met on the diagonal alone is both at once. Each wall costs one bounce,
     * and the wall after the last one is where the storm dies.</p>
     */
    static final class Course {
        private Vec3 velocity;
        private int bounces;

        Course(Vec3 velocity, int bounces) {
            this.velocity = velocity;
            this.bounces = Math.max(0, bounces);
        }

        Vec3 velocity() {
            return velocity;
        }

        void setVelocity(Vec3 value) {
            velocity = value;
        }

        int bounces() {
            return bounces;
        }

        /**
         * Comes off a wall.
         *
         * @return false when the storm had no bounce left for it and is over
         */
        boolean bounce(boolean wallX, boolean wallZ) {
            if (bounces <= 0) {
                return false;
            }
            bounces--;
            velocity = reflect(velocity, wallX, wallZ);
            return true;
        }
    }

    /** The heading with the component of each wall met turned round. */
    static Vec3 reflect(Vec3 velocity, boolean wallX, boolean wallZ) {
        return new Vec3(wallX ? -velocity.x : velocity.x, velocity.y, wallZ ? -velocity.z : velocity.z);
    }

    /**
     * The axes a launch sends its storms along: the committed one alone for a straight storm,
     * the world's four for a cross, the four diagonals for the other cross, each set starting
     * from the one nearest the gaze so the first storm leaves roughly where the boss looks.
     */
    static List<Vec3> launchAxes(int launchMode, Vec3 committed) {
        Vec3[] set = switch (launchMode) {
            case BossPhaseData.HURRICANE_MODE_CROSS -> AXES;
            case BossPhaseData.HURRICANE_MODE_DIAGONAL -> DIAGONALS;
            default -> null;
        };
        if (set == null) {
            return List.of(committed);
        }
        int first = 0;
        double best = -2.0D;
        for (int i = 0; i < set.length; i++) {
            double along = set[i].x * committed.x + set[i].z * committed.z;
            if (along > best) {
                best = along;
                first = i;
            }
        }
        List<Vec3> axes = new ArrayList<>(set.length);
        for (int i = 0; i < set.length; i++) {
            axes.add(set[(first + i) % set.length]);
        }
        return axes;
    }

    /** A point on the circle of {@code radius} round {@code centre}, at the centre's height. */
    static Vec3 orbitPoint(Vec3 centre, double radius, double angleDegrees) {
        // Double precision on purpose: Mth's float constants put a storm's point a few
        // millionths off, which a client re-deriving the same point would see as a shiver.
        double angle = Math.toRadians(angleDegrees);
        return new Vec3(centre.x + Math.cos(angle) * radius, centre.y, centre.z + Math.sin(angle) * radius);
    }

    /** The angle, in degrees, at which a point stands off a centre; nought is east, ninety south. */
    static double angleOf(Vec3 centre, double x, double z) {
        return Math.toDegrees(Math.atan2(z - centre.z, x - centre.x));
    }

    /** A spiral's circle after one more tick: wider by its growth, and never past its range. */
    static double spiralRadius(double radius, double growth, double range) {
        return Math.min(radius + growth, range);
    }

    /** Where a spiral's storms set off: equal angles round the boss, the first at the gaze. */
    static double spiralStartAngle(double gazeDegrees, int index, int count) {
        return gazeDegrees + 360.0D * index / Math.max(1, count);
    }

    /**
     * A random point inside the circle of {@code range} round {@code centre}, spread evenly
     * over the disc rather than bunched at the middle, at the centre's height.
     */
    static Vec3 randomPointWithin(RandomSource random, Vec3 centre, double range) {
        double radius = range * Math.sqrt(random.nextDouble());
        double angle = random.nextDouble() * 360.0D;
        return orbitPoint(centre, radius, angle);
    }

    /**
     * A typhoon's point, seen through the wall the storm just came off: the storm carries on
     * along its reflected heading and still has exactly as far left to go.
     */
    static Vec3 mirrorTarget(Vec3 pos, Vec3 target, boolean wallX, boolean wallZ) {
        return new Vec3(wallX ? 2.0D * pos.x - target.x : target.x, target.y,
                wallZ ? 2.0D * pos.z - target.z : target.z);
    }

    /** The step from one point toward another: at most {@code speed} long, flat, and the whole way when it is nearer. */
    static Vec3 stepToward(Vec3 from, Vec3 to, double speed) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= speed || distance < 1.0E-6D) {
            return new Vec3(dx, 0.0D, dz);
        }
        return new Vec3(dx / distance * speed, 0.0D, dz / distance * speed);
    }

    /** Whether one point is within a step of another, flat. */
    static boolean arrived(Vec3 pos, Vec3 target, double speed) {
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        return dx * dx + dz * dz <= speed * speed;
    }
}
