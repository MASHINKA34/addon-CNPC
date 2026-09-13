package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the box a minion has to fit in and the hole in the fallback ring to the literals they
 * replaced.
 *
 * <p>Spelled out rather than compared against the fields they came from: "the summon stopped
 * placing minions where it used to" is a regression nobody would trace back to a settings
 * class.</p>
 */
class BossSummonTuningDefaultsTest {

    @Test
    @DisplayName("a fresh summon is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossSummonSettings summon = new BossSummonSettings();

        assertEquals(35, summon.getFitHalfWidthHundredths(), "0.35 either side, as the constant was");
        assertEquals(18, summon.getFitHeightTenths(), "1.8 up, as the constant was");
        assertEquals(10, summon.getRingInnerRadiusTenths(), "one block of hole, as the ring had");
    }

    @Test
    @DisplayName("a boss saved before any of this existed places minions exactly as it used to")
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
        BossSummonSettings summon = reloaded.summon();
        assertEquals(35, summon.getFitHalfWidthHundredths());
        assertEquals(18, summon.getFitHeightTenths());
        assertEquals(10, summon.getRingInnerRadiusTenths());
    }

    /** A ring with no hole is a real answer: minions may land on the boss' own feet. */
    @Test
    @DisplayName("the ring may be told to leave no hole, and a minion always needs some room")
    void theRangesAreTheOnesTheScreenOffers() {
        BossSummonSettings summon = new BossSummonSettings();

        summon.setRingInnerRadiusTenths(0);
        assertEquals(0, summon.getRingInnerRadiusTenths());
        summon.setFitHalfWidthHundredths(0);
        assertEquals(5, summon.getFitHalfWidthHundredths(), "a box of no width would fit inside a wall");
        summon.setFitHeightTenths(0);
        assertEquals(5, summon.getFitHeightTenths());
        summon.setFitHalfWidthHundredths(100);
        assertEquals(100, summon.getFitHalfWidthHundredths(), "a two block wide clone is a fair minion");
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("SummonFitHalfWidth") || key.equals("SummonFitHeight")
                || key.equals("SummonRingInner");
    }
}
