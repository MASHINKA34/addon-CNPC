package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossCocoonSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clocks and the noises a hold runs on are taken on the tick it is laid and never read again.
 *
 * <p>A cocoon, a leash and a gravity field all outlive the cast that started them - seconds of
 * level ticks while the boss is already back on its rotation - so what they run on is
 * snapshotted rather than asked for again. A snapshot that kept a reference instead of a copy
 * would let a builder editing the ability change a hold the party is already answering, which
 * nobody would trace back here: the countdown would simply start carrying further, or the
 * chain would start tugging harder, halfway through.</p>
 */
class BossHoldCastSnapshotTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a cocoon is closed with the look the settings had on that tick")
    void theCocoonTakesItsLookOnTheCast() {
        BossCocoonSettings cocoon = new BossCocoonSettings();
        cocoon.setEffectIntervalTicks(5);
        cocoon.setAnnounceIntervalTicks(40);
        cocoon.setAnnounceRange(0);
        cocoon.getFreedSound().setEnabled(false);
        cocoon.getTimeoutParticles().setCount(3);

        BossCocoonManager.Look look = BossCocoonManager.look(cocoon);
        assertEquals(5, look.effectIntervalTicks());
        assertEquals(40, look.announceIntervalTicks());
        assertEquals(0.0D, look.announceRange(), EPSILON);
        assertFalse(look.freedSound().isEnabled());
        assertEquals(3, look.timeoutParticles().getCount());
    }

    @Test
    @DisplayName("editing the cocoon after it closed leaves the shut one alone")
    void theCocoonLookDoesNotFollowLaterEdits() {
        BossCocoonSettings cocoon = new BossCocoonSettings();
        BossCocoonManager.Look look = BossCocoonManager.look(cocoon);

        cocoon.setEffectIntervalTicks(1);
        cocoon.setAnnounceIntervalTicks(200);
        cocoon.setAnnounceRange(64);
        cocoon.getWrapSound().setEnabled(false);
        cocoon.getFreedParticles().setCount(0);

        assertEquals(20, look.effectIntervalTicks());
        assertEquals(10, look.announceIntervalTicks());
        assertEquals(12.0D, look.announceRange(), EPSILON);
        assertTrue(look.wrapSound().isEnabled());
        assertEquals(12, look.freedParticles().getCount());
    }
}
