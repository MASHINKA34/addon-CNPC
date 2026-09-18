package com.goodbird.cnpcgeckoaddon.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The policy every guarded place in the addon shares: what is held back, what is let through,
 * how often the log hears of it and what the {@code guards} command is told.
 */
class CrashGuardTest {

    /** One line the guard wrote, as the sink was handed it. */
    private record Report(String site, String detail, long failures, long unreported, Throwable error) {
    }

    private final List<Report> reports = new ArrayList<>();
    private long nanos;
    private long millis;

    @BeforeEach
    void startClean() {
        CrashGuard.reset();
        GuardSelfTest.disarmAll();
        reports.clear();
        // Deliberately not zero and close to the wrap: nanoTime promises neither.
        nanos = Long.MAX_VALUE - 5_000_000_000L;
        millis = 1_000L;
        CrashGuard.useForTests(() -> nanos, () -> millis,
                (site, detail, failures, unreported, error) ->
                        reports.add(new Report(site, detail, failures, unreported, error)));
    }

    @AfterEach
    void putTheRealClockBack() {
        CrashGuard.useForTests(null, null, null);
        CrashGuard.reset();
        GuardSelfTest.disarmAll();
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }

    @Test
    @DisplayName("a failure is held back, logged with its stack and counted")
    void aFailureIsHeldBackAndReported() {
        IllegalStateException thrown = new IllegalStateException("bad block");
        assertDoesNotThrow(() -> CrashGuard.run("scheduler.geyser", () -> {
            throw thrown;
        }));
        assertEquals(1, reports.size());
        assertEquals("scheduler.geyser", reports.get(0).site());
        assertSame(thrown, reports.get(0).error(), "the log line has to carry the stack");
        assertEquals(1L, reports.get(0).failures());

        List<CrashGuard.Counter> counters = CrashGuard.counters();
        assertEquals(1, counters.size());
        assertEquals("scheduler.geyser", counters.get(0).site());
        assertEquals(1L, counters.get(0).failures());
        assertTrue(counters.get(0).lastError().contains("bad block"), counters.get(0).lastError());
        assertEquals(1_000L, counters.get(0).lastFailureMillis());
    }

    @Test
    @DisplayName("a body that does not fail is run once and leaves no trace")
    void aHealthyBodyLeavesNoTrace() {
        AtomicInteger runs = new AtomicInteger();
        CrashGuard.run("healthy", runs::incrementAndGet);
        CrashGuard.run("healthy", runs, AtomicInteger::incrementAndGet);
        CrashGuard.tick("healthy", runs::incrementAndGet, () -> fail("nothing to recover from"));
        assertEquals("value", CrashGuard.get("healthy", () -> "value", "fallback"));
        assertEquals(3, runs.get());
        assertTrue(reports.isEmpty());
        assertTrue(CrashGuard.counters().isEmpty());
    }

    @Test
    @DisplayName("one site is logged at most once in ten seconds, and every failure is still counted")
    void theLogHearsOfASiteOnceInTenSeconds() {
        for (int tick = 0; tick < 100; tick++) {
            CrashGuard.run("scheduler.geyser", () -> fail("every tick"));
            nanos += 50_000_000L;
        }
        // 100 ticks are five seconds: the first failure was logged, the other 99 only counted.
        assertEquals(1, reports.size());
        assertEquals(100L, CrashGuard.counters().get(0).failures());

        nanos += 4_000_000_000L;
        CrashGuard.run("scheduler.geyser", () -> fail("nine seconds in"));
        assertEquals(1, reports.size(), "nine seconds after the first line is still too soon");

        nanos += 1_000_000_000L;
        CrashGuard.run("scheduler.geyser", () -> fail("ten seconds in"));
        assertEquals(2, reports.size(), "ten seconds after the first line the next one is due");
        assertEquals(102L, reports.get(1).failures());
        assertEquals(100L, reports.get(1).unreported(), "the second line says how many went unlogged");
    }

    @Test
    @DisplayName("the ten seconds are kept per site")
    void sitesDoNotShareTheirTenSeconds() {
        CrashGuard.run("scheduler.geyser", () -> fail("one"));
        CrashGuard.run("scheduler.mark", () -> fail("two"));
        CrashGuard.run("scheduler.geyser", () -> fail("three"));
        assertEquals(2, reports.size());
        assertEquals("scheduler.geyser", reports.get(0).site());
        assertEquals("scheduler.mark", reports.get(1).site());
    }

    @Test
    @DisplayName("counters come worst first, and a reset forgets them and the ten seconds")
    void countersAreSortedAndReset() {
        CrashGuard.run("b.rare", () -> fail("once"));
        for (int i = 0; i < 3; i++) {
            CrashGuard.run("a.frequent", () -> fail("often"));
        }
        List<CrashGuard.Counter> counters = CrashGuard.counters();
        assertEquals(List.of("a.frequent", "b.rare"), counters.stream().map(CrashGuard.Counter::site).toList());
        assertEquals(3L, counters.get(0).failures());

        CrashGuard.reset();
        assertTrue(CrashGuard.counters().isEmpty());
        int before = reports.size();
        CrashGuard.run("a.frequent", () -> fail("after the reset"));
        assertEquals(before + 1, reports.size(), "a reset site is logged again at once");
        assertEquals(1L, CrashGuard.counters().get(0).failures());
    }

    @Test
    @DisplayName("get returns the fallback when the body fails")
    void getFallsBack() {
        String value = CrashGuard.get("client.texture", () -> {
            throw new IllegalArgumentException("Non [a-z0-9/._-] character in path");
        }, "fallback");
        assertEquals("fallback", value);
        assertNull(CrashGuard.get("client.texture", () -> {
            throw new IllegalArgumentException("again");
        }, null));
        assertEquals(2L, CrashGuard.counters().get(0).failures());
    }

    @Test
    @DisplayName("tick runs the recovery exactly once per failed step, after the failure is on record")
    void tickRecoversOnce() {
        AtomicInteger recoveries = new AtomicInteger();
        AtomicReference<Long> failuresSeenByRecovery = new AtomicReference<>();
        CrashGuard.tick("scheduler.geyser", () -> fail("head of the queue"), () -> {
            recoveries.incrementAndGet();
            failuresSeenByRecovery.set(CrashGuard.counters().get(0).failures());
        });
        assertEquals(1, recoveries.get());
        assertEquals(1L, failuresSeenByRecovery.get());

        List<String> cleared = new ArrayList<>();
        CrashGuard.tick("scheduler.geyser", "overworld", level -> fail("again"), cleared::add);
        assertEquals(List.of("overworld"), cleared, "the recovery is handed what the step was");
        assertEquals(2L, CrashGuard.counters().get(0).failures());
    }

    @Test
    @DisplayName("a recovery that fails too is held back under a site of its own")
    void aFailingRecoveryIsHeldBack() {
        assertDoesNotThrow(() -> CrashGuard.tick("scheduler.geyser",
                () -> fail("the step"), () -> fail("the clear")));
        assertDoesNotThrow(() -> CrashGuard.tick("scheduler.geyser", "overworld",
                level -> fail("the step"), level -> fail("the clear")));
        List<CrashGuard.Counter> counters = CrashGuard.counters();
        assertEquals(List.of("scheduler.geyser", "scheduler.geyser.recovery"),
                counters.stream().map(CrashGuard.Counter::site).toList());
        assertEquals(2L, counters.get(1).failures());
    }

    @Test
    @DisplayName("what nothing can carry on after goes straight through, uncounted")
    @SuppressWarnings("removal")
    void fatalErrorsAreNotHeldBack() {
        OutOfMemoryError oom = new OutOfMemoryError("Java heap space");
        assertSame(oom, assertThrows(OutOfMemoryError.class, () -> CrashGuard.run("site", () -> {
            throw oom;
        })));
        assertThrows(OutOfMemoryError.class, () -> CrashGuard.run("site", "argument", argument -> {
            throw oom;
        }));
        assertThrows(OutOfMemoryError.class, () -> CrashGuard.get("site", () -> {
            throw oom;
        }, "fallback"));
        AtomicInteger recoveries = new AtomicInteger();
        assertThrows(OutOfMemoryError.class, () -> CrashGuard.tick("site", () -> {
            throw oom;
        }, recoveries::incrementAndGet));
        assertThrows(OutOfMemoryError.class, () -> CrashGuard.caught("site", oom));
        assertEquals(0, recoveries.get(), "there is no recovering from running out of memory");
        assertThrows(ThreadDeath.class, () -> CrashGuard.run("site", () -> {
            throw new ThreadDeath();
        }));
        assertTrue(CrashGuard.counters().isEmpty());
        assertTrue(reports.isEmpty());
    }

    @Test
    @DisplayName("any other error is held back, a stack overflow included")
    void otherErrorsAreHeldBack() {
        assertDoesNotThrow(() -> CrashGuard.run("site", () -> {
            throw new StackOverflowError();
        }));
        assertDoesNotThrow(() -> CrashGuard.run("site", () -> {
            throw new NoClassDefFoundError("some/optional/Mod");
        }));
        assertEquals(2L, CrashGuard.counters().get(0).failures());
    }

    @Test
    @DisplayName("the detail goes to the log line and not into the site's name")
    void detailIsForTheLogOnly() {
        CrashGuard.caught("boss.controller.tick", "Warden of Moss", new IllegalStateException("tick"));
        assertEquals("Warden of Moss", reports.get(0).detail());
        assertEquals("boss.controller.tick", CrashGuard.counters().get(0).site());
    }

    @Test
    @DisplayName("a log that throws does not make the guard throw")
    void aBrokenLogIsHeldBackToo() {
        CrashGuard.useForTests(() -> nanos, () -> millis, (site, detail, failures, unreported, error) -> {
            throw new IllegalStateException("appender closed");
        });
        assertDoesNotThrow(() -> CrashGuard.run("site", () -> fail("body")));
        assertEquals(1L, CrashGuard.counters().get(0).failures(), "the count survives a lost line");
    }

    @Test
    @DisplayName("a long message is cut for the command and an unprintable one still names its class")
    void summariesAreBounded() {
        CrashGuard.run("long", () -> fail("x".repeat(1000)));
        assertTrue(CrashGuard.counters().get(0).lastError().length() <= 160);

        CrashGuard.reset();
        CrashGuard.run("unprintable", () -> {
            throw new RuntimeException() {
                @Override
                public String toString() {
                    throw new IllegalStateException("no message for you");
                }
            };
        });
        assertEquals(1L, CrashGuard.counters().get(0).failures());
    }

    @Test
    @DisplayName("a self test wire fails its site once, and only once it is armed")
    void aSelfTestWireFiresOnce() {
        assertDoesNotThrow(() -> GuardSelfTest.trip(GuardSelfTest.SCHEDULER));
        GuardSelfTest.arm(GuardSelfTest.SCHEDULER);
        assertTrue(GuardSelfTest.isArmed(GuardSelfTest.SCHEDULER));
        assertDoesNotThrow(() -> GuardSelfTest.trip(GuardSelfTest.DAMAGE), "only the armed site fails");

        CrashGuard.run("scheduler.geyser", () -> GuardSelfTest.trip(GuardSelfTest.SCHEDULER));
        assertEquals(1, reports.size());
        assertTrue(reports.get(0).error() instanceof GuardSelfTest.SelfTestFailure);
        assertDoesNotThrow(() -> GuardSelfTest.trip(GuardSelfTest.SCHEDULER), "the wire is one-shot");
        assertTrue(!GuardSelfTest.isArmed(GuardSelfTest.SCHEDULER));
    }

    @Test
    @DisplayName("a controller's wire keeps failing that one boss until it is taken up")
    void aControllerWireStaysArmed() {
        UUID boss = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        GuardSelfTest.armController(boss);
        for (int tick = 0; tick < 3; tick++) {
            assertThrows(GuardSelfTest.SelfTestFailure.class, () -> GuardSelfTest.tripController(boss));
        }
        assertDoesNotThrow(() -> GuardSelfTest.tripController(bystander));
        GuardSelfTest.disarmController(boss);
        assertDoesNotThrow(() -> GuardSelfTest.tripController(boss));
    }

    @Test
    @DisplayName("every site the command offers is one the wires know")
    void theCommandsSitesAreTheWires() {
        assertEquals(List.of("scheduler", "damage", "packet", "client", "controller"), GuardSelfTest.SITES);
    }
}
