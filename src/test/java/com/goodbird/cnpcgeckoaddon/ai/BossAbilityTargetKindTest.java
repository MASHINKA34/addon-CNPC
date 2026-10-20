package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whom one ability may land on: the kinds of target its profile names, or the boss' own setting
 * when the profile leaves it to the boss - which, for every ability nobody touched, is exactly
 * the filter every ability shared before profiles.
 */
class BossAbilityTargetKindTest {

    private static final int[] KINDS = {TeleportPathData.ABILITY_TARGET_PLAYERS,
            TeleportPathData.ABILITY_TARGET_PLAYERS_AND_NPCS, TeleportPathData.ABILITY_TARGET_ALL};

    @Test
    @DisplayName("a profile left to the boss takes the boss' kinds, any other its own")
    void theProfileOrTheBossDecides() {
        for (int boss : KINDS) {
            assertEquals(boss, BossTargetingRuntime.effectiveTargetKind(BossAbilityProfile.TARGET_KIND_AS_BOSS, boss),
                    "as the boss, with the boss on " + boss);
            for (int own : KINDS) {
                assertEquals(own, BossTargetingRuntime.effectiveTargetKind(own, boss),
                        "a profile on " + own + " under a boss on " + boss);
            }
        }
    }

    @Test
    @DisplayName("the kinds read the way the boss-wide setting always did")
    void theKindsMatchTheOldFilter() {
        for (int kind : KINDS) {
            assertTrue(BossTargetingRuntime.matchesTargetKind(true, false, kind), "a player is always a target");
        }
        // An npc: players only says no, the other two say yes.
        assertFalse(BossTargetingRuntime.matchesTargetKind(false, true, TeleportPathData.ABILITY_TARGET_PLAYERS));
        assertTrue(BossTargetingRuntime.matchesTargetKind(false, true, TeleportPathData.ABILITY_TARGET_PLAYERS_AND_NPCS));
        assertTrue(BossTargetingRuntime.matchesTargetKind(false, true, TeleportPathData.ABILITY_TARGET_ALL));
        // A mob or an animal: only everyone takes it in.
        assertFalse(BossTargetingRuntime.matchesTargetKind(false, false, TeleportPathData.ABILITY_TARGET_PLAYERS));
        assertFalse(BossTargetingRuntime.matchesTargetKind(false, false,
                TeleportPathData.ABILITY_TARGET_PLAYERS_AND_NPCS));
        assertTrue(BossTargetingRuntime.matchesTargetKind(false, false, TeleportPathData.ABILITY_TARGET_ALL));
    }

    @Test
    @DisplayName("an untouched profile filters exactly as the boss' setting, whatever that is")
    void anUntouchedProfileIsTheBossSetting() {
        BossPhaseData phase = new BossPhaseData();
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            int profileKind = phase.profile(ability).getTargetKind();
            for (int boss : KINDS) {
                int kind = BossTargetingRuntime.effectiveTargetKind(profileKind, boss);
                for (boolean npc : new boolean[]{false, true}) {
                    assertEquals(BossTargetingRuntime.matchesTargetKind(false, npc, boss),
                            BossTargetingRuntime.matchesTargetKind(false, npc, kind),
                            "ability " + ability + " under a boss on " + boss + (npc ? ", an npc" : ", a mob"));
                }
            }
        }
    }

    @Test
    @DisplayName("players only keeps an npc out of an ability the boss would aim at everyone")
    void aProfileNarrowsOrWidensTheBoss() {
        int narrowed = BossTargetingRuntime.effectiveTargetKind(TeleportPathData.ABILITY_TARGET_PLAYERS,
                TeleportPathData.ABILITY_TARGET_ALL);
        assertFalse(BossTargetingRuntime.matchesTargetKind(false, true, narrowed), "the npc beside the player is spared");
        assertTrue(BossTargetingRuntime.matchesTargetKind(true, false, narrowed));
        int widened = BossTargetingRuntime.effectiveTargetKind(TeleportPathData.ABILITY_TARGET_ALL,
                TeleportPathData.ABILITY_TARGET_PLAYERS);
        assertTrue(BossTargetingRuntime.matchesTargetKind(false, false, widened), "and everyone takes the mobs in too");
    }
}
