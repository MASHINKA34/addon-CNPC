package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the journey to a cast spot to the numbers it used to be written with.
 *
 * <p>Every ability on the rotation carries one of these, so a default moved here moves
 * twenty-two of them at once. What the walk speed percentage does to the pace is pinned by
 * {@code BossCastSpotWalkTest}, against the runtime that works it out.</p>
 */
class BossCastSpotTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh spot is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossCastSpot spot = new BossCastSpot();

        assertEquals(10, spot.getArrivalDistanceTenths());
        assertEquals(1.0D, spot.getArrivalDistance(), EPSILON);
        assertEquals(3, spot.getGroundSearch());
        assertEquals(4, spot.getRepathInterval());
        assertEquals(100, spot.getRetryTicks());
        assertEquals(100, spot.getWalkSpeedPercent());
    }

    @Test
    @DisplayName("a boss saved before any of this existed walks the way it always did")
    void anOldSaveKeepsTheOldBehaviour() {
        CompoundTag old = new BossPhaseData().writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (key.startsWith("BeamSpotArrival") || key.startsWith("BeamSpotGroundSearch")
                    || key.startsWith("BeamSpotRepath") || key.startsWith("BeamSpotRetry")
                    || key.startsWith("BeamSpotWalkSpeed")) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        BossCastSpot spot = reloaded.beam().castSpot();
        assertEquals(1.0D, spot.getArrivalDistance(), EPSILON);
        assertEquals(3, spot.getGroundSearch());
        assertEquals(4, spot.getRepathInterval());
        assertEquals(100, spot.getRetryTicks());
        assertEquals(100, spot.getWalkSpeedPercent());
    }
}
