package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the eruption's wave, its column, its boil and its two noises to the literals they
 * replaced.
 *
 * <p>Spelled out rather than compared against the fields they came from: "the geyser is shorter
 * than it used to be" is a regression nobody would trace back to a settings class.</p>
 */
class BossGeyserTuningDefaultsTest {

    @Test
    @DisplayName("a fresh geyser is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossGeyserSettings geyser = new BossGeyserSettings();

        assertEquals(20, geyser.getVfxTicks());
        assertEquals(15, geyser.getColumnPerRadiusTenths());
        assertEquals(30, geyser.getColumnMinTenths());
        assertEquals(120, geyser.getColumnMaxTenths());
        assertEquals(2, geyser.getBoilMinHundredths());
        assertEquals(12, geyser.getBoilMaxHundredths());
    }

    @Test
    @DisplayName("both geyser cues are born as the call they replaced")
    void cuesAreTheOldCalls() {
        BossGeyserSettings geyser = new BossGeyserSettings();

        assertSound(geyser.getLitSound(), "minecraft:block.lava.pop", 16, 5);
        assertSound(geyser.getEruptSound(), "minecraft:block.lava.extinguish", 30, 5);
    }

    /**
     * The puddle used to be held to four blocks whatever the screen offered, so a builder who
     * asked for sixteen got four and no word about it.
     */
    @Test
    @DisplayName("the radius the screen offers is the radius the puddle is laid at")
    void theScreenAndThePuddleAgreeOnTheRadius() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        geyser.setRadius(16);
        assertEquals(16, geyser.getRadius());
        geyser.setRadius(Integer.MAX_VALUE);
        assertEquals(16, geyser.getRadius(), "sixteen is the whole of what the screen offers");
    }

    @Test
    @DisplayName("a boss saved before any of this existed erupts exactly as it used to")
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
        BossGeyserSettings geyser = reloaded.geyser();
        assertEquals(20, geyser.getVfxTicks());
        assertEquals(15, geyser.getColumnPerRadiusTenths());
        assertEquals(30, geyser.getColumnMinTenths());
        assertEquals(120, geyser.getColumnMaxTenths());
        assertEquals(2, geyser.getBoilMinHundredths());
        assertEquals(12, geyser.getBoilMaxHundredths());
        assertSound(geyser.getLitSound(), "minecraft:block.lava.pop", 16, 5);
        assertSound(geyser.getEruptSound(), "minecraft:block.lava.extinguish", 30, 5);
    }

    /**
     * {@code GeyserVfx} was already taken by the style the wave is drawn in, so the wave's
     * length had to be given a key of its own: writing both under one name would have left a
     * boss whose style silently reset to none.
     */
    @Test
    @DisplayName("the wave's length and the wave's style keep their own keys")
    void theWaveKeysDoNotCollide() {
        BossPhaseData phase = new BossPhaseData();
        phase.geyser().setVfx(AreaVfxStyles.FIRE);
        phase.geyser().setVfxTicks(77);

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(phase.writeToNBT());
        assertEquals(AreaVfxStyles.FIRE, reloaded.geyser().getVfx());
        assertEquals(77, reloaded.geyser().getVfxTicks());
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("GeyserVfxTicks") || key.equals("GeyserColumnPerRadius")
                || key.equals("GeyserColumnMin") || key.equals("GeyserColumnMax")
                || key.equals("GeyserBoilMin") || key.equals("GeyserBoilMax")
                || key.startsWith("GeyserLitSound") || key.startsWith("GeyserEruptSound");
    }

    private static void assertSound(BossSoundCue cue, String id, int volume, int pitch) {
        assertNotNull(cue);
        assertTrue(cue.isEnabled());
        assertEquals(id, cue.getSoundId());
        assertEquals(volume, cue.getVolume());
        assertEquals(pitch, cue.getPitch());
    }
}
