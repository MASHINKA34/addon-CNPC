package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the field's doses, its bite, its opening wave, its slack, its wait for a landing, its
 * wind and its four noises to the literals they replaced.
 *
 * <p>Spelled out rather than compared against the fields they came from: "the pull chews
 * faster than it used to" is a regression nobody would trace back to a settings class.</p>
 */
class BossGravityTuningDefaultsTest {

    @Test
    @DisplayName("a fresh gravity field is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossGravitySettings gravity = new BossGravitySettings();

        assertEquals(20, gravity.getEffectIntervalTicks());
        assertEquals(20, gravity.getBiteIntervalTicks());
        assertEquals(20, gravity.getVfxTicks());
        assertEquals(10, gravity.getPullSlackTenths(), "one block of slack, as the constant was");
        assertEquals(400, gravity.getLandingTimeoutTicks());
        assertEquals(3, gravity.getStreamParticles());
        assertEquals(35, gravity.getStreamInnerPercent());
        assertEquals(20, gravity.getStreamHeightTenths(), "two blocks of wind, as the constant was");
    }

    @Test
    @DisplayName("all four gravity cues are born as the calls they replaced")
    void cuesAreTheOldCalls() {
        BossGravitySettings gravity = new BossGravitySettings();

        assertSound(gravity.getOpenSound(), "minecraft:block.beacon.activate", 15, 5);
        assertSound(gravity.getPushSound(), "minecraft:entity.wind_charge.wind_burst", 15, 7);
        assertSound(gravity.getLaunchSound(), "minecraft:entity.wind_charge.wind_burst", 20, 5);
        assertSound(gravity.getLandingSound(), "minecraft:entity.generic.big_fall", 10, 8);
    }

    @Test
    @DisplayName("a boss saved before any of this existed pulls exactly as it used to")
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
        BossGravitySettings gravity = reloaded.gravity();
        assertEquals(20, gravity.getEffectIntervalTicks());
        assertEquals(20, gravity.getBiteIntervalTicks());
        assertEquals(20, gravity.getVfxTicks());
        assertEquals(10, gravity.getPullSlackTenths());
        assertEquals(400, gravity.getLandingTimeoutTicks());
        assertEquals(3, gravity.getStreamParticles());
        assertEquals(35, gravity.getStreamInnerPercent());
        assertEquals(20, gravity.getStreamHeightTenths());
        assertSound(gravity.getOpenSound(), "minecraft:block.beacon.activate", 15, 5);
        assertSound(gravity.getPushSound(), "minecraft:entity.wind_charge.wind_burst", 15, 7);
        assertSound(gravity.getLaunchSound(), "minecraft:entity.wind_charge.wind_burst", 20, 5);
        assertSound(gravity.getLandingSound(), "minecraft:entity.generic.big_fall", 10, 8);
    }

    /**
     * {@code GravityVfx} was already taken by the style the wave is drawn in, so the wave's
     * length had to be given a key of its own: writing both under one name would have left a
     * boss whose style silently reset to none.
     */
    @Test
    @DisplayName("the wave's length and the wave's style keep their own keys")
    void theWaveKeysDoNotCollide() {
        BossPhaseData phase = new BossPhaseData();
        phase.gravity().setVfx(AreaVfxStyles.FIRE);
        phase.gravity().setVfxTicks(77);

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(phase.writeToNBT());
        assertEquals(AreaVfxStyles.FIRE, reloaded.gravity().getVfx());
        assertEquals(77, reloaded.gravity().getVfxTicks());
    }

    /** A wind of no motes is a real answer: the field keeps its ring and goes quiet. */
    @Test
    @DisplayName("the wind may be switched off by the count alone")
    void aWindOfNoMotesIsAllowed() {
        BossGravitySettings gravity = new BossGravitySettings();

        gravity.setStreamParticles(0);
        assertEquals(0, gravity.getStreamParticles());
        gravity.setStreamHeightTenths(0);
        assertEquals(1, gravity.getStreamHeightTenths(), "no height at all would be a flat line, not a wind");
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("GravityEffectInterval") || key.equals("GravityBiteInterval")
                || key.equals("GravityVfxTicks") || key.equals("GravityPullSlack")
                || key.equals("GravityLandingTimeout") || key.startsWith("GravityStream")
                || key.startsWith("GravityOpenSound") || key.startsWith("GravityPushSound")
                || key.startsWith("GravityLaunchSound") || key.startsWith("GravityLandingSound");
    }

    private static void assertSound(BossSoundCue cue, String id, int volume, int pitch) {
        assertNotNull(cue);
        assertTrue(cue.isEnabled());
        assertEquals(id, cue.getSoundId());
        assertEquals(volume, cue.getVolume());
        assertEquals(pitch, cue.getPitch());
    }
}
