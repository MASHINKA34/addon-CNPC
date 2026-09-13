package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.phys.Vec3;

/**
 * The arc of something pushed once and then left to fall the way vanilla falls, worked out
 * before the push so that it comes down where it was aimed.
 *
 * <p>The boss leap and the launch pad both throw this way. Neither steers the flight, so the
 * only thing that puts the landing on the mark is solving the push over the numbers the game
 * flies with: every tick gravity comes off the vertical speed and a drag takes a share of what
 * is left, and an entity in the air keeps {@link #AIR_DRAG} of its sideways speed.</p>
 */
public final class ArcPhysics {

    public static final double GRAVITY = 0.08D;
    public static final double VERTICAL_DRAG = 0.98D;
    /** What an entity off the ground keeps of its horizontal speed from one tick to the next. */
    public static final double AIR_DRAG = 0.91D;
    /** Ceiling on the launch speed, past which the boss would leave the loaded chunks. */
    public static final double MAX_RISE_SPEED = 5.0D;
    /**
     * The most one axis of a motion packet carries: the packet clamps each component to this
     * before it goes out, so a player handed anything faster flies somewhere else.
     */
    public static final double MAX_SENT_SPEED = 3.9D;
    /**
     * The vertical drag exactly as vanilla's movement pass multiplies by it - a float widened
     * to a double - so that undoing that pass in advance undoes it to the last bit.
     */
    private static final double TRAVEL_VERTICAL_DRAG = 0.98F;

    private ArcPhysics() {
    }

    /** One throw at a player: the speed their client has to be handed, and how long they fly. */
    public record Launch(Vec3 velocity, int flightTicks) {
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

    /**
     * The throw that lands a player {@code dx, dy, dz} away from where their feet are.
     *
     * <p>The arc climbs {@code arcHeight} above the higher of the two ends, and the flight lasts
     * the climb plus the drop to the landing. Sideways, the client keeps {@code firstTickKeep}
     * of the speed after the first tick and {@link #AIR_DRAG} after every one that follows: a
     * player standing on the ground when the push arrives is still on it for that first tick,
     * and ground friction takes nearly half the speed there - solved as air all the way, a
     * twelve-block throw from the floor comes down under eight blocks out.</p>
     *
     * <p>Both speeds are kept inside what a motion packet can carry. Past that the throw falls
     * short rather than being sent in some direction it was never aimed.</p>
     *
     * @param firstTickKeep what the player keeps of a sideways speed on the tick the push
     *                      arrives: {@link #AIR_DRAG} in the air, friction times it on the ground
     */
    public static Launch launch(double dx, double dy, double dz, double arcHeight, double firstTickKeep) {
        double rise = Math.min(speedForHeight(arcHeight + Math.max(0.0D, dy)), MAX_SENT_SPEED);
        // Measured off the push actually solved, which is the asked-for climb unless the packet
        // ceiling cut it down.
        double drop = Math.max(0.0D, peakHeight(rise) - dy);
        int ticks = Math.max(1, (int) Math.ceil(riseTicks(rise)) + (int) Math.ceil(fallTicks(drop)));
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4D) {
            return new Launch(new Vec3(0.0D, rise, 0.0D), ticks);
        }
        double speed = Math.min(MAX_SENT_SPEED, horizontalSpeed(distance, ticks, firstTickKeep));
        return new Launch(new Vec3(dx / distance * speed, rise, dz / distance * speed), ticks);
    }

    /**
     * The sideways speed that covers {@code distance} in {@code ticks}, when the first tick keeps
     * {@code firstTickKeep} of it and every later one {@link #AIR_DRAG}.
     *
     * <p>The first tick moves by the whole speed; tick {@code k} after it by the speed times
     * {@code firstTickKeep * AIR_DRAG^(k-1)}. With the air drag on the first tick too this is
     * {@code distance * (1 - AIR_DRAG) / (1 - AIR_DRAG^ticks)}.</p>
     */
    public static double horizontalSpeed(double distance, int ticks, double firstTickKeep) {
        double later = (1.0D - Math.pow(AIR_DRAG, Math.max(0, ticks - 1))) / (1.0D - AIR_DRAG);
        return distance / (1.0D + firstTickKeep * later);
    }

    /**
     * What to set on a server player so that the speed which reaches their client is {@code sent}.
     *
     * <p>A player's own movement pass runs on the server after every level has ticked and before
     * the tracker sends the speed on: it multiplies the sideways speed by {@code horizontalKeep}
     * and takes one tick of gravity and drag off the vertical. Both are undone here in advance,
     * so the pass lands on exactly the speed that was solved.</p>
     *
     * @param horizontalKeep what that pass keeps of a sideways speed, which is the same number
     *                       the client keeps on the first tick of the flight
     */
    public static Vec3 beforeServerTravel(Vec3 sent, double horizontalKeep) {
        return new Vec3(sent.x / horizontalKeep, sent.y / TRAVEL_VERTICAL_DRAG + GRAVITY,
                sent.z / horizontalKeep);
    }
}
