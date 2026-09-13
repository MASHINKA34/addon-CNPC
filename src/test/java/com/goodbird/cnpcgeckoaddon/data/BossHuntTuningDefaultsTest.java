package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the hook's yank, the chase's bite and mark, and the grab's noise to the literals they
 * replaced.
 *
 * <p>Three abilities in one file because they are one batch of the same promise: a boss nobody
 * has touched hooks, hunts and grabs exactly as it did.</p>
 */
class BossHuntTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh hook, hunt and capture are yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossHookSettings hook = new BossHookSettings();
        assertEquals(35, hook.getLiftMaxHundredths());
        assertEquals(0.35D, hook.getLiftMax(), EPSILON);
        assertEquals(3, hook.getLiftPerBlockHundredths());
        assertEquals(0.03D, hook.getLiftPerBlock(), EPSILON);
        assertTrue(hook.getCordSound().isEnabled());
        assertTrue(BossSoundCue.isOwnSound(hook.getCordSound().getSoundId()),
                "an empty id is what means 'the cord style's own voice'");
        assertEquals(20, hook.getCordSound().getVolume());
        assertParticles(hook.getCordParticles(), "minecraft:crit", 1);

        BossHuntSettings hunt = new BossHuntSettings();
        assertEquals(20, hunt.getCatchIntervalTicks());
        assertEquals("minecraft:glowing", hunt.getMarkEffect());
        assertEquals(0, hunt.getMarkAmplifier());
        assertEquals(1, hunt.getMarkLevel());
        assertTrue(hunt.isReachAddsModels());

        BossCaptureSettings capture = new BossCaptureSettings();
        assertTrue(capture.getCaptureSound().isEnabled());
        assertEquals("minecraft:block.beacon.activate", capture.getCaptureSound().getSoundId());
        assertEquals(8, capture.getCaptureSound().getVolume());
        assertEquals(14, capture.getCaptureSound().getPitch());
        assertParticles(capture.getCaptureParticles(), "minecraft:end_rod", 12);
    }

    @Test
    @DisplayName("a boss saved before any of this existed hooks, hunts and grabs as it used to")
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
        assertEquals(0.35D, reloaded.hook().getLiftMax(), EPSILON);
        assertEquals(0.03D, reloaded.hook().getLiftPerBlock(), EPSILON);
        assertTrue(BossSoundCue.isOwnSound(reloaded.hook().getCordSound().getSoundId()));
        assertParticles(reloaded.hook().getCordParticles(), "minecraft:crit", 1);
        assertEquals(20, reloaded.hunt().getCatchIntervalTicks());
        assertEquals("minecraft:glowing", reloaded.hunt().getMarkEffect());
        assertEquals(0, reloaded.hunt().getMarkAmplifier());
        assertTrue(reloaded.hunt().isReachAddsModels(), "the catch used to grow with both models");
        assertEquals("minecraft:block.beacon.activate", reloaded.capture().getCaptureSound().getSoundId());
        assertParticles(reloaded.capture().getCaptureParticles(), "minecraft:end_rod", 12);
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("HookLiftMax") || key.equals("HookLiftPerBlock")
                || key.startsWith("HookCordSound") || key.startsWith("HookCordParticles")
                || key.equals("HuntCatchInterval") || key.equals("HuntMarkEffect")
                || key.equals("HuntMarkAmplifier") || key.equals("HuntReachModels")
                || key.startsWith("CaptureSound") || key.startsWith("CaptureParticles");
    }

    private static void assertParticles(BossParticleCue cue, String id, int count) {
        assertTrue(cue.isEnabled());
        assertEquals(id, cue.getParticleId());
        assertEquals(count, cue.getCount());
    }
}
