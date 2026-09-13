package com.goodbird.cnpcgeckoaddon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the npc mechanics that became settings to the literals they replaced.
 *
 * <p>Spelled out rather than compared against the constants they came from: "the pad is harder
 * to step on than it used to be" is a regression nobody would trace back to a settings class,
 * and a default quietly edited along with a range would take the old behaviour with it.</p>
 */
class NpcTuningDefaultsTest {

    @Test
    @DisplayName("a fresh launch pad is yesterday's constants")
    void launchPadDefaultsAreTheOldConstants() {
        NpcLaunchPadData pad = new NpcLaunchPadData();

        assertEquals(3, pad.getTouchMarginTenths(), "TOUCH_MARGIN was 0.3 of a block");
        assertEquals(40, pad.getLandingGraceTicks(), "LANDING_GRACE_TICKS was 40");
    }

    @Test
    @DisplayName("the pad's three cues are born as the calls they replaced")
    void launchPadCuesAreTheOldCalls() {
        NpcLaunchPadData pad = new NpcLaunchPadData();

        assertSound(pad.getLaunchSound(), "minecraft:block.slime_block.fall", 10, 12);
        assertParticles(pad.getLaunchParticles(), "minecraft:cloud", 12);
        assertParticles(pad.getExpireParticles(), "minecraft:poof", 8);
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
