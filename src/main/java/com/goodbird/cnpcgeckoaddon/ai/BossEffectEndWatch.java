package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;

/**
 * Which abilities' lasting effects were running on the last look, so the controller can tell
 * the tick one of them ran out on its own - the moment an ability's end animation is for.
 *
 * <p>An effect that a reset cut short is not one that ended: a phase change, a lost fight, a
 * death or a stun stop it, and the end animation playing then would read as the ability having
 * finished. So a reset suppresses whatever is running when it happens, and anything that is still
 * running after it, until it has been seen stopped once - however it stopped, that end was the
 * reset's. A kind first seen idle after a reset is the controller's to watch again.</p>
 *
 * <p>One bit per kind in a {@code long}, the masks' own way; nothing is saved. A server that
 * goes down mid effect owes no end animation for it.</p>
 */
final class BossEffectEndWatch {

    /** Every kind there is, for a reset that suppresses them all. */
    private static final long ALL_KINDS = (1L << BossAbilityKind.COUNT) - 1L;

    /** The kinds whose effect was running on the last look. */
    private long running;
    /** The kinds whose effect a reset caught running, owed no end animation for it. */
    private long suppressed;

    /**
     * Takes one look at one kind.
     *
     * @return true on the look its effect is first seen stopped after running, when nothing cut it
     */
    boolean observe(int kind, boolean runningNow) {
        long bit = bit(kind);
        if (bit == 0L) {
            return false;
        }
        if ((suppressed & bit) != 0L) {
            // Still the effect the reset caught, or already gone: either way it ends nobody's cast.
            if (!runningNow) {
                suppressed &= ~bit;
            }
            running &= ~bit;
            return false;
        }
        boolean wasRunning = (running & bit) != 0L;
        if (runningNow) {
            running |= bit;
        } else {
            running &= ~bit;
        }
        return wasRunning && !runningNow;
    }

    /**
     * Stops watching one kind - its profile has no end animation - without forgetting a reset's
     * claim on it: watched again later, an effect the reset caught still ends as the reset's.
     */
    void forget(int kind) {
        running &= ~bit(kind);
    }

    /** One kind's effect cut short by something other than a reset: a stun breaking off a run. */
    void suppress(int kind) {
        long bit = bit(kind);
        suppressed |= bit;
        running &= ~bit;
    }

    /** A reset: a phase change, the fight ending, the boss dying. Nothing running now ends on its own. */
    void suppressAll() {
        suppressed = ALL_KINDS;
        running = 0L;
    }

    /** Whether the watch thinks this kind's effect is running; for the status line and the tests. */
    boolean isRunning(int kind) {
        return (running & bit(kind)) != 0L;
    }

    /** Whether a reset's claim is still on this kind. */
    boolean isSuppressed(int kind) {
        return (suppressed & bit(kind)) != 0L;
    }

    private static long bit(int kind) {
        return kind < 0 || kind >= BossAbilityKind.COUNT ? 0L : 1L << kind;
    }
}
