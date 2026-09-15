package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The swap's arithmetic, checked without a world: which copy, when, whether, and what goes
 * across. None of it throws when it is wrong; a swap that lands the boss facing the wrong way,
 * or that goes off in the middle of a cast, simply gives the trick away.
 */
class BossShadowSwapTest {

    @Test
    @DisplayName("any of the copies may be picked, and one alone is picked without a roll")
    void everyCopyMayBePicked() {
        RandomSource random = RandomSource.create(82L);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            int picked = BossShadowSwap.pick(random, 4);
            assertTrue(picked >= 0 && picked < 4, "picked outside the copies: " + picked);
            seen.add(picked);
        }
        assertEquals(Set.of(0, 1, 2, 3), seen, "two hundred draws reach all four");
        assertEquals(0, BossShadowSwap.pick(random, 1));
        assertEquals(0, BossShadowSwap.pick(random, 0), "nothing to pick from is the first slot, never a roll of nought");
    }

    @Test
    @DisplayName("with the quiet rule on, both the boss and the copy have to be free")
    void theQuietRuleNeedsBoth() {
        assertTrue(BossShadowSwap.allowed(false, false, false), "off, a swap goes whatever either is doing");
        assertTrue(BossShadowSwap.allowed(true, true, true));
        assertFalse(BossShadowSwap.allowed(true, false, true), "the boss is mid cast");
        assertFalse(BossShadowSwap.allowed(true, true, false), "the copy is mid cast");
        assertFalse(BossShadowSwap.allowed(true, false, false));
    }

    @Test
    @DisplayName("a swap comes due one interval after the last, and never sooner")
    void theClockRunsByTheInterval() {
        long next = BossShadowSwap.nextSwapAt(100L, 100);
        assertEquals(200L, next);
        assertFalse(BossShadowSwap.due(199L, next));
        assertTrue(BossShadowSwap.due(200L, next));
        assertTrue(BossShadowSwap.due(500L, next), "a late tick is still due");
        assertEquals(101L, BossShadowSwap.nextSwapAt(100L, 0), "an interval of nothing is one tick, not this tick");
    }

    @Test
    @DisplayName("the two poses go across whole and the other way about")
    void posesAreTradedWhole() {
        BossShadowSwap.Pose boss = new BossShadowSwap.Pose(new Vec3(1.0D, 64.0D, 2.0D), 90.0F, 95.0F, 80.0F,
                -10.0F, new Vec3(0.1D, 0.0D, -0.2D), 1.5F);
        BossShadowSwap.Pose copy = new BossShadowSwap.Pose(new Vec3(-7.0D, 65.0D, 3.0D), 180.0F, 170.0F, 185.0F,
                5.0F, new Vec3(0.0D, 0.4D, 0.0D), 0.0F);
        BossShadowSwap.Pose[] swapped = BossShadowSwap.swapped(boss, copy);
        assertSame(copy, swapped[0], "the boss gets the copy's pose");
        assertSame(boss, swapped[1], "and the copy the boss'");
        // Nothing of a pose is dropped on the way: the record holds all seven and only those.
        assertEquals(new Vec3(-7.0D, 65.0D, 3.0D), swapped[0].position());
        assertEquals(180.0F, swapped[0].yaw());
        assertEquals(170.0F, swapped[0].headYaw());
        assertEquals(185.0F, swapped[0].bodyYaw());
        assertEquals(5.0F, swapped[0].pitch());
        assertEquals(new Vec3(0.0D, 0.4D, 0.0D), swapped[0].motion());
        assertEquals(0.0F, swapped[0].fallDistance());
        assertEquals(1.5F, swapped[1].fallDistance(), "the boss' fall goes to the copy, not to nobody");
    }
}
