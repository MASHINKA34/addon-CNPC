package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertOldColumnAndNothingMore(geyser);
    }

    /**
     * The column as it was always drawn, and neither of the two things a later batch let an
     * eruption do afterwards: a boss that never asked for them must not get them.
     */
    private static void assertOldColumnAndNothingMore(BossGeyserSettings geyser) {
        assertEquals(BossGeyserSettings.COLUMN_STRAIGHT, geyser.getColumnShape(), "a line at the middle");
        assertEquals(0, geyser.getColumnRiseTicks(), "drawn at once");
        assertEquals(5, geyser.getColumnTopRadiusTenths());
        assertEquals(6, geyser.getColumnPointsPerSlice());
        assertParticles(geyser.getColumnParticles(), "minecraft:cloud", 2);
        assertParticles(geyser.getColumnSmoke(), "minecraft:large_smoke", 1);
        assertFalse(geyser.isSkyEnabled(), "one eruption, nothing from above");
        assertFalse(geyser.isResidueEnabled(), "nothing left on the floor");
        assertEquals(30, geyser.getSkyDelayTicks());
        assertEquals(12, geyser.getSkyHeight());
        assertEquals(10, geyser.getSkyFallTicks());
        assertEquals(0, geyser.getSkyRadius());
        assertEquals(6, geyser.getSkyDamage());
        assertEquals(12, geyser.getSkyPressTenths());
        assertEquals(AreaVfxStyles.NONE, geyser.getSkyVfx());
        assertParticles(geyser.getSkyParticles(), "minecraft:cloud", 3);
        assertSound(geyser.getSkySound(), "minecraft:entity.generic.splash", 15, 7);
        assertSound(geyser.getSkyHitSound(), "minecraft:entity.generic.explode", 10, 8);
        assertParticles(geyser.getSkyHitParticles(), "minecraft:splash", 12);
        assertEquals(200, geyser.getResidueLifetimeTicks());
        assertEquals(0, geyser.getResidueRadius());
        assertEquals(2, geyser.getResidueDamage());
        assertEquals(20, geyser.getResidueIntervalTicks());
        assertEquals(40, geyser.getResidueStackTicks());
        assertEquals(4, geyser.getResidueMaxStacks());
        assertEquals(60, geyser.getResidueDecayTicks());
        assertEquals(10, geyser.getResidueHeightTenths());
        assertEquals(40, geyser.getResidueSoundIntervalTicks());
        assertParticles(geyser.getResidueParticles(), "minecraft:bubble_pop", 4);
        assertSound(geyser.getResidueSound(), "minecraft:block.bubble_column.bubble_pop", 6, 9);
        assertParticles(geyser.getResidueHitParticles(), "minecraft:smoke", 4);
        assertFalse(geyser.getSkyEffects().isAnyEnabled());
        assertFalse(geyser.getResidueEffects().isAnyEnabled());
    }

    @Test
    @DisplayName("the strike's and the residue's numbers are held to the ranges their screens offer")
    void theNewNumbersAreClamped() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        geyser.setColumnShape(7);
        assertEquals(BossGeyserSettings.COLUMN_CONE, geyser.getColumnShape());
        geyser.setColumnShape(-1);
        assertEquals(BossGeyserSettings.COLUMN_STRAIGHT, geyser.getColumnShape());
        geyser.setColumnTopRadiusTenths(999);
        assertEquals(BossGeyserSettings.MAX_COLUMN_TOP_RADIUS, geyser.getColumnTopRadiusTenths());
        geyser.setColumnRiseTicks(-5);
        assertEquals(0, geyser.getColumnRiseTicks());
        geyser.setColumnPointsPerSlice(0);
        assertEquals(BossGeyserSettings.MIN_COLUMN_POINTS, geyser.getColumnPointsPerSlice());
        geyser.setSkyDelayTicks(0);
        assertEquals(BossGeyserSettings.MIN_SKY_DELAY_TICKS, geyser.getSkyDelayTicks());
        geyser.setSkyHeight(1);
        assertEquals(BossGeyserSettings.MIN_SKY_HEIGHT, geyser.getSkyHeight());
        geyser.setSkyFallTicks(1000);
        assertEquals(BossGeyserSettings.MAX_SKY_FALL_TICKS, geyser.getSkyFallTicks());
        geyser.setSkyRadius(99);
        assertEquals(BossGeyserSettings.MAX_SKY_RADIUS, geyser.getSkyRadius());
        geyser.setSkyPressTenths(-1);
        assertEquals(0, geyser.getSkyPressTenths());
        geyser.setResidueLifetimeTicks(1);
        assertEquals(BossGeyserSettings.MIN_RESIDUE_LIFETIME_TICKS, geyser.getResidueLifetimeTicks());
        geyser.setResidueMaxStacks(0);
        assertEquals(BossGeyserSettings.MIN_RESIDUE_MAX_STACKS, geyser.getResidueMaxStacks());
        geyser.setResidueHeightTenths(0);
        assertEquals(BossGeyserSettings.MIN_RESIDUE_HEIGHT, geyser.getResidueHeightTenths());
        geyser.setResidueSoundIntervalTicks(1);
        assertEquals(BossGeyserSettings.MIN_RESIDUE_SOUND_INTERVAL_TICKS, geyser.getResidueSoundIntervalTicks());
    }

    @Test
    @DisplayName("the strike's and the residue's settings round-trip through the save")
    void theNewSettingsRoundTrip() {
        BossPhaseData phase = new BossPhaseData();
        BossGeyserSettings geyser = phase.geyser();
        geyser.setColumnShape(BossGeyserSettings.COLUMN_CONE);
        geyser.setColumnTopRadiusTenths(20);
        geyser.setColumnRiseTicks(15);
        geyser.setColumnPointsPerSlice(9);
        geyser.setSkyEnabled(true);
        geyser.setSkyDelayTicks(45);
        geyser.setSkyHeight(20);
        geyser.setSkyFallTicks(12);
        geyser.setSkyRadius(5);
        geyser.setSkyDamage(9);
        geyser.setSkyPressTenths(30);
        geyser.setSkyVfx(AreaVfxStyles.STONE);
        geyser.getSkyEffects().get(1).setEnabled(true);
        geyser.setResidueEnabled(true);
        geyser.setResidueLifetimeTicks(300);
        geyser.setResidueRadius(4);
        geyser.setResidueDamage(0);
        geyser.setResidueIntervalTicks(10);
        geyser.setResidueStackTicks(50);
        geyser.setResidueMaxStacks(6);
        geyser.setResidueDecayTicks(80);
        geyser.setResidueHeightTenths(25);
        geyser.setResidueSoundIntervalTicks(60);
        geyser.getResidueEffects().get(2).setEnabled(true);
        geyser.getColumnSmoke().setEnabled(false);
        geyser.getResidueSound().setEnabled(false);

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(phase.writeToNBT());
        BossGeyserSettings back = reloaded.geyser();
        assertEquals(BossGeyserSettings.COLUMN_CONE, back.getColumnShape());
        assertEquals(20, back.getColumnTopRadiusTenths());
        assertEquals(15, back.getColumnRiseTicks());
        assertEquals(9, back.getColumnPointsPerSlice());
        assertTrue(back.isSkyEnabled());
        assertEquals(45, back.getSkyDelayTicks());
        assertEquals(20, back.getSkyHeight());
        assertEquals(12, back.getSkyFallTicks());
        assertEquals(5, back.getSkyRadius());
        assertEquals(9, back.getSkyDamage());
        assertEquals(30, back.getSkyPressTenths());
        assertEquals(AreaVfxStyles.STONE, back.getSkyVfx());
        assertTrue(back.getSkyEffects().get(1).isEnabled());
        assertTrue(back.isResidueEnabled());
        assertEquals(300, back.getResidueLifetimeTicks());
        assertEquals(4, back.getResidueRadius());
        assertEquals(0, back.getResidueDamage());
        assertEquals(10, back.getResidueIntervalTicks());
        assertEquals(50, back.getResidueStackTicks());
        assertEquals(6, back.getResidueMaxStacks());
        assertEquals(80, back.getResidueDecayTicks());
        assertEquals(25, back.getResidueHeightTenths());
        assertEquals(60, back.getResidueSoundIntervalTicks());
        assertTrue(back.getResidueEffects().get(2).isEnabled());
        assertFalse(back.getColumnSmoke().isEnabled());
        assertFalse(back.getResidueSound().isEnabled());
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
        assertOldColumnAndNothingMore(geyser);
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

    /**
     * Whether this key is one a later batch added, and so one an older save would not carry:
     * the tuning's, and then the cone's, the strike's and the residue's.
     */
    private static boolean isNewKey(String key) {
        return key.equals("GeyserVfxTicks") || key.equals("GeyserColumnPerRadius")
                || key.equals("GeyserColumnMin") || key.equals("GeyserColumnMax")
                || key.equals("GeyserBoilMin") || key.equals("GeyserBoilMax")
                || key.startsWith("GeyserLitSound") || key.startsWith("GeyserEruptSound")
                || key.startsWith("GeyserColumnShape") || key.startsWith("GeyserColumnTop")
                || key.startsWith("GeyserColumnRise") || key.startsWith("GeyserColumnPoints")
                || key.startsWith("GeyserColumnParticles") || key.startsWith("GeyserColumnSmoke")
                || key.startsWith("GeyserSky") || key.startsWith("GeyserResidue");
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
