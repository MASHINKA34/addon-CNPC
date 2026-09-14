package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;

import java.util.Arrays;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The hold a phase keeps after an ability it sees through: the ticks the boss goes on
 * finishing once the ability has done its work, starting nothing else.
 *
 * <p>Owned by {@link TeleportPathController}, which asks the world whether an effect is still
 * running and tells this what it saw. The server never knows how long a swing's animation
 * runs - that lives in the client's assets - so an instant ability has nothing to wait for but
 * this number, counted from the moment it went off. A lasting one counts it from the tick its
 * effect is first seen over, and from the moment it went off when the effect never started at
 * all: a hook that caught nobody has still been thrown. Everything here is bookkeeping on game
 * ticks, so it is tested without a world, and nothing is saved: a hold is long over by the time
 * a server is back up.</p>
 */
final class BossFinishHold {

    /** Game time each ability's hold runs out at, by {@link BossAbility#ordinal()}; NOT_SCHEDULED while none. */
    private final long[] holdUntil = new long[BossAbility.values().length];
    /** Whether each ability's effect was running the last time it was looked at, by ordinal. */
    private final boolean[] wasRunning = new boolean[BossAbility.values().length];

    BossFinishHold() {
        Arrays.fill(holdUntil, NOT_SCHEDULED);
    }

    /**
     * An ability has just gone off: its hold counts from now. That is the whole hold of an
     * instant one, and the hold of a lasting one whose effect never gets going.
     */
    void onPerformed(BossAbility ability, BossPhaseData phase, long gameTime) {
        arm(ability, phase, gameTime);
    }

    /**
     * Notes whether an ability's effect is running on this tick, and starts its hold on the
     * tick the effect is first seen over.
     *
     * <p>Told the effect alone, never the hold: a hold that counted as running would start
     * itself over the moment it ended. Told "not running" for an ability the phase does not
     * mark, whatever its effect is doing, so marking it mid effect starts its watch afresh and
     * unmarking it forgets one.</p>
     */
    void observe(BossAbility ability, boolean effectRunning, BossPhaseData phase, long gameTime) {
        int ordinal = ability.ordinal();
        boolean was = wasRunning[ordinal];
        wasRunning[ordinal] = effectRunning;
        if (was && !effectRunning) {
            arm(ability, phase, gameTime);
        }
    }

    /** True while this ability's hold is running. */
    boolean isHolding(BossAbility ability, long gameTime) {
        return gameTime < holdUntil[ability.ordinal()];
    }

    /** Ticks left on this ability's hold, or 0 while none is running. */
    long remainingTicks(BossAbility ability, long gameTime) {
        return isHolding(ability, gameTime) ? holdUntil[ability.ordinal()] - gameTime : 0L;
    }

    /**
     * Drops every hold and every watch: a phase that is over, a fight that ended or a boss
     * that died owes nobody its finishing, and an effect a reset cut short is not seen over.
     */
    void clear() {
        Arrays.fill(holdUntil, NOT_SCHEDULED);
        Arrays.fill(wasRunning, false);
    }

    /**
     * Starts the hold the phase keeps after this ability, if it keeps one: the ability has to
     * be marked, and the hold has to be more than nothing - an unmarked ability's number is
     * one nobody reads, the way the gate never looks at an unmarked effect. Never cut short: a
     * hold already running past the new end keeps its own.
     */
    private void arm(BossAbility ability, BossPhaseData phase, long gameTime) {
        int kind = ability.kind();
        if (!phase.waitsForFinish(kind)) {
            return;
        }
        int hold = phase.finishHoldTicks(kind);
        if (hold <= 0) {
            return;
        }
        int ordinal = ability.ordinal();
        holdUntil[ordinal] = Math.max(holdUntil[ordinal], gameTime + hold);
    }
}
