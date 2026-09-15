package com.goodbird.cnpcgeckoaddon.ai;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * What the boss gets for a copy taken back: health, and a stack of damage that runs out.
 *
 * <p>Kept away from the world so it can be checked without one. The health goes through
 * {@code heal()} on the npc so the health link shares it; the stacks are a second multiplier
 * beside the rage, read where the rage is read - on the swing the boss deals with its own
 * body, and on every number an ability hits for.</p>
 */
final class BossShadowAbsorb {

    private BossShadowAbsorb() {
    }

    /** Health for one copy taken back: a share of the boss' maximum, and nothing for no share. */
    static float healAmount(float maxHealth, int percent) {
        if (maxHealth <= 0.0F || percent <= 0) {
            return 0.0F;
        }
        return maxHealth * percent / 100.0F;
    }

    /**
     * The stacks the boss is holding, and the one clock they run out on together: every stack
     * restarts it, so the buff lasts as long after the last copy as the phase says.
     */
    static final class Stacks {
        private int count;
        private int percent;
        private long expiresAt = NOT_SCHEDULED;

        /** One more stack, up to the cap, and the clock restarted from now. */
        void add(long gameTime, int percent, int maxStacks, int buffTicks) {
            expire(gameTime);
            count = Math.min(Math.max(1, maxStacks), count + 1);
            this.percent = Math.max(0, percent);
            expiresAt = gameTime + Math.max(1, buffTicks);
        }

        int count(long gameTime) {
            expire(gameTime);
            return count;
        }

        long ticksLeft(long gameTime) {
            expire(gameTime);
            return count == 0 ? 0L : Math.max(0L, expiresAt - gameTime);
        }

        /** What the stacks multiply by: one with none, and one plus the per cent per stack with any. */
        double multiplier(long gameTime) {
            expire(gameTime);
            return 1.0D + count * percent / 100.0D;
        }

        /**
         * A number the stacks make bigger. A zero passes through untouched, for the rage's
         * reason: zero damage is "none at all", and the stacks must not invent a hit.
         */
        int scale(int value, long gameTime) {
            double multiplier = multiplier(gameTime);
            if (value <= 0 || multiplier <= 1.0D) {
                return value;
            }
            return Math.max(1, (int) Math.round(value * multiplier));
        }

        void clear() {
            count = 0;
            percent = 0;
            expiresAt = NOT_SCHEDULED;
        }

        private void expire(long gameTime) {
            if (expiresAt != NOT_SCHEDULED && gameTime >= expiresAt) {
                clear();
            }
        }
    }
}
