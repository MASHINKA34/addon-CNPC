package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The sums a seismic series is made of, kept apart from the world so they can be checked
 * without one: which rings the floor is cut into, which of them a pulse takes, who stands in
 * one, and how hard a slam has to be set to arrive as asked.
 */
final class BossSeismicPlan {

    /** What every tick takes off a vertical speed, and what it takes off before that: vanilla's own two numbers. */
    private static final double VERTICAL_DRAG = 0.98D;
    private static final double GRAVITY = 0.08D;

    /**
     * One ring of the plan: the floor from {@code inner} out to, but not including, {@code outer}.
     * The core circle is the ring whose inner edge is nought.
     */
    record Ring(double inner, double outer) {

        /** Whether a point this far from the centre stands in the ring: the inner edge is in, the outer is out. */
        boolean holds(double distance) {
            return distance >= inner && distance < outer;
        }

        double middle() {
            return (inner + outer) * 0.5D;
        }

        boolean isCore() {
            return inner <= 0.0D;
        }
    }

    private BossSeismicPlan() {
    }

    /**
     * The rings a cast cuts the floor into, innermost first.
     *
     * <p>Ring nought is the circle under the boss, {@code [0, core)}. Every ring after it is
     * {@code width} wide and stands {@code gap} past the one before, so with no gap they
     * touch. Rings start while their inner edge is short of {@code maxRadius}, and the last
     * one is cut to it rather than reaching past.</p>
     */
    static List<Ring> rings(int core, int width, int gap, int maxRadius) {
        List<Ring> plan = new ArrayList<>();
        plan.add(new Ring(0.0D, Math.min(core, maxRadius)));
        // width is one at least, so every ring starts further out than the last and the walk ends.
        int step = Math.max(1, width + gap);
        for (int k = 1; ; k++) {
            double inner = core + (k - 1) * (double) step + gap;
            if (inner >= maxRadius) {
                break;
            }
            plan.add(new Ring(inner, Math.min(inner + width, maxRadius)));
        }
        return plan;
    }

    /**
     * How many pulses one series runs: one per ring going outward, and for random radii the
     * number the phase asked for, or again one per ring when it asked for nought.
     */
    static int pulseCount(int mode, int ringCount, int randomPulses) {
        if (mode == BossSeismicSettings.MODE_RANDOM && randomPulses > 0) {
            return randomPulses;
        }
        return ringCount;
    }

    /**
     * The rings a random pulse takes: between {@code min} and {@code max} of them, no two the
     * same, and the core circle only when {@code core} allows it. A plan with fewer rings to
     * offer gives what it has.
     */
    static List<Ring> randomPulse(List<Ring> plan, int min, int max, boolean core, RandomSource random) {
        List<Ring> candidates = new ArrayList<>();
        for (Ring ring : plan) {
            if (core || !ring.isCore()) {
                candidates.add(ring);
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        // Read as a range whichever way round the two were typed.
        int low = Math.max(1, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));
        int count = Math.min(candidates.size(), low + random.nextInt(high - low + 1));
        List<Ring> picked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            picked.add(candidates.remove(random.nextInt(candidates.size())));
        }
        return picked;
    }

    /** Whether somebody standing {@code dx, dz} from the centre, flat, is in the ring. */
    static boolean inRing(Ring ring, double dx, double dz) {
        return ring.holds(Math.sqrt(dx * dx + dz * dz));
    }

    /**
     * Whether a body reaching from {@code minY} to {@code maxY} is within the wave's reach of
     * the floor at {@code centreY}: it crosses the layer {@code height} above and below it.
     * No height is no check at all, so a jump is no escape.
     */
    static boolean withinHeight(double centreY, double height, double minY, double maxY) {
        if (height <= 0.0D) {
            return true;
        }
        return maxY >= centreY - height && minY <= centreY + height;
    }

    /**
     * The slam as it has to be set so that it arrives as {@code down} straight down, on top of
     * whatever run the victim had.
     *
     * <p>The gravity throw's sum with the sign turned: for a player the server's own pass
     * runs before the send and takes one tick of gravity off the speed, so that tick is put
     * back in advance. A mob moves by exactly what it is handed.</p>
     */
    static Vec3 slamVelocity(Vec3 movement, double down, boolean player) {
        double y = player ? -down / VERTICAL_DRAG + GRAVITY : -down;
        return new Vec3(movement.x, y, movement.z);
    }
}
