package com.goodbird.cnpcgeckoaddon.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What taking the copies back is worth, checked without a world: the health each one gives,
 * as the health link will share it, and the stacks with their cap and their clock.
 */
class BossShadowAbsorbTest {

    private static final float EPSILON = 1.0E-6F;

    @Test
    @DisplayName("each copy heals a share of the maximum, and the link shares exactly what was really healed")
    void healingIsAShareOfTheMaximum() {
        float amount = BossShadowAbsorb.healAmount(200.0F, 10);
        assertEquals(20.0F, amount, EPSILON);
        assertEquals(0.1F, BossHealthLinkRuntime.healedShare(amount, 150.0F, 200.0F), EPSILON,
                "with room to heal, the whole ten per cent goes to the partners");
        assertEquals(0.025F, BossHealthLinkRuntime.healedShare(amount, 195.0F, 200.0F), EPSILON,
                "near full, only what fits is shared");
        assertEquals(0.0F, BossHealthLinkRuntime.healedShare(amount, 200.0F, 200.0F), EPSILON);
        assertEquals(0.0F, BossShadowAbsorb.healAmount(200.0F, 0), EPSILON, "no share is no heal");
        assertEquals(0.0F, BossShadowAbsorb.healAmount(0.0F, 10), EPSILON);
    }

    @Test
    @DisplayName("the stacks stop at the cap and multiply by the per cent each")
    void stacksAreCapped() {
        BossShadowAbsorb.Stacks stacks = new BossShadowAbsorb.Stacks();
        assertEquals(1.0D, stacks.multiplier(0L), EPSILON, "none held is no bonus");
        assertEquals(10, stacks.scale(10, 0L));
        for (int i = 0; i < 6; i++) {
            stacks.add(100L, 15, 4, 600);
        }
        assertEquals(4, stacks.count(100L), "six copies, four stacks");
        assertEquals(1.6D, stacks.multiplier(100L), EPSILON);
        assertEquals(16, stacks.scale(10, 100L));
        assertEquals(24, stacks.scale(15, 100L), "on top of a number the rage already raised");
        assertEquals(0, stacks.scale(0, 100L), "a zero is none at all, not one");
        assertEquals(2, stacks.scale(1, 100L), "and a one is rounded like the rest, never down to none");
    }

    @Test
    @DisplayName("the stacks run out together, counted from the last one added")
    void stacksRunOutFromTheLastOne() {
        BossShadowAbsorb.Stacks stacks = new BossShadowAbsorb.Stacks();
        stacks.add(100L, 15, 4, 600);
        stacks.add(400L, 15, 4, 600);
        assertEquals(2, stacks.count(999L), "the second stack restarted the clock");
        assertEquals(1L, stacks.ticksLeft(999L));
        assertEquals(0, stacks.count(1000L), "and at the end both go at once");
        assertEquals(1.0D, stacks.multiplier(1000L), EPSILON);
        assertEquals(10, stacks.scale(10, 1000L));
        stacks.add(1000L, 20, 4, 100);
        assertEquals(1, stacks.count(1050L), "a fresh stack after the end starts a fresh clock");
        assertEquals(1.2D, stacks.multiplier(1050L), EPSILON, "at the per cent it was added with");
        stacks.clear();
        assertEquals(0, stacks.count(1050L));
        assertEquals(0L, stacks.ticksLeft(1050L));
    }
}
