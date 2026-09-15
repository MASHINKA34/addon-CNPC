package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossSeismicPlan.Ring;
import com.goodbird.cnpcgeckoaddon.ai.BossSeismicSeries.Rules;
import com.goodbird.cnpcgeckoaddon.ai.BossSeismicSeries.Waiting;
import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pace of a seismic cast, checked tick by tick without a world.
 *
 * <p>The clock is what a player reads the fight by: a ring that hits before its outline has
 * been up for the promised ticks, a pulse a tick early, a repeat that plays its swing twice or
 * not at all - none of it throws, all of it is felt.</p>
 */
class BossSeismicSeriesTest {

    /** Three rings: the core and two more. */
    private static final List<Ring> PLAN = BossSeismicPlan.rings(3, 2, 0, 7);

    /** Writes down what the clock asked for, tick by tick, and answers every pulse with the same floor. */
    private static final class Log implements BossSeismicSeries.Sink {
        final List<String> events = new ArrayList<>();
        /** What a pulse is answered with; null skips it, the way a boss over a hole does. */
        Object floor = "floor";
        int animations;

        @Override
        public Object pulse(long gameTime, List<Ring> rings) {
            events.add(gameTime + ":pulse " + rings.size());
            return floor;
        }

        @Override
        public void hit(long gameTime, Waiting waiting) {
            events.add(gameTime + ":hit [" + (int) waiting.ring().inner() + "," + (int) waiting.ring().outer() + ")");
        }

        @Override
        public void animation(long gameTime) {
            animations++;
            events.add(gameTime + ":animation");
        }
    }

    private static Rules growing(int intervalTicks, int warnTicks, int repeats, int repeatDelayTicks,
                                 boolean repeatAnimation) {
        return new Rules(BossSeismicSettings.MODE_GROWING, 1, 3, true, 0, intervalTicks, warnTicks,
                repeats, repeatDelayTicks, repeatAnimation);
    }

    private static void run(BossSeismicSeries clock, Log log, long from, long to) {
        for (long tick = from; tick <= to; tick++) {
            clock.tick(tick, log);
        }
    }

    @Test
    @DisplayName("each ring is outlined for the warning and hits when it runs out; pulses come every interval")
    void warningsAndPulsesKeepTheirTicks() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 1, 0, true), 100L, RandomSource.create(1L));
        run(clock, log, 100L, 124L);
        assertEquals(List.of("100:pulse 1", "105:hit [0,3)", "110:pulse 1", "115:hit [3,5)", "120:pulse 1"),
                log.events, "the first pulse comes on the tick of the cast, the rest one interval apart");
        assertFalse(clock.isOver(), "the last ring is still outlined");
        assertEquals(1, clock.waiting().size());
        clock.tick(125L, log);
        assertEquals("125:hit [5,7)", log.events.getLast());
        assertTrue(clock.isOver(), "over the tick the last ring hit");
        assertEquals(0, log.animations, "the wind-up's swing was the controller's; the first series plays none");
    }

    @Test
    @DisplayName("with no warning a ring hits on the tick of its pulse")
    void noWarningHitsAtOnce() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 0, 1, 0, true), 100L, RandomSource.create(1L));
        run(clock, log, 100L, 130L);
        assertEquals(List.of("100:pulse 1", "100:hit [0,3)", "110:pulse 1", "110:hit [3,5)",
                "120:pulse 1", "120:hit [5,7)"), log.events);
        assertTrue(clock.isOver());
    }

    @Test
    @DisplayName("a repeat waits its pause after the last hit, plays the swing once and runs the plan again")
    void repeatsPauseAndPlayTheSwing() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 3, 20, true), 100L, RandomSource.create(1L));
        run(clock, log, 100L, 144L);
        assertEquals("125:hit [5,7)", log.events.getLast(), "nothing happens through the pause");
        assertFalse(clock.isOver());
        clock.tick(145L, log);
        assertEquals(List.of("145:animation", "145:pulse 1"), log.events.subList(log.events.size() - 2, log.events.size()),
                "the swing goes off on the tick of the repeat's first pulse, before it");
        run(clock, log, 146L, 400L);
        assertTrue(clock.isOver());
        assertEquals(2, log.animations, "one swing per repeat, none for the first series");
        assertEquals(9, log.events.stream().filter(event -> event.contains(":hit")).count(), "three series of three rings");
        assertTrue(log.events.contains("190:animation"), "the third series starts a pause after the second's last hit");
    }

    @Test
    @DisplayName("with the repeat's swing switched off the repeats run silent")
    void repeatsMayRunWithoutTheSwing() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 2, 20, false), 100L, RandomSource.create(1L));
        run(clock, log, 100L, 300L);
        assertTrue(clock.isOver());
        assertEquals(0, log.animations);
        assertEquals(6, log.events.stream().filter(event -> event.contains(":hit")).count());
    }

    @Test
    @DisplayName("a pulse with no floor to land on is spent, not held back: the series keeps its pace")
    void aSkippedPulseKeepsThePace() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 1, 0, true), 100L, RandomSource.create(1L));
        clock.tick(100L, log);
        log.floor = null;
        run(clock, log, 101L, 110L);
        log.floor = "floor";
        run(clock, log, 111L, 130L);
        assertEquals(List.of("100:pulse 1", "105:hit [0,3)", "110:pulse 1", "120:pulse 1", "125:hit [5,7)"),
                log.events, "the second ring never hit, the third came on time");
        assertTrue(clock.isOver());
    }

    @Test
    @DisplayName("a hit is handed back whatever its pulse was answered with, so rings that follow the boss keep their centre")
    void hitsKeepTheirPulsesAnchor() {
        List<Object> anchors = new ArrayList<>();
        List<Object> handedBack = new ArrayList<>();
        BossSeismicSeries.Sink sink = new BossSeismicSeries.Sink() {
            @Override
            public Object pulse(long gameTime, List<Ring> rings) {
                Object anchor = new Object();
                anchors.add(anchor);
                return anchor;
            }

            @Override
            public void hit(long gameTime, Waiting waiting) {
                handedBack.add(waiting.anchor());
            }

            @Override
            public void animation(long gameTime) {
            }
        };
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 1, 0, true), 100L, RandomSource.create(1L));
        for (long tick = 100L; tick <= 130L; tick++) {
            clock.tick(tick, sink);
        }
        assertEquals(3, anchors.size());
        assertEquals(3, handedBack.size());
        for (int i = 0; i < 3; i++) {
            assertSame(anchors.get(i), handedBack.get(i), "ring " + i + " hit somewhere other than where its pulse was");
        }
    }

    @Test
    @DisplayName("at random radii a series runs the pulses asked for, each with its handful of rings")
    void randomSeriesRunsItsPulses() {
        Log log = new Log();
        Rules rules = new Rules(BossSeismicSettings.MODE_RANDOM, 1, 3, true, 4, 10, 5, 1, 0, true);
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, rules, 100L, RandomSource.create(5L));
        run(clock, log, 100L, 200L);
        List<String> pulses = log.events.stream().filter(event -> event.contains(":pulse")).toList();
        assertEquals(4, pulses.size(), "four pulses were asked for");
        assertEquals("130:pulse", pulses.get(3).substring(0, 9), "one interval apart");
        for (String pulse : pulses) {
            int rings = Integer.parseInt(pulse.substring(pulse.indexOf(' ') + 1));
            assertTrue(rings >= 1 && rings <= 3, pulse);
        }
        assertTrue(clock.isOver());
        assertEquals(log.events.stream().filter(event -> event.contains(":hit")).count(),
                pulses.stream().mapToInt(pulse -> Integer.parseInt(pulse.substring(pulse.indexOf(' ') + 1))).sum(),
                "every ring a pulse took hit once");
    }

    @Test
    @DisplayName("a second cast is refused for as long as the clock runs, and welcome once it is over")
    void aRunningClockRefusesASecondCast() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 2, 20, true), 100L, RandomSource.create(1L));
        for (long tick = 100L; tick < 170L; tick++) {
            clock.tick(tick, log);
            assertFalse(clock.isOver(), "still running at " + tick + ", so the cast is refused");
        }
        run(clock, log, 170L, 171L);
        assertTrue(clock.isOver(), "the second series' last ring hit at 170: the next cast may start");
    }

    @Test
    @DisplayName("calling the cast off drops every outlined ring and every pulse and repeat still to come")
    void clearDropsEverything() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 3, 20, true), 100L, RandomSource.create(1L));
        run(clock, log, 100L, 112L);
        assertEquals(1, clock.waiting().size(), "the second ring is outlined");
        clock.clear();
        assertTrue(clock.isOver());
        assertTrue(clock.waiting().isEmpty(), "an outlined ring that was called off never hits");
        int before = log.events.size();
        run(clock, log, 113L, 400L);
        assertEquals(before, log.events.size(), "nothing more comes of a cast that was called off");
        assertEquals(0, log.animations);
    }

    @Test
    @DisplayName("the status line says which series and pulse the cast is on, and how long to the next thing")
    void statusLine() {
        Log log = new Log();
        BossSeismicSeries clock = new BossSeismicSeries(PLAN, growing(10, 5, 2, 20, true), 100L, RandomSource.create(1L));
        assertEquals("series 1/2, pulse 0/3, next in 0", clock.status(100L));
        clock.tick(100L, log);
        assertEquals("series 1/2, pulse 1/3, next in 8", clock.status(102L));
        run(clock, log, 101L, 121L);
        assertEquals("series 1/2, pulse 3/3, next in 4", clock.status(121L), "the plan is spent: the last ring's hit is next");
        run(clock, log, 122L, 130L);
        assertEquals("series 2/2, pulse 0/3, next in 15", clock.status(130L), "the pause before the repeat");
    }
}
