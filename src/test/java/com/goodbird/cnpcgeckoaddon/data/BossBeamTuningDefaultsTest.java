package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins where the beams sit and what they are drawn with to the literals they replaced.
 *
 * <p>The height in particular is the whole mechanic: a sweep raised a block would pass over a
 * crouching player, and a default nudged here would do that to every boss at once.</p>
 */
class BossBeamTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh beam is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossBeamSettings beam = new BossBeamSettings();

        assertEquals(10, beam.getMaxHeightTenths());
        assertEquals(1.0D, beam.getMaxHeight(), EPSILON);
        assertEquals(20, beam.getVictimSlackTenths());
        assertEquals(2.0D, beam.getVictimSlack(), EPSILON);
        assertEquals(2, beam.getWallSparks());
        assertEquals(6, beam.getAccentOneIn());
        assertEquals(12, beam.getRareAccentOneIn());

        assertTrue(beam.getStartSound().isEnabled());
        assertEquals("minecraft:entity.guardian.attack", beam.getStartSound().getSoundId());
        assertEquals(20, beam.getStartSound().getVolume());
        assertEquals(6, beam.getStartSound().getPitch());
    }

    @Test
    @DisplayName("a boss saved before any of this existed sweeps exactly as it used to")
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
        BossBeamSettings beam = reloaded.beam();
        assertEquals(1.0D, beam.getMaxHeight(), EPSILON);
        assertEquals(2.0D, beam.getVictimSlack(), EPSILON);
        assertEquals(2, beam.getWallSparks());
        assertEquals(6, beam.getAccentOneIn());
        assertEquals(12, beam.getRareAccentOneIn());
        assertTrue(beam.getStartSound().isEnabled());
        assertEquals("minecraft:entity.guardian.attack", beam.getStartSound().getSoundId());
        assertEquals(20, beam.getStartSound().getVolume());
        assertEquals(6, beam.getStartSound().getPitch());
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("BeamMaxHeight") || key.equals("BeamVictimSlack")
                || key.equals("BeamWallSparks") || key.equals("BeamAccent")
                || key.equals("BeamRareAccent") || key.startsWith("BeamStartSound");
    }
}
