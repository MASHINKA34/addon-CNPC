package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shield's pace, its own hurt cooldown, its ring and its seven noises to the literals
 * they replaced.
 *
 * <p>The hurt cooldown is the one that matters most: it is the ceiling on how fast a party can
 * spend a shield, and moving it turns every barrier check on every server into a different
 * fight. Spelled out here rather than read off the field for that reason.</p>
 */
class BossBarrierTuningDefaultsTest {

    @Test
    @DisplayName("a fresh barrier is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossBarrierSettings barrier = new BossBarrierSettings();

        assertEquals(5, barrier.getPaintIntervalTicks());
        assertEquals(10, barrier.getHurtCooldownTicks());
        assertEquals(75, barrier.getAuraPercent());
        assertEquals(3, barrier.getAuraExtraTenths());
    }

    @Test
    @DisplayName("every barrier cue is born as the call it replaced")
    void cuesAreTheOldCalls() {
        BossBarrierSettings barrier = new BossBarrierSettings();

        assertSound(barrier.getUpSound(), "minecraft:block.beacon.activate", 10, 13);
        assertParticles(barrier.getUpParticles(), BossParticleCue.DUST_ID, 40);
        assertSound(barrier.getBrokenSound(), "minecraft:item.shield.break", 15, 6);
        assertParticles(barrier.getBrokenParticles(), "minecraft:end_rod", 40);
        assertSound(barrier.getExpiredSound(), "minecraft:block.beacon.deactivate", 15, 6);
        assertSound(barrier.getFailHealSound(), "minecraft:item.totem.use", 10, 10);
        assertParticles(barrier.getFailHealParticles(), "minecraft:heart", 20);
        assertSound(barrier.getFailCurseSound(), "minecraft:entity.elder_guardian.curse", 10, 8);
        assertSound(barrier.getHitSound(), "minecraft:block.amethyst_block.chime", 10, 10);
        assertParticles(barrier.getHitParticles(), BossParticleCue.DUST_ID, 8);
    }

    @Test
    @DisplayName("a boss saved before any of this existed holds exactly the same shield")
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
        BossBarrierSettings barrier = reloaded.barrier();
        assertEquals(5, barrier.getPaintIntervalTicks());
        assertEquals(10, barrier.getHurtCooldownTicks());
        assertEquals(75, barrier.getAuraPercent());
        assertEquals(3, barrier.getAuraExtraTenths());
        assertSound(barrier.getUpSound(), "minecraft:block.beacon.activate", 10, 13);
        assertSound(barrier.getBrokenSound(), "minecraft:item.shield.break", 15, 6);
        assertSound(barrier.getExpiredSound(), "minecraft:block.beacon.deactivate", 15, 6);
        assertSound(barrier.getFailHealSound(), "minecraft:item.totem.use", 10, 10);
        assertSound(barrier.getFailCurseSound(), "minecraft:entity.elder_guardian.curse", 10, 8);
        assertSound(barrier.getHitSound(), "minecraft:block.amethyst_block.chime", 10, 10);
        assertParticles(barrier.getHitParticles(), BossParticleCue.DUST_ID, 8);
    }

    @Test
    @DisplayName("the shield and its screen offer the same pace and the same ring")
    void theRangesAreOnePair() {
        BossBarrierSettings barrier = new BossBarrierSettings();
        // Never nought: the paint is a modulus, and a nought there is a crash rather than a
        // ring drawn every tick.
        barrier.setPaintIntervalTicks(0);
        assertEquals(BossBarrierSettings.MIN_PAINT_INTERVAL_TICKS, barrier.getPaintIntervalTicks());
        barrier.setPaintIntervalTicks(Integer.MAX_VALUE);
        assertEquals(BossBarrierSettings.MAX_PAINT_INTERVAL_TICKS, barrier.getPaintIntervalTicks());
        // Nought here is a real answer: a shield that takes every click of a held button.
        barrier.setHurtCooldownTicks(Integer.MIN_VALUE);
        assertEquals(0, barrier.getHurtCooldownTicks());
        barrier.setAuraPercent(Integer.MAX_VALUE);
        assertEquals(BossBarrierSettings.MAX_AURA_PERCENT, barrier.getAuraPercent());
        barrier.setAuraExtraTenths(Integer.MIN_VALUE);
        assertEquals(0, barrier.getAuraExtraTenths());
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("BarrierPaintInterval") || key.equals("BarrierHurtCooldown")
                || key.equals("BarrierAuraPercent") || key.equals("BarrierAuraExtra")
                || key.startsWith("BarrierUpSound") || key.startsWith("BarrierUpParticles")
                || key.startsWith("BarrierBrokenSound") || key.startsWith("BarrierBrokenParticles")
                || key.startsWith("BarrierExpiredSound")
                || key.startsWith("BarrierFailHealSound") || key.startsWith("BarrierFailHealParticles")
                || key.startsWith("BarrierFailCurseSound")
                || key.startsWith("BarrierHitSound") || key.startsWith("BarrierHitParticles");
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
