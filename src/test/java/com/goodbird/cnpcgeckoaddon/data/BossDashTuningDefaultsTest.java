package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins every one of the dash's new slacks and cues to the literal it replaced.
 *
 * <p>The promise of this batch is that a boss nobody has touched runs exactly as it did before
 * any of these were settings. A default quietly changed here would change every dash on every
 * server at once and show up as "it stops on walls it used to scrape past", so each one is
 * spelled out rather than compared against the field it came from.</p>
 */
class BossDashTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh dash is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossDashSettings dash = new BossDashSettings();

        assertEquals(10, dash.getMinReachTenths());
        assertEquals(25, dash.getWallSharePercent());
        assertEquals(4, dash.getContactSliceTenths());
        assertEquals(5, dash.getMaxSteerTenths());
        assertEquals(10, dash.getSweepSlackTenths());
        assertEquals(20, dash.getTeleportSlackTenths());
        assertEquals(5, dash.getChainHeightSlackTenths());
        assertEquals(20, dash.getSlamVfxTicks());
    }

    @Test
    @DisplayName("every dash cue is born as the call it replaced")
    void cuesAreTheOldCalls() {
        BossDashSettings dash = new BossDashSettings();

        assertSound(dash.getStartSound(), "minecraft:entity.ravager.roar", 12, 14);
        assertParticles(dash.getStartParticles(), "minecraft:cloud", 12);
        assertSound(dash.getHitSound(), "minecraft:entity.player.attack.knockback", 12, 7);
        assertSound(dash.getSlamSound(), "minecraft:block.anvil.land", 20, 5);
        assertParticles(dash.getSlamParticles(), "minecraft:explosion", 1);
        assertSound(dash.getChainSound(), "minecraft:block.chain.hit", 20, 6);
        assertParticles(dash.getChainParticles(), BossParticleCue.DUST_ID, 20);
        assertSound(dash.getWallSound(), "minecraft:entity.zombie.break_wooden_door", 10, 7);
    }

    @Test
    @DisplayName("a boss saved before any of this existed reads back as the old dash")
    void anOldSaveKeepsTheOldBehaviour() {
        BossPhaseData written = new BossPhaseData();
        CompoundTag old = written.writeToNBT();
        // Exactly the state a save from before this batch is in: every new dash key missing.
        for (String key : List.copyOf(old.getAllKeys())) {
            if (isNewDashKey(key)) {
                old.remove(key);
            }
        }

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        BossDashSettings dash = reloaded.dash();
        assertEquals(1.0D, dash.getMinReach(), EPSILON);
        assertEquals(0.25D, dash.getWallShare(), EPSILON);
        assertEquals(0.4D, dash.getContactSlice(), EPSILON);
        assertEquals(0.5D, dash.getMaxSteer(), EPSILON);
        assertEquals(1.0D, dash.getSweepSlack(), EPSILON);
        assertEquals(2.0D, dash.getTeleportSlack(), EPSILON);
        assertEquals(0.5D, dash.getChainHeightSlack(), EPSILON);
        assertEquals(20, dash.getSlamVfxTicks());
        assertSound(dash.getStartSound(), "minecraft:entity.ravager.roar", 12, 14);
        assertParticles(dash.getChainParticles(), BossParticleCue.DUST_ID, 20);
    }

    /** Whether this key is one this batch added, and so one an older save would not carry. */
    private static boolean isNewDashKey(String key) {
        return key.equals("DashMinReach") || key.equals("DashWallShare")
                || key.equals("DashContactSlice") || key.equals("DashMaxSteer")
                || key.equals("DashSweepSlack") || key.equals("DashTeleportSlack")
                || key.equals("DashChainHeightSlack") || key.equals("DashSlamVfxTicks")
                || key.startsWith("DashStartSound") || key.startsWith("DashStartParticles")
                || key.startsWith("DashHitSound") || key.startsWith("DashSlamSound")
                || key.startsWith("DashSlamParticles") || key.startsWith("DashChainSound")
                || key.startsWith("DashChainParticles") || key.startsWith("DashWallSound");
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
