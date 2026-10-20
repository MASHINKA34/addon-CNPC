package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conditions an ability's profile can put on its casts, as the plain predicates the
 * rotation asks them through: the combat target's distance, the players in the fight, and the
 * boss' health window. Each at its default has to let everything through, or every boss saved
 * before profiles would start holding its abilities back.
 */
class BossCastConditionsTest {

    @Test
    @DisplayName("a default profile lets every distance, party and health through")
    void defaultsLetEverythingThrough() {
        BossAbilityProfile profile = new BossAbilityProfile();
        for (double distance : new double[]{BossCastConditions.NO_TARGET, 0.0D, 0.5D, 5.0D, 127.0D, 500.0D}) {
            assertTrue(BossCastConditions.distanceAllows(profile, distance), "distance " + distance);
        }
        for (int players = 0; players <= 70; players++) {
            assertTrue(BossCastConditions.playersAllow(profile, players), players + " players");
        }
        for (float health : new float[]{0.0F, 0.1F, 50.0F, 99.9F, 100.0F}) {
            assertTrue(BossCastConditions.healthAllows(profile, health, 100.0F), "health " + health);
        }
        assertTrue(BossCastConditions.allow(profile, 3.0D, 1, 20.0F, 100.0F));
    }

    @Test
    @DisplayName("the distance window holds both ends, and leaves an end open at nought")
    void distanceWindow() {
        BossAbilityProfile near = new BossAbilityProfile();
        near.setMaxTargetDistance(5);
        assertTrue(BossCastConditions.distanceAllows(near, 0.0D));
        assertTrue(BossCastConditions.distanceAllows(near, 5.0D), "the far end is inside the window");
        assertFalse(BossCastConditions.distanceAllows(near, 5.01D));
        assertFalse(BossCastConditions.distanceAllows(near, 40.0D));

        BossAbilityProfile far = new BossAbilityProfile();
        far.setMinTargetDistance(8);
        assertFalse(BossCastConditions.distanceAllows(far, 7.99D));
        assertTrue(BossCastConditions.distanceAllows(far, 8.0D), "the near end is inside the window");
        assertTrue(BossCastConditions.distanceAllows(far, 120.0D));

        BossAbilityProfile band = new BossAbilityProfile();
        band.setMinTargetDistance(3);
        band.setMaxTargetDistance(6);
        assertFalse(BossCastConditions.distanceAllows(band, 2.0D));
        assertTrue(BossCastConditions.distanceAllows(band, 4.5D));
        assertFalse(BossCastConditions.distanceAllows(band, 6.5D));
    }

    @Test
    @DisplayName("with no combat target the distance rule has nothing to measure and lets the cast through")
    void noTargetIgnoresTheDistance() {
        BossAbilityProfile band = new BossAbilityProfile();
        band.setMinTargetDistance(3);
        band.setMaxTargetDistance(6);
        assertTrue(BossCastConditions.distanceAllows(band, BossCastConditions.NO_TARGET));
    }

    @Test
    @DisplayName("a player minimum keeps a solo fight from casting")
    void playerMinimum() {
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setMinPlayers(2);
        assertFalse(BossCastConditions.playersAllow(profile, 0));
        assertFalse(BossCastConditions.playersAllow(profile, 1), "one player is a solo fight");
        assertTrue(BossCastConditions.playersAllow(profile, 2));
        assertTrue(BossCastConditions.playersAllow(profile, 9));
    }

    @Test
    @DisplayName("the health window holds both ends and silences the ability outside them")
    void healthWindow() {
        BossAbilityProfile upperHalf = new BossAbilityProfile();
        upperHalf.setHealthMinPercent(50);
        assertTrue(BossCastConditions.healthAllows(upperHalf, 200.0F, 200.0F));
        assertTrue(BossCastConditions.healthAllows(upperHalf, 100.0F, 200.0F), "exactly half is inside 50..100");
        assertFalse(BossCastConditions.healthAllows(upperHalf, 99.0F, 200.0F), "under half is silent");

        BossAbilityProfile lowerHalf = new BossAbilityProfile();
        lowerHalf.setHealthMaxPercent(50);
        assertTrue(BossCastConditions.healthAllows(lowerHalf, 10.0F, 100.0F));
        assertFalse(BossCastConditions.healthAllows(lowerHalf, 60.0F, 100.0F));

        BossAbilityProfile closed = new BossAbilityProfile();
        closed.setHealthMinPercent(70);
        closed.setHealthMaxPercent(30);
        assertTrue(BossCastConditions.healthAllows(closed, 70.0F, 100.0F), "a window closed to its bottom still opens there");
        assertFalse(BossCastConditions.healthAllows(closed, 69.0F, 100.0F));
        assertFalse(BossCastConditions.healthAllows(closed, 71.0F, 100.0F));

        assertTrue(BossCastConditions.healthAllows(upperHalf, 0.0F, 0.0F),
                "a boss with no maximum health has nothing to measure against");
    }

    @Test
    @DisplayName("all three conditions have to hold at once")
    void everyConditionHasToHold() {
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setMaxTargetDistance(5);
        profile.setMinPlayers(2);
        profile.setHealthMinPercent(50);
        assertTrue(BossCastConditions.allow(profile, 4.0D, 2, 80.0F, 100.0F));
        assertFalse(BossCastConditions.allow(profile, 9.0D, 2, 80.0F, 100.0F), "the target is too far");
        assertFalse(BossCastConditions.allow(profile, 4.0D, 1, 80.0F, 100.0F), "the fight is solo");
        assertFalse(BossCastConditions.allow(profile, 4.0D, 2, 40.0F, 100.0F), "the boss is under half");
    }
}
