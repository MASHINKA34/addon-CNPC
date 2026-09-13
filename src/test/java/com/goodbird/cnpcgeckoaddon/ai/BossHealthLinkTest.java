package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The health link's sums and its idea of a group, checked without a world.
 *
 * <p>None of it throws when it is wrong. A share worked out against the wrong maximum is a
 * partner that dies a third of the way through the fight or lives past the other's death; a
 * heal shared in full off a boss already at full health is a pool that tops itself up; and a
 * group that depends on which of its bosses asks kills one twin and leaves the other standing.</p>
 */
class BossHealthLinkTest {

    private static final float EPSILON = 1.0E-4F;

    @Test
    @DisplayName("a hit on one boss takes the same share of its maximum off a partner with a different maximum")
    void aLossIsMirroredAsAShare() {
        // The first boss has 200 health and takes 60: thirty percent of it.
        float share = BossHealthLinkRuntime.share(60.0F, 200.0F);
        assertEquals(0.3F, share, EPSILON);
        // Its partner has 50 and is at full, so it loses fifteen.
        assertEquals(35.0F, BossHealthLinkRuntime.afterLoss(50.0F, 50.0F, share), EPSILON);
        // And a partner that is half down loses the same fifteen, not a share of what it has left.
        assertEquals(10.0F, BossHealthLinkRuntime.afterLoss(25.0F, 50.0F, share), EPSILON);
    }

    @Test
    @DisplayName("two bosses hit in turn stay on the same share of their health")
    void turnsKeepTheSharesTogether() {
        float firstMax = 300.0F;
        float secondMax = 40.0F;
        float first = firstMax;
        float second = secondMax;
        float[][] hits = {{0, 45.0F}, {1, 8.0F}, {0, 12.5F}, {1, 3.0F}, {0, 90.0F}};
        for (float[] hit : hits) {
            if (hit[0] == 0) {
                float share = BossHealthLinkRuntime.share(hit[1], firstMax);
                first -= hit[1];
                second = BossHealthLinkRuntime.afterLoss(second, secondMax, share);
            } else {
                float share = BossHealthLinkRuntime.share(hit[1], secondMax);
                second -= hit[1];
                first = BossHealthLinkRuntime.afterLoss(first, firstMax, share);
            }
            assertEquals(first / firstMax, second / secondMax, EPSILON,
                    "after a hit for " + hit[1] + " the two bars read different shares");
        }
    }

    @Test
    @DisplayName("a heal hands on only the share that really lands")
    void aHealIsMirroredAsTheShareThatLands() {
        // 100 of 200 healed by 50: a quarter of the maximum.
        float share = BossHealthLinkRuntime.healedShare(50.0F, 100.0F, 200.0F);
        assertEquals(0.25F, share, EPSILON);
        assertEquals(32.5F, BossHealthLinkRuntime.afterGain(20.0F, 50.0F, share), EPSILON);

        assertEquals(0.05F, BossHealthLinkRuntime.healedShare(50.0F, 190.0F, 200.0F), EPSILON,
                "a heal past full only gives back what was missing");
        assertEquals(0.0F, BossHealthLinkRuntime.healedShare(50.0F, 200.0F, 200.0F),
                "a boss healing at full health fills nobody's pool");
        assertEquals(0.0F, BossHealthLinkRuntime.healedShare(50.0F, 0.0F, 200.0F),
                "a dead boss is not healed, so neither is anyone through it");
        assertEquals(0.0F, BossHealthLinkRuntime.healedShare(-5.0F, 100.0F, 200.0F));
        assertEquals(50.0F, BossHealthLinkRuntime.afterGain(45.0F, 50.0F, 0.5F), EPSILON,
                "a partner is never healed past its own maximum");
    }

    @Test
    @DisplayName("a partner whose share drifted lower is the one whose pool runs dry first")
    void aDriftedShareRunsDryFirst() {
        // Linked after it had already been hurt: 10 of 50 while the other stands at full.
        float share = BossHealthLinkRuntime.share(60.0F, 200.0F);
        assertTrue(BossHealthLinkRuntime.afterLoss(10.0F, 50.0F, share) <= 0.0F,
                "thirty percent off a boss on twenty percent leaves it nothing");
        assertEquals(0.0F, BossHealthLinkRuntime.share(0.0F, 200.0F));
        assertEquals(0.0F, BossHealthLinkRuntime.share(10.0F, 0.0F), "a boss with no maximum shares nothing");
    }

    @Test
    @DisplayName("a range of nothing is the whole level, anything else is a sphere")
    void rangeZeroIsTheWholeLevel() {
        assertTrue(BossHealthLinkRuntime.withinRange(1.0E12D, 0));
        assertTrue(BossHealthLinkRuntime.withinRange(64.0D * 64.0D, 64), "the edge itself still counts");
        assertFalse(BossHealthLinkRuntime.withinRange(100.0D * 100.0D, 64),
                "a partner a hundred blocks off is out of a range of sixty-four");
    }

    @Test
    @DisplayName("only the same group and the same mode link two bosses, and a blank group links nobody")
    void theGroupAndTheModeDecide() {
        TeleportPathData twin = linked("twins", TeleportPathData.HEALTH_LINK_SHARED);
        assertTrue(BossHealthLinkRuntime.sameLink(twin, linked("twins", TeleportPathData.HEALTH_LINK_SHARED)));
        assertFalse(BossHealthLinkRuntime.sameLink(twin, linked("triplets", TeleportPathData.HEALTH_LINK_SHARED)));
        assertFalse(BossHealthLinkRuntime.sameLink(twin, linked("twins", TeleportPathData.HEALTH_LINK_TOGETHER)),
                "a boss sharing its health must not pour it into one that lies down");
        assertFalse(BossHealthLinkRuntime.sameLink(linked("", TeleportPathData.HEALTH_LINK_SHARED),
                linked("", TeleportPathData.HEALTH_LINK_SHARED)), "two bosses left blank are not a group");
    }

    @Test
    @DisplayName("a chain of partners is one group, whichever end of it asks")
    void aChainIsOneGroup() {
        // The middle boss stands in range of both ends; the ends are out of range of each other.
        Map<String, List<String>> partners = Map.of(
                "left", List.of("middle"),
                "middle", List.of("left", "right"),
                "right", List.of("middle"),
                "alone", List.of());
        Set<String> fromLeft = BossHealthLinkRuntime.connected("left", partners::get);
        Set<String> fromRight = BossHealthLinkRuntime.connected("right", partners::get);
        assertEquals(Set.of("left", "middle", "right"), fromLeft);
        assertEquals(fromLeft, fromRight, "the far end of the chain sees the same group");
        assertEquals(Set.of("alone"), BossHealthLinkRuntime.connected("alone", partners::get));
    }

    @Test
    @DisplayName("a killing blow lays a boss down only while it has a partner still standing")
    void aKillingBlowDownsOnlyWithAStandingPartner() {
        assertTrue(BossHealthLinkRuntime.isLethal(30.0F, 30.0F), "a hit for exactly what is left kills");
        assertFalse(BossHealthLinkRuntime.isLethal(29.5F, 30.0F));
        assertTrue(BossHealthLinkRuntime.downsOnLethalHit(1, false), "one twin still up: the other lies down");
        assertFalse(BossHealthLinkRuntime.downsOnLethalHit(0, true),
                "a boss with no partner in reach just dies - the link needs somebody to wait for");
        assertFalse(BossHealthLinkRuntime.downsOnLethalHit(2, true),
                "the last one standing dies on the hit, and takes the ones lying down with it");
        assertEquals(29.0F, BossHealthLinkRuntime.downedDamage(30.0F, 1.0F), EPSILON,
                "the cut hit leaves the guard's worth of health");
        assertEquals(0.0F, BossHealthLinkRuntime.downedDamage(0.5F, 1.0F),
                "a boss already under one health is left on what it has, never healed by the cut");
        assertEquals(25.0F, BossHealthLinkRuntime.downedDamage(30.0F, 5.0F), EPSILON,
                "a boss told to lie down on five is left on five");
    }

    @Test
    @DisplayName("downed with a partner standing, the boss waits out the window and gets up on its last tick")
    void theWindowRunsOutIntoGettingUp() {
        long downedAt = 1000L;
        long until = BossHealthLinkRuntime.downedUntil(downedAt, 200);
        assertEquals(1200L, until);
        for (long tick = downedAt; tick < until; tick++) {
            assertEquals(BossHealthLinkRuntime.Verdict.WAIT, BossHealthLinkRuntime.verdict(1, false, tick, until),
                    "tick " + tick + " is still inside the window");
        }
        assertEquals(BossHealthLinkRuntime.Verdict.GET_UP, BossHealthLinkRuntime.verdict(1, false, until, until));
        assertEquals(50.0F, BossHealthLinkRuntime.reviveHealth(100.0F, 50), EPSILON);
        assertEquals(1.0F, BossHealthLinkRuntime.reviveHealth(100.0F, 0), EPSILON,
                "a share of nothing is read as the least a boss can get up with");
        assertEquals(100.0F, BossHealthLinkRuntime.reviveHealth(100.0F, 250), EPSILON);
    }

    @Test
    @DisplayName("the partner falling inside the window takes the group down, even on the window's last tick")
    void aFallingPartnerEndsTheWait() {
        long until = BossHealthLinkRuntime.downedUntil(0L, 200);
        assertEquals(BossHealthLinkRuntime.Verdict.DIE_TOGETHER, BossHealthLinkRuntime.verdict(1, true, 150L, until));
        assertEquals(BossHealthLinkRuntime.Verdict.DIE_TOGETHER, BossHealthLinkRuntime.verdict(1, true, until, until),
                "deaths are read before the clock");
        assertEquals(BossHealthLinkRuntime.Verdict.DIE_ALONE, BossHealthLinkRuntime.verdict(0, true, 10L, until),
                "a partner that died, unloaded or walked out of range leaves nobody to wait for");
        assertEquals(BossHealthLinkRuntime.Verdict.WAIT, BossHealthLinkRuntime.verdict(2, false, 10L, until),
                "one of two partners down is not the group down");
    }

    @Test
    @DisplayName("a death takes the ones lying down along only once nobody of the group stands")
    void aDeathTakesTheRestOnlyWhenNobodyStands() {
        assertTrue(BossHealthLinkRuntime.deathTakesTheRest(1, true), "the last twin standing fell: the other goes too");
        assertFalse(BossHealthLinkRuntime.deathTakesTheRest(1, false),
                "a downed boss /killed while its partner stands takes nobody along");
        assertFalse(BossHealthLinkRuntime.deathTakesTheRest(0, true));
    }

    @Test
    @DisplayName("the countdown reads whole seconds rounded up and is told once a second from the fall")
    void theCountdownIsWholeSeconds() {
        assertEquals(10, BossHealthLinkRuntime.secondsLeft(200L, 0L));
        assertEquals(10, BossHealthLinkRuntime.secondsLeft(200L, 1L), "nineteen and a half seconds read as ten");
        assertEquals(1, BossHealthLinkRuntime.secondsLeft(200L, 199L), "the last tick reads as one, not none");
        assertEquals(1, BossHealthLinkRuntime.secondsLeft(200L, 200L));
        assertTrue(BossHealthLinkRuntime.announcesOn(37L, 37L, 20), "told on the tick the boss goes down");
        assertFalse(BossHealthLinkRuntime.announcesOn(37L, 38L, 20));
        assertTrue(BossHealthLinkRuntime.announcesOn(37L, 57L, 20));
        assertTrue(BossHealthLinkRuntime.announcesOn(37L, 42L, 5), "a shorter interval is told oftener");
        assertFalse(BossHealthLinkRuntime.announcesOn(37L, 42L, 20));
    }

    private static TeleportPathData linked(String group, int mode) {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.setHealthLinkGroup(group);
        data.setHealthLinkMode(mode);
        return data;
    }
}
