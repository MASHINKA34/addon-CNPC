package com.goodbird.cnpcgeckoaddon.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The row of repeats an ability's profile asks for, without a world: which casts are still
 * owed, when the next is due, and when the chain behind the row may go on. Whether an effect is
 * still running, and whether a starter takes a repeat, is the controller's question; here a cast
 * goes off and ends when the test says so.
 */
class BossAbilityRepeatTest {

    @Test
    @DisplayName("no repeats owe nothing")
    void noRepeatsOweNothing() {
        BossAbilityRepeat repeat = new BossAbilityRepeat();
        repeat.arm(BossAbility.MELEE_ATTACK, 0, 20, 1);
        assertFalse(repeat.isActive());
        assertFalse(repeat.holds(BossAbility.MELEE_ATTACK));
        assertEquals("", repeat.status(0L));
    }

    @Test
    @DisplayName("two repeats make three casts in a row, each due a pause after the last one ends")
    void twoRepeatsMakeThreeCasts() {
        BossAbilityRepeat repeat = new BossAbilityRepeat();
        repeat.arm(BossAbility.MELEE_ATTACK, 2, 20, 1);
        assertTrue(repeat.isWindingUp());
        assertTrue(repeat.holds(BossAbility.MELEE_ATTACK), "the chain behind the row waits from its first cast");
        assertFalse(repeat.holds(BossAbility.HOOK));

        assertFalse(repeat.performed(BossAbility.HOOK), "another ability going off is none of the row's business");
        assertTrue(repeat.isWindingUp());
        assertFalse(repeat.performed(BossAbility.MELEE_ATTACK), "two casts are still owed");
        assertTrue(repeat.isRunning());
        assertFalse(repeat.isDue(1000L), "nothing is due while the cast's effect runs");

        repeat.ended(100L);
        assertTrue(repeat.isWaiting());
        assertFalse(repeat.isDue(119L));
        assertTrue(repeat.isDue(120L), "the pause counts from the end of the last cast");
        assertEquals("repeat 0/2 in 5", repeat.status(115L));

        repeat.repeatStarted();
        assertEquals(1, repeat.done());
        assertEquals("repeat 1/2", repeat.status(130L));
        assertFalse(repeat.performed(BossAbility.MELEE_ATTACK));
        assertEquals("repeat 1/2 after its effect", repeat.status(131L));
        repeat.ended(140L);
        assertTrue(repeat.isDue(160L));

        repeat.repeatStarted();
        assertEquals(2, repeat.done());
        assertEquals(0, repeat.left());
        assertTrue(repeat.holds(BossAbility.MELEE_ATTACK), "the last cast still holds the chain until it goes off");
        assertTrue(repeat.performed(BossAbility.MELEE_ATTACK), "the last cast going off completes the row");
        assertFalse(repeat.isActive());
        assertFalse(repeat.holds(BossAbility.MELEE_ATTACK), "the chain goes on after the last cast");
    }

    @Test
    @DisplayName("every repeat keeps the chain place of the cast that asked for it")
    void repeatsKeepTheirChainPlace() {
        BossAbilityRepeat repeat = new BossAbilityRepeat();
        repeat.arm(BossAbility.HOOK, 3, 0, 2);
        assertEquals(2, repeat.links());
        repeat.performed(BossAbility.HOOK);
        repeat.ended(50L);
        assertTrue(repeat.isDue(50L), "no pause is due on the tick the last cast ended");
        repeat.repeatStarted();
        assertEquals(2, repeat.links());
    }

    @Test
    @DisplayName("a newer row takes the place of the one owed, and a clear owes nothing")
    void newerRowsReplaceOlderOnes() {
        BossAbilityRepeat repeat = new BossAbilityRepeat();
        repeat.arm(BossAbility.MELEE_ATTACK, 2, 20, 1);
        repeat.performed(BossAbility.MELEE_ATTACK);
        repeat.arm(BossAbility.GROUND_ATTACK, 1, 10, 1);
        assertFalse(repeat.holds(BossAbility.MELEE_ATTACK));
        assertTrue(repeat.holds(BossAbility.GROUND_ATTACK));
        assertEquals(1, repeat.total());
        assertTrue(repeat.isWindingUp());

        repeat.clear();
        assertFalse(repeat.isActive());
        assertEquals(BossAbility.NONE, repeat.ability());
        assertFalse(repeat.isDue(Long.MAX_VALUE));
    }
}
