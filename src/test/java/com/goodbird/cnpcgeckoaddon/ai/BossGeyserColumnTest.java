package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geyser column's shape and pace, checked without a world: where each slice lies, how
 * wide it is, how many come up on a tick, and what the particle ceiling does to that.
 *
 * <p>None of it throws when it is wrong: a cone whose slices narrow the wrong way, a rise
 * that ends a tick late or a column that spends three hundred packets on one tick only ever
 * read in play as an eruption that looks odd or a server that stutters.</p>
 */
class BossGeyserColumnTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a cone's slices run straight from the bottom radius to the top one, half a block apart")
    void aConeNarrowsFromTheFloorToItsTop() {
        BossGeyserColumn column = new BossGeyserColumn(3.0D, 0.5D, 4.0D, 0, 6, 2, 1, false);
        assertEquals(9, column.slices(), "one slice on the floor and one every half block up to four");
        assertEquals(3.0D, column.radiusAt(0), EPSILON, "the floor slice is the geyser's own circle");
        assertEquals(0.5D, column.radiusAt(8), EPSILON, "the top slice is the top radius");
        assertEquals(1.75D, column.radiusAt(4), EPSILON, "halfway up is halfway between");
        assertEquals(2.375D, column.radiusAt(2), EPSILON);
        assertEquals(0.0D, column.yAt(0), EPSILON);
        assertEquals(2.0D, column.yAt(4), EPSILON);
        assertEquals(4.0D, column.yAt(8), EPSILON);
        assertEquals(6, column.points());
    }

    @Test
    @DisplayName("a straight column is the numbers it was always drawn with: one dot at the middle of every half block")
    void aStraightColumnIsTheOldNumbers() {
        BossGeyserColumn column = new BossGeyserColumn(0.0D, 0.0D, 12.0D, 0, 1, 2, 1, false);
        assertEquals(25, column.slices(), "round(12 / 0.5) + 1 emits, the old loop's count");
        assertEquals(1, column.points());
        for (int step = 0; step < 25; step++) {
            assertEquals(0.0D, column.radiusAt(step), EPSILON, "every dot sits on the centre line");
            assertEquals(step * 0.5D, column.yAt(step), EPSILON);
        }
        assertEquals(3, column.costOf(0), "two cloud and one smoke on an even slice");
        assertEquals(2, column.costOf(1), "two cloud and no smoke on an odd one");
        List<Integer> drawn = new ArrayList<>();
        assertEquals(25, column.advance(drawn::add), "with no rise the whole column comes up on one tick");
        assertTrue(column.isDone());
        for (int step = 0; step < 25; step++) {
            assertEquals(step, drawn.get(step), "from the floor up, in order");
        }
        assertEquals(0, column.advance(drawn::add), "and nothing more after");
    }

    @Test
    @DisplayName("a fresh geyser's look draws the straight column of old, and a cone once asked for")
    void theLookBuildsTheColumnTheSettingsAsk() {
        BossGeyserSettings geyser = new BossGeyserSettings();
        BossGeyserColumn straight = BossGeyserScheduler.look(geyser).column(3.0D);
        assertEquals(10, straight.slices(), "three blocks of radius is four and a half of column, so ten slices");
        assertEquals(1, straight.points(), "a straight column is one dot whatever the dots setting says");
        assertEquals(0.0D, straight.radiusAt(0), EPSILON);
        assertEquals(0.0D, straight.radiusAt(9), EPSILON);
        assertEquals(3, straight.costOf(0), "cloud two and smoke one, as it always was");

        geyser.setColumnShape(BossGeyserSettings.COLUMN_CONE);
        geyser.setColumnPointsPerSlice(8);
        geyser.setColumnTopRadiusTenths(10);
        geyser.getColumnSmoke().setEnabled(false);
        BossGeyserColumn cone = BossGeyserScheduler.look(geyser).column(3.0D);
        assertEquals(8, cone.points());
        assertEquals(3.0D, cone.radiusAt(0), EPSILON, "the cone's bottom is the geyser's radius");
        assertEquals(1.0D, cone.radiusAt(9), EPSILON, "and its top the top radius");
        assertEquals(16, cone.costOf(0), "a cue switched off costs nothing, so no smoke even on an even slice");
    }

    @Test
    @DisplayName("a column rising over ten ticks is all there on the tenth, never more than the ceiling of slices per tick")
    void theRiseTakesExactlyItsTicks() {
        BossGeyserColumn column = new BossGeyserColumn(0.0D, 0.0D, 12.0D, 10, 1, 2, 1, false);
        List<Integer> drawn = new ArrayList<>();
        int ticks = 0;
        while (!column.isDone()) {
            int before = drawn.size();
            column.advance(drawn::add);
            ticks++;
            assertTrue(drawn.size() - before <= 3, "never more than ceil(25 / 10) slices on tick " + ticks);
            assertTrue(drawn.size() - before >= 2, "and never fewer than floor(25 / 10) on tick " + ticks);
            assertTrue(ticks <= 10, "a ten tick rise is over by the tenth tick");
        }
        assertEquals(10, ticks, "the column is all there on exactly the tick the rise was set to");
        assertEquals(25, drawn.size());
        for (int step = 0; step < 25; step++) {
            assertEquals(step, drawn.get(step), "every slice once, from the floor up");
        }
        assertEquals(3, column.reachAfter(1), "ceil(25 / 10) slices are up after the first tick");
        assertEquals(25, column.reachAfter(10));
        assertEquals(25, column.reachAfter(50), "and no further after the rise is over");
    }

    @Test
    @DisplayName("a rise of as many ticks as there are slices but one still ends on its last tick, not one late")
    void theRiseIsMeasuredInTicksNotSlices() {
        BossGeyserColumn column = new BossGeyserColumn(0.0D, 0.0D, 12.0D, 24, 1, 2, 1, false);
        int ticks = 0;
        while (!column.isDone()) {
            column.advance(step -> { });
            ticks++;
        }
        assertEquals(24, ticks);
    }

    @Test
    @DisplayName("the particle ceiling stretches a wide cone over more ticks rather than thinning it")
    void theBudgetStretchesTheColumn() {
        // Twenty-four dots of two cloud each, with one smoke on the even slices: 72 and 48 a slice.
        BossGeyserColumn column = new BossGeyserColumn(3.0D, 1.0D, 12.0D, 0, 24, 2, 1, false);
        assertEquals(72, column.costOf(0));
        assertEquals(48, column.costOf(1));
        int ticks = 0;
        int total = 0;
        while (!column.isDone()) {
            List<Integer> batch = new ArrayList<>();
            column.advance(batch::add);
            ticks++;
            int cost = batch.stream().mapToInt(column::costOf).sum();
            assertTrue(cost <= BossGeyserColumn.PARTICLE_BUDGET, "tick " + ticks + " spent " + cost);
            assertEquals(1, batch.size(), "no two of these slices fit under the ceiling together");
            total += batch.size();
        }
        assertEquals(25, total, "every slice is still drawn");
        assertEquals(25, ticks);
    }

    @Test
    @DisplayName("a column that fits under the ceiling in a handful of slices per tick is up in a handful of ticks")
    void theBudgetPacksWhatFits() {
        // Six dots of two cloud and one smoke: 18 on an even slice, 12 on an odd one.
        BossGeyserColumn column = new BossGeyserColumn(3.0D, 0.5D, 12.0D, 0, 6, 2, 1, false);
        int ticks = 0;
        int total = 0;
        while (!column.isDone()) {
            List<Integer> batch = new ArrayList<>();
            column.advance(batch::add);
            ticks++;
            int cost = batch.stream().mapToInt(column::costOf).sum();
            assertTrue(cost <= BossGeyserColumn.PARTICLE_BUDGET, "tick " + ticks + " spent " + cost);
            total += batch.size();
        }
        assertEquals(25, total);
        assertEquals(5, ticks, "six slices a tick at ninety particles, and the last one alone");
    }

    @Test
    @DisplayName("a slice that alone costs more than the ceiling is still drawn whole, one per tick")
    void anOversizedSliceStillGoes() {
        BossGeyserColumn column = new BossGeyserColumn(3.0D, 0.5D, 2.0D, 0, 24, 5, 0, false);
        assertEquals(120, column.costOf(0), "over the ceiling on its own");
        List<Integer> batch = new ArrayList<>();
        assertEquals(1, column.advance(batch::add), "the first slice of a tick always goes");
        assertEquals(List.of(0), batch);
        assertFalse(column.isDone());
    }

    @Test
    @DisplayName("a column drawn downward starts with its narrow top and ends on the floor")
    void aDownwardColumnStartsAtTheTop() {
        BossGeyserColumn column = new BossGeyserColumn(4.0D, 0.5D, 12.0D, 10, 6, 3, 1, true);
        assertEquals(24, column.stepAt(0), "the first slice drawn is the top one");
        assertEquals(0, column.stepAt(24), "and the last the floor's");
        List<Integer> drawn = new ArrayList<>();
        column.advance(drawn::add);
        assertEquals(24, drawn.get(0));
        assertEquals(0.5D, column.radiusAt(drawn.get(0)), EPSILON, "narrow at the top");
        assertEquals(12.0D, column.yAt(drawn.get(0)), EPSILON);
        while (!column.isDone()) {
            column.advance(drawn::add);
        }
        assertEquals(0, drawn.get(24), "the floor slice comes last");
        assertEquals(4.0D, column.radiusAt(0), EPSILON, "and is as wide as the circle it lands on");
        for (int i = 1; i < drawn.size(); i++) {
            assertTrue(drawn.get(i) < drawn.get(i - 1), "top down, in order");
        }
    }

    @Test
    @DisplayName("a column of no height is no column: nothing to draw, done before it starts")
    void noHeightIsNoColumn() {
        assertEquals(0, BossGeyserColumn.slicesFor(0.0D));
        assertEquals(0, BossGeyserColumn.slicesFor(-1.0D));
        assertEquals(1, BossGeyserColumn.slicesFor(0.1D), "any height at all has its floor slice");
        BossGeyserColumn column = new BossGeyserColumn(3.0D, 0.5D, 0.0D, 5, 6, 2, 1, false);
        assertEquals(0, column.slices());
        assertTrue(column.isDone());
        assertEquals(0, column.advance(step -> { throw new AssertionError("nothing to draw"); }));
    }
}
