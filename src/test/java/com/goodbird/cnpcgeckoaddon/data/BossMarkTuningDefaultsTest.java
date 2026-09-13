package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the mark's wave, its fuse sparks, the sparks over its carrier, how far it is handed out
 * and its three noises to the literals they replaced.
 *
 * <p>The reach is the one that changes a fight without looking like it: shorten it and the boss
 * simply stops marking the people it used to. Spelled out here rather than read off the field
 * for that reason.</p>
 */
class BossMarkTuningDefaultsTest {

    @Test
    @DisplayName("a fresh mark is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossMarkSettings mark = new BossMarkSettings();

        assertEquals(20, mark.getVfxTicks());
        assertEquals(2, mark.getFuseMinHundredths());
        assertEquals(12, mark.getFuseMaxHundredths());
        assertEquals(2, mark.getCarrierParticles());
        assertEquals(32, mark.getReach());
    }

    @Test
    @DisplayName("every mark cue is born as the call it replaced")
    void cuesAreTheOldCalls() {
        BossMarkSettings mark = new BossMarkSettings();

        assertSound(mark.getMarkedSound(), "minecraft:block.note_block.bell", 12, 18);
        assertSound(mark.getDefusedSound(), "minecraft:block.fire.extinguish", 10, 14);
        assertSound(mark.getBlastSound(), "minecraft:entity.generic.explode", 20, 14);
    }

    @Test
    @DisplayName("a boss saved before any of this existed marks exactly as it used to")
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
        BossMarkSettings mark = reloaded.mark();
        assertEquals(20, mark.getVfxTicks());
        assertEquals(2, mark.getFuseMinHundredths());
        assertEquals(12, mark.getFuseMaxHundredths());
        assertEquals(2, mark.getCarrierParticles());
        assertEquals(32, mark.getReach());
        assertSound(mark.getMarkedSound(), "minecraft:block.note_block.bell", 12, 18);
        assertSound(mark.getDefusedSound(), "minecraft:block.fire.extinguish", 10, 14);
        assertSound(mark.getBlastSound(), "minecraft:entity.generic.explode", 20, 14);
    }

    /**
     * {@code MarkVfx} was already taken by the style the wave is drawn in, so the wave's length
     * had to be given a key of its own.
     */
    @Test
    @DisplayName("the wave's length and the wave's style keep their own keys")
    void theWaveKeysDoNotCollide() {
        BossPhaseData phase = new BossPhaseData();
        phase.mark().setVfx(AreaVfxStyles.FIRE);
        phase.mark().setVfxTicks(77);

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(phase.writeToNBT());
        assertEquals(AreaVfxStyles.FIRE, reloaded.mark().getVfx());
        assertEquals(77, reloaded.mark().getVfxTicks());
    }

    @Test
    @DisplayName("the mark and its screen offer the same reach and the same spark count")
    void theRangesAreOnePair() {
        BossMarkSettings mark = new BossMarkSettings();
        mark.setReach(Integer.MAX_VALUE);
        assertEquals(BossMarkSettings.MAX_REACH, mark.getReach());
        mark.setReach(Integer.MIN_VALUE);
        assertEquals(BossMarkSettings.MIN_REACH, mark.getReach());
        // Nought is a real answer here: no sparks over the carrier at all.
        mark.setCarrierParticles(Integer.MIN_VALUE);
        assertEquals(0, mark.getCarrierParticles());
        mark.setCarrierParticles(Integer.MAX_VALUE);
        assertEquals(BossMarkSettings.MAX_CARRIER_PARTICLES, mark.getCarrierParticles());
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("MarkVfxTicks") || key.equals("MarkFuseMin")
                || key.equals("MarkFuseMax") || key.equals("MarkCarrierParticles")
                || key.equals("MarkReach") || key.startsWith("MarkMarkedSound")
                || key.startsWith("MarkDefusedSound") || key.startsWith("MarkBlastSound");
    }

    private static void assertSound(BossSoundCue cue, String id, int volume, int pitch) {
        assertNotNull(cue);
        assertTrue(cue.isEnabled());
        assertEquals(id, cue.getSoundId());
        assertEquals(volume, cue.getVolume());
        assertEquals(pitch, cue.getPitch());
    }
}
