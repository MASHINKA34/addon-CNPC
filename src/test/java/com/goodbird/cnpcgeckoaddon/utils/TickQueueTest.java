package com.goodbird.cnpcgeckoaddon.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickQueueTest {

    private static <T> TickQueue<T> queue(int maxPerTick) {
        return new TickQueue<>("test entries", maxPerTick);
    }

    @Test
    @DisplayName("work scheduled while a tick runs waits for the next one")
    void arrivalsAreHeldBackUntilTheRunIsOver() {
        TickQueue<String> queue = queue(16);
        List<String> run = new ArrayList<>();
        queue.add("first");
        queue.drain(entry -> true, entry -> {
            run.add(entry);
            if (entry.equals("first")) {
                queue.add("scheduled from inside");
            }
        });
        assertEquals(List.of("first"), run);
        assertFalse(queue.isEmpty());
        queue.drain(entry -> true, run::add);
        assertEquals(List.of("first", "scheduled from inside"), run);
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("no more than maxPerTick entries run in one tick")
    void theCapSpreadsABacklogOverSeveralTicks() {
        TickQueue<Integer> queue = queue(2);
        for (int i = 0; i < 5; i++) {
            queue.add(i);
        }
        List<Integer> run = new ArrayList<>();
        queue.drain(entry -> true, run::add);
        assertEquals(List.of(0, 1), run);
        queue.drain(entry -> true, run::add);
        assertEquals(List.of(0, 1, 2, 3), run);
        queue.drain(entry -> true, run::add);
        assertEquals(List.of(0, 1, 2, 3, 4), run);
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("a cancellation mid-run takes the entries behind it out too")
    void removeIfDuringARunDropsWhatHasNotRunYet() {
        TickQueue<String> queue = queue(16);
        queue.add("keep");
        queue.add("drop me");
        queue.add("drop me too");
        List<String> run = new ArrayList<>();
        queue.drain(entry -> true, entry -> {
            run.add(entry);
            if (entry.equals("keep")) {
                queue.removeIf(pending -> pending.startsWith("drop"));
            }
        });
        assertEquals(List.of("keep"), run);
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("a broken entry is dropped and the rest of the tick carries on")
    void aThrowingEntryDoesNotEscapeTheTick() {
        TickQueue<String> queue = queue(16);
        queue.add("boom");
        queue.add("after");
        List<String> run = new ArrayList<>();
        queue.drain(entry -> true, entry -> {
            if (entry.equals("boom")) {
                throw new IllegalStateException("this one is broken");
            }
            run.add(entry);
        });
        assertEquals(List.of("after"), run);
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("sweep keeps an entry whose action asks for another tick")
    void sweepReschedulesLongLivedEntries() {
        TickQueue<String> queue = queue(16);
        queue.add("lives twice");
        List<String> run = new ArrayList<>();
        queue.sweep(entry -> true, entry -> {
            run.add(entry);
            return run.size() < 2;
        });
        assertFalse(queue.isEmpty());
        queue.sweep(entry -> true, entry -> {
            run.add(entry);
            return false;
        });
        assertEquals(2, run.size());
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("runNow queues instead of nesting when work is already on the stack")
    void runNowNeverNestsInsideARunningTick() {
        TickQueue<String> queue = queue(16);
        List<String> run = new ArrayList<>();
        queue.add("outer");
        queue.drain(entry -> true, entry -> {
            run.add(entry);
            queue.runNow("immediate", run::add);
        });
        assertEquals(List.of("outer"), run);
        queue.drain(entry -> true, run::add);
        assertEquals(List.of("outer", "immediate"), run);
    }

    @Test
    @DisplayName("find reaches entries wherever the tick left them")
    void findSeesQueuedAndInFlightEntries() {
        TickQueue<String> queue = queue(16);
        queue.add("waiting");
        assertEquals("waiting", queue.find("waiting"::equals));
        queue.drain(entry -> true, entry -> {
            queue.add("arrived");
            assertEquals("arrived", queue.find("arrived"::equals));
            assertEquals(entry, queue.find(entry::equals));
        });
    }

    @Test
    @DisplayName("a running entry can cancel itself and stays visible while it does")
    void aRunningEntryCanCancelItself() {
        TickQueue<String> queue = queue(16);
        queue.add("active");
        boolean[] visible = new boolean[3];
        queue.sweep(entry -> true, entry -> {
            visible[0] = !queue.isEmpty();
            visible[1] = entry.equals(queue.find(entry::equals));
            queue.removeIf(entry::equals);
            visible[2] = queue.find(entry::equals) == null;
            return true;
        });
        assertTrue(visible[0], "the running entry must remain visible to cancellation guards");
        assertTrue(visible[1], "find must include the running entry");
        assertTrue(visible[2], "a cancelled entry must stop being discoverable");
        assertTrue(queue.isEmpty(), "returning true must not revive a cancelled action");
        List<String> next = new ArrayList<>();
        queue.drain(entry -> true, next::add);
        assertTrue(next.isEmpty(), "a cancelled action must never run on the next tick");
    }

    @Test
    @DisplayName("a wholesale cancellation covers pulled, deferred and arriving work")
    void cancellationRemovesPulledAndDeferredEntries() {
        TickQueue<String> queue = queue(2);
        queue.add("first");
        queue.add("pulled");
        queue.add("deferred");
        List<String> run = new ArrayList<>();
        queue.sweep(entry -> true, entry -> {
            run.add(entry);
            queue.add("arrival");
            queue.removeIf(candidate -> true);
            return true;
        });
        assertEquals(List.of("first"), run);
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("work scheduled after a self-cancellation runs exactly once")
    void workAddedAfterCancellationWaitsForNextTick() {
        TickQueue<String> queue = queue(16);
        queue.add("boss");
        queue.sweep(entry -> true, entry -> {
            queue.removeIf(entry::equals);
            queue.add("boss");
            return true;
        });
        List<String> run = new ArrayList<>();
        queue.drain(entry -> true, run::add);
        assertEquals(List.of("boss"), run);
        assertTrue(queue.isEmpty());
    }
}
