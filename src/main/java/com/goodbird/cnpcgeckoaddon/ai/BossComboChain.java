package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

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

    /** Drops the follow-up waiting to start; abilities still running keep their claim. */
    void clearPending() {
        next = BossAbility.NONE;
        from = BossAbility.NONE;
        readyAt = NOT_SCHEDULED;
        links = 0;
    }

    /** Drops everything: the follow-up waiting to start, and every claim on one. */
    void clear() {
        watched.clear();
        clearPending();
    }
}
