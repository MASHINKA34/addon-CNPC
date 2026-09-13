package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A platform's clock - the fuse, the one blast, the doses of its smoulder and the tick it goes
 * out - checked without a world.
 *
 * <p>None of it throws when it is wrong: a blast a tick early is a player hit while the countdown
 * still read one, a blast that fires twice is a double hit nobody could have dodged, and a
 * smoulder one dose short or long only reads in play as a platform that burns oddly.</p>
 */
class BossPlatformBurnTest {

    @Test
    @DisplayName("the fuse burns until its last tick and the platform goes off exactly once")
    void theFuseEndsInOneBlast() {
        BossPlatformScheduler.Burn burn = new BossPlatformScheduler.Burn(100L, 60, 0, 20);
        assertEquals(160L, burn.blastAt());
        for (long tick = 100L; tick < 160L; tick++) {
            assertTrue(burn.isFusing(tick), "tick " + tick + " is still inside the fuse");
            assertFalse(burn.takeBlast(tick), "nothing goes off before the fuse runs out, tick " + tick);
            assertFalse(burn.isOver(tick));
        }
        assertFalse(burn.isFusing(160L));
        assertTrue(burn.takeBlast(160L), "the platform goes off on the tick the fuse runs out");
        assertFalse(burn.takeBlast(160L), "and never a second time on that tick");
        assertFalse(burn.takeBlast(161L), "nor on any tick after");
    }

    @Test
    @DisplayName("with no smoulder the platform is out on the tick it goes off, and doses nobody")
    void noSmoulderIsOneHit() {
        BossPlatformScheduler.Burn burn = new BossPlatformScheduler.Burn(0L, 10, 0, 20);
        assertTrue(burn.takeBlast(10L));
        assertFalse(burn.takeDose(10L));
        assertTrue(burn.isOver(10L), "a linger of nought is the one hit and nothing after it");
    }

    @Test
    @DisplayName("a hundred ticks of smoulder dosed every twenty land five doses, the last on its final tick")
    void theSmoulderDosesOnItsInterval() {
        BossPlatformScheduler.Burn burn = new BossPlatformScheduler.Burn(0L, 60, 100, 20);
        assertTrue(burn.takeBlast(60L));
        List<Long> doses = new ArrayList<>();
        long outAt = -1L;
        for (long tick = 60L; tick <= 200L; tick++) {
            if (burn.takeDose(tick)) {
                doses.add(tick);
            }
            if (burn.isOver(tick)) {
                outAt = tick;
                break;
            }
        }
        assertEquals(List.of(80L, 100L, 120L, 140L, 160L), doses,
                "the blast is not a dose, and the doses come one interval apart from it");
        assertEquals(160L, outAt, "the platform goes out a hundred ticks after it went off");
    }

    @Test
    @DisplayName("a smoulder shorter than its interval glows for its length and doses nobody")
    void aShortSmoulderDosesNobody() {
        BossPlatformScheduler.Burn burn = new BossPlatformScheduler.Burn(0L, 20, 10, 20);
        assertTrue(burn.takeBlast(20L));
        for (long tick = 20L; tick < 30L; tick++) {
            assertFalse(burn.takeDose(tick));
            assertFalse(burn.isOver(tick), "tick " + tick + " is still inside the smoulder");
        }
        assertFalse(burn.takeDose(30L), "the first dose would fall after the smoulder is out");
        assertTrue(burn.isOver(30L));
    }

    @Test
    @DisplayName("a platform held up goes off late once, and its smoulder counts from when it really did")
    void aHeldBlastMovesTheSmoulderAlong() {
        BossPlatformScheduler.Burn burn = new BossPlatformScheduler.Burn(0L, 40, 60, 20);
        assertFalse(burn.isOver(45L), "a platform that has not gone off yet is not out, however late it is");
        assertTrue(burn.takeBlast(55L), "fifteen ticks late, it still goes off");
        assertFalse(burn.isOver(100L), "the smoulder runs sixty ticks from the real blast, not from the fuse's end");
        assertTrue(burn.takeDose(75L));
        assertTrue(burn.isOver(115L));
    }

    @Test
    @DisplayName("a smoulder held up picks up one dose at a time, its interval counted from the last")
    void aHeldSmoulderDoesNotBurst() {
        BossPlatformScheduler.Burn burn = new BossPlatformScheduler.Burn(0L, 10, 200, 20);
        assertTrue(burn.takeBlast(10L));
        // Not ticked for seventy ticks, so three doses fell due in the meantime.
        assertTrue(burn.takeDose(80L), "the dose that is due lands");
        assertFalse(burn.takeDose(80L), "but only one of the three that fell due");
        assertFalse(burn.takeDose(95L), "and the next waits a whole interval from there");
        assertTrue(burn.takeDose(100L));
    }

    @Test
    @DisplayName("a platform's wave reaches its corners")
    void theWaveReachesTheCorners() {
        assertEquals(Math.sqrt(2.0D) * 2.5D, BossPlatformScheduler.halfDiagonal(new AABB(0, 64, 0, 5, 66, 5)), 1.0E-9D);
        assertEquals(5.0D, BossPlatformScheduler.halfDiagonal(new AABB(0, 64, 0, 6, 90, 8)), 1.0E-9D,
                "measured flat: the wave runs along the floor, so the box's height is not part of it");
    }
}
