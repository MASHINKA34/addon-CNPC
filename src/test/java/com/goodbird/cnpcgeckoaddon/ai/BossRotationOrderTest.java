package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rotation's bookkeeping without a world: the order it looks at its rows in, the cast chance
 * rolled once per readiness, the casts counted against a limit, and how a cooldown is spread.
 * Whether anything is due, and what a starter makes of it, is the controller's business; here
 * the numbers are handed in and drawn from a seeded random.
 */
class BossRotationOrderTest {

    @Test
    @DisplayName("no spread is the configured cooldown to the tick, and draws nothing from the random")
    void noSpreadKeepsTheCooldownAndTheRandom() {
        BossAbilityProfile profile = new BossAbilityProfile();
        RandomSource random = RandomSource.create(42L);
        RandomSource twin = RandomSource.create(42L);
        for (int cooldown : new int[]{1, 30, 200, 12000}) {
            assertEquals(cooldown, profile.spreadCooldown(cooldown, random));
        }
        assertEquals(twin.nextInt(), random.nextInt(),
                "a profile without a spread must leave every other random pick of the fight where it was");
    }

    @Test
    @DisplayName("a spread keeps the cooldown within plus or minus it, and uses the whole window")
    void spreadStaysInsideItsWindow() {
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setCooldownJitterTicks(100);
        RandomSource random = RandomSource.create(20261020L);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < 2000; i++) {
            int cooldown = profile.spreadCooldown(200, random);
            assertTrue(cooldown >= 100 && cooldown <= 300, "200 +- 100 drew " + cooldown);
            lowest = Math.min(lowest, cooldown);
            highest = Math.max(highest, cooldown);
        }
        assertTrue(lowest <= 110, "the short end of the window is never drawn: lowest " + lowest);
        assertTrue(highest >= 290, "the long end of the window is never drawn: highest " + highest);
    }

    @Test
    @DisplayName("a spread wider than the cooldown never takes it under one tick")
    void spreadNeverGoesUnderOneTick() {
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setCooldownJitterTicks(100);
        RandomSource random = RandomSource.create(7L);
        boolean floored = false;
        for (int i = 0; i < 2000; i++) {
            int cooldown = profile.spreadCooldown(30, random);
            assertTrue(cooldown >= 1 && cooldown <= 130, "30 +- 100 drew " + cooldown);
            floored |= cooldown == 1;
        }
        assertTrue(floored, "half the window lies under a tick, so the floor has to be reached");
    }

    @Test
    @DisplayName("every priority at nought is the old walk: round the whole rotation from the moving start")
    void zeroPrioritiesKeepTheOldRound() {
        int count = BossAbility.ROTATION.size();
        int[] priorities = new int[count];
        for (int start = 0; start < count; start++) {
            int[] expected = new int[count];
            for (int offset = 0; offset < count; offset++) {
                expected[offset] = (start + offset) % count;
            }
            assertArrayEquals(expected, BossRotationOrder.order(count, start, priorities),
                    "the walk from row " + start + " changed with no priority set");
        }
    }

    @Test
    @DisplayName("higher priorities go first, and each priority keeps the round from the moving start")
    void mixedPrioritiesGoHighestFirst() {
        int[] priorities = {0, 9, 0, 3, 9};
        // Nines round from row 2: 4 then 1. The three next. The noughts round from row 2: 2 then 0.
        assertArrayEquals(new int[]{4, 1, 3, 2, 0}, BossRotationOrder.order(5, 2, priorities));
        assertArrayEquals(new int[]{1, 4, 3, 0, 2}, BossRotationOrder.order(5, 0, priorities));
        // A start past the end wraps, and a priority out of range is read as the nearer end.
        assertArrayEquals(new int[]{4, 1, 3, 2, 0}, BossRotationOrder.order(5, 7, priorities));
        assertArrayEquals(new int[]{1, 0}, BossRotationOrder.order(2, 0, new int[]{-5, 42}));
    }

    @Test
    @DisplayName("the cast chance is rolled once per readiness, against a seeded random")
    void chanceIsRolledOncePerReadiness() {
        int kind = BossAbilityKind.MELEE;
        // The fight's random, and a twin that draws exactly once per readiness: any second draw
        // would put the two out of step and every answer after it would stop matching.
        RandomSource random = RandomSource.create(99L);
        RandomSource twin = RandomSource.create(99L);
        BossCastRolls rolls = new BossCastRolls();
        int passes = 0;
        for (int readiness = 0; readiness < 400; readiness++) {
            boolean expected = twin.nextInt(100) < 25;
            boolean first = rolls.roll(kind, 25, random);
            assertEquals(expected, first, "readiness " + readiness + " rolled differently from the seeded draw");
            if (first) {
                passes++;
                assertFalse(rolls.isSkipped(kind));
                // The starter turns it away and it is looked at again: no second draw, same answer.
                for (int look = 0; look < 5; look++) {
                    assertTrue(rolls.roll(kind, 25, random), "a success must hold until the ability starts");
                }
                rolls.started(kind);
            } else {
                // The miss already cost it a cooldown; the next look is the next readiness.
                assertTrue(rolls.isSkipped(kind), "a miss is shown as a skip until the next roll");
            }
        }
        assertEquals(twin.nextInt(), random.nextInt(), "some readiness drew more than once from the fight's random");
        assertTrue(passes > 60 && passes < 140, "a 25% chance came up " + passes + " times in 400");
    }

    @Test
    @DisplayName("a full chance never draws, and a fresh readiness after re-arming rolls again")
    void fullChanceNeverDrawsAndRearmingRollsAgain() {
        BossCastRolls rolls = new BossCastRolls();
        RandomSource random = RandomSource.create(5L);
        RandomSource twin = RandomSource.create(5L);
        for (int i = 0; i < 10; i++) {
            assertTrue(rolls.roll(BossAbilityKind.HOOK, 100, random));
        }
        assertEquals(twin.nextInt(), random.nextInt(), "a hundred percent must not touch the random");

        // Roll readinesses until one comes up; it is then kept without a draw until the ability
        // is armed afresh, and the fresh readiness draws again.
        int kind = BossAbilityKind.LEAP;
        RandomSource fight = RandomSource.create(3L);
        RandomSource mirror = RandomSource.create(3L);
        boolean passed;
        do {
            boolean expected = mirror.nextInt(100) < 50;
            passed = rolls.roll(kind, 50, fight);
            assertEquals(expected, passed);
        } while (!passed);
        assertTrue(rolls.roll(kind, 50, fight), "the success is kept without a draw");
        rolls.rearmed(kind);
        assertEquals(mirror.nextInt(100) < 50, rolls.roll(kind, 50, fight), "a re-armed ability rolls afresh");
        assertEquals(mirror.nextInt(), fight.nextInt(), "every readiness drew exactly once");
    }

    @Test
    @DisplayName("casts count against the limit, repeats included, and a new phase counts from nought")
    void limitCountsCastsUntilThePhaseChanges() {
        BossCastRolls rolls = new BossCastRolls();
        int kind = BossAbilityKind.GEYSER;
        assertFalse(rolls.limitReached(kind, 2));
        rolls.started(kind);
        assertFalse(rolls.limitReached(kind, 2));
        rolls.started(kind);
        assertTrue(rolls.limitReached(kind, 2), "two casts of two leave the geyser silent");
        assertFalse(rolls.limitReached(kind, BossAbilityProfile.NO_CAST_LIMIT), "nought is no limit at all");
        assertFalse(rolls.limitReached(BossAbilityKind.HOOK, 2), "another ability keeps its own count");

        rolls.clearRolls();
        assertTrue(rolls.limitReached(kind, 2), "a lost target wipes the clocks, not the phase's count");
        rolls.resetPhase();
        assertEquals(0, rolls.casts(kind));
        assertFalse(rolls.limitReached(kind, 2), "the next phase counts from nought");
    }
}
