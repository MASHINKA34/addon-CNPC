package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
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

    @Test
    @DisplayName("an ability the phase chains nothing onto is not watched")
    void unchainedAbilitiesAreNotWatched() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = new BossPhaseData();
        chain.watch(BossAbility.LEAP, 1, phase);
        assertFalse(chain.isWatching());
        assertFalse(chain.finish(BossAbility.LEAP, phase, 100L), "nobody watched the leap, so its end arms nothing");
        assertFalse(chain.hasPending());

        // Neither the hop nor an idle boss has a slot to chain from.
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.BOULDER_RAIN);
        chain.watch(BossAbility.TELEPORT, 1, phase);
        chain.watch(BossAbility.NONE, 1, phase);
        assertFalse(chain.isWatching());
    }

    @Test
    @DisplayName("the end of a watched ability arms its follow-up after the delay")
    void anEndArmsTheFollowUp() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = leapIntoRain(10);
        chain.watch(BossAbility.LEAP, 1, phase);
        assertTrue(chain.isWatching());
        assertEquals(List.of(BossAbility.LEAP), chain.watchedAbilities());

        assertTrue(chain.finish(BossAbility.LEAP, phase, 100L));
        assertTrue(chain.hasPending());
        assertEquals(BossAbility.BOULDER_RAIN, chain.next());
        assertEquals(BossAbility.LEAP, chain.from());
        assertEquals(110L, chain.readyAt(), "the delay counts from the end, not from the cast");
        assertEquals(2, chain.links(), "the follow-up of an ability the boss started itself is the second link");
        assertFalse(chain.isWatching(), "an ability hands on once per time it went off");
        assertFalse(chain.finish(BossAbility.LEAP, phase, 120L));
    }

    @Test
    @DisplayName("the chain read at the end is the one that runs")
    void theEndReadsThePhase() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = leapIntoRain(10);
        chain.watch(BossAbility.LEAP, 1, phase);
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.HOOK);
        phase.setComboDelay(BossAbilityKind.LEAP, 25);
        assertTrue(chain.finish(BossAbility.LEAP, phase, 40L));
        assertEquals(BossAbility.HOOK, chain.next());
        assertEquals(65L, chain.readyAt());

        chain.clear();
        chain.watch(BossAbility.LEAP, 1, phase);
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossPhaseData.NO_COMBO);
        assertFalse(chain.finish(BossAbility.LEAP, phase, 40L), "a chain emptied mid effect hands on nothing");
        assertFalse(chain.hasPending());
    }

    @Test
    @DisplayName("a newer follow-up takes the place of the one still waiting")
    void aNewerFollowUpReplacesTheWaitingOne() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = new BossPhaseData();
        phase.setComboFollowUp(BossAbilityKind.BEAM, BossAbilityKind.GRAVITY);
        phase.setComboDelay(BossAbilityKind.BEAM, 5);
        phase.setComboFollowUp(BossAbilityKind.GEYSER, BossAbilityKind.CAPTURE);
        chain.watch(BossAbility.BEAM, 1, phase);
        chain.watch(BossAbility.GEYSER, 1, phase);
        assertEquals(2, chain.watchedAbilities().size(), "two effects running at once are two claims");

        assertTrue(chain.finish(BossAbility.GEYSER, phase, 50L));
        assertEquals(BossAbility.CAPTURE, chain.next());
        assertTrue(chain.isWatching(), "the sweep is still running and still owed its own");

        assertTrue(chain.finish(BossAbility.BEAM, phase, 80L));
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
        chain.watch(BossAbility.GEYSER, 1, phase);
        chain.watch(BossAbility.GEYSER, 5, phase);
        assertEquals(List.of(BossAbility.GEYSER), chain.watchedAbilities());
        assertTrue(chain.finish(BossAbility.GEYSER, phase, 0L));
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
            chain.watch(ability, links, phase);
            if (!chain.finish(ability, phase, gameTime)) {
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
        assertTrue(BossComboChain.hasRoomAfter(BossComboChain.MAX_LINKS - 1));
        assertFalse(BossComboChain.hasRoomAfter(BossComboChain.MAX_LINKS));
        assertFalse(chain.isWatching(), "the last link is not even watched");
    }

    @Test
    @DisplayName("dropping the waiting follow-up keeps the claims; clearing drops everything")
    void clearPendingKeepsClaims() {
        BossComboChain chain = new BossComboChain();
        BossPhaseData phase = leapIntoRain(10);
        phase.setComboFollowUp(BossAbilityKind.BEAM, BossAbilityKind.GRAVITY);
        chain.watch(BossAbility.LEAP, 1, phase);
        chain.watch(BossAbility.BEAM, 1, phase);
        chain.finish(BossAbility.LEAP, phase, 0L);

        chain.clearPending();
        assertFalse(chain.hasPending());
        assertEquals(BossAbility.NONE, chain.next());
        assertEquals(BossAbility.NONE, chain.from());
        assertTrue(chain.isWatching(), "an effect still running keeps its claim through a stagger");

        chain.clear();
        assertFalse(chain.isWatching());
        assertFalse(chain.finish(BossAbility.BEAM, phase, 10L));
        assertFalse(chain.hasPending());
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

    private static BossPhaseData leapIntoRain(int delay) {
        BossPhaseData phase = new BossPhaseData();
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.BOULDER_RAIN);
        phase.setComboDelay(BossAbilityKind.LEAP, delay);
        return phase;
    }
}
