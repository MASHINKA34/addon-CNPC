package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three rage flags of an ability's profile against the enrage's own arithmetic: a flag left
 * on lets the multiplier through exactly as before profiles existed, a flag turned off hands the
 * number back as it stands however enraged the boss is.
 */
class BossAbilityRageTest {

    /** An enrage running at one and a half, the way the controller's doors apply it. */
    private static final double MULTIPLIER = 1.5D;
    private static final IntUnaryOperator UP = value -> BossRageRuntime.scaleUp(value, MULTIPLIER);
    private static final IntUnaryOperator DOWN = value -> BossRageRuntime.scaleDown(value, MULTIPLIER);

    @Test
    @DisplayName("flags turned off keep every number as it stands while the boss is enraged")
    void flagsOffKeepTheNumbers() {
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setRageCooldown(false);
        profile.setRageDamage(false);
        profile.setRageKnockback(false);
        for (int value : new int[]{0, 1, 3, 10, 200, 12000}) {
            assertEquals(value, profile.cooldownUnderRage(value, DOWN), "cooldown " + value);
            assertEquals(value, profile.damageUnderRage(value, UP), "damage " + value);
            assertEquals(value, profile.knockbackUnderRage(value, UP), "knockback " + value);
        }
    }

    @Test
    @DisplayName("the default flags let the enrage through exactly as before profiles")
    void defaultFlagsLetTheRageThrough() {
        BossAbilityProfile profile = new BossAbilityProfile();
        assertEquals(133, profile.cooldownUnderRage(200, DOWN), "200 / 1.5, rounded");
        assertEquals(15, profile.damageUnderRage(10, UP));
        assertEquals(5, profile.knockbackUnderRage(3, UP), "3 * 1.5 rounds up to 5");
        assertEquals(0, profile.knockbackUnderRage(0, UP), "the rage does not invent a knockback the ability never had");
        assertEquals(1, profile.cooldownUnderRage(1, DOWN), "a cooldown never drops under a tick");
    }

    @Test
    @DisplayName("each flag reaches only its own number")
    void flagsAreIndependent() {
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setRageDamage(false);
        assertEquals(10, profile.damageUnderRage(10, UP));
        assertEquals(15, profile.knockbackUnderRage(10, UP), "the knockback is still enraged");
        assertEquals(133, profile.cooldownUnderRage(200, DOWN), "and so is the cooldown");
    }
}
