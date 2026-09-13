package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;
import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_TICKS;

/**
 * The chains a phase hangs off its abilities: which abilities that went off are still owed
 * their follow-up, and the one follow-up waiting to start.
 *
 * <p>Owned by {@link TeleportPathController}, which asks the world whether an effect is still
 * running and does the starting. Everything here is bookkeeping on game ticks, so it is tested
 * without a world. Nothing is saved: a server that goes down between an ability and its
 * follow-up owes nobody the follow-up.</p>
 */
final class BossComboChain {

    /**
     * The longest chain, counted in abilities from the one the boss started on its own.
     *
     * <p>A loop - the leap onto the rain and the rain back onto the leap - would otherwise run
     * for ever with every cooldown skipped; one this long hands the boss back to its rotation.</p>
     */
    static final int MAX_LINKS = BossAbilityKind.COUNT;

    /**
     * How far past its time a follow-up may be kept waiting - by a silence, an immune window, a
     * wind-up or a lock - before it is dropped: past ten seconds it no longer reads as one.
     */
    static final int STALE_TICKS = 200;

    /** How long a follow-up that refuses to start - nobody in reach, say - is tried again for. */
    static final int RETRY_WINDOW_TICKS = 60;

    /** Abilities that went off and are owed a follow-up when they end, each with its place in a chain. */
    private final Map<BossAbility, Integer> watched = new EnumMap<>(BossAbility.class);

    /** The follow-up waiting to start, or NONE; the fields below describe it. */
    private BossAbility next = BossAbility.NONE;
    /** The ability whose end armed it. */
    private BossAbility from = BossAbility.NONE;
    /** Game time it may start at. */
    private long readyAt = NOT_SCHEDULED;
    /** Its place in its chain: 2 for the follow-up of an ability the boss started on its own. */
    private int links;
    /** Game time it may next be tried at: its ready time at first, a little later after each refusal. */
    private long nextTryAt = NOT_SCHEDULED;
    /** Game time the retries give up at, or NOT_SCHEDULED while it has never refused. */
    private long retryUntil = NOT_SCHEDULED;

    /**
     * Notes an action that has just gone off, when the phase chains something onto it.
     *
     * @param links the action's place in its chain: 1 for one the boss started on its own
     */
    void watch(BossAbility performed, int links, BossPhaseData phase) {
        int kind = performed.kind();
        if (kind < 0 || phase.comboFollowUp(kind) == BossPhaseData.NO_COMBO || !hasRoomAfter(links)) {
            return;
        }
        // Cast again before the last one ended: its end is still one moment, and the newer
        // place in a chain is the one that moment belongs to.
        watched.put(performed, links);
    }

    /** Whether any ability that went off is still owed its follow-up. */
    boolean isWatching() {
        return !watched.isEmpty();
    }

    /**
     * Drops one ability's claim on its follow-up, for an effect that was cut short rather than
     * run out: its end is no moment the chain was waiting for.
     */
    void forget(BossAbility ability) {
        watched.remove(ability);
    }

    /** The abilities still owed a follow-up, copied so the caller can finish them while it walks. */
    List<BossAbility> watchedAbilities() {
        return List.copyOf(watched.keySet());
    }

    /**
     * A watched ability is over, and its follow-up is armed in place of whatever was waiting.
     *
     * <p>The follow-up and the delay are read off the phase now rather than when the ability
     * went off, so a chain edited mid effect is the chain that runs.</p>
     *
     * @return whether a follow-up was armed; not for an ability nobody watched, nor for one the
     *         phase no longer chains anything onto
     */
    boolean finish(BossAbility ended, BossPhaseData phase, long gameTime) {
        Integer place = watched.remove(ended);
        if (place == null || !hasRoomAfter(place)) {
            return false;
        }
        BossAbility followUp = BossAbility.ofKind(phase.comboFollowUp(ended.kind()));
        if (followUp == BossAbility.NONE) {
            return false;
        }
        next = followUp;
        from = ended;
        readyAt = gameTime + phase.comboDelay(ended.kind());
        links = place + 1;
        nextTryAt = readyAt;
        retryUntil = NOT_SCHEDULED;
        return true;
    }

    /** Whether the waiting follow-up may be tried now: its delay is over, and so is any pause after a refusal. */
    boolean isDue(long gameTime) {
        return hasPending() && gameTime >= Math.max(readyAt, nextTryAt);
    }

    /** Whether the waiting follow-up was kept from starting for too long past its time to still be owed. */
    boolean isStale(long gameTime) {
        return hasPending() && gameTime - readyAt > STALE_TICKS;
    }

    /**
     * The waiting follow-up refused to start: it is tried again every {@code RETRY_TICKS} for
     * {@link #RETRY_WINDOW_TICKS} from the first refusal, and then dropped without a word - a
     * boss with nobody in reach is not a broken boss.
     *
     * @return whether the follow-up is still waiting
     */
    boolean refused(long gameTime) {
        if (!hasPending()) {
            return false;
        }
        if (retryUntil == NOT_SCHEDULED) {
            retryUntil = gameTime + RETRY_WINDOW_TICKS;
        }
        if (gameTime >= retryUntil) {
            clearPending();
            return false;
        }
        nextTryAt = gameTime + RETRY_TICKS;
        return true;
    }

    /** Whether an ability this far down a chain may still hand on to one more. */
    static boolean hasRoomAfter(int links) {
        return links < MAX_LINKS;
    }

    /** Whether a follow-up is waiting to start. */
    boolean hasPending() {
        return next != BossAbility.NONE;
    }

    BossAbility next() {
        return next;
    }

    BossAbility from() {
        return from;
    }

    long readyAt() {
        return readyAt;
    }

    int links() {
        return links;
    }

    /**
     * Read-only status used by the boss diagnostic command: the follow-up waiting and how soon it
     * is tried, else the abilities still owed one.
     */
    String status(long gameTime) {
        if (hasPending()) {
            return "Combo: " + from + " -> " + next + " in " + Math.max(0L, Math.max(readyAt, nextTryAt) - gameTime);
        }
        if (isWatching()) {
            return "Combo: waiting for " + watched.keySet().stream().map(Enum::name)
                    .collect(Collectors.joining(", ")) + " to end";
        }
        return "Combo: none";
    }

    /** Drops the follow-up waiting to start; abilities still running keep their claim. */
    void clearPending() {
        next = BossAbility.NONE;
        from = BossAbility.NONE;
        readyAt = NOT_SCHEDULED;
        links = 0;
        nextTryAt = NOT_SCHEDULED;
        retryUntil = NOT_SCHEDULED;
    }

    /** Drops everything: the follow-up waiting to start, and every claim on one. */
    void clear() {
        watched.clear();
        clearPending();
    }
}
