package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order a cone strike takes the builder's points in, and the pace of a series over them,
 * checked without a world.
 *
 * <p>None of it throws when it is wrong: a random order that never changes, a count that strikes
 * one point too many, a series that always leaves out the last point on the list - each only
 * reads in play as a boss that is oddly predictable, or oddly not.</p>
 */
class BossConeSeriesTest {

    private static final List<String> POINTS = List.of("a", "b", "c", "d", "e");

    @Test
    @DisplayName("in list order a cast takes the points as listed, cut to its count")
    void listOrderKeepsTheList() {
        RandomSource random = RandomSource.create(7L);
        assertEquals(POINTS, BossConeRuntime.seriesOf(POINTS, BossPhaseData.CONE_ORDER_LIST, 0, random),
                "a count of zero strikes every point");
        assertEquals(List.of("a", "b"), BossConeRuntime.seriesOf(POINTS, BossPhaseData.CONE_ORDER_LIST, 2, random),
                "a count takes that many from the top of the list");
        assertEquals(POINTS, BossConeRuntime.seriesOf(POINTS, BossPhaseData.CONE_ORDER_LIST, 16, random),
                "a count past the list strikes every point once, not some of them twice");
        assertEquals(List.of(), BossConeRuntime.seriesOf(List.of(), BossPhaseData.CONE_ORDER_LIST, 3, random),
                "no points is no series");
    }

    @Test
    @DisplayName("a random order strikes each point at most once, and not the same order every cast")
    void randomOrderShuffles() {
        RandomSource random = RandomSource.create(20260913L);
        Set<List<String>> orders = new HashSet<>();
        Set<String> firsts = new HashSet<>();
        for (int cast = 0; cast < 40; cast++) {
            List<String> series = BossConeRuntime.seriesOf(POINTS, BossPhaseData.CONE_ORDER_RANDOM, 0, random);
            assertEquals(POINTS.size(), series.size());
            assertEquals(Set.copyOf(POINTS), Set.copyOf(series), "a shuffle strikes every point exactly once");
            orders.add(series);
            firsts.add(series.getFirst());
        }
        assertTrue(orders.size() > 1, "forty random casts all struck the points in one order");
        assertEquals(Set.copyOf(POINTS), firsts, "every point should get to be struck first sooner or later");
    }

    @Test
    @DisplayName("a series strikes its first cone at once and each of the rest one pause after the last")
    void aSeriesKeepsItsPauses() {
        BossConeRuntime.Series<String> series = new BossConeRuntime.Series<>(List.of("a", "b", "c"), 10, 100L);
        assertEquals(List.of("a"), series.due(100L), "the first cone lands on the tick the wind-up ends");
        assertEquals("b", series.upcoming());
        assertEquals(List.of("b", "c"), series.remaining());
        assertEquals(List.of(), series.due(101L));
        assertEquals(List.of(), series.due(109L), "nine ticks into a ten tick pause is still the pause");
        assertEquals(List.of("b"), series.due(110L));
        assertFalse(series.isOver());
        assertEquals(List.of(), series.due(115L));
        assertEquals(List.of("c"), series.due(120L));
        assertTrue(series.isOver(), "the last cone ends the series");
        assertEquals(List.of(), series.due(200L), "a finished series strikes nothing more");
        assertNull(series.upcoming());
        assertEquals(List.of(), series.remaining());
    }

    @Test
    @DisplayName("with no pause every cone of the series lands together")
    void noPauseStrikesEveryConeAtOnce() {
        BossConeRuntime.Series<String> series = new BossConeRuntime.Series<>(POINTS, 0, 40L);
        assertEquals(POINTS, series.due(40L), "a pause of nothing lands every cone on the wind-up's tick");
        assertTrue(series.isOver(), "so there is no series left to hold the boss");
    }

    @Test
    @DisplayName("a series held up picks up one cone at a time, its pause counted from the last one")
    void aHeldSeriesDoesNotBurst() {
        BossConeRuntime.Series<String> series = new BossConeRuntime.Series<>(List.of("a", "b", "c"), 10, 0L);
        series.due(0L);
        // Not ticked for forty ticks - a carried boss - so both of the rest are overdue.
        assertEquals(List.of("b"), series.due(40L), "only the next cone lands, not every one that fell due");
        assertEquals(List.of(), series.due(45L), "and the one after it waits a whole pause from there");
        assertEquals(List.of("c"), series.due(50L));
        assertTrue(series.isOver());
    }

    @Test
    @DisplayName("a series of one cone is over as soon as that cone lands")
    void aSingleConeIsNoSeries() {
        BossConeRuntime.Series<String> series = new BossConeRuntime.Series<>(List.of("a"), 25, 7L);
        assertEquals(List.of("a"), series.due(7L));
        assertTrue(series.isOver(), "one point leaves nothing for the boss to stay busy with");
    }

    @Test
    @DisplayName("a random series cut to a count picks its points at random, not the top of the list")
    void randomCountPicksAnyPoints() {
        RandomSource random = RandomSource.create(99L);
        Set<String> struck = new HashSet<>();
        for (int cast = 0; cast < 40; cast++) {
            List<String> series = BossConeRuntime.seriesOf(POINTS, BossPhaseData.CONE_ORDER_RANDOM, 2, random);
            assertEquals(2, series.size());
            assertTrue(!series.get(0).equals(series.get(1)), "one point struck twice in one series");
            struck.addAll(series);
        }
        assertEquals(Set.copyOf(POINTS), struck, "some point was never picked by a two-point random series");
    }
}
