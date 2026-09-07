package com.goodbird.cnpcgeckoaddon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two sums a fight is actually decided by: which phase the boss is in, and how much
 * health a party of a given size gives it.
 *
 * <p>Both are read every tick of every encounter and neither can fail loudly. A phase
 * lookup off by one puts the boss in the wrong phase - wrong abilities, wrong animations -
 * for the whole fight, and a scaling formula that reads one player as none turns a raid
 * boss into a solo one. Both are pure arithmetic, so both can be pinned down here.</p>
 */
class BossFightRulesTest {

    @Test
    @DisplayName("a boss with one phase is always in it, whatever its health")
    void onePhaseIsAlwaysTheAnswer() {
        TeleportPathData data = new TeleportPathData();
        // A fresh boss comes with two phases, so the single-phase case has to be asked for.
        data.setPhaseCount(1);
        for (int percent = -50; percent <= 150; percent += 10) {
            assertEquals(0, data.resolvePhaseIndex(percent),
                    "a single-phase boss left phase 0 at " + percent + "% health");
        }
    }

    @Test
    @DisplayName("each phase owns health from its own threshold down to the next one")
    void phasesOwnTheBandBelowTheirThreshold() {
        TeleportPathData data = new TeleportPathData();
        data.setPhaseCount(3);
        data.getPhase(1).setStartHealthPercent(70);
        data.getPhase(2).setStartHealthPercent(30);

        assertEquals(0, data.resolvePhaseIndex(100), "full health belongs to the first phase");
        assertEquals(0, data.resolvePhaseIndex(71));
        assertEquals(1, data.resolvePhaseIndex(70), "the threshold itself starts its own phase");
        assertEquals(1, data.resolvePhaseIndex(31));
        assertEquals(2, data.resolvePhaseIndex(30));
        assertEquals(2, data.resolvePhaseIndex(1));
        assertEquals(2, data.resolvePhaseIndex(0), "a dying boss is in its last phase, not past it");
    }

    @Test
    @DisplayName("the phase lookup never walks off either end")
    void phaseLookupStaysInsideTheList() {
        TeleportPathData data = new TeleportPathData();
        data.setPhaseCount(4);
        for (int percent = -1000; percent <= 1000; percent += 7) {
            int index = data.resolvePhaseIndex(percent);
            assertTrue(index >= 0 && index < data.getPhaseCount(),
                    "health " + percent + "% resolved to phase " + index);
            assertNotNull(data.getPhase(index));
        }
        // Out-of-range indexes are clamped rather than thrown, because a save can hold one.
        assertSame(data.getPhase(0), data.getPhase(-5));
        assertSame(data.getPhase(data.getPhaseCount() - 1), data.getPhase(999));
    }

    @Test
    @DisplayName("a solo player gets the boss at exactly its configured health")
    void onePlayerAddsNothing() {
        TeleportPathData data = new TeleportPathData();
        data.setHealthScalingEnabled(true);
        data.setHealthPerPlayerPercent(50);
        assertEquals(200.0D, data.calculateScaledMaxHealth(200.0D, 1), 1.0E-6D,
                "the first player is the baseline, not a bonus on top of it");
        assertEquals(200.0D, data.calculateScaledMaxHealth(200.0D, 0), 1.0E-6D,
                "a count of nobody has to be read as the one player who is there");
    }

    @Test
    @DisplayName("percent scaling adds its share per extra player")
    void percentScalingCountsExtraPlayers() {
        TeleportPathData data = new TeleportPathData();
        data.setHealthScalingMode(TeleportPathData.HEALTH_SCALING_PERCENT);
        data.setHealthPerPlayerPercent(50);
        assertEquals(300.0D, data.calculateScaledMaxHealth(200.0D, 2), 1.0E-6D);
        assertEquals(400.0D, data.calculateScaledMaxHealth(200.0D, 3), 1.0E-6D);
    }

    @Test
    @DisplayName("flat scaling adds its number per extra player, and both modes add both")
    void flatAndCombinedScaling() {
        TeleportPathData data = new TeleportPathData();
        data.setHealthScalingMode(TeleportPathData.HEALTH_SCALING_FLAT);
        data.setHealthPerPlayerFlat(30);
        assertEquals(160.0D, data.calculateScaledMaxHealth(100.0D, 3), 1.0E-6D);

        data.setHealthScalingMode(TeleportPathData.HEALTH_SCALING_PERCENT_AND_FLAT);
        data.setHealthPerPlayerPercent(10);
        // Two extra players: 100 + 2*10% of 100 + 2*30.
        assertEquals(180.0D, data.calculateScaledMaxHealth(100.0D, 3), 1.0E-6D);
    }

    @Test
    @DisplayName("the player cap is what the party is counted as, however many turned up")
    void scalingHonoursThePlayerCap() {
        TeleportPathData data = new TeleportPathData();
        data.setHealthScalingMode(TeleportPathData.HEALTH_SCALING_PERCENT);
        data.setHealthPerPlayerPercent(100);
        data.setHealthScalingPlayerCap(3);
        double atCap = data.calculateScaledMaxHealth(100.0D, 3);
        assertEquals(300.0D, atCap, 1.0E-6D);
        assertEquals(atCap, data.calculateScaledMaxHealth(100.0D, 50), 1.0E-6D,
                "a party past the cap has to be charged at the cap, not past it");
    }

    @Test
    @DisplayName("scaling survives a nonsense base health instead of handing back one")
    void scalingSurvivesRubbishInput() {
        TeleportPathData data = new TeleportPathData();
        data.setHealthScalingMode(TeleportPathData.HEALTH_SCALING_PERCENT);
        data.setHealthPerPlayerPercent(100);
        for (double base : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -5.0D, 0.0D}) {
            double scaled = data.calculateScaledMaxHealth(base, 4);
            assertTrue(Double.isFinite(scaled) && scaled >= 1.0D,
                    "a base of " + base + " came back as " + scaled);
        }
    }
}
