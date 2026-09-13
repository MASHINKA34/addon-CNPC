package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the stone's roll, its arc, its life and its break to the literals they replaced, and
 * the volley's mark to its own two.
 *
 * <p>Spelled out rather than compared against the fields they came from: "the boulder stops at
 * the step it used to climb" is a regression nobody would trace back to a settings class.</p>
 */
class BossBoulderTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh boulder is yesterday's constants")
    void boulderDefaultsAreTheOldConstants() {
        BossBoulderSettings boulder = new BossBoulderSettings();

        assertEquals(10, boulder.getStepHeightTenths());
        assertEquals(1.0D, boulder.getStepHeight(), EPSILON);
        assertEquals(15, boulder.getMaxFallSpeedTenths());
        assertEquals(1.5D, boulder.getMaxFallSpeed(), EPSILON);
        assertEquals(16, boulder.getMaxPitDepth());
        assertEquals(50, boulder.getThrowGravityThousandths());
        assertEquals(0.05D, boulder.getThrowGravity(), EPSILON);
        assertEquals(60, boulder.getLifetimeMarginTicks());
        assertEquals(1500, boulder.getLifetimeMaxTicks());
        assertEquals(20, boulder.getDebrisBase());
        assertEquals(15, boulder.getDebrisPerSize());
        assertEquals(20, boulder.getShatterVfxTicks());
        assertEquals(25, boulder.getMuzzleOffsetHundredths());
        assertEquals(0.25D, boulder.getMuzzleOffset(), EPSILON);

        // No id at all: the stone is still heard as the block it is made of.
        assertOwnSound(boulder.getThrowSound(), 15, 6);
        assertOwnSound(boulder.getBreakSound(), 20, 7);
    }

    @Test
    @DisplayName("a fresh volley is yesterday's constants")
    void rainDefaultsAreTheOldConstants() {
        BossBoulderRainSettings rain = new BossBoulderRainSettings();

        assertEquals(10, rain.getMinMarkRadiusTenths());
        assertEquals(1.0D, rain.getMinMarkRadius(), EPSILON);
        assertEquals(10, rain.getMinDropTenths());
        assertEquals(1.0D, rain.getMinDrop(), EPSILON);
        assertOwnSound(rain.getMarkSound(), 12, 5);
    }

    @Test
    @DisplayName("the stone and the screen offer the same speed and the same size")
    void theRangesAreOnePair() {
        BossBoulderSettings boulder = new BossBoulderSettings();
        boulder.setSpeed(Integer.MAX_VALUE);
        assertEquals(BossBoulderSettings.MAX_SPEED, boulder.getSpeed());
        boulder.setSpeed(Integer.MIN_VALUE);
        assertEquals(BossBoulderSettings.MIN_SPEED, boulder.getSpeed());
        boulder.setScale(Integer.MAX_VALUE);
        assertEquals(BossBoulderSettings.MAX_SCALE, boulder.getScale());
        boulder.setScale(Integer.MIN_VALUE);
        assertEquals(BossBoulderSettings.MIN_SCALE, boulder.getScale());
    }

    @Test
    @DisplayName("a boss saved before any of this existed rolls exactly as it used to")
    void anOldSaveKeepsTheOldBehaviour() {
        BossPhaseData written = new BossPhaseData();
        CompoundTag old = written.writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (isNewKey(key)) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        BossBoulderSettings boulder = reloaded.boulder();
        assertEquals(1.0D, boulder.getStepHeight(), EPSILON);
        assertEquals(1.5D, boulder.getMaxFallSpeed(), EPSILON);
        assertEquals(16, boulder.getMaxPitDepth());
        assertEquals(0.05D, boulder.getThrowGravity(), EPSILON);
        assertEquals(60, boulder.getLifetimeMarginTicks());
        assertEquals(1500, boulder.getLifetimeMaxTicks());
        assertEquals(20, boulder.getDebrisBase());
        assertEquals(15, boulder.getDebrisPerSize());
        assertEquals(20, boulder.getShatterVfxTicks());
        assertEquals(0.25D, boulder.getMuzzleOffset(), EPSILON);
        assertOwnSound(boulder.getThrowSound(), 15, 6);
        assertOwnSound(boulder.getBreakSound(), 20, 7);

        BossBoulderRainSettings rain = reloaded.boulderRain();
        assertEquals(1.0D, rain.getMinMarkRadius(), EPSILON);
        assertEquals(1.0D, rain.getMinDrop(), EPSILON);
        assertOwnSound(rain.getMarkSound(), 12, 5);
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("BoulderStepHeight") || key.equals("BoulderMaxFallSpeed")
                || key.equals("BoulderMaxPitDepth") || key.equals("BoulderThrowGravity")
                || key.equals("BoulderLifetimeMargin") || key.equals("BoulderLifetimeMax")
                || key.equals("BoulderDebrisBase") || key.equals("BoulderDebrisPerSize")
                || key.equals("BoulderShatterVfx") || key.equals("BoulderMuzzle")
                || key.startsWith("BoulderThrowSound") || key.startsWith("BoulderBreakSound")
                || key.equals("BoulderRainMinMark") || key.equals("BoulderRainMinDrop")
                || key.startsWith("BoulderRainMarkSound");
    }

    private static void assertOwnSound(BossSoundCue cue, int volume, int pitch) {
        assertTrue(cue.isEnabled());
        assertTrue(BossSoundCue.isOwnSound(cue.getSoundId()),
                "an empty id is what means 'the block's own sound'");
        assertEquals(volume, cue.getVolume());
        assertEquals(pitch, cue.getPitch());
    }
}
