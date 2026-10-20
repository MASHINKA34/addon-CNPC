package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rotation's bookkeeping without a world: how a cooldown is spread. Whether anything is
 * due, and what a starter makes of it, is the controller's business; here a number is drawn
 * from a seeded random and has to land where the profile says it may.
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
}
