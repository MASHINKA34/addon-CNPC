package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-phase choice of which lasting abilities the boss sees out before it starts anything
 * else.
 *
 * <p>The gate reads it every tick and fails quietly either way: a bit that loads onto an old
 * boss freezes it for the length of every effect it casts, and a bit that sticks to an
 * ability outside the list is one no screen shows and nobody can switch off.</p>
 */
class BossFinishMaskTest {

    private static final String KEY = "FinishMask";

    @Test
    @DisplayName("a boss saved before the finish mask existed waits for nothing")
    void anOldSaveWaitsForNothing() {
        BossPhaseData phase = new BossPhaseData();
        assertEquals(0, phase.writeToNBT().getInt(KEY), "a fresh phase should wait for nothing");
        for (int ability : BossAbilityKind.LASTING_ABILITIES) {
            phase.setWaitsForFinish(ability, true);
        }
        CompoundTag tag = phase.writeToNBT();
        tag.remove(KEY);

        // Read into the phase that had every bit up, so the load has to clear them rather
        // than merely leave a fresh field at its default.
        phase.readFromNBT(tag);
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            assertFalse(phase.waitsForFinish(ability),
                    "a tag with no " + KEY + " key should not wait for ability " + ability);
        }
        assertEquals(0, phase.writeToNBT().getInt(KEY));
    }

    @Test
    @DisplayName("a saved bit outside the lasting list is dropped on load")
    void bitsOutsideTheListAreDroppedOnLoad() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        tag.putInt(KEY, -1);

        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(tag);
        assertEquals(BossAbilityKind.LASTING_ALL, reread.writeToNBT().getInt(KEY),
                "only the lasting abilities' bits should survive a mask with every bit set");
        Set<Integer> lasting = lasting();
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            assertEquals(lasting.contains(ability), reread.waitsForFinish(ability),
                    "ability " + ability + " read back wrong from a mask with every bit set");
        }
    }

    @Test
    @DisplayName("an ability whose effect ends with its cast cannot be marked")
    void abilitiesOutsideTheListTakeNoBit() {
        BossPhaseData phase = new BossPhaseData();
        Set<Integer> lasting = lasting();
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            if (!lasting.contains(ability)) {
                phase.setWaitsForFinish(ability, true);
            }
        }
        // Past the int, a shift wraps round: 36 would land on the hook's bit without the guard.
        phase.setWaitsForFinish(-1, true);
        phase.setWaitsForFinish(Integer.SIZE + BossAbilityKind.HOOK, true);
        assertEquals(0, phase.writeToNBT().getInt(KEY));
        assertFalse(phase.waitsForFinish(BossAbilityKind.LEAP), "the leap's flight already holds the boss");
        assertFalse(phase.waitsForFinish(BossAbilityKind.HUNT), "the hunt has its own silence switch");
        assertFalse(phase.waitsForFinish(Integer.SIZE + BossAbilityKind.HOOK));
    }

    @Test
    @DisplayName("each lasting ability's bit comes back from the save on its own")
    void everyLastingBitRoundTripsAlone() {
        for (int marked : BossAbilityKind.LASTING_ABILITIES) {
            BossPhaseData saved = new BossPhaseData();
            saved.setWaitsForFinish(marked, true);
            BossPhaseData reread = new BossPhaseData();
            reread.readFromNBT(saved.writeToNBT());
            for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
                assertEquals(ability == marked, reread.waitsForFinish(ability),
                        "marking only " + marked + " read back wrong for " + ability);
            }
            reread.setWaitsForFinish(marked, false);
            assertEquals(0, reread.writeToNBT().getInt(KEY), "unmarking " + marked + " should clear its bit");
        }
    }

    @Test
    @DisplayName("the lasting list is exactly the abilities whose effect outlives the cast")
    void theLastingListIsWrittenOutOnPurpose() {
        assertEquals(Set.of(BossAbilityKind.HOOK, BossAbilityKind.CAPTURE, BossAbilityKind.GEYSER,
                        BossAbilityKind.BOULDER_RAIN, BossAbilityKind.TETHER, BossAbilityKind.GRAVITY,
                        BossAbilityKind.MARK, BossAbilityKind.BEAM, BossAbilityKind.COCOON,
                        BossAbilityKind.PLATFORM), lasting());
        assertEquals(BossAbilityKind.LASTING_ABILITIES.length, lasting().size(), "an ability is listed twice");
        // The gate looks for marked abilities on the rotation, so one with no row there would
        // show a button on the screen and never be waited for.
        Set<Integer> rotation = BossAbility.ROTATION.stream().map(BossAbility::kind).collect(Collectors.toSet());
        assertTrue(rotation.containsAll(lasting()), "a lasting ability is not on the rotation: " + lasting());
    }

    private static Set<Integer> lasting() {
        return Arrays.stream(BossAbilityKind.LASTING_ABILITIES).boxed().collect(Collectors.toSet());
    }
}
