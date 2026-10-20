package com.goodbird.cnpcgeckoaddon.ai;

/**
 * What one ability hit comes to once its profile has had its say: the ability's own number, a
 * share of the victim's maximum and of its remaining health on top, and the whole of it raised
 * for every player past the first in the fight, up to the profile's ceiling.
 *
 * <p>Kept apart from the damage door so the arithmetic can be pinned without a world: the door
 * only gathers the numbers and hands the result to vanilla. Nothing is rounded - vanilla takes
 * a fractional hit as it is, and a tenth of an odd health is half a point, not a whole one or
 * none.</p>
 */
final class BossDamageProfileMath {

    private BossDamageProfileMath() {
    }

    /**
     * The hit, in health points.
     *
     * @param damage       the ability's own number, with whatever the enrage adds already in it
     * @param pctMax       per cent of {@code maxHealth} added on top
     * @param pctCurrent   per cent of {@code health} added on top
     * @param participants players in the fight; nought or one adds nothing
     * @param partyPct     per cent added for each of them past the first
     * @param cap          the most, in per cent, the party may add
     * @return what to hurt the victim for; nought or less means no hit at all
     */
    static double total(int damage, int pctMax, int pctCurrent, double maxHealth, double health,
                        int participants, int partyPct, int cap) {
        double base = damage + maxHealth * pctMax / 100.0D + health * pctCurrent / 100.0D;
        // A fight nobody has joined yet is a party of one, not of minus one.
        int extra = Math.max(0, participants - 1);
        double bonus = Math.min(Math.max(0, cap), (double) extra * Math.max(0, partyPct));
        return base * (1.0D + bonus / 100.0D);
    }
}
