package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins every tuning default to the constant it replaced, written out as a literal.
 *
 * <p>The whole promise of this object is that a boss nobody has touched behaves exactly as it
 * did before any of these were settings. A default quietly changed here would change every
 * boss on every server at once and show up as "the warning sounds different now", so each one
 * is spelled out rather than compared against the field it came from.</p>
 */
class BossTuningDefaultsTest {

    private static final float EPSILON = 1.0E-6F;

    @Test
    @DisplayName("a fresh tuning is yesterday's constants")
    void defaultsAreTheOldConstants() {
        BossTuningSettings tuning = new BossTuningSettings();

        assertEquals(10, tuning.postActionLockTicks());
        assertEquals(5, tuning.retryShortTicks());
        assertEquals(10, tuning.retryTicks());
        assertEquals(20, tuning.retryLongTicks());
        assertEquals(5, tuning.blockFeedbackIntervalTicks());
        assertEquals(40, tuning.dodgeRetryTicks());
        assertEquals(15.0F, tuning.lineFaceTurnDegrees(), EPSILON);
        assertEquals(90.0F, tuning.trackTurnDegrees(), EPSILON);
        assertEquals(150, tuning.targetLeashPercent());
        assertEquals(48.0D, tuning.targetLeash(32.0D), EPSILON, "the leash was one and a half radii");

        assertEquals(64.0D, tuning.telegraphAudienceRange(), EPSILON);
        assertEquals(2, tuning.telegraphIntervalTicks());
        assertEquals(6, tuning.telegraphAuraParticles());
        assertEquals(55, tuning.telegraphFadedPercent());
        assertEquals(0.55F, tuning.telegraphFadedBrightness(), EPSILON);
        assertEquals(60.0D, tuning.telegraphMeleeHalfAngle(), EPSILON);
        assertEquals(1.0D, tuning.telegraphSpawnRingRadius(), EPSILON);
        assertEquals(8, tuning.telegraphSpawnRings());

        assertEquals(200, tuning.comboStaleTicks());
        assertEquals(60, tuning.comboRetryWindowTicks());
        // 29 is one link per ability, today's BossAbilityKind.COUNT. Spelled out so a new
        // ability has to come past this line instead of moving the default on its own.
        assertEquals(29, tuning.comboMaxLinks());
        assertTrue(tuning.comboMaxLinks() <= BossTuningSettings.COMBO_LINK_CEILING);

        assertEquals(20, tuning.totemRetryIntervalTicks());
        assertEquals(200, tuning.totemLinkDurationTicks());
        assertEquals(160, tuning.totemLinkRefreshTicks());
        assertEquals(1.8D, tuning.totemFitHeight(), EPSILON);
        assertEquals(0.3D, tuning.totemFitHalfWidth(), EPSILON);
        assertEquals(48.0D, tuning.totemReportRange(), EPSILON);

        assertEquals(2, tuning.chestSearchRadius());
        assertEquals(2, tuning.chestSearchHeight());
        assertEquals(8, tuning.chestMaxDropHeight());
        assertEquals(100, tuning.chestStagedDropsTimeoutTicks());
        assertEquals(2, tuning.chestAfterExplosionTicks());
        assertEquals(100, tuning.explosionParticlePercent());

        assertEquals(20, tuning.healthLinkAnnounceIntervalTicks());
        assertEquals(0xFF6A5A, tuning.healthLinkDownedColor());
        assertEquals(1.0F, tuning.lethalGuardHealth(), EPSILON, "the guard left exactly one point");

        assertEquals(0.5D, tuning.waveCorridorSpeed(), EPSILON);
        assertEquals(10, tuning.waveMinTicks());
        assertEquals(60, tuning.waveMaxTicks());
        assertEquals(4, tuning.waveFloorSearchDepth());
        assertEquals(40, tuning.waveBlockLifetimeTicks());
    }

    @Test
    @DisplayName("every cue is born as the call it replaced")
    void cuesAreTheOldCalls() {
        BossTuningSettings tuning = new BossTuningSettings();

        assertSound(tuning.telegraphSound(), "minecraft:block.note_block.bell", 8, 6);
        assertSound(tuning.totemHitSound(), "minecraft:item.shield.block", 8, 9);
        assertParticles(tuning.totemHitParticles(), "minecraft:enchant", 8);
        assertSound(tuning.totemLinkSound(), "minecraft:block.amethyst_block.resonate", 10, 12);
        assertSound(tuning.blockedHitSound(), "minecraft:entity.zombie.attack_iron_door", 12, 6);
        assertParticles(tuning.blockedHitParticles(), "minecraft:crit", 20);

        assertSound(tuning.explosionSound(), "minecraft:entity.generic.explode", 40, 9);
        assertSound(tuning.rageSound(), "minecraft:entity.ender_dragon.growl", 20, 7);
        assertParticles(tuning.rageParticles(), "minecraft:angry_villager", 40);
        assertParticles(tuning.rageSmoke(), "minecraft:large_smoke", 30);
        assertSound(tuning.teleportSound(), "minecraft:entity.enderman.teleport", 10, 10);
        assertParticles(tuning.minionDespawnParticles(), "minecraft:poof", 8);

        assertSound(tuning.healthLinkDownedSound(), "minecraft:entity.ravager.stunned", 12, 7);
        assertParticles(tuning.healthLinkDownedParticles(), "minecraft:soul", 24);
        assertSound(tuning.healthLinkReviveSound(), "minecraft:item.totem.use", 10, 10);
        assertParticles(tuning.healthLinkReviveParticles(), "minecraft:totem_of_undying", 40);

        assertSound(tuning.waveSound(AreaVfxStyles.VINES), "minecraft:block.azalea_leaves.break", 25, 7);
        assertSound(tuning.waveSound(AreaVfxStyles.STONE), "minecraft:block.stone.break", 40, 5);
        assertSound(tuning.waveSound(AreaVfxStyles.HURRICANE), "minecraft:entity.breeze.wind_burst", 30, 8);
        assertSound(tuning.waveSound(AreaVfxStyles.FIRE), "minecraft:item.firecharge.use", 30, 7);
        assertSound(tuning.waveSound(AreaVfxStyles.GHOST), "minecraft:particle.soul_escape", 30, 6);
        assertSound(tuning.waveSound(AreaVfxStyles.SCULK_WAVE), "minecraft:block.sculk_shrieker.shriek", 25, 9);
        assertNull(tuning.waveSound(AreaVfxStyles.NONE), "a styleless wave was always silent");
    }

    @Test
    @DisplayName("a boss saved before any of this existed reads back as the old boss")
    void anOldSaveKeepsTheOldBehaviour() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        CompoundTag old = data.writeToNBT(new CompoundTag());
        // Exactly the state a save from before this object is in: every tuning key missing.
        old.getAllKeys().removeIf(key -> key.startsWith("GeckoBossTuning"));

        TeleportPathData reloaded = new TeleportPathData();
        reloaded.readFromNBT(old);
        BossTuningSettings tuning = reloaded.tuning();
        assertEquals(10, tuning.postActionLockTicks());
        assertEquals(150, tuning.targetLeashPercent());
        assertEquals(2, tuning.telegraphIntervalTicks());
        assertEquals(0xFF6A5A, tuning.healthLinkDownedColor());
        assertEquals(1.0F, tuning.lethalGuardHealth(), EPSILON);
        assertSound(tuning.telegraphSound(), "minecraft:block.note_block.bell", 8, 6);
        assertParticles(tuning.rageSmoke(), "minecraft:large_smoke", 30);
    }

    @Test
    @DisplayName("the link refresh never outlives the link it refreshes")
    void theRefreshStaysInsideTheLink() {
        BossTuningSettings tuning = new BossTuningSettings();
        tuning.setTotemLinkDurationTicks(40);
        tuning.setTotemLinkRefreshTicks(1200);
        assertEquals(39, tuning.totemLinkRefreshTicks(), "a refresh past the link would blink the beam");

        tuning.setTotemLinkDurationTicks(1200);
        assertEquals(1200, tuning.totemLinkDurationTicks());
        assertEquals(1199, tuning.totemLinkRefreshTicks());
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
