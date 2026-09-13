package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the leap's flight numbers and cues to the literals they replaced.
 *
 * <p>The arc is solved from these: a reach correction or a speed ceiling quietly changed here
 * would leave every jump on every server landing short of the ring it drew, which reads as a
 * broken mechanic rather than as an edited setting. So each one is spelled out.</p>
 */
class BossLeapTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh leap is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossLeapSettings leap = new BossLeapSettings();

        assertEquals(103, leap.getReachPercent());
        assertEquals(1.03D, leap.getReachCorrection(), EPSILON);
        assertEquals(40, leap.getMaxSpeedTenths());
        assertEquals(4.0D, leap.getMaxSpeed(), EPSILON);
        assertEquals(5, leap.getLaunchGraceTicks());
        assertEquals(20, leap.getVfxTicks());
        assertEquals(15, leap.getLeashMarginTenths());
        assertEquals(1.5D, leap.getLeashMargin(), EPSILON, "the dash stops inside this edge too");
        assertEquals(30, leap.getDebrisCount());
    }

    @Test
    @DisplayName("every leap cue is born as the call it replaced")
    void cuesAreTheOldCalls() {
        BossLeapSettings leap = new BossLeapSettings();

        assertSound(leap.getTakeoffSound(), "minecraft:entity.ravager.roar", 15, 12);
        assertParticles(leap.getTakeoffParticles(), "minecraft:cloud", 20);
        assertSound(leap.getLandingSound(), "minecraft:block.anvil.land", 20, 5);
        assertParticles(leap.getLandingParticles(), "minecraft:explosion", 1);
        assertParticles(leap.getTrailParticles(), "minecraft:small_flame", 1);
    }

    @Test
    @DisplayName("a boss saved before any of this existed reads back as the old leap")
    void anOldSaveKeepsTheOldBehaviour() {
        CompoundTag old = new BossPhaseData().writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (isNewLeapKey(key)) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        BossLeapSettings leap = reloaded.leap();
        assertEquals(1.03D, leap.getReachCorrection(), EPSILON);
        assertEquals(4.0D, leap.getMaxSpeed(), EPSILON);
        assertEquals(5, leap.getLaunchGraceTicks());
        assertEquals(20, leap.getVfxTicks());
        assertEquals(1.5D, leap.getLeashMargin(), EPSILON);
        assertEquals(30, leap.getDebrisCount());
        assertSound(leap.getTakeoffSound(), "minecraft:entity.ravager.roar", 15, 12);
        assertParticles(leap.getTrailParticles(), "minecraft:small_flame", 1);
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewLeapKey(String key) {
        return key.equals("LeapReachPercent") || key.equals("LeapMaxSpeed")
                || key.equals("LeapLaunchGrace") || key.equals("LeapVfxTicks")
                || key.equals("LeapLeashMargin") || key.equals("LeapDebris")
                || key.startsWith("LeapTakeoffSound") || key.startsWith("LeapTakeoffParticles")
                || key.startsWith("LeapLandingSound") || key.startsWith("LeapLandingParticles")
                || key.startsWith("LeapTrailParticles");
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
