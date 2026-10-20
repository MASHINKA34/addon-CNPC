package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.util.Mth;

/**
 * Where an ability that acts round a point puts that point: on the boss, as every one of them
 * did before this setting existed, or where a target stood the moment the cast began.
 *
 * <p>A target is picked the way the aimed abilities pick theirs, by a {@link BossTargetMode}
 * within the boss' target search radius; with nobody there the cast goes off round the boss
 * rather than not at all.</p>
 */
public final class BossCentreMode {
    /** Round the boss: the behaviour from before this setting existed. */
    public static final int BOSS = 0;
    /** Round the spot a target stood on when the cast began, fixed from then on. */
    public static final int TARGET = 1;

    private BossCentreMode() {
    }

    public static int clamp(int mode) {
        return Mth.clamp(mode, BOSS, TARGET);
    }
}
