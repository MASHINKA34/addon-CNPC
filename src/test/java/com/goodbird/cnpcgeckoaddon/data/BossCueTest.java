package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two cue types without a world: what they write, what a tag missing their keys reads
 * back as, and what an id nothing is registered under falls back to.
 *
 * <p>Whether a sound or a particle exists is the one question a cue cannot answer without the
 * game's registries, so it is asked through a swappable answer and the test supplies its own.
 * Everything else here - the prefix, the clamps, the defaults - is arithmetic on a tag.</p>
 */
class BossCueTest {

    private static final Set<String> SOUNDS = Set.of("minecraft:block.note_block.bell",
            "minecraft:block.note_block.pling");
    private static final Set<String> PARTICLES = Set.of("minecraft:enchant", "minecraft:crit");

    @BeforeEach
    void useTestRegistry() {
        BossSoundCue.useRegistry(SOUNDS::contains);
        BossParticleCue.useRegistry(PARTICLES::contains);
    }

    @AfterEach
    void restoreRegistry() {
        BossSoundCue.useRegistry(null);
        BossParticleCue.useRegistry(null);
    }

    private static BossSoundCue bell() {
        return new BossSoundCue("minecraft:block.note_block.bell", 0.8F, 0.6F);
    }

    private static BossParticleCue sparks() {
        return new BossParticleCue("minecraft:enchant", 8);
    }

    @Test
    @DisplayName("a sound cue is born as the call it replaced")
    void aSoundCueStartsAtItsDefaults() {
        BossSoundCue cue = bell();
        assertTrue(cue.isEnabled());
        assertEquals("minecraft:block.note_block.bell", cue.getSoundId());
        assertEquals(8, cue.getVolume(), "0.8F is eight tenths");
        assertEquals(6, cue.getPitch());
        assertEquals(0.8F, cue.volumeValue(), 1.0E-6F);
        assertEquals(0.6F, cue.pitchValue(), 1.0E-6F);
    }

    @Test
    @DisplayName("a sound cue round-trips under its owner's prefix")
    void aSoundCueRoundTrips() {
        BossSoundCue cue = bell();
        cue.setEnabled(false);
        cue.setSoundId("minecraft:block.note_block.pling");
        cue.setVolume(25);
        cue.setPitch(20);

        CompoundTag tag = new CompoundTag();
        cue.writeToNBT(tag, "GeckoBossTuningTelegraphSound");
        assertTrue(tag.contains("GeckoBossTuningTelegraphSoundOn"));
        assertTrue(tag.contains("GeckoBossTuningTelegraphSoundSound"));
        assertTrue(tag.contains("GeckoBossTuningTelegraphSoundVolume"));
        assertTrue(tag.contains("GeckoBossTuningTelegraphSoundPitch"));
        assertEquals(4, tag.size(), "the cue writes four keys and nothing else");

        BossSoundCue read = bell();
        read.readFromNBT(tag, "GeckoBossTuningTelegraphSound");
        assertFalse(read.isEnabled());
        assertEquals("minecraft:block.note_block.pling", read.getSoundId());
        assertEquals(25, read.getVolume());
        assertEquals(20, read.getPitch());
    }

    @Test
    @DisplayName("a tag without the sound keys is a boss that never heard of them")
    void anEmptyTagLeavesTheSoundDefaults() {
        BossSoundCue cue = bell();
        cue.setEnabled(false);
        cue.setSoundId("minecraft:block.note_block.pling");
        cue.setVolume(100);
        cue.setPitch(30);

        cue.readFromNBT(new CompoundTag(), "GeckoBossTuningTelegraphSound");
        assertTrue(cue.isEnabled(), "the cue was on before it was a setting");
        assertEquals("minecraft:block.note_block.bell", cue.getSoundId());
        assertEquals(8, cue.getVolume());
        assertEquals(6, cue.getPitch());
    }

    @Test
    @DisplayName("volume and pitch are clamped, whoever wrote them")
    void soundNumbersAreClamped() {
        BossSoundCue cue = bell();
        cue.setVolume(Integer.MAX_VALUE);
        assertEquals(BossSoundCue.MAX_VOLUME, cue.getVolume());
        cue.setVolume(Integer.MIN_VALUE);
        assertEquals(BossSoundCue.MIN_VOLUME, cue.getVolume());
        cue.setPitch(Integer.MAX_VALUE);
        assertEquals(BossSoundCue.MAX_PITCH, cue.getPitch());
        cue.setPitch(Integer.MIN_VALUE);
        assertEquals(BossSoundCue.MIN_PITCH, cue.getPitch());

        CompoundTag tag = new CompoundTag();
        tag.putInt("CueVolume", Integer.MAX_VALUE);
        tag.putInt("CuePitch", Integer.MIN_VALUE);
        BossSoundCue read = bell();
        read.readFromNBT(tag, "Cue");
        assertEquals(BossSoundCue.MAX_VOLUME, read.getVolume());
        assertEquals(BossSoundCue.MIN_PITCH, read.getPitch());
    }

    @Test
    @DisplayName("an id nothing is registered under plays the sound the cue was born with")
    void anUnknownSoundFallsBack() {
        BossSoundCue cue = bell();
        cue.setSoundId("somemod:gone");
        assertEquals("somemod:gone", cue.getSoundId(), "what was typed is what stays saved");
        assertEquals("minecraft:block.note_block.bell", cue.resolvedId());
        assertFalse(BossSoundCue.isKnownSound("somemod:gone"));
        assertTrue(BossSoundCue.isKnownSound("minecraft:block.note_block.pling"));

        cue.setSoundId("");
        assertEquals("minecraft:block.note_block.bell", cue.getSoundId(), "an empty field is no id at all");
    }

    @Test
    @DisplayName("a particle cue round-trips, clamps its count and falls back on an unknown id")
    void aParticleCueRoundTrips() {
        BossParticleCue cue = sparks();
        assertTrue(cue.isEnabled());
        assertEquals("minecraft:enchant", cue.getParticleId());
        assertEquals(8, cue.getCount());

        cue.setEnabled(false);
        cue.setParticleId("minecraft:crit");
        cue.setCount(Integer.MAX_VALUE);
        assertEquals(BossParticleCue.MAX_COUNT, cue.getCount());

        CompoundTag tag = new CompoundTag();
        cue.writeToNBT(tag, "GeckoBossTuningTotemHitParticles");
        assertEquals(3, tag.size(), "the cue writes three keys and nothing else");

        BossParticleCue read = sparks();
        read.readFromNBT(tag, "GeckoBossTuningTotemHitParticles");
        assertFalse(read.isEnabled());
        assertEquals("minecraft:crit", read.getParticleId());
        assertEquals(BossParticleCue.MAX_COUNT, read.getCount());

        read.readFromNBT(new CompoundTag(), "GeckoBossTuningTotemHitParticles");
        assertTrue(read.isEnabled());
        assertEquals("minecraft:enchant", read.getParticleId());
        assertEquals(8, read.getCount());

        read.setParticleId("somemod:gone");
        assertEquals("minecraft:enchant", read.resolvedId());
        read.setCount(Integer.MIN_VALUE);
        assertEquals(BossParticleCue.MIN_COUNT, read.getCount());
    }

    @Test
    @DisplayName("the ability's own dust is an id of its own, not one the registry has to hold")
    void dustIsItsOwnId() {
        BossParticleCue cue = sparks();
        cue.setParticleId(BossParticleCue.DUST_ID);
        assertTrue(BossParticleCue.isDust(cue.getParticleId()));
        assertTrue(BossParticleCue.isKnownParticle(BossParticleCue.DUST_ID));
        assertEquals(BossParticleCue.DUST_ID, cue.resolvedId(),
                "no registry knows the dust, and it must not fall back because of that");
    }

    @Test
    @DisplayName("a copy is its own cue")
    void aCopyIsIndependent() {
        BossSoundCue cue = bell();
        cue.setVolume(40);
        BossSoundCue copy = cue.copy();
        cue.setVolume(10);
        assertEquals(40, copy.getVolume(), "a scheduler keeps what it was told on the cast");
        assertEquals(8, copy.getDefaultVolume(), "and can still be put back to the old call");

        copy.reset();
        assertEquals(8, copy.getVolume());
        assertEquals(6, copy.getPitch());
        assertTrue(copy.isEnabled());
    }
}
