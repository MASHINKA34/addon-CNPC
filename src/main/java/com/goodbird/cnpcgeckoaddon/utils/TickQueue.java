package com.goodbird.cnpcgeckoaddon.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A queue of work a level tick owes the world, drained one tick at a time.
 *
 * <p>Written for one particular way of killing a server. A scheduler that walks its own list
 * and detonates, spawns or places blocks from inside the walk hands control to the world in
 * the middle of an iteration: the explosion kills another boss, the death event schedules
 * another explosion, the list grows under the iterator, and the next {@code hasNext()} throws
 * {@link java.util.ConcurrentModificationException} straight out of the level tick - which
 * takes the whole server down with it.</p>
 *
 * <p>So nothing here touches the world while the list is being read. A tick takes the entries
 * it is going to run out of the queue first and runs them afterwards, and everything that
 * arrives in the meantime waits in a buffer that is merged once the tick is over. Whatever a
 * running entry sets off is then free to schedule, cancel and clear as much as it likes.</p>
 *
 * <p>Two more things follow from the same idea. No more than {@code maxPerTick} entries run
 * in one tick, so a chain reaction of bosses blowing each other up is spread over several
 * ticks instead of spending one of them entirely; and an entry that throws is logged and
 * dropped rather than allowed to escape into the level tick.</p>
 *
 * <p>None of this is thread safe, and none of it needs to be: every caller is on the server
 * thread.</p>
 */
public final class TickQueue<T> {

    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");

    /** Names the queue in the log, as a plural: "16 boss explosions came due". */
    private final String name;
    private final int maxPerTick;

    private final List<T> entries = new ArrayList<>();
    /** Entries scheduled while work was running; merged back the moment it stops. */
    private final List<T> arrivals = new ArrayList<>();
    /** Removals asked for while work was running; applied at the same moment. */
    private final List<Predicate<? super T>> cancellations = new ArrayList<>();
    /** What the current tick pulled out of the queue and has not run yet. */
    private final ArrayDeque<T> pulled = new ArrayDeque<>();

    private boolean running;
    private boolean heldBack;

    public TickQueue(String name, int maxPerTick) {
        this.name = name;
        this.maxPerTick = maxPerTick;
    }

    public void add(T entry) {
        if (running) {
            arrivals.add(entry);
            return;
        }
        entries.add(entry);
    }

    public boolean isEmpty() {
        return entries.isEmpty() && arrivals.isEmpty() && pulled.isEmpty();
    }

    /**
     * Whether work from this queue is on the stack right now.
     *
     * <p>What a caller does with the answer is its own business: a scheduler that would
     * otherwise do something immediately queues it instead, so the work lands in the next
     * tick rather than nesting inside this one.</p>
     */
    public boolean isRunning() {
        return running;
    }

    public void removeIf(Predicate<? super T> filter) {
        if (running) {
            // Entries already pulled out go too: a level that is being dropped must not be
            // worked on by the rest of the tick either.
            pulled.removeIf(filter);
            arrivals.removeIf(filter);
            cancellations.add(filter);
            return;
        }
        entries.removeIf(filter);
    }

    /** The first entry the filter accepts, wherever it currently sits. */
    public T find(Predicate<? super T> filter) {
        T found = firstOf(entries, filter);
        if (found == null) {
            found = firstOf(arrivals, filter);
        }
        if (found == null) {
            found = firstOf(pulled, filter);
        }
        return found;
    }

    /**
     * Takes every entry {@code ready} accepts out of the queue and runs {@code action} on it
     * afterwards, outside the walk.
     */
    public void drain(Predicate<? super T> ready, Consumer<? super T> action) {
        sweep(ready, entry -> {
            action.accept(entry);
            return false;
        });
    }

    /**
     * The same, for a queue whose entries live across several ticks: an entry whose action
     * returns true goes back in for the next one.
     */
    public void sweep(Predicate<? super T> ready, Predicate<? super T> action) {
        if (running || entries.isEmpty()) {
            return;
        }
        pull(ready);
        run(action);
    }

    /**
     * Runs one entry that never joins the queue, under the same guard a tick runs under.
     *
     * <p>For the schedulers that do their work immediately when no delay was configured.
     * Going through here is what stops that from nesting: anything the entry sets off finds
     * the queue running and takes the ordinary queued route.</p>
     */
    public void runNow(T entry, Consumer<? super T> action) {
        if (running) {
            add(entry);
            return;
        }
        pulled.add(entry);
        run(queued -> {
            action.accept(queued);
            return false;
        });
    }

    private void pull(Predicate<? super T> ready) {
        boolean full = false;
        Iterator<T> iterator = entries.iterator();
        while (iterator.hasNext()) {
            T entry = iterator.next();
            if (!ready.test(entry)) {
                continue;
            }
            if (pulled.size() >= maxPerTick) {
                full = true;
                break;
            }
            iterator.remove();
            pulled.add(entry);
        }
        // One line as the backlog starts, not one per tick for as long as it lasts.
        if (full && !heldBack) {
            LOGGER.warn("More than {} {} came due in the same tick, spreading them over several ticks instead",
                    maxPerTick, name);
        }
        heldBack = full;
    }

    private void run(Predicate<? super T> action) {
        running = true;
        try {
            T entry;
            // Polled rather than iterated, so a cancellation arriving mid-run can still take
            // the entries behind this one out.
            while ((entry = pulled.poll()) != null) {
                boolean keep;
                try {
                    keep = action.test(entry);
                } catch (Throwable throwable) {
                    // One broken entry is not worth a server: it goes, and the tick carries on.
                    LOGGER.error("A queued {} entry failed and was dropped", name, throwable);
                    continue;
                }
                if (keep) {
                    arrivals.add(entry);
                }
            }
        } finally {
            running = false;
            pulled.clear();
            merge();
        }
    }

    /** Everything that arrived during the run, in the order it was asked for. */
    private void merge() {
        for (Predicate<? super T> cancellation : cancellations) {
            entries.removeIf(cancellation);
        }
        cancellations.clear();
        entries.addAll(arrivals);
        arrivals.clear();
    }

    private T firstOf(Iterable<T> source, Predicate<? super T> filter) {
        for (T entry : source) {
            if (filter.test(entry)) {
                return entry;
            }
        }
        return null;
    }
}
