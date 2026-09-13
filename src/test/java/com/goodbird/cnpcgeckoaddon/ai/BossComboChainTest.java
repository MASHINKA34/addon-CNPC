package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the chain bookkeeping without a world: which ability that went off is owed a follow-up,
 * what its end arms, and where a loop hands the boss back to its rotation. Whether an effect is
 * still running is the controller's question; here an ability ends when the test says so.
 */
class BossComboChainTest {

    /** A boss nobody has retuned: every number below is the constant the chain shipped with. */
    private static final BossTuningSettings TUNING = new BossTuningSettings();

    @Test
    @DisplayName("an ability the phase chains nothing onto is not watched")
    void unchainedAbilitiesAreNotWatched() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = new BossPhaseData();
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        assertFalse(chain.isWatching());
        assertFalse(chain.finish(BossAbility.LEAP, phase, 100L, TUNING), "nobody watched the leap, so its end arms nothing");
        assertFalse(chain.hasPending());

        // Neither the hop nor an idle boss has a slot to chain from.
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.BOULDER_RAIN);
        chain.watch(BossAbility.TELEPORT, 1, phase, TUNING);
        chain.watch(BossAbility.NONE, 1, phase, TUNING);
        assertFalse(chain.isWatching());
    }

    @Test
    @DisplayName("the end of a watched ability arms its follow-up after the delay")
    void anEndArmsTheFollowUp() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = leapIntoRain(10);
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        assertTrue(chain.isWatching());
        assertEquals(List.of(BossAbility.LEAP), chain.watchedAbilities());

        assertTrue(chain.finish(BossAbility.LEAP, phase, 100L, TUNING));
        assertTrue(chain.hasPending());
        assertEquals(BossAbility.BOULDER_RAIN, chain.next());
        assertEquals(BossAbility.LEAP, chain.from());
        assertEquals(110L, chain.readyAt(), "the delay counts from the end, not from the cast");
        assertEquals(2, chain.links(), "the follow-up of an ability the boss started itself is the second link");
        assertFalse(chain.isWatching(), "an ability hands on once per time it went off");
        assertFalse(chain.finish(BossAbility.LEAP, phase, 120L, TUNING));
    }

    @Test
    @DisplayName("the chain read at the end is the one that runs")
    void theEndReadsThePhase() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = leapIntoRain(10);
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.HOOK);
        phase.setComboDelay(BossAbilityKind.LEAP, 25);
        assertTrue(chain.finish(BossAbility.LEAP, phase, 40L, TUNING));
        assertEquals(BossAbility.HOOK, chain.next());
        assertEquals(65L, chain.readyAt());

        chain.clear();
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossPhaseData.NO_COMBO);
        assertFalse(chain.finish(BossAbility.LEAP, phase, 40L, TUNING), "a chain emptied mid effect hands on nothing");
        assertFalse(chain.hasPending());
    }

    @Test
    @DisplayName("an effect cut short gives up its claim, and only its own")
    void aForgottenClaimHandsOnNothing() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = new BossPhaseData();
        phase.setComboFollowUp(BossAbilityKind.DASH, BossAbilityKind.LEAP);
        phase.setComboFollowUp(BossAbilityKind.BEAM, BossAbilityKind.GRAVITY);
        chain.watch(BossAbility.DASH, 1, phase, TUNING);
        chain.watch(BossAbility.BEAM, 1, phase, TUNING);

        // A dash a stun stopped: its end is not the end its chain was waiting for.
        chain.forget(BossAbility.DASH);
        assertEquals(List.of(BossAbility.BEAM), chain.watchedAbilities(), "the sweep still running keeps its claim");
        assertFalse(chain.finish(BossAbility.DASH, phase, 30L, TUNING), "a forgotten dash arms nothing when it is seen to be over");
        assertFalse(chain.hasPending());
        assertTrue(chain.finish(BossAbility.BEAM, phase, 40L, TUNING));
        assertEquals(BossAbility.GRAVITY, chain.next());

        chain.forget(BossAbility.HOOK);
        assertEquals(BossAbility.GRAVITY, chain.next(), "forgetting an ability nobody watched touches nothing waiting");
    }

    @Test
    @DisplayName("a newer follow-up takes the place of the one still waiting")
    void aNewerFollowUpReplacesTheWaitingOne() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = new BossPhaseData();
        phase.setComboFollowUp(BossAbilityKind.BEAM, BossAbilityKind.GRAVITY);
        phase.setComboDelay(BossAbilityKind.BEAM, 5);
        phase.setComboFollowUp(BossAbilityKind.GEYSER, BossAbilityKind.CAPTURE);
        chain.watch(BossAbility.BEAM, 1, phase, TUNING);
        chain.watch(BossAbility.GEYSER, 1, phase, TUNING);
        assertEquals(2, chain.watchedAbilities().size(), "two effects running at once are two claims");

        assertTrue(chain.finish(BossAbility.GEYSER, phase, 50L, TUNING));
        assertEquals(BossAbility.CAPTURE, chain.next());
        assertTrue(chain.isWatching(), "the sweep is still running and still owed its own");

        assertTrue(chain.finish(BossAbility.BEAM, phase, 80L, TUNING));
        assertEquals(BossAbility.GRAVITY, chain.next());
        assertEquals(BossAbility.BEAM, chain.from());
        assertEquals(85L, chain.readyAt());
        assertFalse(chain.isWatching());
    }

    @Test
    @DisplayName("an ability cast again before it ended keeps one claim, at its newer place")
    void aRecastKeepsOneClaim() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = new BossPhaseData();
        phase.setComboFollowUp(BossAbilityKind.GEYSER, BossAbilityKind.MARK);
        chain.watch(BossAbility.GEYSER, 1, phase, TUNING);
        chain.watch(BossAbility.GEYSER, 5, phase, TUNING);
        assertEquals(List.of(BossAbility.GEYSER), chain.watchedAbilities());
        assertTrue(chain.finish(BossAbility.GEYSER, phase, 0L, TUNING));
        assertEquals(6, chain.links());
    }

    @Test
    @DisplayName("a loop hands the boss back to its rotation after the longest chain")
    void aLoopStopsAtTheCap() {
        BossPhaseData phase = leapIntoRain(0);
        phase.setComboFollowUp(BossAbilityKind.BOULDER_RAIN, BossAbilityKind.LEAP);
        BossComboChain chain = new BossComboChain();

        // The leap the rotation started, then every follow-up the chain starts in turn: each
        // start takes the waiting follow-up, and each end arms the next one.
        BossAbility ability = BossAbility.LEAP;
        int links = 1;
        int abilities = 1;
        long gameTime = 0L;
        while (true) {
            chain.watch(ability, links, phase, TUNING);
            if (!chain.finish(ability, phase, gameTime, TUNING)) {
                break;
            }
            ability = chain.next();
            links = chain.links();
            chain.clearPending();
            abilities++;
            gameTime += 30L;
            assertTrue(abilities <= BossAbilityKind.COUNT + 1, "the loop ran past its cap");
        }
        assertEquals(BossComboChain.MAX_LINKS, abilities, "the longest chain is the ability count, the first cast included");
        assertEquals(BossAbilityKind.COUNT, BossComboChain.MAX_LINKS);
        assertTrue(BossComboChain.hasRoomAfter(BossComboChain.MAX_LINKS - 1, TUNING));
        assertFalse(BossComboChain.hasRoomAfter(BossComboChain.MAX_LINKS, TUNING));
        assertFalse(chain.isWatching(), "the last link is not even watched");
    }

    @Test
    @DisplayName("dropping the waiting follow-up keeps the claims; clearing drops everything")
    void clearPendingKeepsClaims() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = leapIntoRain(10);
        phase.setComboFollowUp(BossAbilityKind.BEAM, BossAbilityKind.GRAVITY);
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        chain.watch(BossAbility.BEAM, 1, phase, TUNING);
        chain.finish(BossAbility.LEAP, phase, 0L, TUNING);

        chain.clearPending();
        assertFalse(chain.hasPending());
        assertEquals(BossAbility.NONE, chain.next());
        assertEquals(BossAbility.NONE, chain.from());
        assertTrue(chain.isWatching(), "an effect still running keeps its claim through a stagger");

        chain.clear();
        assertFalse(chain.isWatching());
        assertFalse(chain.finish(BossAbility.BEAM, phase, 10L, TUNING));
        assertFalse(chain.hasPending());
    }

    @Test
    @DisplayName("a follow-up is due once its delay is over, and goes stale two hundred ticks past it")
    void dueAndStale() {
        BossComboChain chain = armed(leapIntoRain(10), 100L);
        assertFalse(chain.isDue(109L));
        assertTrue(chain.isDue(110L));
        assertFalse(chain.isStale(110L + TUNING.comboStaleTicks(), TUNING), "exactly that late is still owed");
        assertTrue(chain.isStale(111L + TUNING.comboStaleTicks(), TUNING));
        chain.clearPending();
        assertFalse(chain.isDue(500L), "nothing waiting is never due");
        assertFalse(chain.isStale(5000L, TUNING));
    }

    @Test
    @DisplayName("a follow-up that refuses is tried again every few ticks for three seconds, then dropped")
    void refusalsRetryThenGiveUp() {
        BossComboChain chain = armed(leapIntoRain(0), 100L);
        long gameTime = 100L;
        int attempts = 0;
        while (chain.hasPending()) {
            assertTrue(chain.isDue(gameTime), "every retry is due on its own tick, attempt " + attempts);
            attempts++;
            if (chain.refused(gameTime, TUNING)) {
                assertFalse(chain.isDue(gameTime + TUNING.retryTicks() - 1), "a retry waits its pause");
                gameTime += TUNING.retryTicks();
            }
        }
        assertEquals(100L + TUNING.comboRetryWindowTicks(), gameTime, "the last try is the one at the end of the window");
        assertEquals(TUNING.comboRetryWindowTicks() / TUNING.retryTicks() + 1, attempts);
        assertEquals(60, TUNING.comboRetryWindowTicks());
        assertFalse(chain.refused(gameTime, TUNING), "nothing is left to refuse");
    }

    @Test
    @DisplayName("the retry window opens at the first refusal, not at the ready time")
    void theWindowOpensAtTheFirstRefusal() {
        BossComboChain chain = armed(leapIntoRain(0), 100L);
        // Kept waiting by a silence for a while, and only then refused for the first time.
        assertTrue(chain.refused(250L, TUNING));
        assertTrue(chain.refused(300L, TUNING));
        assertFalse(chain.refused(310L, TUNING));
        assertFalse(chain.hasPending());
    }

    @Test
    @DisplayName("a newer follow-up starts its retries afresh")
    void aNewerFollowUpStartsFresh() {
        BossPhaseData phase = leapIntoRain(0);
        phase.setComboFollowUp(BossAbilityKind.BEAM, BossAbilityKind.GRAVITY);
        BossComboChain chain = new BossComboChain();
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        chain.watch(BossAbility.BEAM, 1, phase, TUNING);
        assertTrue(chain.finish(BossAbility.LEAP, phase, 100L, TUNING));
        chain.refused(100L, TUNING);
        chain.refused(150L, TUNING);
        assertFalse(chain.isDue(155L));

        assertTrue(chain.finish(BossAbility.BEAM, phase, 155L, TUNING));
        assertTrue(chain.isDue(155L), "the pause after the rain's refusal is not the gravity's");
        assertTrue(chain.refused(155L, TUNING));
        assertTrue(chain.refused(210L, TUNING), "and neither is the rain's window");
    }

    @Test
    @DisplayName("the status line says what the chain is waiting on, the way the boss command prints it")
    void theStatusLineNamesTheChain() {
        BossComboChain chain = new BossComboChain();
        assertEquals("Combo: none", chain.status(0L));

        BossPhaseData phase = leapIntoRain(10);
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        assertEquals("Combo: waiting for LEAP to end", chain.status(50L));

        chain.finish(BossAbility.LEAP, phase, 100L, TUNING);
        assertEquals("Combo: LEAP -> BOULDER_RAIN in 10", chain.status(100L));
        assertEquals("Combo: LEAP -> BOULDER_RAIN in 0", chain.status(140L), "an overdue follow-up is not counted below zero");
        chain.refused(140L, TUNING);
        assertEquals("Combo: LEAP -> BOULDER_RAIN in " + TUNING.retryTicks(), chain.status(140L),
                "after a refusal the count is to the retry");
    }

    @Test
    @DisplayName("every chainable kind is started through a rotation row")
    void everyKindHasARow() {
        for (int kind : BossAbilityKind.COMBO_ABILITIES) {
            BossAbility row = BossAbility.ofKind(kind);
            assertTrue(BossAbility.ROTATION.contains(row), "kind " + kind + " has no rotation row to start from");
            assertEquals(kind, row.kind());
        }
        assertEquals(BossAbility.NONE, BossAbility.ofKind(BossAbilityKind.BLAST));
        assertEquals(BossAbility.NONE, BossAbility.ofKind(BossAbilityKind.HAZARD));
        assertEquals(BossAbility.NONE, BossAbility.ofKind(-1), "the hop and an idle boss are on no list");
    }

    /** A chain whose leap has just ended, with its follow-up waiting. */
    private static BossComboChain armed(BossPhaseData phase, long endedAt) {
        BossComboChain chain = new BossComboChain();
        chain.watch(BossAbility.LEAP, 1, phase, TUNING);
        assertTrue(chain.finish(BossAbility.LEAP, phase, endedAt, TUNING));
        return chain;
    }

    private static BossPhaseData leapIntoRain(int delay) {
        BossPhaseData phase = new BossPhaseData();
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.BOULDER_RAIN);
        phase.setComboDelay(BossAbilityKind.LEAP, delay);
        return phase;
    }
}
