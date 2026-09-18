package com.goodbird.cnpcgeckoaddon.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Turns an exception inside the addon into a log line and a skipped step instead of a dead game.
 *
 * <p>Everything the addon does is called from somebody else's loop - the level tick, the event
 * bus, a packet queue, a frame being drawn, a method of CustomNPCs a mixin was merged into -
 * and an exception that escapes from there takes that loop down: the server stops with a crash
 * report, or the client does. One boss with a setting nothing foresaw is not worth that. Every
 * such entry point runs behind one of the methods here, under a name for the place, its
 * <em>site</em>.</p>
 *
 * <p>Nothing is swallowed silently. Every failure is counted per site and the last one is kept
 * for the {@code /cnpcgecko guards} command; the log gets the whole stack, but at most once per
 * site per {@link #LOG_INTERVAL_NANOS}, because a place that fails usually fails again on the
 * next tick, and twenty stacks a second would bury the first one. {@link OutOfMemoryError} and
 * {@code ThreadDeath} are never held back: there is nothing to carry on with after either.</p>
 *
 * <p>Deliberately free of the game's classes, so the policy itself is covered by a plain unit
 * test. The clock is {@link System#nanoTime()} rather than a game tick for the same reason,
 * and because half the sites are on a client that has no level to read a tick from.</p>
 */
public final class CrashGuard {

    /** At most one logged stack per site per ten seconds. Technical, not a setting. */
    public static final long LOG_INTERVAL_NANOS = 10_000_000_000L;

    /** Longer messages are cut for the command's one line per site; the log has the whole of it. */
    private static final int MAX_SUMMARY_LENGTH = 160;

    /** What one site has been through: for the {@code guards} command. */
    public record Counter(String site, long failures, String lastError, long lastFailureMillis) {
    }

    /** Where a failure that is due a log line goes; the tests put their own in. */
    interface Sink {
        /**
         * @param unreported failures at this site since its last line that got none of their own
         */
        void report(String site, String detail, long failures, long unreported, Throwable error);
    }

    private static final class Site {
        private long failures;
        private long unreported;
        private boolean logged;
        private long nextLogAt;
        private String lastError = "";
        private long lastFailureMillis;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcgeckoaddon");

    private static final Sink LOG_SINK = (site, detail, failures, unreported, error) -> LOGGER.error(
            "Guarded failure at {}{} (failure #{}, {} more since the last report); skipped, the game carries on",
            site, detail == null || detail.isEmpty() ? "" : " [" + detail + "]", failures, unreported, error);

    // Sites are hit from the server thread, the client thread and, for packets, whichever
    // thread the payload was handled on - and in single player all of them share this class.
    private static final Map<String, Site> SITES = new ConcurrentHashMap<>();

    private static volatile LongSupplier nanoClock = System::nanoTime;
    private static volatile LongSupplier wallClock = System::currentTimeMillis;
    private static volatile Sink sink = LOG_SINK;

    private CrashGuard() {
    }

    /** Runs {@code body}; a failure is reported under {@code site} and the rest of it skipped. */
    public static void run(String site, Runnable body) {
        try {
            body.run();
        } catch (Throwable error) {
            caught(site, error);
        }
    }

    /**
     * The same with the body's one argument handed over, so a call from a tick can pass a
     * static method reference - a constant - instead of a lambda allocated on every call.
     */
    public static <T> void run(String site, T argument, Consumer<? super T> body) {
        try {
            body.accept(argument);
        } catch (Throwable error) {
            caught(site, error);
        }
    }

    /** @return what {@code body} returned, or {@code fallback} when it failed */
    public static <T> T get(String site, Supplier<? extends T> body, T fallback) {
        try {
            return body.get();
        } catch (Throwable error) {
            caught(site, error);
            return fallback;
        }
    }

    /**
     * Runs one step of something that is stepped every tick, and when it fails lets its owner
     * drop whatever it was stepping: a queue that threw this tick has the same entry at its head
     * on the next one, and a wave that vanished is better than one that fails twenty times a
     * second for the rest of the session.
     *
     * <p>{@code onFailure} runs exactly once per failed step, behind a guard of its own.</p>
     */
    public static void tick(String site, Runnable body, Runnable onFailure) {
        try {
            body.run();
        } catch (Throwable error) {
            caught(site, error);
            recover(site, onFailure);
        }
    }

    /** {@link #tick(String, Runnable, Runnable)} without a lambda, the way {@link #run(String, Object, Consumer)} is. */
    public static <T> void tick(String site, T argument, Consumer<? super T> body, Consumer<? super T> onFailure) {
        try {
            body.accept(argument);
        } catch (Throwable error) {
            caught(site, error);
            try {
                onFailure.accept(argument);
            } catch (Throwable recovery) {
                caught(recoverySite(site), recovery);
            }
        }
    }

    /**
     * The catch block's half, for the places that guard with a {@code try} written out: the
     * bodies of mixins, where a lambda is one more method to merge into somebody else's class,
     * and the paths walked so often that nothing should be allocated on them.
     *
     * @throws Error when {@code error} is one nothing can carry on after
     */
    public static void caught(String site, Throwable error) {
        caught(site, null, error);
    }

    /** @param detail what it happened to - a boss' name, a block's position - for the log line only */
    public static void caught(String site, String detail, Throwable error) {
        if (isFatal(error)) {
            throw (Error) error;
        }
        Site entry = SITES.computeIfAbsent(site, key -> new Site());
        long failures;
        long unreported;
        synchronized (entry) {
            entry.failures++;
            entry.lastError = summary(error);
            entry.lastFailureMillis = wallClock.getAsLong();
            long now = nanoClock.getAsLong();
            // Compared by difference: nanoTime is only meaningful that way, and may be negative.
            if (entry.logged && now - entry.nextLogAt < 0L) {
                entry.unreported++;
                return;
            }
            entry.logged = true;
            entry.nextLogAt = now + LOG_INTERVAL_NANOS;
            failures = entry.failures;
            unreported = entry.unreported;
            entry.unreported = 0L;
        }
        try {
            sink.report(site, detail, failures, unreported, error);
        } catch (Throwable loggingFailed) {
            // The line is lost, the count above is not - and a guard must not be the thing that
            // throws. Only what nothing survives goes on up.
            if (isFatal(loggingFailed)) {
                throw (Error) loggingFailed;
            }
        }
    }

    /** Every site that has failed since the start or the last {@link #reset()}, worst first. */
    public static List<Counter> counters() {
        List<Counter> counters = new ArrayList<>();
        for (Map.Entry<String, Site> entry : SITES.entrySet()) {
            Site site = entry.getValue();
            synchronized (site) {
                counters.add(new Counter(entry.getKey(), site.failures, site.lastError, site.lastFailureMillis));
            }
        }
        counters.sort(Comparator.comparingLong(Counter::failures).reversed().thenComparing(Counter::site));
        return counters;
    }

    /** Forgets every count, and with them the log's ten seconds: the next failure is logged. */
    public static void reset() {
        SITES.clear();
    }

    private static void recover(String site, Runnable onFailure) {
        try {
            onFailure.run();
        } catch (Throwable recovery) {
            caught(recoverySite(site), recovery);
        }
    }

    private static String recoverySite(String site) {
        return site + ".recovery";
    }

    @SuppressWarnings("removal")
    private static boolean isFatal(Throwable error) {
        return error instanceof OutOfMemoryError || error instanceof ThreadDeath;
    }

    private static String summary(Throwable error) {
        String text;
        try {
            text = String.valueOf(error);
        } catch (Throwable unprintable) {
            // An exception whose own message throws: its class is still worth a line.
            text = error.getClass().getName();
        }
        return text.length() <= MAX_SUMMARY_LENGTH ? text : text.substring(0, MAX_SUMMARY_LENGTH - 1) + "…";
    }

    /** For the tests: a clock that is moved by hand and a sink that remembers. Null puts the real one back. */
    static void useForTests(LongSupplier nanos, LongSupplier millis, Sink reports) {
        nanoClock = nanos == null ? System::nanoTime : nanos;
        wallClock = millis == null ? System::currentTimeMillis : millis;
        sink = reports == null ? LOG_SINK : reports;
    }
}
