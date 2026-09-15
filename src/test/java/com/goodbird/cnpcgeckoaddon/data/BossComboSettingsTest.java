package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-phase chain table: which ability each ability hands straight on to, and after how long.
 *
 * <p>Both arrays are indexed by {@link BossAbilityKind} and arrive from saves written by other
 * builds, so a load that trusted them would either throw on a short array or start abilities
 * nobody can see on the screen. Every way a saved array can be wrong is fed in here.</p>
 */
class BossComboSettingsTest {

    private static final String FOLLOW_UP = "ComboFollowUp";
    private static final String DELAY = "ComboDelay";
    private static final int NONE = BossPhaseData.NO_COMBO;

    @Test
    @DisplayName("a boss saved before the chains existed chains nothing")
    void anOldSaveChainsNothing() {
        BossPhaseData phase = new BossPhaseData();
        assertNoChains(phase, "a fresh phase");
        for (int ability : BossAbilityKind.COMBO_ABILITIES) {
            phase.setComboFollowUp(ability, ability == BossAbilityKind.AREA ? BossAbilityKind.MELEE : BossAbilityKind.AREA);
            phase.setComboDelay(ability, 40);
        }
        CompoundTag tag = phase.writeToNBT();
        tag.remove(FOLLOW_UP);
        tag.remove(DELAY);

        // Read into the phase that had every slot filled, so the load has to empty them rather
        // than merely leave a fresh table at its defaults.
        phase.readFromNBT(tag);
        assertNoChains(phase, "a tag with no chain keys");
        assertArrayEquals(emptyFollowUps(), phase.writeToNBT().getIntArray(FOLLOW_UP));
        assertArrayEquals(new int[BossAbilityKind.COUNT], phase.writeToNBT().getIntArray(DELAY));
    }

    @Test
    @DisplayName("a short saved array is padded with empty slots")
    void aShortArrayIsPadded() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        tag.putIntArray(FOLLOW_UP, new int[]{BossAbilityKind.MELEE, NONE, BossAbilityKind.AREA});
        tag.putIntArray(DELAY, new int[]{15, 30});

        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(tag);
        assertEquals(BossAbilityKind.MELEE, phase.comboFollowUp(BossAbilityKind.AREA));
        assertEquals(NONE, phase.comboFollowUp(BossAbilityKind.RANGED));
        assertEquals(BossAbilityKind.AREA, phase.comboFollowUp(BossAbilityKind.MELEE));
        assertEquals(15, phase.comboDelay(BossAbilityKind.AREA));
        assertEquals(30, phase.comboDelay(BossAbilityKind.RANGED));
        for (int ability = BossAbilityKind.FLUID; ability < BossAbilityKind.COUNT; ability++) {
            assertEquals(NONE, phase.comboFollowUp(ability), "slot " + ability + " was past the end of the save");
        }
        for (int ability = BossAbilityKind.MELEE; ability < BossAbilityKind.COUNT; ability++) {
            assertEquals(0, phase.comboDelay(ability), "slot " + ability + " was past the end of the save");
        }
        CompoundTag written = phase.writeToNBT();
        assertEquals(BossAbilityKind.COUNT, written.getIntArray(FOLLOW_UP).length, "a short array should be saved back whole");
        assertEquals(BossAbilityKind.COUNT, written.getIntArray(DELAY).length);
    }

    @Test
    @DisplayName("a long saved array is cut to the ability count")
    void aLongArrayIsCut() {
        int[] followUps = new int[BossAbilityKind.COUNT + 7];
        int[] delays = new int[BossAbilityKind.COUNT + 7];
        Arrays.fill(followUps, BossAbilityKind.AREA);
        Arrays.fill(delays, 90);
        followUps[BossAbilityKind.AREA] = BossAbilityKind.COCOON;
        CompoundTag tag = new BossPhaseData().writeToNBT();
        tag.putIntArray(FOLLOW_UP, followUps);
        tag.putIntArray(DELAY, delays);

        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(tag);
        assertEquals(BossAbilityKind.COCOON, phase.comboFollowUp(BossAbilityKind.AREA));
        assertEquals(BossAbilityKind.AREA, phase.comboFollowUp(BossAbilityKind.COCOON));
        assertEquals(90, phase.comboDelay(BossAbilityKind.COCOON));
        CompoundTag written = phase.writeToNBT();
        assertEquals(BossAbilityKind.COUNT, written.getIntArray(FOLLOW_UP).length, "the tail a newer build saved should be dropped");
        assertEquals(BossAbilityKind.COUNT, written.getIntArray(DELAY).length);
    }

    @Test
    @DisplayName("a saved slot pointing nowhere that can start is emptied, a wild delay clamped")
    void garbageIsEmptied() {
        int[] garbage = {-5, BossAbilityKind.COUNT, 999, BossAbilityKind.BLAST, BossAbilityKind.HAZARD,
                Integer.MIN_VALUE, Integer.MAX_VALUE, -1};
        int[] followUps = emptyFollowUps();
        int[] delays = new int[BossAbilityKind.COUNT];
        for (int i = 0; i < garbage.length; i++) {
            int ability = BossAbilityKind.COMBO_ABILITIES[i];
            followUps[ability] = garbage[i];
            delays[ability] = i % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        // And one slot that names its own ability: a chain onto itself is a loop with one link.
        followUps[BossAbilityKind.BEAM] = BossAbilityKind.BEAM;
        CompoundTag tag = new BossPhaseData().writeToNBT();
        tag.putIntArray(FOLLOW_UP, followUps);
        tag.putIntArray(DELAY, delays);

        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(tag);
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            assertEquals(NONE, phase.comboFollowUp(ability), "slot " + ability + " kept a follow-up that cannot start");
            int delay = phase.comboDelay(ability);
            assertTrue(delay >= 0 && delay <= BossPhaseData.MAX_COMBO_DELAY, "slot " + ability + " kept a delay of " + delay);
        }
        assertEquals(0, phase.comboDelay(BossAbilityKind.COMBO_ABILITIES[0]));
        assertEquals(BossPhaseData.MAX_COMBO_DELAY, phase.comboDelay(BossAbilityKind.COMBO_ABILITIES[1]));
    }

    @Test
    @DisplayName("the death blast and the arena hazard own no chain slot")
    void unchainableKindsOwnNoSlot() {
        int[] followUps = emptyFollowUps();
        int[] delays = new int[BossAbilityKind.COUNT];
        followUps[BossAbilityKind.BLAST] = BossAbilityKind.AREA;
        followUps[BossAbilityKind.HAZARD] = BossAbilityKind.AREA;
        delays[BossAbilityKind.BLAST] = 20;
        delays[BossAbilityKind.HAZARD] = 20;
        CompoundTag tag = new BossPhaseData().writeToNBT();
        tag.putIntArray(FOLLOW_UP, followUps);
        tag.putIntArray(DELAY, delays);

        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(tag);
        assertArrayEquals(emptyFollowUps(), phase.writeToNBT().getIntArray(FOLLOW_UP),
                "neither is cast, so neither ends in a way that could hand anything on");
        assertArrayEquals(new int[BossAbilityKind.COUNT], phase.writeToNBT().getIntArray(DELAY));

        phase.setComboFollowUp(BossAbilityKind.HAZARD, BossAbilityKind.AREA);
        phase.setComboDelay(BossAbilityKind.BLAST, 20);
        assertEquals(NONE, phase.comboFollowUp(BossAbilityKind.HAZARD));
        assertEquals(0, phase.comboDelay(BossAbilityKind.BLAST));
        assertFalse(phase.isComboFollowUp(BossAbilityKind.HAZARD));
        // Past either end of the table, a question gets an empty answer rather than an exception.
        for (int outside : new int[]{-1, BossAbilityKind.COUNT, Long.SIZE + BossAbilityKind.AREA}) {
            phase.setComboFollowUp(outside, BossAbilityKind.AREA);
            phase.setComboDelay(outside, 20);
            assertEquals(NONE, phase.comboFollowUp(outside));
            assertEquals(0, phase.comboDelay(outside));
            assertFalse(phase.isComboFollowUp(outside));
        }
    }

    @Test
    @DisplayName("the setters hold the same line the load does")
    void settersValidateLikeTheLoad() {
        BossPhaseData phase = new BossPhaseData();
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.BOULDER_RAIN);
        assertEquals(BossAbilityKind.BOULDER_RAIN, phase.comboFollowUp(BossAbilityKind.LEAP));
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.LEAP);
        assertEquals(NONE, phase.comboFollowUp(BossAbilityKind.LEAP), "an ability cannot follow itself");
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.BOULDER_RAIN);
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.BLAST);
        assertEquals(NONE, phase.comboFollowUp(BossAbilityKind.LEAP), "the death blast is never started");
        phase.setComboFollowUp(BossAbilityKind.LEAP, BossAbilityKind.COUNT);
        assertEquals(NONE, phase.comboFollowUp(BossAbilityKind.LEAP));

        phase.setComboDelay(BossAbilityKind.LEAP, -1);
        assertEquals(0, phase.comboDelay(BossAbilityKind.LEAP));
        phase.setComboDelay(BossAbilityKind.LEAP, 99999);
        assertEquals(BossPhaseData.MAX_COMBO_DELAY, phase.comboDelay(BossAbilityKind.LEAP));
        phase.setComboDelay(BossAbilityKind.LEAP, 10);
        assertEquals(10, phase.comboDelay(BossAbilityKind.LEAP));
    }

    @Test
    @DisplayName("every chain slot comes back from the save on its own")
    void everySlotRoundTrips() {
        int[] abilities = BossAbilityKind.COMBO_ABILITIES;
        BossPhaseData saved = new BossPhaseData();
        for (int i = 0; i < abilities.length; i++) {
            // Each onto the one after it, so no two slots hold the same pair.
            saved.setComboFollowUp(abilities[i], abilities[(i + 1) % abilities.length]);
            saved.setComboDelay(abilities[i], 3 + i * 7);
        }
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(saved.writeToNBT());
        for (int i = 0; i < abilities.length; i++) {
            assertEquals(abilities[(i + 1) % abilities.length], reread.comboFollowUp(abilities[i]));
            assertEquals(3 + i * 7, reread.comboDelay(abilities[i]));
        }
        assertEquals(saved.writeToNBT(), reread.writeToNBT());

        // A slot emptied again saves as empty, whatever it held.
        reread.setComboFollowUp(abilities[0], NONE);
        assertEquals(NONE, reread.writeToNBT().getIntArray(FOLLOW_UP)[abilities[0]]);
    }

    @Test
    @DisplayName("an ability somebody chains onto knows it is a follow-up")
    void followUpsAreKnown() {
        BossPhaseData phase = new BossPhaseData();
        assertFalse(phase.isComboFollowUp(BossAbilityKind.CAPTURE));
        phase.setComboFollowUp(BossAbilityKind.GEYSER, BossAbilityKind.CAPTURE);
        assertTrue(phase.isComboFollowUp(BossAbilityKind.CAPTURE));
        assertFalse(phase.isComboFollowUp(BossAbilityKind.GEYSER), "the ability that hands on is not a follow-up itself");
        phase.setComboFollowUp(BossAbilityKind.GEYSER, NONE);
        assertFalse(phase.isComboFollowUp(BossAbilityKind.CAPTURE));
    }

    @Test
    @DisplayName("the chainable list is exactly the abilities the rotation casts")
    void theComboListIsTheRotation() {
        Set<Integer> combo = Arrays.stream(BossAbilityKind.COMBO_ABILITIES).boxed().collect(Collectors.toSet());
        assertEquals(BossAbilityKind.COMBO_ABILITIES.length, combo.size(), "an ability is listed twice");
        // The controller starts a follow-up through its rotation row, so a kind with no row
        // there would be offered on the screen and never start.
        Set<Integer> rotation = BossAbility.ROTATION.stream().map(BossAbility::kind).collect(Collectors.toSet());
        assertEquals(rotation, combo);
        assertEquals(combo.size(), Long.bitCount(BossAbilityKind.COMBO_ALL));
    }

    private static void assertNoChains(BossPhaseData phase, String what) {
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            assertEquals(NONE, phase.comboFollowUp(ability), what + " should chain nothing onto " + ability);
            assertEquals(0, phase.comboDelay(ability), what + " should wait nothing after " + ability);
            assertFalse(phase.isComboFollowUp(ability), what + " should hold no follow-up " + ability);
        }
    }

    private static int[] emptyFollowUps() {
        int[] slots = new int[BossAbilityKind.COUNT];
        Arrays.fill(slots, NONE);
        return slots;
    }
}
