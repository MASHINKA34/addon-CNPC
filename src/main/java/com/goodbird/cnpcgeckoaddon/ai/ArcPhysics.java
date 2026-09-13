package com.goodbird.cnpcgeckoaddon.ai;

/**
 * The arc of something pushed once and then left to fall the way vanilla falls, worked out
 * before the push so that it comes down where it was aimed.
 *
 * <p>The boss leap throws this way. It does not steer the flight, so the only thing that puts
 * the landing on the mark is solving the push over the numbers the game flies with: every tick
 * gravity comes off the vertical speed and a drag takes a share of what is left.</p>
 */
public final class ArcPhysics {

    public static final double GRAVITY = 0.08D;
    public static final double VERTICAL_DRAG = 0.98D;
    /** Ceiling on the launch speed, past which the boss would leave the loaded chunks. */
    public static final double MAX_RISE_SPEED = 5.0D;

    private ArcPhysics() {
    }

    /** Ticks the climb from an upward push of {@code speed} takes to reach its peak. */
    public static double riseTicks(double speed) {
        double terminal = terminalSpeed();
        return Math.log(terminal / (speed + terminal)) / Math.log(VERTICAL_DRAG);
    }

    /** How high that climb gets. */
    public static double peakHeight(double speed) {
        return speed / (1.0D - VERTICAL_DRAG) - terminalSpeed() * riseTicks(speed);
    }

    /** Ticks a fall from a standstill takes to cover {@code drop} blocks. */
    public static double fallTicks(double drop) {
        double terminal = terminalSpeed();
        double low = 0.0D;
        double high = 400.0D;
        for (int step = 0; step < 24; step++) {
            double mid = (low + high) * 0.5D;
            double fallen = terminal * (mid - (1.0D - Math.pow(VERTICAL_DRAG, mid)) / (1.0D - VERTICAL_DRAG));
            if (fallen < drop) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5D;
    }

    /**
     * The push that gets something {@code height} blocks up.
     *
     * <p>Searched rather than solved: with the drag in it {@link #peakHeight} has no
     * neat inverse, and a couple of dozen halvings once per throw costs nothing.</p>
     */
    public static double speedForHeight(double height) {
        double low = 0.0D;
        double high = MAX_RISE_SPEED;
        for (int step = 0; step < 24; step++) {
            double mid = (low + high) * 0.5D;
            if (peakHeight(mid) < height) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5D;
    }

    /** The speed a falling entity settles at, which is what the drag is measured against. */
    public static double terminalSpeed() {
        return GRAVITY * VERTICAL_DRAG / (1.0D - VERTICAL_DRAG);
    }
}
