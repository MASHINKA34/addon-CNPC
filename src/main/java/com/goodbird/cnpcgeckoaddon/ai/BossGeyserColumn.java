package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/**
 * The shape of one geyser column and the pace it is drawn at, kept apart from the world so
 * both can be checked without one.
 *
 * <p>A column is a stack of slices half a block apart, from the floor up. Each slice is a
 * circle whose radius runs straight from the bottom's to the top's, so a cone is two radii
 * and a straight column is the same thing with both at nought: one dot at the middle of
 * every slice, exactly the line the geyser always drew. The dots of a slice are the caller's
 * to place; this only says which slices are drawn on a tick, and where each one lies.</p>
 *
 * <p>The rise is measured against the ticks it was asked for rather than against a count of
 * slices, so a column of any height comes up in exactly that many ticks, and one asked for
 * at once comes up at once. On top of that sits a ceiling on the particles one tick spends
 * on one column: a wide cone with many dots would otherwise cost a few hundred packets in a
 * single tick. When the ceiling bites, the column simply takes a tick or two longer, since
 * a column that is all there a moment later reads better than one with its dots thinned
 * out; a slice that alone costs more than the ceiling is still drawn whole, one per tick,
 * so the column can never stall.</p>
 *
 * <p>A column can also be drawn top down, for the strike that falls back onto the circle:
 * the same slices in the opposite order, the narrow top first.</p>
 */
final class BossGeyserColumn {

    /** Spacing between the column's slices; how high it climbs is the ability's to say. */
    static final double SPACING = 0.5D;
    /** Ceiling on the particles one column spends in one tick, whatever the builder asked for. */
    static final int PARTICLE_BUDGET = 96;

    private final double bottomRadius;
    private final double topRadius;
    /** How many slices there are; nought for a column of no height, which is no column at all. */
    private final int slices;
    private final int riseTicks;
    private final int points;
    private final int perPoint;
    private final int smokePerPoint;
    private final boolean downward;
    /** How many slices have been drawn, counted in the order they are drawn in. */
    private int drawn;
    /** How many ticks the column has been advanced by. */
    private int ticks;

    /**
     * @param bottomRadius  the radius of the slice on the floor
     * @param topRadius     the radius of the slice at the top
     * @param height        how tall the column is, in blocks; nought or less draws nothing
     * @param riseTicks     over how many ticks it is drawn; nought or less for all at once
     * @param points        dots round each slice
     * @param perPoint      particles each dot is sent with
     * @param smokePerPoint smoke each dot is sent with on every other slice
     * @param downward      whether the slices are drawn from the top down rather than up
     */
    BossGeyserColumn(double bottomRadius, double topRadius, double height, int riseTicks, int points,
                     int perPoint, int smokePerPoint, boolean downward) {
        this.bottomRadius = Math.max(0.0D, bottomRadius);
        this.topRadius = Math.max(0.0D, topRadius);
        this.slices = slicesFor(height);
        this.riseTicks = Math.max(0, riseTicks);
        this.points = Math.max(1, points);
        this.perPoint = Math.max(0, perPoint);
        this.smokePerPoint = Math.max(0, smokePerPoint);
        this.downward = downward;
    }

    /** How many slices a column of this height has: one on the floor, then one every half block. */
    static int slicesFor(double height) {
        return height <= 0.0D ? 0 : (int) Math.round(height / SPACING) + 1;
    }

    int slices() {
        return slices;
    }

    int points() {
        return points;
    }

    boolean isDone() {
        return drawn >= slices;
    }

    /** How many slices have been drawn so far. */
    int drawn() {
        return drawn;
    }

    /** The height of a slice over the floor: step nought lies on it. */
    double yAt(int step) {
        return step * SPACING;
    }

    /** The radius of a slice, run straight from the bottom's to the top's. */
    double radiusAt(int step) {
        int steps = slices - 1;
        if (steps <= 0) {
            return bottomRadius;
        }
        return Mth.lerp(Mth.clamp((double) step / steps, 0.0D, 1.0D), bottomRadius, topRadius);
    }

    /** Whether this slice carries the smoke: every other one, which keeps a tall column affordable. */
    boolean smokeOn(int step) {
        return (step & 1) == 0;
    }

    /** What one slice costs in particles, dots and smoke together. */
    int costOf(int step) {
        return points * perPoint + (smokeOn(step) ? points * smokePerPoint : 0);
    }

    /** The slice drawn {@code index}th: from the floor up, or from the top down. */
    int stepAt(int index) {
        return downward ? slices - 1 - index : index;
    }

    /**
     * How many slices the rise has reached after this many ticks, counted from one.
     *
     * <p>Even slices per tick where the count divides, and the remainder spread along the
     * rise rather than dumped on its last tick.</p>
     */
    int reachAfter(int ticksSoFar) {
        if (riseTicks <= 0) {
            return slices;
        }
        return (int) Math.ceil((double) slices * Math.min(ticksSoFar, riseTicks) / riseTicks);
    }

    /**
     * Moves the column on by one tick and hands every slice that comes up on it to
     * {@code draw}, in the order they are drawn in.
     *
     * @return how many slices were drawn
     */
    int advance(IntConsumer draw) {
        ticks++;
        int wanted = Math.max(0, reachAfter(ticks) - drawn);
        int fit = 0;
        int cost = 0;
        for (int i = 0; i < wanted; i++) {
            int stepCost = costOf(stepAt(drawn + i));
            // The first slice of a tick always goes, however much it costs: the ceiling
            // stretches a column over more ticks, it never leaves one hanging.
            if (fit > 0 && cost + stepCost > PARTICLE_BUDGET) {
                break;
            }
            cost += stepCost;
            fit++;
        }
        int first = drawn;
        drawn += fit;
        for (int i = 0; i < fit; i++) {
            draw.accept(stepAt(first + i));
        }
        return fit;
    }
}
