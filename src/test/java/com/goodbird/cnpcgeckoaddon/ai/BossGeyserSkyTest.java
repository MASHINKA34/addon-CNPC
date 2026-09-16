package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strike from above, checked without a world: when it starts down, when it lands, what
 * the warning reads meanwhile, how wide it is, what falls and how the airborne are pressed.
 *
 * <p>None of it throws when it is wrong: a strike a tick early lands on a player the ring
 * still said was safe, one that lands twice is a double hit nobody could have dodged, and a
 * press a player's client wears down on the way reads only as a slam that did not slam.</p>
 */
class BossGeyserSkyTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("the strike waits out the delay, falls for its ticks and lands once on the tick after")
    void theTimelineRunsEruptionDelayFallStrike() {
        BossGeyserSky sky = new BossGeyserSky(100L, 30, 10);
        assertEquals(130L, sky.fallsAt());
        assertEquals(140L, sky.hitsAt());
        for (long tick = 100L; tick < 130L; tick++) {
            assertTrue(sky.isWaiting(tick), "tick " + tick + " is still inside the delay");
            assertFalse(sky.takeStart(tick), "nothing falls before the delay is out, tick " + tick);
            assertFalse(sky.isFalling(tick));
            assertFalse(sky.takeStrike(tick));
            assertFalse(sky.isOver());
        }
        assertFalse(sky.isWaiting(130L));
        assertTrue(sky.takeStart(130L), "the fall starts on the tick the delay runs out");
        assertFalse(sky.takeStart(130L), "and only once");
        for (long tick = 130L; tick < 140L; tick++) {
            assertTrue(sky.isFalling(tick), "tick " + tick + " is inside the fall");
            assertFalse(sky.takeStrike(tick), "nothing lands before the fall is over, tick " + tick);
            assertFalse(sky.takeStart(tick));
        }
        assertFalse(sky.isFalling(140L));
        assertTrue(sky.takeStrike(140L), "the strike lands on the tick after the fall's last");
        assertTrue(sky.isOver());
        assertFalse(sky.takeStrike(140L), "never a second time on that tick");
        assertFalse(sky.takeStrike(141L), "nor on any tick after");
    }

    @Test
    @DisplayName("the warning runs from nought at the eruption to one at the landing")
    void theWarningReadsTheWholeWay() {
        BossGeyserSky sky = new BossGeyserSky(100L, 30, 10);
        assertEquals(0.0F, sky.warnProgress(100L), 1.0E-6F, "the tick the ground opened");
        assertEquals(0.5F, sky.warnProgress(120L), 1.0E-6F, "halfway to the landing");
        assertEquals(0.75F, sky.warnProgress(130L), 1.0E-6F, "the tick the fall starts");
        assertEquals(1.0F, sky.warnProgress(140L), 1.0E-6F, "the tick it lands");
        assertEquals(0.0F, sky.warnProgress(90L), 1.0E-6F, "held to the range either side");
        assertEquals(1.0F, sky.warnProgress(200L), 1.0E-6F);
    }

    @Test
    @DisplayName("a strike held up lands late once rather than twice or never")
    void aHeldStrikeStillLandsOnce() {
        BossGeyserSky sky = new BossGeyserSky(0L, 30, 10);
        // Not ticked until long after both moments have passed.
        assertTrue(sky.takeStart(150L));
        assertTrue(sky.takeStrike(150L));
        assertFalse(sky.takeStrike(151L));
        assertTrue(sky.isOver());
    }

    @Test
    @DisplayName("the delay and the fall are at least a tick each, so the moments never coincide")
    void theMomentsAreKeptApart() {
        BossGeyserSky sky = new BossGeyserSky(50L, 0, 0);
        assertEquals(51L, sky.fallsAt());
        assertEquals(52L, sky.hitsAt());
    }

    @Test
    @DisplayName("a strike radius of nought is the geyser's own radius, any other its own")
    void noRadiusIsTheGeysers() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        assertEquals(3.0D, BossGeyserScheduler.look(geyser).skyRadius(3.0D), EPSILON);
        assertEquals(7.0D, BossGeyserScheduler.look(geyser).skyRadius(7.0D), EPSILON);
        geyser.setSkyRadius(5);
        assertEquals(5.0D, BossGeyserScheduler.look(geyser).skyRadius(3.0D), EPSILON);
    }

    @Test
    @DisplayName("the falling column is the cone the other way up, in the strike's own particle, and is down in the fall's ticks")
    void theFallingColumnIsTheConeMirrored() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        geyser.setColumnShape(BossGeyserSettings.COLUMN_CONE);
        geyser.setColumnTopRadiusTenths(5);
        geyser.setColumnPointsPerSlice(6);
        geyser.setSkyHeight(12);
        geyser.setSkyFallTicks(10);
        BossGeyserColumn column = BossGeyserScheduler.look(geyser).skyColumn(4.0D);
        assertEquals(25, column.slices(), "twelve blocks of fall is twenty-five slices");
        assertEquals(24, column.stepAt(0), "the narrow top comes first");
        assertEquals(0.5D, column.radiusAt(24), EPSILON, "as narrow as the cone's top");
        assertEquals(4.0D, column.radiusAt(0), EPSILON, "and the floor slice as wide as the circle it lands on");
        assertEquals(6, column.points());
        assertEquals(6 * 3 + 6, column.costOf(0), "the strike's three per dot, and the column's smoke on an even slice");
        int ticks = 0;
        while (!column.isDone()) {
            column.advance(step -> { });
            ticks++;
        }
        assertEquals(10, ticks, "the front reaches the floor on the fall's last tick");

        geyser.setColumnShape(BossGeyserSettings.COLUMN_STRAIGHT);
        BossGeyserColumn straight = BossGeyserScheduler.look(geyser).skyColumn(4.0D);
        assertEquals(1, straight.points(), "a straight column falls as the one line it rises as");
        assertEquals(0.0D, straight.radiusAt(0), EPSILON);
        assertEquals(0.0D, straight.radiusAt(24), EPSILON);
    }

    @Test
    @DisplayName("a press arrives as asked: a mob is handed it whole, a player gets the server's pass put back")
    void thePressArrivesAsAsked() {
        Vec3 run = new Vec3(0.2D, 0.5D, -0.1D);
        Vec3 mob = BossGeyserSky.pressVelocity(run, 1.2D, false);
        assertEquals(0.2D, mob.x, EPSILON, "the run is kept");
        assertEquals(-1.2D, mob.y, EPSILON, "straight down at the press");
        assertEquals(-0.1D, mob.z, EPSILON);
        Vec3 player = BossGeyserSky.pressVelocity(run, 1.2D, true);
        assertEquals(0.2D, player.x, EPSILON);
        assertEquals(-1.2D / 0.98D + 0.08D, player.y, EPSILON);
        // What the server's own pass leaves of it is exactly the press asked for.
        assertEquals(-1.2D, (player.y - 0.08D) * 0.98D, EPSILON);
        assertEquals(-0.1D, player.z, EPSILON);
    }
}
