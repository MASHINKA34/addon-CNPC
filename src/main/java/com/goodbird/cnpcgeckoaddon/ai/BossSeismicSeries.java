package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossSeismicPlan.Ring;
import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The clock of one seismic cast: which ring comes up on which tick, how long it is outlined
 * before it hits, and when the whole series starts over.
 *
 * <p>Kept apart from the world on purpose. Everything that touches a level - the floor under
 * the boss, the outline on it, the hit itself - is asked of the {@link Sink} the scheduler
 * hands in, so the pace of a series can be checked tick by tick without a server to run it on.
 * The clock owns nothing but ticks: what a pulse is centred on is the sink's to say, and is
 * handed back to it, untouched, with every hit that pulse produces.</p>
 */
final class BossSeismicSeries {

    /** What the clock asks the world to do. */
    interface Sink {
        /**
         * A pulse is due. Says where its rings are centred, or null to skip this pulse
         * altogether - the boss over a hole, with nothing to run the rings on.
         */
        Object pulse(long gameTime, List<Ring> rings);

        /** A ring hits now: its warning ran out, or none was asked for. */
        void hit(long gameTime, Waiting waiting);

        /** A repeat of the series is starting: the wind-up's swing is played again. */
        void animation(long gameTime);
    }

    /** The rules a cast runs by, taken off the settings on the tick it was cast. */
    record Rules(int mode, int randomMin, int randomMax, boolean randomCore, int randomPulses,
                 int intervalTicks, int warnTicks, int repeats, int repeatDelayTicks,
                 boolean repeatAnimation) {

        static Rules of(BossSeismicSettings seismic) {
            return new Rules(seismic.getMode(), seismic.getRandomMin(), seismic.getRandomMax(),
                    seismic.isRandomCore(), seismic.getRandomPulses(), seismic.getIntervalTicks(),
                    seismic.getWarnTicks(), seismic.getRepeats(), seismic.getRepeatDelayTicks(),
                    seismic.isRepeatAnimation());
        }
    }

    /**
     * A ring outlined on the floor, waiting to hit.
     *
     * @param anchor whatever the sink answered the pulse with: where the ring is centred
     */
    record Waiting(Ring ring, Object anchor, long warnedAt, long hitsAt) {

        /** How far the warning has run, from 0 the tick it went up to 1 the tick it hits. */
        float progress(long now) {
            long warn = hitsAt - warnedAt;
            return warn <= 0L ? 1.0F : (float) Mth.clamp((double) (now - warnedAt) / warn, 0.0D, 1.0D);
        }
    }

    private final List<Ring> plan;
    private final Rules rules;
    private final RandomSource random;
    private final int pulseCount;
    private final List<Waiting> waiting = new ArrayList<>();
    /** The pulse the running series takes next, and the series this cast is on, both from nought. */
    private int pulseIndex;
    private int seriesIndex;
    private long nextPulseAt;
    /** Set when a repeat starts, so the swing goes off on the tick of its first pulse and once. */
    private boolean animationPending;
    private boolean over;

    /**
     * @param startedAt the tick the cast landed on; the first pulse comes on that very tick
     * @param random    what a random series draws its rings with; seeded in a test
     */
    BossSeismicSeries(List<Ring> plan, Rules rules, long startedAt, RandomSource random) {
        this.plan = List.copyOf(plan);
        this.rules = rules;
        this.random = random;
        this.pulseCount = BossSeismicPlan.pulseCount(rules.mode(), plan.size(), rules.randomPulses());
        this.nextPulseAt = startedAt;
    }

    /**
     * Moves the clock one tick on: lands the rings whose warning ran out, fires the pulse if
     * one is due, and starts the next series or ends the cast once the plan is spent and every
     * outlined ring has hit.
     */
    void tick(long now, Sink sink) {
        if (over) {
            return;
        }
        // The rings that were already warned for land first, so a warning as long as the
        // interval hits on the tick the next pulse goes up rather than a tick after it.
        Iterator<Waiting> due = waiting.iterator();
        while (due.hasNext()) {
            Waiting ring = due.next();
            if (now >= ring.hitsAt()) {
                due.remove();
                sink.hit(now, ring);
            }
        }
        if (pulseIndex < pulseCount && now >= nextPulseAt) {
            if (animationPending) {
                animationPending = false;
                sink.animation(now);
            }
            List<Ring> rings = pick();
            Object anchor = rings.isEmpty() ? null : sink.pulse(now, rings);
            // A pulse with nowhere to go is a pulse spent, not a pulse held back: the series
            // keeps its pace and comes round again on the next interval.
            if (anchor != null) {
                for (Ring ring : rings) {
                    Waiting outlined = new Waiting(ring, anchor, now, now + rules.warnTicks());
                    if (rules.warnTicks() <= 0) {
                        sink.hit(now, outlined);
                    } else {
                        waiting.add(outlined);
                    }
                }
            }
            pulseIndex++;
            nextPulseAt = now + rules.intervalTicks();
        }
        if (pulseIndex >= pulseCount && waiting.isEmpty()) {
            seriesIndex++;
            if (seriesIndex >= rules.repeats()) {
                over = true;
                return;
            }
            pulseIndex = 0;
            nextPulseAt = now + rules.repeatDelayTicks();
            animationPending = rules.repeatAnimation();
        }
    }

    /** The rings the next pulse takes: the next one outward, or a random handful. */
    private List<Ring> pick() {
        if (rules.mode() == BossSeismicSettings.MODE_RANDOM) {
            return BossSeismicPlan.randomPulse(plan, rules.randomMin(), rules.randomMax(),
                    rules.randomCore(), random);
        }
        return pulseIndex < plan.size() ? List.of(plan.get(pulseIndex)) : List.of();
    }

    /** Calls the cast off: nothing outlined hits, and no pulse or repeat comes. */
    void clear() {
        waiting.clear();
        over = true;
    }

    /** Whether the cast has run its course, or been called off; what a second cast waits on. */
    boolean isOver() {
        return over;
    }

    /** The rings outlined right now, oldest first, for whoever paints them. */
    List<Waiting> waiting() {
        return Collections.unmodifiableList(waiting);
    }

    List<Ring> plan() {
        return plan;
    }

    /** The diagnostic line: which series of how many, which pulse of how many, and how long to the next thing. */
    String status(long now) {
        long next;
        if (pulseIndex < pulseCount || waiting.isEmpty()) {
            next = nextPulseAt - now;
        } else {
            next = Long.MAX_VALUE;
            for (Waiting ring : waiting) {
                next = Math.min(next, ring.hitsAt() - now);
            }
        }
        return "series " + Math.min(seriesIndex + 1, rules.repeats()) + "/" + rules.repeats()
                + ", pulse " + Math.min(pulseIndex, pulseCount) + "/" + pulseCount
                + ", next in " + Math.max(0L, next);
    }
}
