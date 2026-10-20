package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic an ability's profile does to its hit: shares of the victim's health on top of
 * the ability's own number, the party's bonus over all of it, its ceiling, and what a profile
 * left alone does - nothing.
 */
class BossDamageProfileMathTest {

    private static final double EPSILON = 1.0E-9D;

    @Test
    @DisplayName("a profile left alone hits for exactly the ability's own number")
    void defaultsKeepTheNumber() {
        BossAbilityProfile profile = new BossAbilityProfile();
        for (int damage : new int[]{0, 1, 8, 1000}) {
            for (int players : new int[]{0, 1, 2, 30}) {
                assertEquals(damage, total(damage, profile, 20.0D, 7.0D, players), EPSILON,
                        damage + " with " + players + " players");
            }
        }
    }

    @Test
    @DisplayName("shares of the victim's maximum and remaining health go on top of the number")
    void healthSharesAreAdded() {
        // Ten per cent of a full twenty is two; half of the ten left is five.
        assertEquals(10.0D, BossDamageProfileMath.total(8, 10, 0, 20.0D, 20.0D, 1, 0, 200), EPSILON);
        assertEquals(13.0D, BossDamageProfileMath.total(8, 0, 50, 20.0D, 10.0D, 1, 0, 200), EPSILON);
        assertEquals(15.0D, BossDamageProfileMath.total(8, 10, 50, 20.0D, 10.0D, 1, 0, 200), EPSILON);
        // A share alone is a hit too: an ability set to nothing but a percentage still lands.
        assertEquals(2.0D, BossDamageProfileMath.total(0, 10, 0, 20.0D, 20.0D, 1, 0, 200), EPSILON);
    }

    @Test
    @DisplayName("every player past the first adds the party share, up to the ceiling")
    void thePartyRaisesTheHitUpToItsCeiling() {
        assertEquals(20.0D, BossDamageProfileMath.total(10, 0, 0, 20.0D, 20.0D, 3, 50, 200), EPSILON,
                "fifty per cent each for two more players doubles the hit");
        assertEquals(15.0D, BossDamageProfileMath.total(10, 0, 0, 20.0D, 20.0D, 3, 50, 50), EPSILON,
                "a ceiling of fifty stops it at one and a half");
        assertEquals(10.0D, BossDamageProfileMath.total(10, 0, 0, 20.0D, 20.0D, 1, 50, 200), EPSILON,
                "one player is nobody past the first");
        assertEquals(10.0D, BossDamageProfileMath.total(10, 0, 0, 20.0D, 20.0D, 0, 50, 200), EPSILON,
                "a fight nobody has joined yet takes nothing off");
        assertEquals(10.0D, BossDamageProfileMath.total(10, 0, 0, 20.0D, 20.0D, 3, 50, 0), EPSILON,
                "a ceiling of nought keeps the party out of it");
        // The party multiplies the shares of health as well as the number.
        assertEquals(30.0D, BossDamageProfileMath.total(10, 25, 0, 20.0D, 20.0D, 3, 50, 200), EPSILON);
    }

    @Test
    @DisplayName("nothing to hit for stays nothing, however big the party")
    void noughtIsNoHit() {
        assertEquals(0.0D, BossDamageProfileMath.total(0, 0, 0, 20.0D, 20.0D, 5, 100, 1000), EPSILON);
        assertEquals(0.0D, BossDamageProfileMath.total(0, 50, 50, 0.0D, 0.0D, 5, 100, 1000), EPSILON,
                "a share of no health is no hit");
        assertTrue(BossDamageProfileMath.total(0, 0, 0, 20.0D, 20.0D, 5, 100, 1000) <= 0.0D,
                "the door lands no hit for this, so no invulnerability frames are spent on it");
    }

    @Test
    @DisplayName("fractions are kept: nothing is rounded to whole points")
    void fractionsAreKept() {
        assertEquals(2.5D, BossDamageProfileMath.total(1, 10, 0, 15.0D, 15.0D, 1, 0, 200), EPSILON,
                "a tenth of fifteen is a point and a half, not one or two");
        assertEquals(0.35D, BossDamageProfileMath.total(0, 0, 1, 35.0D, 35.0D, 1, 0, 200), EPSILON);
        assertEquals(11.0D, BossDamageProfileMath.total(10, 0, 0, 20.0D, 20.0D, 2, 10, 200), EPSILON);
        assertEquals(10.5D * 1.33D, BossDamageProfileMath.total(10, 5, 0, 10.0D, 10.0D, 4, 11, 200), EPSILON);
    }

    private static double total(int damage, BossAbilityProfile profile, double maxHealth, double health,
                                int participants) {
        return BossDamageProfileMath.total(damage, profile.getDamagePercentMax(), profile.getDamagePercentCurrent(),
                maxHealth, health, participants, profile.getPartyDamagePercent(), profile.getPartyDamageCapPercent());
    }
}
