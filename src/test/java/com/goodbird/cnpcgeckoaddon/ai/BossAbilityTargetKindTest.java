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
    @DisplayName("the boss' own target suits an ability only by the profile's own kinds, any under one left to the boss")
    void theMainTargetFitsByTheProfileAlone() {
        // {profile kind, player, npc, anything else alive}
        int[][] table = {
                {BossAbilityProfile.TARGET_KIND_AS_BOSS, 1, 1, 1},
                {TeleportPathData.ABILITY_TARGET_PLAYERS, 1, 0, 0},
                {TeleportPathData.ABILITY_TARGET_PLAYERS_AND_NPCS, 1, 1, 0},
                {TeleportPathData.ABILITY_TARGET_ALL, 1, 1, 1}
        };
        for (int[] row : table) {
            int kind = row[0];
            assertEquals(row[1] == 1, BossTargetingRuntime.fitsProfileKind(true, false, kind), "a player, profile " + kind);
            assertEquals(row[2] == 1, BossTargetingRuntime.fitsProfileKind(false, true, kind), "an npc, profile " + kind);
            assertEquals(row[3] == 1, BossTargetingRuntime.fitsProfileKind(false, false, kind), "a mob, profile " + kind);
        }
    }

    @Test
    @DisplayName("an untouched profile turns no target of the boss away, whatever the boss aims at")
    void anUntouchedProfileKeepsEveryMainTarget() {
        BossPhaseData phase = new BossPhaseData();
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            int profileKind = phase.profile(ability).getTargetKind();
            for (boolean player : new boolean[]{false, true}) {
                for (boolean npc : new boolean[]{false, true}) {
                    assertTrue(BossTargetingRuntime.fitsProfileKind(player, npc, profileKind),
                            "ability " + ability + (player ? ", a player" : npc ? ", an npc" : ", a mob"));
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
