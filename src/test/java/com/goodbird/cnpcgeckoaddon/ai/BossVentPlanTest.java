package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossVentPlan.Activation;
import com.goodbird.cnpcgeckoaddon.ai.BossVentPlan.Rules;
import com.goodbird.cnpcgeckoaddon.ai.BossVentPlan.Slot;
import com.goodbird.cnpcgeckoaddon.data.BossVentSettings;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The beat of a vent timer, checked tick by tick without a world.
 *
 * <p>The clock is what a player reads the fight by: a vent that goes before its outline has been
 * up for the promised ticks, a beat a tick early, a flame that burns one tick short or a timer
 * that runs a fourth cycle when it was told three - none of it throws, all of it is felt.</p>
 */
class BossVentPlanTest {

    private static final int BURST = BossVentSettings.MODE_BURST;
    private static final int FLAME = BossVentSettings.MODE_FLAME;

    /** Writes down what the clock asked for, tick by tick; the lasting ticks are only counted. */
    private static final class Log implements BossVentPlan.Sink {
        final List<String> events = new ArrayList<>();
        final Map<Integer, Integer> acts = new HashMap<>();
        long firstAct = -1L;
        long lastAct = -1L;

        @Override
        public void warn(long now, Activation activation) {
            events.add(now + ":warn " + activation.vent());
        }

        @Override
        public void fire(long now, Activation activation) {
            events.add(now + ":fire " + activation.vent());
        }

        @Override
        public void act(long now, Activation activation) {
            acts.merge(activation.vent(), 1, Integer::sum);
            if (firstAct < 0L) {
                firstAct = now;
            }
            lastAct = now;
        }

        @Override
        public void end(long now, Activation activation) {
            events.add(now + ":end " + activation.vent());
        }

        /** Which vents went on each tick something went, in the order they went. */
        Map<Long, List<Integer>> firesByTick() {
            Map<Long, List<Integer>> fires = new TreeMap<>();
            for (String event : events) {
                if (event.contains(":fire ")) {
                    String[] parts = event.split(":fire ");
                    fires.computeIfAbsent(Long.parseLong(parts[0]), tick -> new ArrayList<>())
                            .add(Integer.parseInt(parts[1]));
                }
            }
            return fires;
        }
    }

    private static List<Slot> slots(int count, int mode) {
        return new ArrayList<>(Collections.nCopies(count, new Slot(1, 0, mode)));
    }

    private static Rules rules(int pattern, int randomCount, int cycle, int warn, int active, int repeats) {
        return new Rules(pattern, randomCount, cycle, warn, active, repeats);
    }

    private static void run(BossVentPlan plan, Log log, long from, long to) {
        for (long tick = from; tick <= to; tick++) {
            plan.tick(tick, log);
        }
    }

    @Test
    @DisplayName("every vent warns on the beat, goes when the warning runs out, lasts its length, and the beat comes round")
    void allAtOnceKeepsTheBeat() {
        Log log = new Log();
        BossVentPlan plan = new BossVentPlan(slots(2, FLAME),
                rules(BossVentSettings.PATTERN_ALL, 1, 100, 20, 40, 0), 1000L, RandomSource.create(1L));
        run(plan, log, 1000L, 1199L);
        assertEquals(List.of("1000:warn 0", "1000:warn 1", "1020:fire 0", "1020:fire 1", "1060:end 0", "1060:end 1",
                "1100:warn 0", "1100:warn 1", "1120:fire 0", "1120:fire 1", "1160:end 0", "1160:end 1"), log.events,
                "the first beat comes on the tick of the cast, the next one cycle later");
        assertEquals(80, log.acts.get(0), "forty ticks of flame a beat, twice");
        assertEquals(1020L, log.firstAct, "the flame burns from the tick it goes");
        assertEquals(1159L, log.lastAct, "and its last tick is the one before it ends");
        assertEquals(2, plan.cyclesDone());
        assertFalse(plan.isOver(), "a timer of no set length runs on");
        assertTrue(plan.hasBeatsLeft());
    }

    @Test
    @DisplayName("a vent's shift in the volley moves its whole turn, warning and all")
    void theShiftMovesTheWholeTurn() {
        Log log = new Log();
        List<Slot> slots = List.of(new Slot(1, 0, BURST), new Slot(1, 10, BURST));
        BossVentPlan plan = new BossVentPlan(slots, rules(BossVentSettings.PATTERN_ALL, 1, 100, 20, 40, 1), 0L,
                RandomSource.create(1L));
        run(plan, log, 0L, 29L);
        assertFalse(plan.isOver(), "the shifted vent has not gone yet");
        run(plan, log, 30L, 60L);
        assertEquals(List.of("0:warn 0", "10:warn 1", "20:fire 0", "20:end 0", "30:fire 1", "30:end 1"), log.events,
                "the second vent goes half a second after the first, after a warning just as long");
        assertTrue(plan.isOver(), "one cycle, and both have gone");
        assertTrue(log.acts.isEmpty(), "a blast has no lasting ticks");
    }

    @Test
    @DisplayName("one after another takes the vents in list order, one a beat, and round again")
    void oneAfterAnotherWalksTheList() {
        Log log = new Log();
        BossVentPlan plan = new BossVentPlan(slots(3, BURST),
                rules(BossVentSettings.PATTERN_SEQUENCE, 1, 10, 0, 40, 5), 0L, RandomSource.create(1L));
        run(plan, log, 0L, 100L);
        assertEquals(List.of("0:fire 0", "0:end 0", "10:fire 1", "10:end 1", "20:fire 2", "20:end 2",
                "30:fire 0", "30:end 0", "40:fire 1", "40:end 1"), log.events,
                "no warning is no hiss, and the vent goes on the beat itself");
        assertTrue(plan.isOver());
        assertEquals(5, plan.cyclesDone());
    }

    @Test
    @DisplayName("a vent still going from its last beat sits the next one out, and goes on the first beat it is free")
    void aBusyVentSitsTheBeatOut() {
        Log log = new Log();
        BossVentPlan plan = new BossVentPlan(slots(1, FLAME),
                rules(BossVentSettings.PATTERN_ALL, 1, 30, 20, 40, 0), 0L, RandomSource.create(1L));
        run(plan, log, 0L, 120L);
        // Busy from 0 to 60: the beat at 30 finds it burning, the one at 60 finds it just out;
        // the one at 90 finds it burning again, the one at 120 just out again.
        assertEquals(List.of("0:warn 0", "20:fire 0", "60:end 0", "60:warn 0", "80:fire 0", "120:end 0",
                "120:warn 0"), log.events, "never two turns of one vent at once");
        assertEquals(5, plan.cyclesDone(), "a beat a vent sat out is still a beat");
        assertEquals(80, log.acts.get(0), "two flames of forty, never overlapping");
    }

    @Test
    @DisplayName("a random beat fires the asked number of different vents, drawn by weight, the same way for the same seed")
    void aRandomBeatDrawsByWeight() {
        Rules twoOfSix = rules(BossVentSettings.PATTERN_RANDOM, 2, 10, 0, 40, 50);
        Log first = new Log();
        BossVentPlan plan = new BossVentPlan(slots(6, BURST), twoOfSix, 0L, RandomSource.create(42L));
        run(plan, first, 0L, 600L);
        Map<Long, List<Integer>> fires = first.firesByTick();
        assertEquals(50, fires.size(), "fifty beats");
        for (Map.Entry<Long, List<Integer>> beat : fires.entrySet()) {
            List<Integer> vents = beat.getValue();
            assertEquals(2, vents.size(), "two vents on the beat at " + beat.getKey());
            assertTrue(vents.get(0) < vents.get(1), "two different vents, in list order: " + vents);
        }
        Log again = new Log();
        run(new BossVentPlan(slots(6, BURST), twoOfSix, 0L, RandomSource.create(42L)), again, 0L, 600L);
        assertEquals(first.events, again.events, "the same seed draws the same vents");

        // One vent a hundred times as likely as each of the other two.
        List<Slot> weighted = List.of(new Slot(100, 0, BURST), new Slot(1, 0, BURST), new Slot(1, 0, BURST));
        Log heavy = new Log();
        run(new BossVentPlan(weighted, rules(BossVentSettings.PATTERN_RANDOM, 1, 10, 0, 40, 200), 0L,
                RandomSource.create(7L)), heavy, 0L, 2000L);
        long heavyFires = heavy.events.stream().filter(event -> event.endsWith(":fire 0")).count();
        assertTrue(heavyFires > 170, "the heavy vent went " + heavyFires + " times of 200");

        // Asking for more vents than there are fires every one of them.
        Log all = new Log();
        run(new BossVentPlan(slots(3, BURST), rules(BossVentSettings.PATTERN_RANDOM, 16, 10, 0, 40, 1), 0L,
                RandomSource.create(3L)), all, 0L, 5L);
        assertEquals(List.of(0, 1, 2), all.firesByTick().get(0L));
    }

    @Test
    @DisplayName("a timer of three cycles gives three beats and is over once its last vent is done")
    void cyclesEndTheTimer() {
        Log log = new Log();
        BossVentPlan plan = new BossVentPlan(slots(1, FLAME),
                rules(BossVentSettings.PATTERN_ALL, 1, 20, 5, 10, 3), 0L, RandomSource.create(1L));
        run(plan, log, 0L, 54L);
        assertEquals(3, plan.cyclesDone());
        assertFalse(plan.hasBeatsLeft(), "no fourth beat is owed");
        assertFalse(plan.isOver(), "the third flame still burns");
        plan.tick(55L, log);
        assertTrue(plan.isOver(), "over the tick its last flame went out");
        assertEquals(30, log.acts.get(0), "three flames of ten");
        assertEquals("55:end 0", log.events.getLast());

        BossVentPlan endless = new BossVentPlan(slots(1, BURST),
                rules(BossVentSettings.PATTERN_ALL, 1, 5, 0, 1, 0), 0L, RandomSource.create(1L));
        Log many = new Log();
        run(endless, many, 0L, 999L);
        assertEquals(200, endless.cyclesDone(), "no set length: a beat every cycle for as long as it runs");
        assertFalse(endless.isOver());
    }

    @Test
    @DisplayName("a cast while the timer runs starts it over, stops it or is turned away; with none running it starts one")
    void theRecastRule() {
        assertEquals(BossVentPlan.CAST_RESTART, BossVentPlan.onCast(BossVentSettings.RECAST_RESTART, true));
        assertEquals(BossVentPlan.CAST_STOP, BossVentPlan.onCast(BossVentSettings.RECAST_STOP, true));
        assertEquals(BossVentPlan.CAST_REFUSE, BossVentPlan.onCast(BossVentSettings.RECAST_IGNORE, true));
        for (int rule = BossVentSettings.RECAST_RESTART; rule <= BossVentSettings.RECAST_IGNORE; rule++) {
            assertEquals(BossVentPlan.CAST_START, BossVentPlan.onCast(rule, false),
                    "nothing running is a plain start whatever the rule");
        }
    }

    @Test
    @DisplayName("a stopped timer sets nothing more off, and names what was going so it can be let go of")
    void aStoppedTimerGoesQuiet() {
        Log log = new Log();
        BossVentPlan plan = new BossVentPlan(slots(2, FLAME),
                rules(BossVentSettings.PATTERN_ALL, 1, 50, 20, 40, 0), 0L, RandomSource.create(1L));
        run(plan, log, 0L, 30L);
        assertEquals(11, log.acts.get(0), "burning since tick twenty");
        plan.stop();
        assertTrue(plan.isOver());
        assertFalse(plan.hasBeatsLeft());
        assertEquals(2, plan.activations().size(), "the flames still going are named for whoever lets go of them");
        int before = log.events.size();
        run(plan, log, 31L, 300L);
        assertEquals(before, log.events.size(), "no beat, no fire and no end after the stop");
        assertEquals(11, log.acts.get(0), "and no more burning");
    }

    @Test
    @DisplayName("the warning runs from nought to one over its ticks, and the status line reads the beat")
    void theWarningAndTheStatus() {
        Log log = new Log();
        BossVentPlan plan = new BossVentPlan(slots(1, FLAME),
                rules(BossVentSettings.PATTERN_ALL, 1, 100, 20, 40, 3), 0L, RandomSource.create(1L));
        plan.tick(0L, log);
        Activation activation = plan.activations().getFirst();
        assertEquals(0.0F, activation.warnProgress(0L));
        assertEquals(0.5F, activation.warnProgress(10L));
        assertEquals(1.0F, activation.warnProgress(20L));
        assertTrue(activation.isWarning(19L));
        assertFalse(activation.isWarning(20L), "the warning is down the tick it goes");
        assertEquals("cycle 1/3, next in 100, active 0", plan.status(0L));
        run(plan, log, 1L, 30L);
        assertTrue(activation.isActing(30L));
        assertEquals(10L, activation.elapsed(30L));
        assertEquals("cycle 1/3, next in 70, active 1", plan.status(30L));
        BossVentPlan endless = new BossVentPlan(slots(1, FLAME),
                rules(BossVentSettings.PATTERN_ALL, 1, 100, 20, 40, 0), 0L, RandomSource.create(1L));
        endless.tick(0L, log);
        assertEquals("cycle 1/inf, next in 100, active 0", endless.status(0L));
    }
}
