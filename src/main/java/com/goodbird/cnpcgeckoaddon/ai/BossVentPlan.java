package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossVentSettings;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The clock of one vent timer: which vents go on which beat, when each one's warning goes up,
 * when it goes, how long it lasts, and when the whole timer is over.
 *
 * <p>Kept apart from the world on purpose, the seismic series' way. Everything that touches a
 * level - the outline, the hiss, the blast, the flame, the wall - is asked of the {@link Sink}
 * the scheduler hands in, so the pace of a timer can be checked tick by tick without a server to
 * run it on. The clock owns nothing but ticks and which vent is which; where a vent is and what
 * its box looks like is the scheduler's.</p>
 *
 * <p>A vent still busy from an earlier beat - warning, burning or standing as a wall - sits a
 * beat out rather than going twice at once: two flames out of one grate are one flame hitting
 * twice, and two walls one wall pushing twice as hard.</p>
 */
final class BossVentPlan {

    /** What a cast does: starts a timer, starts the running one over, stops it, or is turned away. */
    static final int CAST_START = 0;
    static final int CAST_RESTART = 1;
    static final int CAST_STOP = 2;
    static final int CAST_REFUSE = 3;

    /** What a cast does, by the phase's rule for a cast while the timer runs and whether one does. */
    static int onCast(int recast, boolean running) {
        if (!running) {
            return CAST_START;
        }
        return switch (recast) {
            case BossVentSettings.RECAST_STOP -> CAST_STOP;
            case BossVentSettings.RECAST_IGNORE -> CAST_REFUSE;
            default -> CAST_RESTART;
        };
    }

    /**
     * One vent as the clock sees it: how likely a random beat is to pick it, how long after its
     * beat it goes, and what it does.
     */
    record Slot(int weight, int delayTicks, int mode) {
    }

    /** The rules a cast runs by, taken off the settings on the tick it landed. */
    record Rules(int pattern, int randomCount, int cycleTicks, int warnTicks, int activeTicks, int repeats) {

        static Rules of(BossVentSettings vent) {
            return new Rules(vent.getPattern(), vent.getRandomCount(), vent.getCycleTicks(), vent.getWarnTicks(),
                    vent.getActiveTicks(), vent.getRepeats());
        }
    }

    /** What the clock asks the world to do. */
    interface Sink {
        /** A vent's warning goes up: once, on its first tick, and only when there is a warning at all. */
        void warn(long now, Activation activation);

        /** A vent goes: a blast lands, or a flame or a wall starts. Once. */
        void fire(long now, Activation activation);

        /** One tick of a flame or a wall, from the tick it went to the last tick it lasts. */
        void act(long now, Activation activation);

        /** A vent is done: the tick after a flame's or a wall's last, or the blast's own. */
        void end(long now, Activation activation);
    }

    /** One vent's turn on one beat: warned for from {@link #warnAt}, going at {@link #startsAt}, over at {@link #endsAt}. */
    static final class Activation {
        private final int vent;
        private final int mode;
        private final long warnAt;
        private final long startsAt;
        private final long endsAt;
        private boolean warned;
        private boolean started;

        private Activation(int vent, int mode, long warnAt, long startsAt, long endsAt) {
            this.vent = vent;
            this.mode = mode;
            this.warnAt = warnAt;
            this.startsAt = startsAt;
            this.endsAt = endsAt;
        }

        /** Which vent, by its place in the list the clock was handed. */
        int vent() {
            return vent;
        }

        int mode() {
            return mode;
        }

        long warnAt() {
            return warnAt;
        }

        long startsAt() {
            return startsAt;
        }

        long endsAt() {
            return endsAt;
        }

        /** Whether this vent is outlined on this tick: its warning up, and it not gone yet. */
        boolean isWarning(long now) {
            return now >= warnAt && now < startsAt;
        }

        /** Whether this vent has gone and is still burning or standing. */
        boolean isActing(long now) {
            return started && now < endsAt;
        }

        /** Ticks since the vent went; nought on the tick it went. */
        long elapsed(long now) {
            return now - startsAt;
        }

        /** How far the warning has run, from 0 the tick it went up to 1 the tick the vent goes. */
        float warnProgress(long now) {
            long warn = startsAt - warnAt;
            return warn <= 0L ? 1.0F : (float) Mth.clamp((double) (now - warnAt) / warn, 0.0D, 1.0D);
        }
    }

    private final List<Slot> slots;
    private final Rules rules;
    private final RandomSource random;
    private final List<Activation> activations = new ArrayList<>();
    /** Per vent, the tick its last turn is over: it sits out any beat before that. */
    private final long[] busyUntil;
    private long nextAt;
    private int cyclesDone;
    /** The vent a timer taking them one after another took last, or -1 before its first beat. */
    private int cursor = -1;
    private boolean stopped;

    /**
     * @param startedAt the tick the cast landed on; the first beat comes on that very tick
     * @param random    what a random beat draws its vents with; seeded in a test
     */
    BossVentPlan(List<Slot> slots, Rules rules, long startedAt, RandomSource random) {
        this.slots = List.copyOf(slots);
        this.rules = rules;
        this.random = random;
        this.busyUntil = new long[this.slots.size()];
        this.nextAt = startedAt;
    }

    /**
     * Moves the clock one tick on: sets the beat off if one is due, then walks every vent that is
     * warning, going or lasting through what this tick owes it, in the order they were set off.
     */
    void tick(long now, Sink sink) {
        if (stopped) {
            return;
        }
        if (hasBeatsLeft() && now >= nextAt) {
            beat(now);
            cyclesDone++;
            nextAt += Math.max(1, rules.cycleTicks());
        }
        Iterator<Activation> walk = activations.iterator();
        while (walk.hasNext()) {
            Activation activation = walk.next();
            if (!activation.warned && now >= activation.warnAt) {
                activation.warned = true;
                if (activation.startsAt > activation.warnAt) {
                    sink.warn(now, activation);
                }
            }
            if (!activation.started && now >= activation.startsAt) {
                activation.started = true;
                sink.fire(now, activation);
            }
            if (activation.started && now < activation.endsAt) {
                sink.act(now, activation);
            }
            if (activation.started && now >= activation.endsAt) {
                walk.remove();
                sink.end(now, activation);
            }
        }
    }

    /** The vents this beat sets off, each with its warning, its going and its end worked out. */
    private void beat(long now) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (busyUntil[i] <= now) {
                free.add(i);
            }
        }
        for (int vent : pick(free)) {
            Slot slot = slots.get(vent);
            long warnAt = now + Math.max(0, slot.delayTicks());
            long startsAt = warnAt + Math.max(0, rules.warnTicks());
            long endsAt = slot.mode() == BossVentSettings.MODE_BURST ? startsAt
                    : startsAt + Math.max(1, rules.activeTicks());
            activations.add(new Activation(vent, slot.mode(), warnAt, startsAt, endsAt));
            busyUntil[vent] = endsAt;
        }
    }

    /**
     * Which of the free vents this beat takes: every one, the next along in list order - which
     * spends its turn even when that one is still busy, so the order is kept - or a few drawn by
     * their weights, each at most once.
     */
    private List<Integer> pick(List<Integer> free) {
        if (slots.isEmpty()) {
            return List.of();
        }
        switch (rules.pattern()) {
            case BossVentSettings.PATTERN_SEQUENCE -> {
                cursor = (cursor + 1) % slots.size();
                return free.contains(cursor) ? List.of(cursor) : List.of();
            }
            case BossVentSettings.PATTERN_RANDOM -> {
                List<Integer> pool = new ArrayList<>(free);
                List<Integer> picked = new ArrayList<>();
                while (!pool.isEmpty() && picked.size() < rules.randomCount()) {
                    picked.add(pool.remove(weightedIndex(pool)));
                }
                // In list order, so the shifts of a random beat ripple the way the list reads.
                Collections.sort(picked);
                return picked;
            }
            default -> {
                return free;
            }
        }
    }

    /** One place in the pool, drawn by the weights of the vents in it; every weight counts as at least one. */
    private int weightedIndex(List<Integer> pool) {
        int total = 0;
        for (int vent : pool) {
            total += Math.max(1, slots.get(vent).weight());
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            roll -= Math.max(1, slots.get(pool.get(i)).weight());
            if (roll < 0) {
                return i;
            }
        }
        return pool.size() - 1;
    }

    /** Whether the timer still owes a beat: always, for one that runs until it is stopped. */
    boolean hasBeatsLeft() {
        return !stopped && (rules.repeats() <= 0 || cyclesDone < rules.repeats());
    }

    /**
     * Calls the timer off: no beat comes and nothing warned for goes. What was still going is
     * the caller's to let go of - {@link #activations()} still names it until then.
     */
    void stop() {
        stopped = true;
    }

    /** Whether the timer has run its course, or been called off; what a second cast and the gates wait on. */
    boolean isOver() {
        return stopped || !hasBeatsLeft() && activations.isEmpty();
    }

    /** Every vent warning, going or lasting right now, oldest first, for whoever paints or lets go of them. */
    List<Activation> activations() {
        return Collections.unmodifiableList(activations);
    }

    /** Beats set off so far. */
    int cyclesDone() {
        return cyclesDone;
    }

    /** The tick the next beat comes on. */
    long nextAt() {
        return nextAt;
    }

    /** The diagnostic line: which beat of how many, how long to the next, and how many vents are going. */
    String status(long now) {
        int acting = 0;
        for (Activation activation : activations) {
            if (activation.isActing(now)) {
                acting++;
            }
        }
        String of = rules.repeats() > 0 ? Integer.toString(rules.repeats()) : "inf";
        String next = hasBeatsLeft() ? Long.toString(Math.max(0L, nextAt - now)) : "-";
        return "cycle " + cyclesDone + "/" + of + ", next in " + next + ", active " + acting;
    }
}
