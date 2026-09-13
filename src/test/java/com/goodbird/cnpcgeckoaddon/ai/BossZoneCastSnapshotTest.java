package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import com.goodbird.cnpcgeckoaddon.data.BossMarkSettings;
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

    private static final double EPSILON = 1.0E-9D;

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

    @Test
    @DisplayName("a geyser is lit with the look the settings had on that tick")
    void theGeyserTakesItsLookOnTheCast() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        geyser.setVfxTicks(44);
        geyser.setColumnPerRadiusTenths(20);
        geyser.setColumnMinTenths(10);
        geyser.setColumnMaxTenths(80);
        geyser.setBoilMinHundredths(5);
        geyser.setBoilMaxHundredths(25);
        geyser.getEruptSound().setEnabled(false);

        BossGeyserScheduler.Look look = BossGeyserScheduler.look(geyser);
        assertEquals(44, look.vfxTicks());
        assertEquals(6.0D, look.columnHeight(3.0D), EPSILON);
        assertEquals(1.0D, look.columnHeight(0.1D), EPSILON, "held up to the floor");
        assertEquals(8.0D, look.columnHeight(16.0D), EPSILON, "and down to the ceiling");
        assertEquals(0.05D, look.boilSpeed(0.0D), EPSILON);
        assertEquals(0.25D, look.boilSpeed(1.0D), EPSILON);
        assertFalse(look.eruptSound().isEnabled());
    }

    @Test
    @DisplayName("editing the geyser after the fuse was lit leaves the burning one alone")
    void theGeyserLookDoesNotFollowLaterEdits() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        BossGeyserScheduler.Look look = BossGeyserScheduler.look(geyser);

        geyser.setVfxTicks(200);
        geyser.setColumnMinTenths(0);
        geyser.setColumnMaxTenths(0);
        geyser.getEruptSound().setEnabled(false);

        assertEquals(20, look.vfxTicks());
        // Three blocks of radius at a block and a half each is four and a half, the old number.
        assertEquals(4.5D, look.columnHeight(3.0D), EPSILON);
        assertEquals(0.02D, look.boilSpeed(0.0D), EPSILON);
        assertEquals(0.12D, look.boilSpeed(1.0D), EPSILON);
        assertTrue(look.eruptSound().isEnabled());
    }

    @Test
    @DisplayName("a geyser held to no column at either end draws none")
    void aColumnOfNoughtIsNoColumnAtAll() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        geyser.setColumnMinTenths(0);
        geyser.setColumnMaxTenths(0);
        assertEquals(0.0D, BossGeyserScheduler.look(geyser).columnHeight(16.0D), EPSILON);
    }

    @Test
    @DisplayName("a mark is set with the look the settings had on that tick")
    void theMarkTakesItsLookOnTheCast() {
        BossMarkSettings mark = new BossMarkSettings();
        mark.setVfxTicks(60);
        mark.setFuseMinHundredths(0);
        mark.setFuseMaxHundredths(50);
        mark.setCarrierParticles(0);
        mark.getBlastSound().setVolume(1);

        BossMarkScheduler.Look look = BossMarkScheduler.look(mark);
        assertEquals(60, look.vfxTicks());
        assertEquals(0.0D, look.fuseSpeed(0.0D), EPSILON);
        assertEquals(0.5D, look.fuseSpeed(1.0D), EPSILON);
        assertEquals(0, look.carrierParticles());
        assertEquals(1, look.blastSound().getVolume());
    }

    @Test
    @DisplayName("editing the mark after it was handed out leaves the burning one alone")
    void theMarkLookDoesNotFollowLaterEdits() {
        BossMarkSettings mark = new BossMarkSettings();
        BossMarkScheduler.Look look = BossMarkScheduler.look(mark);

        mark.setVfxTicks(200);
        mark.setCarrierParticles(0);
        mark.setFuseMaxHundredths(100);
        mark.getDefusedSound().setEnabled(false);

        assertEquals(20, look.vfxTicks());
        assertEquals(2, look.carrierParticles());
        assertEquals(0.12D, look.fuseSpeed(1.0D), EPSILON);
        assertTrue(look.defusedSound().isEnabled());
    }
}
