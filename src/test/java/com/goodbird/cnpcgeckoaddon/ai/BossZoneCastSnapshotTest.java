package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The look a zone is cast with is taken on the tick it is cast and never read again.
 *
 * <p>Every one of these abilities outlives the cast that started it - a fuse burns for seconds
 * while the boss is already back on its rotation - so the settings are snapshotted rather than
 * asked for again. A snapshot that kept a reference instead of a copy would let a builder
 * editing the ability change a fuse already burning, which nobody would trace back here: the
 * platform would simply blink at a rate the party did not start with.</p>
 */
class BossZoneCastSnapshotTest {

    @Test
    @DisplayName("a platform is lit with the look the settings had on that tick")
    void thePlatformTakesItsLookOnTheCast() {
        BossPlatformSettings platform = new BossPlatformSettings();
        platform.setBlinkTicks(9);
        platform.setCountdownIntervalTicks(7);
        platform.setFlareMax(30);
        platform.setFlareArea(20);
        platform.getBlastSound().setVolume(3);
        platform.getBlastParticles().setCount(5);
        platform.getOutlineParticles().setEnabled(false);

        BossPlatformScheduler.Look look = BossPlatformScheduler.look(platform);
        assertEquals(9, look.blinkTicks());
        assertEquals(7, look.countdownIntervalTicks());
        // Twenty tenths is two square blocks a pop, so a sixteen block floor throws eight.
        assertEquals(8, look.pops(16.0D));
        assertEquals(3, look.blastSound().getVolume());
        assertEquals(5, look.blastParticles().getCount());
        assertFalse(look.outlineParticles().isEnabled());
    }

    @Test
    @DisplayName("editing the platform after it was lit leaves the burning one alone")
    void thePlatformLookDoesNotFollowLaterEdits() {
        BossPlatformSettings platform = new BossPlatformSettings();
        BossPlatformScheduler.Look look = BossPlatformScheduler.look(platform);

        platform.setBlinkTicks(20);
        platform.setCountdownIntervalTicks(5);
        platform.setFlareMax(0);
        platform.getBlastSound().setEnabled(false);
        platform.getBlastParticles().setCount(0);

        assertEquals(4, look.blinkTicks());
        assertEquals(20, look.countdownIntervalTicks());
        assertEquals(4, look.pops(16.0D), "four square blocks a pop is what it was cast with");
        assertTrue(look.blastSound().isEnabled());
        assertEquals(1, look.blastParticles().getCount());
    }

    @Test
    @DisplayName("a platform told to throw no pops throws none, and every other one throws at least one")
    void aCapOfNoughtIsNoPopsAtAll() {
        BossPlatformSettings platform = new BossPlatformSettings();
        platform.setFlareMax(0);
        assertEquals(0, BossPlatformScheduler.look(platform).pops(400.0D));

        platform.setFlareMax(24);
        BossPlatformScheduler.Look look = BossPlatformScheduler.look(platform);
        assertEquals(1, look.pops(0.25D), "the smallest platform still reads as going off");
        assertEquals(24, look.pops(10_000.0D), "and the biggest is still held to the cap");
    }
}
