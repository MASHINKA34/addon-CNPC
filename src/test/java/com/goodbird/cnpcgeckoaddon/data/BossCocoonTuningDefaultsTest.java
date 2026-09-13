package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cocoon's doses, its countdown, its reach, its guard's stand and its three noises to
 * the literals they replaced.
 *
 * <p>Spelled out rather than compared against the fields they came from: "the cocoon tells
 * people from further away than it used to" is a regression nobody would trace back to a
 * settings class.</p>
 */
class BossCocoonTuningDefaultsTest {

    @Test
    @DisplayName("a fresh cocoon is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossCocoonSettings cocoon = new BossCocoonSettings();

        assertEquals(20, cocoon.getEffectIntervalTicks());
        assertEquals(10, cocoon.getAnnounceIntervalTicks());
        assertEquals(12, cocoon.getAnnounceRange());
        assertEquals(32, cocoon.getReach());
        assertEquals(20, cocoon.getGuardDistanceTenths());
    }

    @Test
    @DisplayName("all three cocoon cue pairs are born as the calls they replaced")
    void cuesAreTheOldCalls() {
        BossCocoonSettings cocoon = new BossCocoonSettings();

        assertSound(cocoon.getWrapSound(), "minecraft:entity.spider.ambient", 12, 6);
        assertParticles(cocoon.getWrapParticles(), "minecraft:cloud", 12);
        assertSound(cocoon.getFreedSound(), "minecraft:entity.item.break", 12, 8);
        assertParticles(cocoon.getFreedParticles(), "minecraft:crit", 12);
        assertSound(cocoon.getTimeoutSound(), "minecraft:entity.generic.explode", 8, 16);
        assertParticles(cocoon.getTimeoutParticles(), "minecraft:smoke", 12);
    }

    @Test
    @DisplayName("a boss saved before any of this existed wraps exactly as it used to")
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
        BossCocoonSettings cocoon = reloaded.cocoon();
        assertEquals(20, cocoon.getEffectIntervalTicks());
        assertEquals(10, cocoon.getAnnounceIntervalTicks());
        assertEquals(12, cocoon.getAnnounceRange());
        assertEquals(32, cocoon.getReach());
        assertEquals(20, cocoon.getGuardDistanceTenths());
        assertSound(cocoon.getWrapSound(), "minecraft:entity.spider.ambient", 12, 6);
        assertParticles(cocoon.getWrapParticles(), "minecraft:cloud", 12);
        assertSound(cocoon.getFreedSound(), "minecraft:entity.item.break", 12, 8);
        assertParticles(cocoon.getFreedParticles(), "minecraft:crit", 12);
        assertSound(cocoon.getTimeoutSound(), "minecraft:entity.generic.explode", 8, 16);
        assertParticles(cocoon.getTimeoutParticles(), "minecraft:smoke", 12);
    }

    /** Nought is a real answer here: a cocoon nobody is told about is one nobody can find. */
    @Test
    @DisplayName("the countdown may be told to nobody, and the doses never to nobody")
    void theRangesAreTheOnesTheScreenOffers() {
        BossCocoonSettings cocoon = new BossCocoonSettings();

        cocoon.setAnnounceRange(0);
        assertEquals(0, cocoon.getAnnounceRange());
        cocoon.setEffectIntervalTicks(0);
        assertEquals(1, cocoon.getEffectIntervalTicks(), "a dose every no ticks is a division by nothing");
        cocoon.setAnnounceIntervalTicks(0);
        assertEquals(1, cocoon.getAnnounceIntervalTicks());
        cocoon.setReach(0);
        assertEquals(8, cocoon.getReach(), "a cocoon that reaches nowhere would never fire");
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("CocoonEffectInterval") || key.equals("CocoonAnnounceInterval")
                || key.equals("CocoonAnnounceRange") || key.equals("CocoonReach")
                || key.equals("CocoonGuardDistance")
                || key.startsWith("CocoonWrap") || key.startsWith("CocoonFreed")
                || key.startsWith("CocoonTimeout");
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
