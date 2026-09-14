package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the cone's flash, its landing turn and its swing to the literals they replaced. */
class BossConeTuningDefaultsTest {

    @Test
    @DisplayName("a fresh cone is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossConeSettings cone = new BossConeSettings();

        assertEquals(3, cone.getFlashArcs(), "a third, two thirds and the whole of the fan");
        assertEquals(360, cone.getSnapDegrees(), "a full circle is the turn finished outright");
        assertTrue(cone.getSwingSound().isEnabled());
        assertEquals("minecraft:entity.player.attack.sweep", cone.getSwingSound().getSoundId());
        assertEquals(15, cone.getSwingSound().getVolume());
        assertEquals(6, cone.getSwingSound().getPitch());
    }

    @Test
    @DisplayName("a boss saved before any of this existed reads back as the old cone")
    void anOldSaveKeepsTheOldBehaviour() {
        CompoundTag old = new BossPhaseData().writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (key.equals("ConeFlashArcs") || key.equals("ConeSnap") || key.startsWith("ConeSwingSound")) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        assertEquals(3, reloaded.cone().getFlashArcs());
        assertEquals(360, reloaded.cone().getSnapDegrees());
        assertEquals("minecraft:entity.player.attack.sweep", reloaded.cone().getSwingSound().getSoundId());
    }

    /**
     * The one place the cone's defaults are deliberately not yesterday's: a dodge by reach and a
     * cooldown from the wind-up are what made the cone swing so rarely. A default drifting back
     * to the old rule here would put every boss back to that without a word.
     */
    @Test
    @DisplayName("a fresh cone, and an old save, take the new start, dodge and cooldown rules")
    void theStartRulesAreNewOnPurpose() {
        BossConeSettings fresh = new BossConeSettings();
        assertEquals(BossPhaseData.CONE_DODGE_NEVER, fresh.getDodgeMode(), "the warning's end calls nothing off");
        assertFalse(fresh.isNeedsVictim(), "a cone along the gaze swings on its cooldown, fan empty or not");
        assertEquals(BossPhaseData.CONE_COOLDOWN_FROM_END, fresh.getCooldownFrom(), "the cooldown counts from the last cone");

        CompoundTag old = new BossPhaseData().writeToNBT();
        for (String key : List.of("ConeDodge", "ConeNeedsVictim", "ConeCooldownFrom")) {
            old.remove(key);
        }
        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        assertEquals(BossPhaseData.CONE_DODGE_NEVER, reloaded.cone().getDodgeMode());
        assertFalse(reloaded.cone().isNeedsVictim());
        assertEquals(BossPhaseData.CONE_COOLDOWN_FROM_END, reloaded.cone().getCooldownFrom());
    }

    @Test
    @DisplayName("no arcs is a strike with no flash at all, and the editor may ask for that")
    void noArcsIsAllowed() {
        BossConeSettings cone = new BossConeSettings();
        cone.setFlashArcs(0);
        assertEquals(0, cone.getFlashArcs());
        cone.setFlashArcs(99);
        assertEquals(BossConeSettings.MAX_FLASH_ARCS, cone.getFlashArcs());
        // A snap of nothing would leave the boss facing wherever the wind-up left it and never
        // finish the turn, so the smallest a strike may finish by is a tenth of a circle.
        cone.setSnapDegrees(0);
        assertEquals(BossConeSettings.MIN_SNAP_DEGREES, cone.getSnapDegrees());
    }
}
