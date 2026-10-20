package com.goodbird.cnpcgeckoaddon.ai;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The casts an ability still owes in a row after the one that started it, by its profile's
 * repeats: which ability, how many are left, and when the next one is due.
 *
 * <p>A row moves through three stages. The cast being wound up waits for its hit; once it has gone
 * off with more owed, the row waits for its effect to end - the end the chains go by - and the
 * pause after that end is the one the profile names. Only then is the next cast due, skipping
 * the cooldown the last cast put on the clock. The last cast of the row empties it, and its own
 * cooldown is the one that stands.</p>
 *
 * <p>One row at a time, owned by the newest cast that asked for one. Nothing here is saved: a row
 * owed when the server stopped is not owed by the boss that loads.</p>
 */
final class BossAbilityRepeat {

    private enum Stage {
        /** No row is owed. */
        NONE,
        /** A cast of the row is winding up; its hit has not gone off yet. */
        WIND_UP,
        /** A cast of the row has gone off and more are owed; its effect is still going. */
        RUNNING,
        /** The last cast is over; the next one is due once the pause is. */
        WAITING
    }

    private BossAbility ability = BossAbility.NONE;
    private Stage stage = Stage.NONE;
    private int total;
    private int left;
    private int delayTicks;
    private int links;
    private long dueAt = NOT_SCHEDULED;

    /**
     * A cast that asks for repeats has started: the row is its now, whatever was owed before.
     *
     * @param links the cast's place in its chain, which every repeat keeps, so a row of repeats
     *              does not lengthen the chain it sits in
     */
    void arm(BossAbility ability, int repeats, int delayTicks, int links) {
        if (repeats <= 0 || ability == BossAbility.NONE) {
            clear();
            return;
        }
        this.ability = ability;
        this.total = repeats;
        this.left = repeats;
        this.delayTicks = Math.max(0, delayTicks);
        this.links = links;
        stage = Stage.WIND_UP;
        dueAt = NOT_SCHEDULED;
    }

    /** One of the owed casts has started winding up. */
    void repeatStarted() {
        if (stage == Stage.NONE) {
            return;
        }
        left = Math.max(0, left - 1);
        stage = Stage.WIND_UP;
        dueAt = NOT_SCHEDULED;
    }

    /**
     * An action went off. Only the row's own cast counts: with more owed the row waits for its
     * effect to end; with none left the row is complete.
     *
     * @return true when this emptied the row
     */
    boolean performed(BossAbility performed) {
        if (stage != Stage.WIND_UP || performed != ability) {
            return false;
        }
        if (left <= 0) {
            clear();
            return true;
        }
        stage = Stage.RUNNING;
        return false;
    }

    /** The last cast's effect is over: the next one is due a pause from now. */
    void ended(long gameTime) {
        if (stage == Stage.RUNNING) {
            stage = Stage.WAITING;
            dueAt = gameTime + delayTicks;
        }
    }

    boolean isActive() {
        return stage != Stage.NONE;
    }

    boolean isWindingUp() {
        return stage == Stage.WIND_UP;
    }

    boolean isRunning() {
        return stage == Stage.RUNNING;
    }

    boolean isWaiting() {
        return stage == Stage.WAITING;
    }

    /** Whether the next cast of the row may start now. */
    boolean isDue(long gameTime) {
        return stage == Stage.WAITING && gameTime >= dueAt;
    }

    /** Whether the row still holds this ability's follow-up back: the chain goes on after its last cast. */
    boolean holds(BossAbility candidate) {
        return stage != Stage.NONE && candidate == ability;
    }

    BossAbility ability() {
        return ability;
    }

    int links() {
        return links;
    }

    /** How many of the owed casts have started so far. */
    int done() {
        return total - left;
    }

    int total() {
        return total;
    }

    int left() {
        return left;
    }

    /** The row's part of a status line: "repeat 1/2", with the wait for the next cast while there is one. */
    String status(long gameTime) {
        if (stage == Stage.NONE) {
            return "";
        }
        String wait = stage == Stage.WAITING ? " in " + Math.max(0L, dueAt - gameTime)
                : stage == Stage.RUNNING ? " after its effect" : "";
        return "repeat " + done() + "/" + total + wait;
    }

    void clear() {
        ability = BossAbility.NONE;
        stage = Stage.NONE;
        total = 0;
        left = 0;
        delayTicks = 0;
        links = 0;
        dueAt = NOT_SCHEDULED;
    }
}
