package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the hold after a marked ability without a world: when it starts for an instant ability
 * and for a lasting one, that nothing is held without the mark, and that a fight that ends
 * drops it. Whether an effect is running is the controller's question; here it runs when the
 * test says so. The phase's side - the hold table, its range and the mask it goes with - is
 * pinned below it.
 */
class BossFinishHoldTest {

    private static final String HOLD = "FinishHold";
    private static final String MASK = "FinishMask";

    @Test
    @DisplayName("an instant ability is held from the moment it went off")
    void anInstantAbilityIsHeldFromItsCast() {
        BossPhaseData phase = marked(BossAbilityKind.MELEE, 20);
        BossFinishHold hold = new BossFinishHold();
        hold.onPerformed(BossAbility.MELEE_ATTACK, phase, 100L);
        assertTrue(hold.isHolding(BossAbility.MELEE_ATTACK, 100L));
        assertEquals(20L, hold.remainingTicks(BossAbility.MELEE_ATTACK, 100L));
        assertTrue(hold.isHolding(BossAbility.MELEE_ATTACK, 119L));
        assertEquals(1L, hold.remainingTicks(BossAbility.MELEE_ATTACK, 119L));
        assertFalse(hold.isHolding(BossAbility.MELEE_ATTACK, 120L));
        assertEquals(0L, hold.remainingTicks(BossAbility.MELEE_ATTACK, 120L));
        assertFalse(hold.isHolding(BossAbility.RANGED_ATTACK, 100L), "only the ability that went off is held");
        // A swing is never seen running, and the ticks after it say so: the hold stands.
        hold.observe(BossAbility.MELEE_ATTACK, false, phase, 101L);
        hold.observe(BossAbility.MELEE_ATTACK, false, phase, 102L);
        assertTrue(hold.isHolding(BossAbility.MELEE_ATTACK, 110L));
    }

    @Test
    @DisplayName("a lasting ability is held from the tick its effect is seen over")
    void aLastingAbilityIsHeldFromTheEndOfItsEffect() {
        BossPhaseData phase = marked(BossAbilityKind.BEAM, 40);
        BossFinishHold hold = new BossFinishHold();
        hold.onPerformed(BossAbility.BEAM, phase, 100L);
        for (long tick = 101L; tick <= 300L; tick++) {
            hold.observe(BossAbility.BEAM, true, phase, tick);
        }
        // The sweep ran past the hold counted from the cast; the one that matters starts now.
        hold.observe(BossAbility.BEAM, false, phase, 301L);
        assertTrue(hold.isHolding(BossAbility.BEAM, 301L));
        assertEquals(40L, hold.remainingTicks(BossAbility.BEAM, 301L));
        assertTrue(hold.isHolding(BossAbility.BEAM, 340L));
        assertFalse(hold.isHolding(BossAbility.BEAM, 341L));
        // Seen over again on the ticks after: the hold is not started over.
        hold.observe(BossAbility.BEAM, false, phase, 302L);
        assertEquals(39L, hold.remainingTicks(BossAbility.BEAM, 302L));
        assertFalse(hold.isHolding(BossAbility.BEAM, 341L));
    }

    @Test
    @DisplayName("a lasting ability whose effect never started is held from its cast")
    void anEffectThatNeverStartedIsHeldFromTheCast() {
        BossPhaseData phase = marked(BossAbilityKind.HOOK, 30);
        BossFinishHold hold = new BossFinishHold();
        hold.onPerformed(BossAbility.HOOK, phase, 100L);
        hold.observe(BossAbility.HOOK, false, phase, 101L);
        hold.observe(BossAbility.HOOK, false, phase, 102L);
        assertTrue(hold.isHolding(BossAbility.HOOK, 129L));
        assertFalse(hold.isHolding(BossAbility.HOOK, 130L));
    }

    @Test
    @DisplayName("a hold is never cut short by a later, shorter claim")
    void aHoldIsNeverCutShort() {
        BossPhaseData phase = marked(BossAbilityKind.TETHER, 50);
        BossFinishHold hold = new BossFinishHold();
        hold.onPerformed(BossAbility.TETHER, phase, 100L);
        phase.setFinishHoldTicks(BossAbilityKind.TETHER, 5);
        hold.observe(BossAbility.TETHER, true, phase, 101L);
        hold.observe(BossAbility.TETHER, false, phase, 102L);
        assertEquals(48L, hold.remainingTicks(BossAbility.TETHER, 102L), "the leash's end claimed 107, the cast already held to 150");
    }

    @Test
    @DisplayName("without the mark the hold is not read, and a hold of nothing is no hold")
    void nothingIsHeldWithoutTheMark() {
        BossPhaseData phase = new BossPhaseData();
        phase.setFinishHoldTicks(BossAbilityKind.MELEE, 20);
        phase.setFinishHoldTicks(BossAbilityKind.BEAM, 40);
        BossFinishHold hold = new BossFinishHold();
        hold.onPerformed(BossAbility.MELEE_ATTACK, phase, 100L);
        assertFalse(hold.isHolding(BossAbility.MELEE_ATTACK, 100L), "a hold without the mark is a number nobody reads");
        hold.observe(BossAbility.BEAM, true, phase, 100L);
        hold.observe(BossAbility.BEAM, false, phase, 101L);
        assertFalse(hold.isHolding(BossAbility.BEAM, 101L));

        // Marked with nothing to hold for: what a lasting ability had before the hold existed.
        phase.setWaitsForFinish(BossAbilityKind.BEAM, true);
        phase.setFinishHoldTicks(BossAbilityKind.BEAM, 0);
        hold.observe(BossAbility.BEAM, true, phase, 200L);
        hold.observe(BossAbility.BEAM, false, phase, 201L);
        assertFalse(hold.isHolding(BossAbility.BEAM, 201L));
        assertEquals(0L, hold.remainingTicks(BossAbility.BEAM, 201L));

        // The hop and an idle boss are on no list, and never held.
        hold.onPerformed(BossAbility.TELEPORT, phase, 300L);
        hold.onPerformed(BossAbility.NONE, phase, 300L);
        assertFalse(hold.isHolding(BossAbility.TELEPORT, 300L));
        assertFalse(hold.isHolding(BossAbility.NONE, 300L));
    }

    @Test
    @DisplayName("the mark is read when the hold starts, so marking mid effect still holds")
    void theMarkIsReadWhenTheHoldStarts() {
        BossPhaseData phase = new BossPhaseData();
        BossFinishHold hold = new BossFinishHold();
        hold.observe(BossAbility.GEYSER, true, phase, 100L);
        phase.setWaitsForFinish(BossAbilityKind.GEYSER, true);
        phase.setFinishHoldTicks(BossAbilityKind.GEYSER, 10);
        hold.observe(BossAbility.GEYSER, true, phase, 101L);
        hold.observe(BossAbility.GEYSER, false, phase, 102L);
        assertTrue(hold.isHolding(BossAbility.GEYSER, 111L));
        assertFalse(hold.isHolding(BossAbility.GEYSER, 112L));
    }

    @Test
    @DisplayName("a fight that ends drops every hold and every watch")
    void clearDropsEverything() {
        BossPhaseData phase = marked(BossAbilityKind.MELEE, 20);
        phase.setWaitsForFinish(BossAbilityKind.BEAM, true);
        phase.setFinishHoldTicks(BossAbilityKind.BEAM, 40);
        BossFinishHold hold = new BossFinishHold();
        hold.onPerformed(BossAbility.MELEE_ATTACK, phase, 100L);
        hold.observe(BossAbility.BEAM, true, phase, 100L);
        hold.clear();
        assertFalse(hold.isHolding(BossAbility.MELEE_ATTACK, 100L));
        assertEquals(0L, hold.remainingTicks(BossAbility.MELEE_ATTACK, 100L));
        // The sweep the reset cut short is not seen over: its end is no moment to hold for.
        hold.observe(BossAbility.BEAM, false, phase, 101L);
        assertFalse(hold.isHolding(BossAbility.BEAM, 101L));
    }

    @Test
    @DisplayName("the hold is saved per ability, clamped, and reads as nothing from an old save")
    void theHoldTableRoundTrips() {
        BossPhaseData phase = new BossPhaseData();
        for (int ability : BossAbilityKind.FINISH_ABILITIES) {
            assertEquals(0, phase.finishHoldTicks(ability), "a fresh phase holds nothing after " + ability);
        }
        phase.setFinishHoldTicks(BossAbilityKind.MELEE, 20);
        phase.setFinishHoldTicks(BossAbilityKind.BEAM, 99999);
        phase.setFinishHoldTicks(BossAbilityKind.GEYSER, -5);
        phase.setFinishHoldTicks(BossAbilityKind.BLAST, 20);
        phase.setFinishHoldTicks(-1, 20);
        phase.setFinishHoldTicks(BossAbilityKind.COUNT, 20);
        assertEquals(20, phase.finishHoldTicks(BossAbilityKind.MELEE));
        assertEquals(BossPhaseData.MAX_FINISH_HOLD, phase.finishHoldTicks(BossAbilityKind.BEAM));
        assertEquals(0, phase.finishHoldTicks(BossAbilityKind.GEYSER));
        assertEquals(0, phase.finishHoldTicks(BossAbilityKind.BLAST), "the death blast is never cast, so never held");
        assertEquals(0, phase.finishHoldTicks(-1));
        assertEquals(0, phase.finishHoldTicks(BossAbilityKind.COUNT));
        assertEquals(1200, BossPhaseData.MAX_FINISH_HOLD, "a minute, like the chain delay");

        CompoundTag tag = phase.writeToNBT();
        int[] saved = tag.getIntArray(HOLD);
        assertEquals(BossAbilityKind.COUNT, saved.length);
        assertEquals(20, saved[BossAbilityKind.MELEE]);
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(tag);
        assertEquals(20, reread.finishHoldTicks(BossAbilityKind.MELEE));
        assertEquals(BossPhaseData.MAX_FINISH_HOLD, reread.finishHoldTicks(BossAbilityKind.BEAM));
        assertEquals(phase.writeToNBT(), reread.writeToNBT());

        // A saved array is laid over the table rather than trusted: short, long, or out of range.
        tag.putIntArray(HOLD, new int[]{5, 7, -3});
        reread.readFromNBT(tag);
        assertEquals(5, reread.finishHoldTicks(BossAbilityKind.AREA));
        assertEquals(7, reread.finishHoldTicks(BossAbilityKind.RANGED));
        assertEquals(0, reread.finishHoldTicks(BossAbilityKind.MELEE), "a slot past a short array is nothing");
        assertEquals(0, reread.finishHoldTicks(BossAbilityKind.BEAM), "the load lays the array over every slot");
        tag.putIntArray(HOLD, filled(Integer.MAX_VALUE, BossAbilityKind.COUNT + 3));
        reread.readFromNBT(tag);
        assertEquals(BossPhaseData.MAX_FINISH_HOLD, reread.finishHoldTicks(BossAbilityKind.MELEE));
        assertEquals(0, reread.finishHoldTicks(BossAbilityKind.HAZARD), "a slot outside the rotation is dropped");
        tag.putIntArray(HOLD, filled(Integer.MIN_VALUE, BossAbilityKind.COUNT));
        reread.readFromNBT(tag);
        assertEquals(0, reread.finishHoldTicks(BossAbilityKind.MELEE));

        // A save from before the hold existed has no key, and the boss finishes the way it did.
        tag.remove(HOLD);
        reread.readFromNBT(tag);
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            assertEquals(0, reread.finishHoldTicks(ability), "an old save should hold nothing after " + ability);
        }
    }

    @Test
    @DisplayName("the mask takes every rotation kind and drops the rest, and an old save reads as it did")
    void theMaskTakesTheWholeRotation() {
        BossPhaseData phase = new BossPhaseData();
        for (BossAbility ability : BossAbility.ROTATION) {
            phase.setWaitsForFinish(ability.kind(), true);
            assertTrue(phase.waitsForFinish(ability.kind()), ability + " cannot be marked");
        }
        assertEquals(24, BossAbility.ROTATION.size(), "every ability of the rotation has a row on the screen");
        assertEquals(BossAbilityKind.FINISH_ALL, phase.writeToNBT().getInt(MASK));
        phase.setWaitsForFinish(BossAbilityKind.BLAST, true);
        phase.setWaitsForFinish(BossAbilityKind.HAZARD, true);
        assertFalse(phase.waitsForFinish(BossAbilityKind.BLAST));
        assertFalse(phase.waitsForFinish(BossAbilityKind.HAZARD));
        assertEquals(BossAbilityKind.FINISH_ALL, phase.writeToNBT().getInt(MASK));
        CompoundTag full = phase.writeToNBT();
        full.putInt(MASK, -1);
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(full);
        assertEquals(BossAbilityKind.FINISH_ALL, reread.writeToNBT().getInt(MASK), "bits outside the rotation are dropped on load");

        // A boss saved by a build that could only mark the lasting abilities: its beams and geyser.
        CompoundTag old = new BossPhaseData().writeToNBT();
        old.putInt(MASK, 1 << BossAbilityKind.BEAM | 1 << BossAbilityKind.GEYSER);
        old.remove(HOLD);
        reread.readFromNBT(old);
        assertTrue(reread.waitsForFinish(BossAbilityKind.BEAM));
        assertTrue(reread.waitsForFinish(BossAbilityKind.GEYSER));
        assertFalse(reread.waitsForFinish(BossAbilityKind.MELEE), "an old save never marked an instant ability");
        assertEquals(0, reread.finishHoldTicks(BossAbilityKind.BEAM), "and holds nothing after the sweep");
        BossFinishHold hold = new BossFinishHold();
        hold.observe(BossAbility.BEAM, true, reread, 10L);
        hold.observe(BossAbility.BEAM, false, reread, 11L);
        assertFalse(hold.isHolding(BossAbility.BEAM, 11L), "the sweep's end frees the boss at once, as it did");
    }

    private static BossPhaseData marked(int kind, int holdTicks) {
        BossPhaseData phase = new BossPhaseData();
        phase.setWaitsForFinish(kind, true);
        phase.setFinishHoldTicks(kind, holdTicks);
        return phase;
    }

    private static int[] filled(int value, int length) {
        int[] slots = new int[length];
        Arrays.fill(slots, value);
        return slots;
    }
}
