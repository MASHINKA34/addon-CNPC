package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Blows a boss up a configurable number of ticks after it dies.
 *
 * <p>The delay cannot be run off the boss itself: CustomNPCs may discard the entity as soon
 * as it dies, so anything counting down on its tick would simply stop. The position and the
 * settings are therefore snapshotted at the moment of death and the explosion is driven from
 * the level tick instead.</p>
 *
 * <p>The blast itself never goes off while the queue is being read. It kills whatever stands
 * around it, and one of those can easily be another boss that blows up in turn - which is a
 * perfectly good mechanic, and was also a way to modify the pending list from inside its own
 * iteration and take the server down with a {@code ConcurrentModificationException}.
 * {@link TickQueue} is what keeps the two apart.</p>
 *
 * <p>Nothing here is persisted. A pending explosion lives for a second or two, and a server
 * that shuts down inside that window should not detonate something on the next start.</p>
 */
public final class BossExplosionScheduler {

    /**
     * How many bosses may go off in a single level tick. A chain reaction is allowed to run
     * its course, just not all at once: the rest keeps its place in the queue and goes off on
     * the following ticks.
     */
    private static final int MAX_PER_TICK = 16;

    private record Pending(ResourceKey<Level> dimension, Entity source, Vec3 pos, long fireAt,
                           int mode, float power, boolean fire) {
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss explosions", MAX_PER_TICK);

    private BossExplosionScheduler() {
    }

    public static void schedule(ServerLevel level, Entity boss, TeleportPathData data) {
        Pending pending = new Pending(level.dimension(), boss, boss.position(),
                level.getGameTime() + data.getExplosionDelayTicks(),
                data.getExplosionMode(), data.getExplosionPower(), data.isExplosionFire());
        if (data.getExplosionDelayTicks() <= 0) {
            // Undelayed still means "in this death event", exactly as it always has - but only
            // while no other blast is already running. One that is has just killed this boss,
            // and detonating inside it would nest the chain instead of queueing it.
            PENDING.runNow(pending, blast -> detonate(level, blast));
            return;
        }
        PENDING.add(pending);
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        PENDING.drain(
                pending -> pending.dimension().equals(level.dimension()) && gameTime >= pending.fireAt(),
                pending -> detonate(level, pending));
    }

    /** Drops anything still waiting in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension().equals(level.dimension()));
    }

    private static void detonate(ServerLevel level, Pending pending) {
        Vec3 pos = pending.pos();
        // The boss is usually gone by now; only credit it while it still exists.
        Entity source = pending.source() != null && !pending.source().isRemoved() ? pending.source() : null;

        if (pending.mode() == TeleportPathData.EXPLOSION_MODE_EFFECT) {
            // Pure spectacle: the same visuals and sound a real blast produces, but the
            // boss cannot take the party down with it.
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5D, pos.z,
                    Math.max(1, Math.round(pending.power() / 2.0F)),
                    pending.power() * 0.25D, pending.power() * 0.25D, pending.power() * 0.25D, 0.0D);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 4.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            return;
        }

        level.explode(source, pos.x, pos.y + 0.5D, pos.z, pending.power(), pending.fire(),
                blockInteraction(pending.mode()));
    }

    private static Level.ExplosionInteraction blockInteraction(int mode) {
        return switch (mode) {
            case TeleportPathData.EXPLOSION_MODE_BLOCKS -> Level.ExplosionInteraction.MOB;
            case TeleportPathData.EXPLOSION_MODE_BLOCKS_ALWAYS -> Level.ExplosionInteraction.TNT;
            default -> Level.ExplosionInteraction.NONE;
        };
    }
}
