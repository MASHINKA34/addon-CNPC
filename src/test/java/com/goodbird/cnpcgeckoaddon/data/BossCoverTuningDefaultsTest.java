package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the take cover strike's sight line, its wave, its shelter spacing and its bang to the
 * literals they replaced.
 *
 * <p>The sight line is the one nobody would trace back here: move it and a slab stops being
 * cover, or starts being it, and the mechanic the party learned answers differently. Spelled
 * out rather than read off the fields for that reason.</p>
 */
class BossCoverTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh strike is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossCoverSettings cover = new BossCoverSettings();

        assertEquals(25, cover.getKneeHeightHundredths());
        assertEquals(0.25D, cover.getKneeHeight(), EPSILON);
        assertEquals(10, cover.getWaveSpeedTenths());
        assertEquals(1.0D, cover.getWaveSpeed(), EPSILON);
        assertEquals(20, cover.getVfxMinTicks());
        assertEquals(60, cover.getVfxMaxTicks());
        assertEquals(3, cover.getPostHeight());
        assertEquals(200, cover.getShelterSpacingPercent());
        // Two radii apart is the two circles standing edge to edge, which is what the
        // literal 2.0 in the placement meant.
        assertEquals(cover.getShelterRadius() * 2.0D, cover.shelterSpacing(), EPSILON);
    }

    @Test
    @DisplayName("both cover cues are born as the call they replaced")
    void cuesAreTheOldCalls() {
        BossCoverSettings cover = new BossCoverSettings();

        assertSound(cover.getBlastSound(), "minecraft:entity.generic.explode", 40, 6);
        assertParticles(cover.getBlastParticles(), BossParticleCue.DUST_ID, 40);
    }

    @Test
    @DisplayName("a boss saved before any of this existed strikes exactly as it used to")
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
        BossCoverSettings cover = reloaded.cover();
        assertEquals(0.25D, cover.getKneeHeight(), EPSILON);
        assertEquals(1.0D, cover.getWaveSpeed(), EPSILON);
        assertEquals(20, cover.getVfxMinTicks());
        assertEquals(60, cover.getVfxMaxTicks());
        assertEquals(3, cover.getPostHeight());
        assertEquals(200, cover.getShelterSpacingPercent());
        assertSound(cover.getBlastSound(), "minecraft:entity.generic.explode", 40, 6);
        assertParticles(cover.getBlastParticles(), BossParticleCue.DUST_ID, 40);
    }

    @Test
    @DisplayName("the strike and its screen offer the same sight line and the same spacing")
    void theRangesAreOnePair() {
        BossCoverSettings cover = new BossCoverSettings();
        cover.setKneeHeightHundredths(Integer.MAX_VALUE);
        assertEquals(BossCoverSettings.MAX_KNEE_HEIGHT, cover.getKneeHeightHundredths());
        cover.setKneeHeightHundredths(Integer.MIN_VALUE);
        assertEquals(0, cover.getKneeHeightHundredths());
        cover.setWaveSpeedTenths(0);
        // Never nought: the duration divides by it, and nought there is not a slow wave.
        assertEquals(BossCoverSettings.MIN_WAVE_SPEED, cover.getWaveSpeedTenths());
        cover.setShelterSpacingPercent(Integer.MIN_VALUE);
        assertEquals(BossCoverSettings.MIN_SHELTER_SPACING, cover.getShelterSpacingPercent());
        cover.setShelterSpacingPercent(Integer.MAX_VALUE);
        assertEquals(BossCoverSettings.MAX_SHELTER_SPACING, cover.getShelterSpacingPercent());
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("CoverKneeHeight") || key.equals("CoverWaveSpeed")
                || key.equals("CoverVfxMin") || key.equals("CoverVfxMax")
                || key.equals("CoverPostHeight") || key.equals("CoverShelterSpacing")
                || key.startsWith("CoverBlastSound") || key.startsWith("CoverBlastParticles");
    }

    private static void assertSound(BossSoundCue cue, String id, int volume, int pitch) {
        assertNotNull(cue);
        assertTrue(cue.isEnabled());
        assertEquals(id, cue.getSoundId());
        assertEquals(volume, cue.getVolume());
        assertEquals(pitch, cue.getPitch());
    }

    private static void assertParticles(BossParticleCue cue, String id, int count) {
        assertNotNull(cue);
        assertTrue(cue.isEnabled());
        assertEquals(id, cue.getParticleId());
        assertEquals(count, cue.getCount());
    }
}
