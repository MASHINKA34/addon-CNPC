package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the arena hazard's reach and its blink to the two literals they replaced.
 *
 * <p>Both are quiet when they are wrong. A reach moved down leaves somebody standing in fire
 * they can no longer be hurt by; a reach moved up bleeds a player who respawned across the map.
 * Neither throws, so the defaults are spelled out here rather than read off the fields.</p>
 */
class BossHazardTuningDefaultsTest {

    @Test
    @DisplayName("a fresh hazard is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossHazardSettings hazard = new BossHazardSettings();

        assertEquals(32, hazard.getRingReach());
        assertEquals(4, hazard.getBlinkTicks());
    }

    @Test
    @DisplayName("a boss saved before any of this existed burns exactly the same ground")
    void anOldSaveKeepsTheOldBehaviour() {
        BossPhaseData written = new BossPhaseData();
        CompoundTag old = written.writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (key.equals("HazardRingReach") || key.equals("HazardBlink")) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        assertEquals(32, reloaded.hazard().getRingReach());
        assertEquals(4, reloaded.hazard().getBlinkTicks());
    }

    @Test
    @DisplayName("the hazard and its screen offer the same reach and the same blink")
    void theRangesAreOnePair() {
        BossHazardSettings hazard = new BossHazardSettings();
        hazard.setRingReach(Integer.MAX_VALUE);
        assertEquals(BossHazardSettings.MAX_RING_REACH, hazard.getRingReach());
        hazard.setRingReach(Integer.MIN_VALUE);
        assertEquals(BossHazardSettings.MIN_RING_REACH, hazard.getRingReach());
        hazard.setBlinkTicks(Integer.MAX_VALUE);
        assertEquals(BossHazardSettings.MAX_BLINK_TICKS, hazard.getBlinkTicks());
        // Never nought: the blink is a modulus, and a nought there is a crash rather than a
        // steady edge.
        hazard.setBlinkTicks(0);
        assertEquals(BossHazardSettings.MIN_BLINK_TICKS, hazard.getBlinkTicks());
    }
}
