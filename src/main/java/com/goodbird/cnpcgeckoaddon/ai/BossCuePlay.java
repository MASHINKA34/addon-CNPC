package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Where an ability's own cues go off on somebody: the sound at their feet, the puff over their
 * middle, spread over their body.
 *
 * <p>The seven abilities that had no cues of their own got them all at once - a slam's victims,
 * a swing's, a strike's, a minion standing up, a dose of fire, a caught prey - and each of them
 * is the same two calls on one entity. A switched-off cue costs nothing here: the cue itself
 * returns before anything is sent.</p>
 */
final class BossCuePlay {

    /** A little drift, so a puff of sparks or smoke reads as a burst rather than hanging still. */
    private static final double PUFF_SPEED = 0.05D;

    private BossCuePlay() {
    }

    /**
     * Plays both cues on one entity; either may be null for an ability that has only the other.
     *
     * @param dust what the particle cue's {@code dust} id stands for here: the ability's colour in
     *             the phase it is cast in
     */
    static void atEntity(ServerLevel level, Entity at, BossSoundCue sound, BossParticleCue particles,
                         DustParticleOptions dust) {
        if (sound != null) {
            sound.play(level, at.getX(), at.getY(), at.getZ(), SoundSource.HOSTILE);
        }
        if (particles != null) {
            particles.emitDust(level, at.getX(), at.getY() + at.getBbHeight() * 0.5D, at.getZ(),
                    at.getBbWidth() * 0.5D, at.getBbHeight() * 0.3D, at.getBbWidth() * 0.5D, PUFF_SPEED, dust);
        }
    }
}
