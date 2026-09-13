package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the platform's blink, its countdown, its pops and its two noises to the literals they
 * replaced.
 *
 * <p>The promise of this batch is that a boss nobody has touched burns exactly as it did before
 * any of these were settings. A default quietly moved here would change every platform on every
 * server at once and read in play as "the outline flickers differently now", so each one is
 * spelled out rather than compared against the field it came from.</p>
 */
class BossPlatformTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh platform is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossPlatformSettings platform = new BossPlatformSettings();

        assertEquals(4, platform.getBlinkTicks());
        assertEquals(20, platform.getCountdownIntervalTicks());
        assertEquals(24, platform.getFlareMax());
        assertEquals(40, platform.getFlareArea());
        assertEquals(4.0D, platform.flareAreaPerPop(), EPSILON);
    }

    @Test
    @DisplayName("every platform cue is born as the call it replaced")
    void cuesAreTheOldCalls() {
        BossPlatformSettings platform = new BossPlatformSettings();

        assertSound(platform.getLitSound(), "minecraft:entity.tnt.primed", 15, 8);
        assertParticles(platform.getOutlineParticles(), "minecraft:flame", 1);
        assertParticles(platform.getBlastParticles(), "minecraft:lava", 1);
        assertSound(platform.getBlastSound(), "minecraft:entity.generic.explode", 20, 9);
    }

    /**
     * The bang's two cues sit under {@code PlatformBlast} and {@code PlatformBlastParticles},
     * which share a prefix: one writing over the other would be silent, and would read back as
     * a builder's edit undoing itself.
     */
    @Test
    @DisplayName("the sound cue and the particle cue of the bang keep their keys apart")
    void theBlastPairDoesNotShareKeys() {
        BossPhaseData phase = new BossPhaseData();
        phase.platform().getBlastSound().setVolume(1);
        phase.platform().getBlastParticles().setCount(7);

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(phase.writeToNBT());
        assertEquals(1, reloaded.platform().getBlastSound().getVolume());
        assertEquals(7, reloaded.platform().getBlastParticles().getCount());
    }

    @Test
    @DisplayName("a boss saved before any of this existed burns exactly as it used to")
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
        BossPlatformSettings platform = reloaded.platform();
        assertEquals(4, platform.getBlinkTicks());
        assertEquals(20, platform.getCountdownIntervalTicks());
        assertEquals(24, platform.getFlareMax());
        assertEquals(4.0D, platform.flareAreaPerPop(), EPSILON);
        assertSound(platform.getLitSound(), "minecraft:entity.tnt.primed", 15, 8);
        assertParticles(platform.getOutlineParticles(), "minecraft:flame", 1);
        assertParticles(platform.getBlastParticles(), "minecraft:lava", 1);
        assertSound(platform.getBlastSound(), "minecraft:entity.generic.explode", 20, 9);
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("PlatformBlink") || key.equals("PlatformCountdownInterval")
                || key.equals("PlatformFlareMax") || key.equals("PlatformFlareArea")
                || key.startsWith("PlatformLit") || key.startsWith("PlatformOutline")
                || key.startsWith("PlatformBlast");
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
