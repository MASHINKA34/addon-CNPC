package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.ArcPhysics;
import com.goodbird.cnpcgeckoaddon.data.NpcLaunchPadData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The launch pad's throw, flown the way a player's client flies it.
 *
 * <p>The pad pushes once and never touches the player again, so whether they come down on the
 * platform is settled by the push alone. Getting it slightly wrong does not throw anything: the
 * player just lands a few blocks short, every time, off the edge of the platform the pad was
 * built for. So the push is solved by the runtime and then stepped here tick by tick - move by
 * the speed, keep 0.91 of it sideways, take gravity and drag off the vertical - until the player
 * is back down at the landing height, and that spot has to be within a block of the aim.</p>
 */
class NpcLaunchArcTest {

    /** Vanilla's own movement factors, as the floats its movement pass multiplies by. */
    private static final double AIR_KEEP = 0.91F;
    private static final double GROUND_KEEP = 0.6F * 0.91F;
    private static final double VERTICAL_DRAG = 0.98F;
    private static final double GRAVITY = 0.08D;

    /** Horizontal distance, height of the landing over the take-off. */
    private static final double[][] PAIRS = {
            {4.0D, 0.0D}, {6.0D, -2.0D}, {8.0D, 0.0D}, {12.0D, 3.0D}, {12.0D, -6.0D},
            {16.0D, 5.0D}, {20.0D, 0.0D}, {5.0D, 9.0D}, {2.0D, 1.0D}};
    private static final int[] ARC_HEIGHTS = {2, 6, 12};

    /** Where a flight comes down: the spot, and the tick it got there on. */
    private record Touchdown(double x, double z, int tick) {
    }

    @Test
    @DisplayName("a player thrown from mid-air lands within a block of the aim")
    void airborneThrowLandsOnTheBlock() {
        for (double[] pair : PAIRS) {
            for (int arcHeight : ARC_HEIGHTS) {
                assertLandsOnTheAim(pair[0], pair[1], arcHeight, AIR_KEEP);
            }
        }
    }

    @Test
    @DisplayName("a player thrown while standing lands within a block of the aim")
    void groundedThrowLandsOnTheBlock() {
        for (double[] pair : PAIRS) {
            for (int arcHeight : ARC_HEIGHTS) {
                assertLandsOnTheAim(pair[0], pair[1], arcHeight, GROUND_KEEP);
            }
        }
    }

    @Test
    @DisplayName("from the air the sideways speed is the closed form over the air drag alone")
    void airborneSpeedIsTheClosedForm() {
        for (double distance : new double[]{3.0D, 10.0D, 25.0D}) {
            for (int ticks : new int[]{5, 20, 60}) {
                double expected = distance * (1.0D - ArcPhysics.AIR_DRAG)
                        / (1.0D - Math.pow(ArcPhysics.AIR_DRAG, ticks));
                assertEquals(expected, ArcPhysics.horizontalSpeed(distance, ticks, ArcPhysics.AIR_DRAG), 1.0E-12D,
                        distance + " blocks in " + ticks + " ticks");
            }
        }
    }

    @Test
    @DisplayName("what is set for the server's movement pass comes out of it as exactly what was solved")
    void serverTravelIsUndoneExactly() {
        Vec3[] sent = {new Vec3(1.2D, 1.3D, -0.7D), new Vec3(-3.9D, 3.9D, 0.0D), new Vec3(0.05D, 0.42D, 2.5D)};
        double[] keeps = {GROUND_KEEP, 0.98F * 0.91F, AIR_KEEP, 0.8F * 0.91F};
        for (Vec3 velocity : sent) {
            for (double keep : keeps) {
                Vec3 set = ArcPhysics.beforeServerTravel(velocity, keep);
                // LivingEntity.travel on plain ground or in the air: sideways times the keep,
                // vertical less one tick of gravity and then times the drag.
                Vec3 afterTravel = new Vec3(set.x * keep, (set.y - GRAVITY) * VERTICAL_DRAG, set.z * keep);
                assertEquals(velocity.x, afterTravel.x, 1.0E-12D, "x for keep " + keep);
                assertEquals(velocity.y, afterTravel.y, 1.0E-12D, "y for keep " + keep);
                assertEquals(velocity.z, afterTravel.z, 1.0E-12D, "z for keep " + keep);
            }
        }
    }

    @Test
    @DisplayName("no throw asks the motion packet for more than it can carry")
    void throwsStayInsideThePacket() {
        double[][] extremes = {{1000.0D, 0.0D, 1.0D}, {0.0D, 300.0D, 64.0D}, {400.0D, -300.0D, 64.0D},
                {27.0D, 60.0D, 64.0D}, {30000000.0D, 30000000.0D, 64.0D}};
        for (double[] extreme : extremes) {
            for (double keep : new double[]{AIR_KEEP, GROUND_KEEP}) {
                ArcPhysics.Launch launch = ArcPhysics.launch(extreme[0], extreme[1], extreme[0] * 0.5D,
                        extreme[2], keep);
                Vec3 velocity = launch.velocity();
                assertTrue(Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z),
                        "a throw came out as " + velocity);
                assertTrue(Math.abs(velocity.x) <= ArcPhysics.MAX_SENT_SPEED
                                && Math.abs(velocity.y) <= ArcPhysics.MAX_SENT_SPEED
                                && Math.abs(velocity.z) <= ArcPhysics.MAX_SENT_SPEED,
                        "the packet would cut " + velocity + " down on its way to the player");
                assertTrue(launch.flightTicks() >= 1, "a flight of " + launch.flightTicks() + " ticks");
            }
        }
    }

    @Test
    @DisplayName("a pad aimed at the spot it stands on throws straight up")
    void zeroDistanceThrowsStraightUp() {
        ArcPhysics.Launch launch = ArcPhysics.launch(0.0D, 0.0D, 0.0D, 6.0D, GROUND_KEEP);
        assertEquals(0.0D, launch.velocity().x, 0.0D);
        assertEquals(0.0D, launch.velocity().z, 0.0D);
        assertEquals(6.0D, ArcPhysics.peakHeight(launch.velocity().y), 0.05D);
    }

    @Test
    @DisplayName("the landing is the top middle of the block, counted from the pad unless absolute")
    void landingPointFollowsTheCoordinateMode() {
        NpcLaunchPadData data = new NpcLaunchPadData();
        data.setPosition(12, 3, -4);
        BlockPos pad = new BlockPos(100, 64, -200);

        data.setCoordinateMode(NpcLaunchPadData.COORDINATE_NPC_OFFSET);
        assertEquals(new Vec3(112.5D, 68.0D, -203.5D), NpcLaunchPadManager.landingPoint(data, pad));
        // Carried somewhere else, the pad takes its landing along.
        assertEquals(new Vec3(12.5D, 4.0D, -3.5D), NpcLaunchPadManager.landingPoint(data, BlockPos.ZERO));

        data.setCoordinateMode(NpcLaunchPadData.COORDINATE_ABSOLUTE);
        assertEquals(new Vec3(12.5D, 4.0D, -3.5D), NpcLaunchPadManager.landingPoint(data, pad));

        data.setPosition(NpcLaunchPadData.MAX_COORDINATE, NpcLaunchPadData.MAX_COORDINATE, NpcLaunchPadData.MAX_COORDINATE);
        data.setCoordinateMode(NpcLaunchPadData.COORDINATE_NPC_OFFSET);
        Vec3 far = NpcLaunchPadManager.landingPoint(data, new BlockPos(30000000, 30000000, 30000000));
        assertEquals(60000000.5D, far.x, 0.0D, "an offset at the edge of the world must not wrap round");
    }

    private static void assertLandsOnTheAim(double distance, double rise, int arcHeight, double firstTickKeep) {
        // An odd bearing, so both horizontal axes carry some of the throw.
        double dx = distance * Math.cos(0.65D);
        double dz = distance * Math.sin(0.65D);
        ArcPhysics.Launch launch = ArcPhysics.launch(dx, rise, dz, arcHeight, firstTickKeep);
        Touchdown touchdown = fly(launch.velocity(), rise, firstTickKeep);
        String label = distance + " blocks, " + rise + " up, arc " + arcHeight + ", first tick keeps " + firstTickKeep;
        double miss = Math.hypot(touchdown.x() - dx, touchdown.z() - dz);
        assertTrue(miss <= 1.0D, label + ": came down " + miss + " blocks from the aim");
        assertTrue(Math.abs(touchdown.tick() - launch.flightTicks()) <= 1,
                label + ": landed on tick " + touchdown.tick() + " of a flight solved as " + launch.flightTicks());
    }

    /**
     * The flight as the client steps it: the whole speed is moved by first, then the drags come
     * off - the ground's on the tick the push arrives, while the client still has the player
     * standing, and the air's after that.
     */
    private static Touchdown fly(Vec3 velocity, double landingHeight, double firstTickKeep) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        for (int tick = 1; tick <= 1000; tick++) {
            x += vx;
            y += vy;
            z += vz;
            double keep = tick == 1 ? firstTickKeep : AIR_KEEP;
            vx *= keep;
            vz *= keep;
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
            // The floor stops the fall, not the sideways move on the same tick.
            if (vy < 0.0D && y <= landingHeight) {
                return new Touchdown(x, z, tick);
            }
        }
        fail("the flight never came back down to " + landingHeight);
        return null;
    }
}
