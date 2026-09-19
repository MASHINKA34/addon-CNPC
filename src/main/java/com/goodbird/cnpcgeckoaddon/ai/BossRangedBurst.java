package com.goodbird.cnpcgeckoaddon.ai;

/**
 * The shots one ranged cast of a boss still owes, and when the next of them is due.
 *
 * <p>The cone's series, cut down to a count: a burst fires the same projectile at the same
 * victim over and over, so there is nothing to keep but how many are left. Kept apart from the
 * world for the series' reason - the order and the timing are worth testing without a boss.</p>
 *
 * <p>The first shot of a cast is fired by the cast itself; what is counted here is the rest.
 * So a burst of one never runs at all, which is exactly the single shot this ability was
 * before a burst was a setting.</p>
 */
final class BossRangedBurst {

    private int shotsLeft;
    private long nextAt;

    /**
     * Starts the follow-up shots of a cast whose first shot has just gone off.
     *
     * @param shots        shots in the whole cast, the one already fired included
     * @param intervalTicks ticks between two of them
     */
    void start(int shots, int intervalTicks, long gameTime) {
        shotsLeft = Math.max(0, shots - 1);
        nextAt = gameTime + Math.max(1, intervalTicks);
    }

    /** Whether the cast still owes a shot, which is what holds the boss busy for it. */
    boolean isRunning() {
        return shotsLeft > 0;
    }

    /**
     * Whether a shot leaves on this tick, taken off the count when it does.
     *
     * <p>The pause is counted from the tick the shot really left rather than from when it fell
     * due, the cone's way: a boss that was not ticked for a while - carried, in an unloaded
     * chunk - picks its burst up one shot at a time instead of emptying it at once.</p>
     */
    boolean due(long gameTime, int intervalTicks) {
        if (shotsLeft <= 0 || gameTime < nextAt) {
            return false;
        }
        shotsLeft--;
        nextAt = gameTime + Math.max(1, intervalTicks);
        return true;
    }

    /** Drops whatever is left: a phase change, a stagger, a death, a target that is gone. */
    void clear() {
        shotsLeft = 0;
        nextAt = 0L;
    }
}
