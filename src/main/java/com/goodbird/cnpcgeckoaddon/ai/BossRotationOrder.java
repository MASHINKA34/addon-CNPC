package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import net.minecraft.util.Mth;

/**
 * The order the rotation looks at its rows in on one tick.
 *
 * <p>Highest priority first; inside one priority, round from the moving start the way the
 * rotation always went, so a short cooldown still cannot starve whatever stands behind it. With
 * every priority at nought - every boss saved before profiles - that is exactly the old walk:
 * the start, the row after it, and so on round.</p>
 */
final class BossRotationOrder {

    private BossRotationOrder() {
    }

    /**
     * The rows to look at, best first.
     *
     * @param count      how many rows the rotation has
     * @param start      the row the round starts from: the one after the last that started
     * @param priorities each row's priority, by row; anything outside 0..9 is read as the nearer end
     */
    static int[] order(int count, int start, int[] priorities) {
        int[] order = new int[count];
        if (count <= 0) {
            return order;
        }
        int first = Math.floorMod(start, count);
        int next = 0;
        for (int priority = BossAbilityProfile.MAX_PRIORITY; priority >= 0; priority--) {
            for (int offset = 0; offset < count; offset++) {
                int index = (first + offset) % count;
                if (Mth.clamp(priorities[index], 0, BossAbilityProfile.MAX_PRIORITY) == priority) {
                    order[next++] = index;
                }
            }
        }
        return order;
    }
}
