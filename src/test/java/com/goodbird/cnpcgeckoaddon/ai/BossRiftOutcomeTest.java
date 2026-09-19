package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftOutcome.Penalty;
import com.goodbird.cnpcgeckoaddon.ai.BossRiftOutcome.Result;
import com.goodbird.cnpcgeckoaddon.data.BossEffectData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.goodbird.cnpcgeckoaddon.data.BossRiftSettings.EXIT_BOTH;
import static com.goodbird.cnpcgeckoaddon.data.BossRiftSettings.EXIT_CRYSTALS;
import static com.goodbird.cnpcgeckoaddon.data.BossRiftSettings.EXIT_MINIONS;
import static com.goodbird.cnpcgeckoaddon.data.BossRiftSettings.EXIT_SURVIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a reality rift goes, decided without a world: solo or group by who is fighting on the arena,
 * success or failure by the way out, its counters and its limit, and what a failure costs by the
 * phase's switches.
 */
class BossRiftOutcomeTest {

    private static final long START = 1000L;
    private static final long END = 2200L;

    @Test
    @DisplayName("a rift is solo while no more fight on the arena than the phase allows")
    void soloOrGroupByHeadCount() {
        assertTrue(BossRiftOutcome.isSolo(1, 1), "one player, one allowed");
        assertFalse(BossRiftOutcome.isSolo(2, 1), "two players against a solo rule of one");
        assertTrue(BossRiftOutcome.isSolo(2, 2));
        assertTrue(BossRiftOutcome.isSolo(0, 1), "nobody else on the arena is still solo");
        assertEquals(100, BossRiftOutcome.damagePercent(true, 50), "a solo rift leaves the boss' damage alone");
        assertEquals(50, BossRiftOutcome.damagePercent(false, 50), "a group rift cuts it to the group share");
        assertEquals(0, BossRiftOutcome.damagePercent(false, -5));
        assertEquals(100, BossRiftOutcome.damagePercent(false, 400));
    }

    @Test
    @DisplayName("surviving the time succeeds when it runs out, and not a tick before")
    void survivalSucceedsAtTheEnd() {
        assertEquals(Result.RUNNING, judge(EXIT_SURVIVE, END - 1, 1, 1, 0, true, 0, 0));
        assertEquals(Result.SUCCESS, judge(EXIT_SURVIVE, END, 1, 1, 0, true, 0, 0));
        assertEquals(Result.SUCCESS, judge(EXIT_SURVIVE, END + 50, 2, 1, 1, true, 0, 0),
                "one of two died, the other lived through it");
    }

    @Test
    @DisplayName("killing the minions succeeds early, and the limit running out first is a failure")
    void minionsSucceedEarlyOrFailAtTheLimit() {
        assertEquals(Result.RUNNING, judge(EXIT_MINIONS, START + 5, 1, 1, 0, true, 3, 2));
        assertEquals(Result.SUCCESS, judge(EXIT_MINIONS, START + 5, 1, 1, 0, true, 3, 0),
                "the last minion down ends it at once");
        assertEquals(Result.FAILURE, judge(EXIT_MINIONS, END, 1, 1, 0, true, 3, 1), "one left at the limit");
        assertEquals(Result.RUNNING, BossRiftOutcome.judge(EXIT_MINIONS, START + 1, END, 1, 1, 0, true,
                false, 0, 0, 0), "before the minions are stood up nothing is decided");
        assertEquals(Result.RUNNING, judge(EXIT_MINIONS, START + 5, 1, 1, 0, true, 0, 0),
                "no minion could be stood up: it runs as a survival rift");
        assertEquals(Result.SUCCESS, judge(EXIT_MINIONS, END, 1, 1, 0, true, 0, 0),
                "and succeeds when the time is up rather than failing its players for a missing clone");
    }

    @Test
    @DisplayName("the crystal ways out count the crystals, and both needs both")
    void crystalsAndBoth() {
        assertEquals(Result.RUNNING, judge(EXIT_CRYSTALS, START + 5, 1, 1, 0, true, 0, 0, 2));
        assertEquals(Result.SUCCESS, judge(EXIT_CRYSTALS, START + 5, 1, 1, 0, true, 0, 0, 0));
        assertEquals(Result.FAILURE, judge(EXIT_CRYSTALS, END, 1, 1, 0, true, 0, 0, 1));
        assertEquals(Result.RUNNING, judge(EXIT_BOTH, START + 5, 1, 1, 0, true, 3, 0, 1), "minions done, crystals not");
        assertEquals(Result.RUNNING, judge(EXIT_BOTH, START + 5, 1, 1, 0, true, 3, 1, 0), "crystals done, minions not");
        assertEquals(Result.SUCCESS, judge(EXIT_BOTH, START + 5, 1, 1, 0, true, 3, 0, 0));
        assertEquals(Result.FAILURE, judge(EXIT_BOTH, END, 1, 1, 0, true, 3, 1, 0));
        // The crystals are in the game, so both ways out that ask for them run as themselves.
        assertEquals(EXIT_CRYSTALS, BossRiftSettings.effectiveExitMode(EXIT_CRYSTALS, BossRiftSettings.CRYSTALS_AVAILABLE));
        assertEquals(EXIT_BOTH, BossRiftSettings.effectiveExitMode(EXIT_BOTH, BossRiftSettings.CRYSTALS_AVAILABLE));
        assertEquals(EXIT_SURVIVE, BossRiftSettings.effectiveExitMode(EXIT_CRYSTALS, false),
                "and would fall back to surviving the time in a build that had none");
    }

    @Test
    @DisplayName("everyone dead is a failure when the phase says so; nobody left otherwise just closes it")
    void deathAndEmptiness() {
        assertEquals(Result.FAILURE, judge(EXIT_SURVIVE, START + 5, 2, 0, 2, true, 0, 0),
                "both taken died");
        assertEquals(Result.EMPTY, judge(EXIT_SURVIVE, START + 5, 2, 0, 2, false, 0, 0),
                "the phase does not count deaths as failing");
        assertEquals(Result.EMPTY, judge(EXIT_MINIONS, START + 5, 2, 0, 1, true, 3, 3),
                "one died, one left some other way: not everyone died");
        assertEquals(Result.RUNNING, judge(EXIT_SURVIVE, START + 5, 2, 1, 1, true, 0, 0),
                "one of two dead is not the end");
        assertEquals(Result.EMPTY, judge(EXIT_SURVIVE, START + 5, 0, 0, 0, true, 0, 0));
    }

    @Test
    @DisplayName("a failure costs exactly what its switches say")
    void penaltiesFollowTheSwitches() {
        assertEquals(EnumSet.noneOf(Penalty.class), BossRiftOutcome.penalties(false, 0, false, 0));
        assertEquals(EnumSet.of(Penalty.RAGE), BossRiftOutcome.penalties(true, 0, false, 0));
        assertEquals(EnumSet.of(Penalty.ARENA_DAMAGE), BossRiftOutcome.penalties(false, 12, false, 0));
        assertEquals(EnumSet.of(Penalty.EFFECTS), BossRiftOutcome.penalties(false, 0, true, 0),
                "no damage still lands the potions");
        assertEquals(EnumSet.of(Penalty.HEAL), BossRiftOutcome.penalties(false, 0, false, 25));
        assertEquals(EnumSet.allOf(Penalty.class), BossRiftOutcome.penalties(true, 5, true, 10));

        BossRiftSettings settings = new BossRiftSettings();
        assertEquals(EnumSet.noneOf(Penalty.class), BossRiftOutcome.penalties(settings),
                "a fresh rift's failure costs nothing but the sound");
        settings.setFailRage(true);
        settings.setFailArenaDamage(20);
        BossEffectData effect = settings.getFailEffects().get(0);
        effect.setEnabled(true);
        effect.setEffectId("minecraft:slowness");
        settings.setFailHealPercent(15);
        Set<Penalty> all = BossRiftOutcome.penalties(settings);
        assertEquals(EnumSet.allOf(Penalty.class), all);
        assertEquals(15.0F, BossRiftOutcome.healAmount(100.0F, 15), 1.0E-6F);
        assertEquals(0.0F, BossRiftOutcome.healAmount(100.0F, 0), 1.0E-6F);
        assertEquals(100.0F, BossRiftOutcome.healAmount(100.0F, 250), 1.0E-6F, "never more than the maximum");
    }

    @Test
    @DisplayName("a failure's hit waits for the players sent back to land, and never longer than its cap")
    void failureHitWaitsForTheLanding() {
        long closed = END;
        int cap = BossRiftOutcome.STRIKE_WAIT_TICKS;
        assertFalse(BossRiftOutcome.strikesNow(closed, closed, 1),
                "not on the tick they were sent back, while vanilla still keeps them out of harm's way");
        assertFalse(BossRiftOutcome.strikesNow(closed + 3, closed, 1), "nor while one of them is still on the way");
        assertTrue(BossRiftOutcome.strikesNow(closed + 3, closed, 0), "but as soon as everyone has landed");
        assertTrue(BossRiftOutcome.strikesNow(closed, closed, 0), "nobody sent back: at once, on whoever stayed");
        assertFalse(BossRiftOutcome.strikesNow(closed + cap - 1, closed, 2));
        assertTrue(BossRiftOutcome.strikesNow(closed + cap, closed, 2),
                "a client that never confirms its teleport does not hold the hit back for good");
        assertTrue(cap >= 20, "the cap leaves a slow connection a second at least to confirm the teleport");

        assertTrue(BossRiftOutcome.hitsArena(EnumSet.of(Penalty.ARENA_DAMAGE)));
        assertTrue(BossRiftOutcome.hitsArena(EnumSet.of(Penalty.EFFECTS)), "the potions alone are a hit to wait for");
        assertTrue(BossRiftOutcome.hitsArena(EnumSet.allOf(Penalty.class)));
        assertFalse(BossRiftOutcome.hitsArena(EnumSet.of(Penalty.RAGE, Penalty.HEAL)),
                "an enrage and a heal owe the arena nothing");
    }

    @Test
    @DisplayName("a solo success exposes the boss when the window is not nought; nothing else does")
    void onlyASoloSuccessExposes() {
        assertTrue(BossRiftOutcome.exposes(Result.SUCCESS, true, 200));
        assertFalse(BossRiftOutcome.exposes(Result.SUCCESS, true, 0), "a window of nought is none");
        assertFalse(BossRiftOutcome.exposes(Result.SUCCESS, false, 200), "a group's success changes nothing");
        assertFalse(BossRiftOutcome.exposes(Result.FAILURE, true, 200));
        assertFalse(BossRiftOutcome.exposes(Result.EMPTY, true, 200));
    }

    @Test
    @DisplayName("the countdown rounds up, so it never reads nought while time remains")
    void secondsLeftRoundUp() {
        assertEquals(60L, BossRiftOutcome.secondsLeft(START, START + 1200));
        assertEquals(1L, BossRiftOutcome.secondsLeft(START, START + 1));
        assertEquals(0L, BossRiftOutcome.secondsLeft(START + 5, START));
    }

    private static Result judge(int mode, long now, int taken, int inside, int deaths, boolean failOnDeath,
                                int spawned, int left) {
        return judge(mode, now, taken, inside, deaths, failOnDeath, spawned, left, 0);
    }

    private static Result judge(int mode, long now, int taken, int inside, int deaths, boolean failOnDeath,
                                int spawned, int left, int crystals) {
        return BossRiftOutcome.judge(mode, now, END, taken, inside, deaths, failOnDeath, true, spawned, left, crystals);
    }
}
