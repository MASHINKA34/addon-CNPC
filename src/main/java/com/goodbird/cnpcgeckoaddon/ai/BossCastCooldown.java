package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;

import java.util.function.IntSupplier;

/**
 * The cooldown one cast buys, for the two casts that stamp theirs more than once: the ranged
 * attack's burst, stamped as it starts and again from its last shot, and the cone's series, stamped
 * at the end it counts from and again when a stagger cuts it short.
 *
 * <p>A cooldown with a spread is a draw, and two draws for one cast - the later one kept only when it
 * came out longer - pull the cooldown towards the long end of the spread. So with a spread the first
 * draw is the cast's, and every later stamp of the cast reuses it. Without one there is nothing to
 * draw: the cooldown is worked out afresh each time it is wanted, exactly as it always was, the
 * enrage of that moment included.</p>
 */
final class BossCastCooldown {

    private boolean rolled;
    private int ticks;

    /** Forgets the cast's draw: a new cast is starting, or the last one is over. */
    void reset() {
        rolled = false;
        ticks = 0;
    }

    /**
     * The cast's cooldown, in ticks.
     *
     * @param spread whether the ability's profile spreads its cooldown, which is what makes it a draw
     * @param roll   works the cooldown out, drawing the spread, each time it is called
     */
    int ticks(boolean spread, IntSupplier roll) {
        if (!spread) {
            return roll.getAsInt();
        }
        if (!rolled) {
            ticks = roll.getAsInt();
            rolled = true;
        }
        return ticks;
    }

    /** Whether this cast has drawn its cooldown yet: the stamps after the first reuse that draw. */
    boolean isRolled() {
        return rolled;
    }

    /** Whether the profile of {@code kind} in this phase spreads its cooldown, so that one draw has to last the cast. */
    static boolean spreads(BossPhaseData phase, int kind) {
        return phase.profile(kind).getCooldownJitterTicks() > 0;
    }
}
