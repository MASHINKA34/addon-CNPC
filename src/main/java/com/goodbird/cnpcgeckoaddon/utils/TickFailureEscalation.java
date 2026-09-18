package com.goodbird.cnpcgeckoaddon.utils;

/**
 * What to do about something ticked every tick that has started failing every tick.
 *
 * <p>One failed tick is skipped and forgotten. A run of them is a state the thing will not
 * leave by itself, so it is first told to drop what it was in the middle of, and if that does
 * not help it is switched off for good - both once per run, however long the run lasts. A
 * tick that succeeds ends the run. The boss controller is the one user; kept apart from it so
 * the counting is covered by a plain test.</p>
 */
public final class TickFailureEscalation {

    /** Ten seconds of nothing but failures. Technical, not a setting: it never fires in a healthy fight. */
    public static final int RESET_AFTER = 200;
    /** Ten more after the reset changed nothing. */
    public static final int DISABLE_AFTER = 400;

    /** What one more failure calls for. */
    public enum Step {
        /** Skip the tick; nothing more. */
        SKIP,
        /** Drop whatever was in progress and start over. */
        RESET,
        /** Give up on it until it is loaded again. */
        DISABLE
    }

    private int consecutive;
    private boolean disabled;

    /** One more failed tick. */
    public Step failed() {
        if (consecutive < DISABLE_AFTER) {
            consecutive++;
        }
        if (consecutive == RESET_AFTER) {
            return Step.RESET;
        }
        // Reported once: the count stays at the limit, and only the step onto it says so.
        if (consecutive == DISABLE_AFTER && !disabled) {
            disabled = true;
            return Step.DISABLE;
        }
        return Step.SKIP;
    }

    /** A tick that went through: the run, if there was one, is over. */
    public void succeeded() {
        consecutive = 0;
        disabled = false;
    }

    /** How many ticks in a row have failed so far. */
    public int consecutiveFailures() {
        return consecutive;
    }
}
