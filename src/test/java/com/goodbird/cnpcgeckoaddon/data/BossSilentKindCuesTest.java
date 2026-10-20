package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cues of the seven abilities that had none - the ground attack, the ranged and melee attacks,
 * the line strike, the summon, the arena hazard and the hunt: each born switched off with the call
 * it starts from once turned on, kept off by a save from before it existed, and on once it was
 * saved on.
 */
class BossSilentKindCuesTest {

    private record Sound(String prefix, Function<BossPhaseData, BossSoundCue> cue, String id, int volume, int pitch) {
    }

    private record Puff(String prefix, Function<BossPhaseData, BossParticleCue> cue, String id, int count) {
    }

    private static final Sound[] SOUNDS = {
            new Sound("AreaHitSound", phase -> phase.areaAttack().getHitSound(), "minecraft:entity.generic.explode", 10, 12),
            new Sound("RangedShotSound", phase -> phase.rangedAttack().getShotSound(), "minecraft:entity.arrow.shoot", 10, 9),
            new Sound("MeleeSwingSound", phase -> phase.meleeAttack().getSwingSound(),
                    "minecraft:entity.player.attack.sweep", 10, 8),
            new Sound("MeleeHitSound", phase -> phase.meleeAttack().getHitSound(),
                    "minecraft:entity.player.attack.strong", 10, 9),
            new Sound("LineSweepSound", phase -> phase.lineAttack().getSweepSound(), "minecraft:entity.wither.shoot", 12, 6),
            new Sound("SummonSpawnSound", phase -> phase.summon().getSpawnSound(),
                    "minecraft:entity.evoker.prepare_summon", 10, 10),
            new Sound("HazardTickSound", phase -> phase.hazard().getTickSound(), "minecraft:block.fire.ambient", 8, 10),
            new Sound("HuntStartSound", phase -> phase.hunt().getStartSound(), "minecraft:entity.ravager.roar", 12, 10),
            new Sound("HuntCatchSound", phase -> phase.hunt().getCatchSound(), "minecraft:entity.player.attack.crit", 10, 7)
    };

    private static final Puff[] PUFFS = {
            new Puff("AreaHitParticles", phase -> phase.areaAttack().getHitParticles(), BossParticleCue.DUST_ID, 12),
            new Puff("RangedMuzzleParticles", phase -> phase.rangedAttack().getMuzzleParticles(), "minecraft:smoke", 6),
            new Puff("MeleeHitParticles", phase -> phase.meleeAttack().getHitParticles(), "minecraft:crit", 8),
            new Puff("LineHitParticles", phase -> phase.lineAttack().getHitParticles(), BossParticleCue.DUST_ID, 10),
            new Puff("SummonSpawnParticles", phase -> phase.summon().getSpawnParticles(), "minecraft:poof", 10),
            new Puff("HazardTickParticles", phase -> phase.hazard().getTickParticles(), "minecraft:flame", 4),
            new Puff("HuntCatchParticles", phase -> phase.hunt().getCatchParticles(), "minecraft:crit", 10),
            new Puff("HuntTrailParticles", phase -> phase.hunt().getTrailParticles(), "minecraft:smoke", 2)
    };

    @TestFactory
    @DisplayName("every new sound cue is born off with its call, stays off on an old save, and loads on once saved on")
    Stream<DynamicTest> soundsAreSilentUntilAsked() {
        return Stream.of(SOUNDS).map(sound -> DynamicTest.dynamicTest(sound.prefix(), () -> {
            BossPhaseData phase = new BossPhaseData();
            BossSoundCue cue = sound.cue().apply(phase);
            assertFalse(cue.isEnabled(), "an old boss must not start making this noise");
            assertEquals(sound.id(), cue.getSoundId());
            assertEquals(sound.volume(), cue.getVolume());
            assertEquals(sound.pitch(), cue.getPitch());

            CompoundTag old = phase.writeToNBT();
            for (String suffix : new String[]{"On", "Sound", "Volume", "Pitch"}) {
                assertTrue(old.contains(sound.prefix() + suffix), "no " + sound.prefix() + suffix + " in the phase tag");
                old.remove(sound.prefix() + suffix);
            }
            BossPhaseData reread = new BossPhaseData();
            sound.cue().apply(reread).setEnabled(true);
            reread.readFromNBT(old);
            assertFalse(sound.cue().apply(reread).isEnabled(), "a save from before the cue reads it off");

            sound.cue().apply(phase).setEnabled(true);
            BossPhaseData on = new BossPhaseData();
            on.readFromNBT(phase.writeToNBT());
            assertTrue(sound.cue().apply(on).isEnabled());
        }));
    }

    @TestFactory
    @DisplayName("every new particle cue is born off with its call, stays off on an old save, and loads on once saved on")
    Stream<DynamicTest> puffsAreUnthrownUntilAsked() {
        return Stream.of(PUFFS).map(puff -> DynamicTest.dynamicTest(puff.prefix(), () -> {
            BossPhaseData phase = new BossPhaseData();
            BossParticleCue cue = puff.cue().apply(phase);
            assertFalse(cue.isEnabled(), "an old boss must not start throwing this puff");
            assertEquals(puff.id(), cue.getParticleId());
            assertEquals(puff.count(), cue.getCount());

            CompoundTag old = phase.writeToNBT();
            for (String suffix : new String[]{"On", "Particle", "Count"}) {
                assertTrue(old.contains(puff.prefix() + suffix), "no " + puff.prefix() + suffix + " in the phase tag");
                old.remove(puff.prefix() + suffix);
            }
            BossPhaseData reread = new BossPhaseData();
            puff.cue().apply(reread).setEnabled(true);
            reread.readFromNBT(old);
            assertFalse(puff.cue().apply(reread).isEnabled(), "a save from before the cue reads it off");

            puff.cue().apply(phase).setEnabled(true);
            BossPhaseData on = new BossPhaseData();
            on.readFromNBT(phase.writeToNBT());
            assertTrue(puff.cue().apply(on).isEnabled());
        }));
    }
}
