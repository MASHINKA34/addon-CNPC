package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The arithmetic of the boss trading places with one of its copies, kept away from the
 * world so it can be checked without one.
 *
 * <p>The swap is meant to go unnoticed - no sound, no puff, nothing said - so what makes it
 * a swap rather than a teleport is that nothing about either body is lost in it: where it
 * stood, which way it faced, which way its head and body were turned, how it was moving and
 * how far it had fallen all go across together. A swap that reset any of those would give
 * the game away with a boss that lands facing north.</p>
 */
final class BossShadowSwap {

    private BossShadowSwap() {
    }

    /** Everything about where a body is and how it is going, taken as one so two can be traded whole. */
    record Pose(Vec3 position, float yaw, float headYaw, float bodyYaw, float pitch, Vec3 motion,
                float fallDistance) {

        static Pose of(LivingEntity entity) {
            return new Pose(entity.position(), entity.getYRot(), entity.getYHeadRot(), entity.yBodyRot,
                    entity.getXRot(), entity.getDeltaMovement(), entity.fallDistance);
        }

        /**
         * Puts everything but the position on: the position went through a teleport of its own,
         * which is also what zeroed the motion and the fall this puts back.
         */
        void applyTo(LivingEntity entity) {
            entity.setYRot(yaw);
            entity.yRotO = yaw;
            entity.setYHeadRot(headYaw);
            entity.yHeadRotO = headYaw;
            entity.yBodyRot = bodyYaw;
            entity.yBodyRotO = bodyYaw;
            entity.setXRot(pitch);
            entity.xRotO = pitch;
            entity.setDeltaMovement(motion);
            entity.fallDistance = fallDistance;
        }
    }

    /** The two poses the other way about: the boss' for the copy, the copy's for the boss. */
    static Pose[] swapped(Pose boss, Pose copy) {
        return new Pose[] {copy, boss};
    }

    static long nextSwapAt(long gameTime, int intervalTicks) {
        return gameTime + Math.max(1, intervalTicks);
    }

    static boolean due(long gameTime, long nextSwapAt) {
        return gameTime >= nextSwapAt;
    }

    /** Whether a swap may go now: always, or only with both bodies between casts and not moving under one. */
    static boolean allowed(boolean onlyIdle, boolean bossIdle, boolean copyIdle) {
        return !onlyIdle || (bossIdle && copyIdle);
    }

    /** Which of {@code alive} copies is traded with: any of them, evenly. */
    static int pick(RandomSource random, int alive) {
        return alive <= 1 ? 0 : random.nextInt(alive);
    }
}
