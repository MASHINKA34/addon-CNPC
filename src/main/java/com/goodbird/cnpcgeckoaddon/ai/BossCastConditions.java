package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;

/**
 * What has to be true for an ability to go, by its profile: how far off the combat target is,
 * how many players are in the fight, and where the boss' health stands.
 *
 * <p>Plain predicates over numbers the controller reads off the world, so they can be pinned
 * without one. Each one at its default lets everything through, which is the rotation as it was
 * before profiles had conditions.</p>
 */
final class BossCastConditions {

    /** What {@link #distanceAllows} is handed when the boss has no combat target to measure from. */
    static final double NO_TARGET = -1.0D;

    private BossCastConditions() {
    }

    /** All three at once: the one question the rotation asks. */
    static boolean allow(BossAbilityProfile profile, double targetDistance, int participants,
                         float health, float maxHealth) {
        return distanceAllows(profile, targetDistance) && playersAllow(profile, participants)
                && healthAllows(profile, health, maxHealth);
    }

    /**
     * Whether the combat target is no nearer than the profile's least distance and no farther
     * than its most, a nought on either end leaving that end open. With no target - a boss that
     * fights without one, or one between targets - the rule has nothing to measure and lets the
     * ability through.
     */
    static boolean distanceAllows(BossAbilityProfile profile, double targetDistance) {
        if (targetDistance < 0.0D) {
            return true;
        }
        int least = profile.getMinTargetDistance();
        int most = profile.getMaxTargetDistance();
        return (least <= 0 || targetDistance >= least) && (most <= 0 || targetDistance <= most);
    }

    /** Whether at least as many players are in the fight as the profile asks for; nought asks for none. */
    static boolean playersAllow(BossAbilityProfile profile, int participants) {
        return profile.getMinPlayers() <= 0 || participants >= profile.getMinPlayers();
    }

    /**
     * Whether the boss' health, as a percentage of its maximum, sits inside the profile's window,
     * both ends included. The full window lets everything through without dividing anything, and
     * so does a boss with no maximum to divide by.
     */
    static boolean healthAllows(BossAbilityProfile profile, float health, float maxHealth) {
        int bottom = profile.getHealthMinPercent();
        int top = profile.getHealthMaxPercent();
        if (bottom <= 0 && top >= 100 || maxHealth <= 0.0F) {
            return true;
        }
        double percent = health * 100.0D / maxHealth;
        return percent >= bottom && percent <= top;
    }
}
