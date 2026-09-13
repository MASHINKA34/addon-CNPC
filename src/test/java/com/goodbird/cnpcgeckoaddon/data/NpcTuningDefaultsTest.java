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

    /**
     * The colours are the old floats rounded onto the 0-255 scale a hex field can hold:
     * 0.35/0.95/0.45 and 0.95/0.25/0.25, each channel to the nearest step.
     */
    @Test
    @DisplayName("a fresh carry is yesterday's constants")
    void carryDefaultsAreTheOldConstants() {
        NpcCarryData carry = new NpcCarryData();

        assertEquals(20, carry.getCarryDistanceTenths(), "CARRY_DISTANCE was 2.0 blocks");
        assertEquals(35, carry.getCarryDropHundredths(), "CARRY_DROP was 0.35 of a block");
        assertEquals(6, carry.getPlaceReach(), "PLACE_REACH was 6 blocks");
        assertEquals(0x59F273, carry.getPreviewFreeColor());
        assertEquals(0xF24040, carry.getPreviewBlockedColor());
        assertEquals(50, carry.getThrowGravityThousandths(), "THROW_GRAVITY was 0.05");
        assertEquals(12, carry.getThrowLiftHundredths(), "THROW_LIFT was 0.12");
        assertEquals(100, carry.getThrowMaxFlightTicks(), "MAX_FLIGHT_TICKS was 100");
    }

    @Test
    @DisplayName("a fresh ranged npc is yesterday's constants")
    void rangedDefaultsAreTheOldConstants() {
        RangedExtraData ranged = new RangedExtraData();

        assertEquals(-2, ranged.getMuzzleHeightTenths(), "the muzzle was eyeY - 0.2");
        assertEquals(20, ranged.getShotSoundVolumeTenths(), "the shot was played at 2.0F");
        assertEquals(10, ranged.getShotSoundPitchTenths(), "and at 1.0F");
    }

    /**
     * The two numbers the addon reads back out of CustomNPCs' own ranged data, held to what
     * its own editor offers: an explosion of none through large, one through ten shots.
     */
    @Test
    @DisplayName("the ranged clamps are CustomNPCs' own limits")
    void rangedClampsAreTheHostsOwn() {
        assertEquals(0, RangedExtraData.MIN_EXPLODE_SIZE, "\"none\" in the CustomNPCs editor is 0");
        assertEquals(3, RangedExtraData.MAX_EXPLODE_SIZE, "\"large\" is the last of its four choices");
        assertEquals(1, RangedExtraData.MIN_SHOT_COUNT);
        assertEquals(10, RangedExtraData.MAX_SHOT_COUNT, "what DataRanged clamps a loaded count to");
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
