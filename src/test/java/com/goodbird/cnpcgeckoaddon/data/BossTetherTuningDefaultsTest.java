package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the leash's doses, its slack, its sag, its force per level and its three noises to the
 * literals they replaced.
 *
 * <p>Spelled out rather than compared against the fields they came from: "the tether tugs
 * harder than it used to" is a regression nobody would trace back to a settings class.</p>
 */
class BossTetherTuningDefaultsTest {

    @Test
    @DisplayName("a fresh tether is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossTetherSettings tether = new BossTetherSettings();

        assertEquals(20, tether.getEffectIntervalTicks());
        assertEquals(10, tether.getPullSlackTenths(), "one block of slack, as the constant was");
        assertEquals(100, tether.getBeamSagPercent());
        assertEquals(20, tether.getPullPerLevelThousandths(), "0.02 a level, as the constant was");
    }

    @Test
    @DisplayName("all three tether cue pairs are born as the calls they replaced")
    void cuesAreTheOldCalls() {
        BossTetherSettings tether = new BossTetherSettings();

        assertSound(tether.getPlaceSound(), "minecraft:block.chain.place", 12, 7);
        assertParticles(tether.getPlaceParticles(), BossParticleCue.DUST_ID, 10);
        assertSound(tether.getBreakSound(), "minecraft:block.chain.break", 15, 12);
        assertParticles(tether.getBreakParticles(), "minecraft:crit", 12);
        assertSound(tether.getFailSound(), "minecraft:block.chain.hit", 20, 5);
        assertParticles(tether.getFailParticles(), "minecraft:smoke", 12);
    }

    @Test
    @DisplayName("a boss saved before any of this existed leashes exactly as it used to")
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
        BossTetherSettings tether = reloaded.tether();
        assertEquals(20, tether.getEffectIntervalTicks());
        assertEquals(10, tether.getPullSlackTenths());
        assertEquals(100, tether.getBeamSagPercent());
        assertEquals(20, tether.getPullPerLevelThousandths());
        assertSound(tether.getPlaceSound(), "minecraft:block.chain.place", 12, 7);
        assertParticles(tether.getPlaceParticles(), BossParticleCue.DUST_ID, 10);
        assertSound(tether.getBreakSound(), "minecraft:block.chain.break", 15, 12);
        assertParticles(tether.getBreakParticles(), "minecraft:crit", 12);
        assertSound(tether.getFailSound(), "minecraft:block.chain.hit", 20, 5);
        assertParticles(tether.getFailParticles(), "minecraft:smoke", 12);
    }

    /**
     * The sag is held to what the beam packet carries. Offering four hundred on the screen
     * would be offering a number cut down to two on the wire, which reads as the setting not
     * working rather than as the setting having an end.
     */
    @Test
    @DisplayName("the sag the screen offers is the sag the beam is drawn with")
    void theSagIsHeldToWhatTheBeamCarries() {
        BossTetherSettings tether = new BossTetherSettings();

        tether.setBeamSagPercent(0);
        assertEquals(0, tether.getBeamSagPercent(), "nought is a straight beam, not a missing setting");
        tether.setBeamSagPercent(Integer.MAX_VALUE);
        assertEquals(200, tether.getBeamSagPercent());
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("TetherEffectInterval") || key.equals("TetherPullSlack")
                || key.equals("TetherBeamSag") || key.equals("TetherPullPerLevel")
                || key.startsWith("TetherPlaceSound") || key.startsWith("TetherPlaceParticles")
                || key.startsWith("TetherBreakSound") || key.startsWith("TetherBreakParticles")
                || key.startsWith("TetherFailSound") || key.startsWith("TetherFailParticles");
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
