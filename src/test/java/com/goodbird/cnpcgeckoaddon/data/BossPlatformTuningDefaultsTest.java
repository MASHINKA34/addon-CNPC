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
 * Pins the platform's blink, its countdown, its pops and its two noises to the literals they
 * replaced, and the look that came after them to the numbers it was designed at.
 *
 * <p>The promise of the first batch is that a boss nobody has touched burns exactly as it did
 * before any of these were settings. A default quietly moved here would change every platform
 * on every server at once and read in play as "the outline flickers differently now", so each
 * one is spelled out rather than compared against the field it came from.</p>
 *
 * <p>The fill, the pillars and the smoulder are the one deliberate exception: the old look was
 * found to be all but invisible, so their defaults reach an old save too. That is a decision
 * about the picture only, and the second half of this file pins that the damage, the fuse and
 * the noises of an old save stay exactly what they were.</p>
 */
class BossPlatformTuningDefaultsTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a fresh platform is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossPlatformSettings platform = new BossPlatformSettings();

        assertEquals(4, platform.getBlinkTicks());
        assertEquals(20, platform.getCountdownIntervalTicks());
        assertEquals(24, platform.getFlareMax());
        assertEquals(40, platform.getFlareArea());
        assertEquals(4.0D, platform.flareAreaPerPop(), EPSILON);
    }

    @Test
    @DisplayName("a fresh platform burns at the visible densities it was designed at")
    void lookDefaultsAreTheDesignedOnes() {
        BossPlatformSettings platform = new BossPlatformSettings();

        assertEquals(50, platform.getEdgeSpacing(), "half a block between two points of the outline");
        assertEquals(0.5D, platform.edgeSpacing(), EPSILON);
        assertEquals(6, platform.getFuseFillDensity());
        assertEquals(200, platform.getFuseRampPercent(), "three times as thick by the end of the fuse");
        assertEquals(20, platform.getPillarHeight());
        assertEquals(2.0D, platform.pillarHeight(), EPSILON);
        assertEquals(4, platform.getSmoulderDensity());
    }

    @Test
    @DisplayName("every platform cue is born as the call it replaced")
    void cuesAreTheOldCalls() {
        BossPlatformSettings platform = new BossPlatformSettings();

        assertSound(platform.getLitSound(), "minecraft:entity.tnt.primed", 15, 8);
        assertParticles(platform.getOutlineParticles(), "minecraft:flame", 1);
        assertParticles(platform.getBlastParticles(), "minecraft:lava", 1);
        assertSound(platform.getBlastSound(), "minecraft:entity.generic.explode", 20, 9);
        assertParticles(platform.getFuseParticles(), "minecraft:flame", 1);
        assertParticles(platform.getPillarParticles(), "minecraft:soul_fire_flame", 1);
        assertParticles(platform.getSmokeParticles(), "minecraft:smoke", 1);
        assertParticles(platform.getBlastFlash(), "minecraft:explosion_emitter", 1);
    }

    /**
     * The fuse's cue writes under {@code PlatformFuse}, which the fuse's own ticks, fill and
     * ramp begin with as well; the same story as the bang's pair below.
     */
    @Test
    @DisplayName("the fuse cue and the fuse's own numbers keep their keys apart")
    void theFuseCueDoesNotShareKeys() {
        BossPhaseData phase = new BossPhaseData();
        phase.platform().setFuseTicks(90);
        phase.platform().setFuseFillDensity(11);
        phase.platform().setFuseRampPercent(33);
        phase.platform().getFuseParticles().setCount(7);
        phase.platform().getFuseParticles().setEnabled(false);

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(phase.writeToNBT());
        assertEquals(90, reloaded.platform().getFuseTicks());
        assertEquals(11, reloaded.platform().getFuseFillDensity());
        assertEquals(33, reloaded.platform().getFuseRampPercent());
        assertEquals(7, reloaded.platform().getFuseParticles().getCount());
        assertFalse(reloaded.platform().getFuseParticles().isEnabled());
    }

    /**
     * The bang's two cues sit under {@code PlatformBlast} and {@code PlatformBlastParticles},
     * which share a prefix: one writing over the other would be silent, and would read back as
     * a builder's edit undoing itself.
     */
    @Test
    @DisplayName("the sound cue and the particle cue of the bang keep their keys apart")
    void theBlastPairDoesNotShareKeys() {
        BossPhaseData phase = new BossPhaseData();
        phase.platform().getBlastSound().setVolume(1);
        phase.platform().getBlastParticles().setCount(7);

        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(phase.writeToNBT());
        assertEquals(1, reloaded.platform().getBlastSound().getVolume());
        assertEquals(7, reloaded.platform().getBlastParticles().getCount());
    }

    @Test
    @DisplayName("a boss saved before any of this existed burns exactly as it used to")
    void anOldSaveKeepsTheOldBehaviour() {
        BossPlatformSettings platform = reloadedWithoutTheTuning(new BossPhaseData()).platform();
        assertEquals(4, platform.getBlinkTicks());
        assertEquals(20, platform.getCountdownIntervalTicks());
        assertEquals(24, platform.getFlareMax());
        assertEquals(4.0D, platform.flareAreaPerPop(), EPSILON);
        assertSound(platform.getLitSound(), "minecraft:entity.tnt.primed", 15, 8);
        assertParticles(platform.getOutlineParticles(), "minecraft:flame", 1);
        assertParticles(platform.getBlastParticles(), "minecraft:lava", 1);
        assertSound(platform.getBlastSound(), "minecraft:entity.generic.explode", 20, 9);
    }

    /**
     * The exception, on purpose: the fill, the pillars and the smoulder were added because the
     * old look could not be seen, so they reach a boss saved before them - and nothing else of
     * that boss moves with them.
     */
    @Test
    @DisplayName("a boss saved before the fill existed gets the visible look, and keeps everything else")
    void anOldSaveGetsTheVisibleLook() {
        BossPhaseData written = new BossPhaseData();
        written.platform().setFuseTicks(140);
        written.platform().setDamage(30);
        written.platform().setKnockback(5);
        written.platform().setLaunch(7);
        written.platform().setLingerTicks(300);
        written.platform().setLingerIntervalTicks(15);

        BossPlatformSettings platform = reloadedWithoutTheTuning(written).platform();
        assertEquals(50, platform.getEdgeSpacing());
        assertEquals(6, platform.getFuseFillDensity());
        assertEquals(200, platform.getFuseRampPercent());
        assertEquals(20, platform.getPillarHeight());
        assertEquals(4, platform.getSmoulderDensity());
        assertParticles(platform.getFuseParticles(), "minecraft:flame", 1);
        assertParticles(platform.getPillarParticles(), "minecraft:soul_fire_flame", 1);
        assertParticles(platform.getSmokeParticles(), "minecraft:smoke", 1);
        assertParticles(platform.getBlastFlash(), "minecraft:explosion_emitter", 1);

        assertEquals(140, platform.getFuseTicks(), "the fuse is the mechanic and is not the picture");
        assertEquals(30, platform.getDamage());
        assertEquals(5, platform.getKnockback());
        assertEquals(7, platform.getLaunch());
        assertEquals(300, platform.getLingerTicks());
        assertEquals(15, platform.getLingerIntervalTicks());
        assertSound(platform.getBlastSound(), "minecraft:entity.generic.explode", 20, 9);
    }

    /** The phase read back from its own tag with every tuning key of both batches stripped out. */
    private static BossPhaseData reloadedWithoutTheTuning(BossPhaseData written) {
        CompoundTag old = written.writeToNBT();
        for (String key : List.copyOf(old.getAllKeys())) {
            if (isNewKey(key)) {
                old.remove(key);
            }
        }
        BossPhaseData reloaded = new BossPhaseData();
        reloaded.readFromNBT(old);
        return reloaded;
    }

    /** Whether this key is one the two tuning batches added, and so one an older save would not carry. */
    private static boolean isNewKey(String key) {
        return key.equals("PlatformBlink") || key.equals("PlatformCountdownInterval")
                || key.equals("PlatformFlareMax") || key.equals("PlatformFlareArea")
                || key.startsWith("PlatformLit") || key.startsWith("PlatformOutline")
                || key.startsWith("PlatformBlast")
                || key.equals("PlatformEdgeSpacing") || key.equals("PlatformFuseFill")
                || key.equals("PlatformFuseRamp") || key.equals("PlatformPillarHeight")
                || key.equals("PlatformSmoulder")
                // The fuse cue's own three keys, and not the fuse's ticks that share their prefix.
                || key.equals("PlatformFuseOn") || key.equals("PlatformFuseParticle")
                || key.equals("PlatformFuseCount")
                || key.startsWith("PlatformPillarCue") || key.startsWith("PlatformSmoke")
                || key.startsWith("PlatformFlash");
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
