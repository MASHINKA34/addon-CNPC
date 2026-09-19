package com.goodbird.cnpcgeckoaddon.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shots a boss' cast still owes after its first: how many, how far apart, and that a
 * burst of one owes none, which is the single shot the ability was before.
 */
class BossRangedBurstTest {

    /** The ticks the follow-up shots leave on, ticking the burst from the tick after the cast. */
    private static List<Long> shotTicks(BossRangedBurst burst, int interval, long from, long to) {
        List<Long> ticks = new ArrayList<>();
        for (long tick = from; tick <= to; tick++) {
            if (burst.due(tick, interval)) {
                ticks.add(tick);
            }
        }
        return ticks;
    }

    @Test
    @DisplayName("a burst of one owes nothing after the cast's own shot")
    void aSingleShotIsNoBurst() {
        BossRangedBurst burst = new BossRangedBurst();
        burst.start(1, 4, 100L);
        assertFalse(burst.isRunning());
        assertTrue(shotTicks(burst, 4, 101L, 140L).isEmpty());
    }

    @Test
    @DisplayName("a burst of three owes two more, one pause apart, and then is over")
    void theRestFollowOnePauseApart() {
        BossRangedBurst burst = new BossRangedBurst();
        burst.start(3, 4, 100L);
        assertTrue(burst.isRunning());
        assertEquals(List.of(104L, 108L), shotTicks(burst, 4, 101L, 140L));
        assertFalse(burst.isRunning(), "the cooldown counts from the last of them");
    }

    @Test
    @DisplayName("a burst held up picks up one shot at a time rather than emptying at once")
    void aHeldUpBurstDoesNotEmptyAtOnce() {
        BossRangedBurst burst = new BossRangedBurst();
        burst.start(4, 2, 100L);
        // Not ticked for a while - a carried boss - then ticked again from far past the due tick.
        assertEquals(List.of(150L, 152L, 154L), shotTicks(burst, 2, 150L, 170L));
    }

    @Test
    @DisplayName("a pause of nought is still one tick: two shots never leave on the same tick")
    void thePauseIsAtLeastATick() {
        BossRangedBurst burst = new BossRangedBurst();
        burst.start(3, 0, 100L);
        assertEquals(List.of(101L, 102L), shotTicks(burst, 0, 100L, 110L));
    }

    @Test
    @DisplayName("clearing drops what is owed")
    void clearDropsTheRest() {
        BossRangedBurst burst = new BossRangedBurst();
        burst.start(5, 3, 100L);
        assertTrue(burst.due(103L, 3));
        burst.clear();
        assertFalse(burst.isRunning());
        assertTrue(shotTicks(burst, 3, 104L, 130L).isEmpty());
    }
}
