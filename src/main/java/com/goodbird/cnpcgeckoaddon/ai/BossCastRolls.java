package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import net.minecraft.util.RandomSource;

import java.util.Arrays;

/**
 * What the rotation remembers about each ability's profile from one tick to the next: whether
 * the cast chance came up for the readiness the ability is in, whether the last roll skipped it,
 * whether its conditions were holding it back, and how often it has gone this phase.
 *
 * <p>The chance is rolled once per readiness, not once per look: an ability whose starter turns
 * it away - nobody in reach - is looked at again a few ticks later, and rolling again then would
 * make a 25 % ability a sure one in a crowded fight and a never one in a sparse one. A success
 * is kept until the ability starts or is armed afresh; a failure has already put it back on its
 * cooldown. Nothing here is saved: a reload is a fresh readiness and a fresh phase count.</p>
 */
final class BossCastRolls {

    private final boolean[] passed = new boolean[BossAbilityKind.COUNT];
    private final boolean[] skipped = new boolean[BossAbilityKind.COUNT];
    private final boolean[] held = new boolean[BossAbilityKind.COUNT];
    private final int[] casts = new int[BossAbilityKind.COUNT];

    /**
     * Whether the ability goes on this readiness. Draws from {@code random} only on the first look
     * of a readiness and only for a chance under a hundred, so an ability without one never moves
     * the random the rest of the fight draws from.
     */
    boolean roll(int kind, int chancePercent, RandomSource random) {
        if (!owns(kind) || chancePercent >= BossAbilityProfile.MAX_CAST_CHANCE || passed[kind]) {
            return true;
        }
        boolean pass = random.nextInt(BossAbilityProfile.MAX_CAST_CHANCE) < chancePercent;
        passed[kind] = pass;
        skipped[kind] = !pass;
        return pass;
    }

    /** The ability started: one more cast this phase, and the readiness it was rolled for is spent. */
    void started(int kind) {
        if (owns(kind)) {
            casts[kind]++;
            passed[kind] = false;
            skipped[kind] = false;
        }
    }

    /** The ability was armed afresh - a new phase, a wiped clock, its switch back on - so it rolls anew. */
    void rearmed(int kind) {
        if (owns(kind)) {
            passed[kind] = false;
        }
    }

    /** Whether the ability has gone as often as the phase allows it; a limit of nought is none. */
    boolean limitReached(int kind, int limit) {
        return owns(kind) && limit > BossAbilityProfile.NO_CAST_LIMIT && casts[kind] >= limit;
    }

    int casts(int kind) {
        return owns(kind) ? casts[kind] : 0;
    }

    /** Whether the last roll for this ability came up short, and it is waiting out the cooldown that cost it. */
    boolean isSkipped(int kind) {
        return owns(kind) && skipped[kind];
    }

    /**
     * Notes whether the profile's conditions are holding the ability back right now.
     *
     * @return true when that changed since the last look, which is what is worth a log line
     */
    boolean noteHeld(int kind, boolean nowHeld) {
        if (!owns(kind) || held[kind] == nowHeld) {
            return false;
        }
        held[kind] = nowHeld;
        return true;
    }

    /** Every clock was wiped: whatever was rolled for the readiness it ended is forgotten. */
    void clearRolls() {
        Arrays.fill(passed, false);
        Arrays.fill(skipped, false);
        Arrays.fill(held, false);
    }

    /** A new phase, or the fight reset: the counts start again from nought along with the rolls. */
    void resetPhase() {
        clearRolls();
        Arrays.fill(casts, 0);
    }

    private static boolean owns(int kind) {
        return kind >= 0 && kind < BossAbilityKind.COUNT;
    }
}
