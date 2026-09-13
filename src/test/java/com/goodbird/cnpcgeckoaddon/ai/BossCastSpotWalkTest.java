package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pace a boss walks to its cast spot at.
 *
 * <p>The percentage is new and the formula under it is not, so the thing worth pinning is
 * that a spot nobody has edited still asks for exactly the speed it always did - whatever the
 * npc's own walking speed happens to be, the ends included.</p>
 */
class BossCastSpotWalkTest {

    private static final double EPSILON = 1.0E-9D;

    /** Precisely what the walk was handed before the percentage existed. */
    private static double yesterday(double walkingSpeed) {
        return Mth.clamp(walkingSpeed / 5.0D, 0.5D, 2.0D);
    }

    @Test
    @DisplayName("a hundred per cent is exactly the pace the walk has always been given")
    void aHundredPercentChangesNothing() {
        for (double walking : new double[]{0.0D, 1.0D, 2.5D, 5.0D, 10.0D, 40.0D}) {
            assertEquals(yesterday(walking), BossCastSpotRuntime.walkSpeed(walking, 100), EPSILON,
                    "a walking speed of " + walking + " must be untouched at a hundred per cent");
        }
    }

    @Test
    @DisplayName("the percentage can actually run, which the old ends would not have allowed")
    void thePercentageReachesPastTheOldCeiling() {
        assertEquals(3.0D, BossCastSpotRuntime.walkSpeed(5.0D, 300), EPSILON,
                "three hundred per cent of an ordinary walk is a run");
        assertEquals(5.0D, BossCastSpotRuntime.walkSpeed(10.0D, 400), EPSILON,
                "and the widened ceiling is five");
        assertEquals(0.1D, BossCastSpotRuntime.walkSpeed(0.0D, 10), EPSILON,
                "a tenth is as slow as a crawl gets");
    }

    @Test
    @DisplayName("more per cent is never slower")
    void theSpeedIsMonotonic() {
        double last = 0.0D;
        for (int percent = 10; percent <= 400; percent += 10) {
            double speed = BossCastSpotRuntime.walkSpeed(5.0D, percent);
            assertTrue(speed >= last, "asking for " + percent + "%% walked slower than less");
            last = speed;
        }
    }
}
