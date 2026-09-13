package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order a cone strike takes the builder's points in, checked without a world.
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
