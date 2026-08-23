package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.util.Mth;

/**
 * How a single boss ability picks who it goes after.
 *
 * <p>Each attack chooses independently, which is what lets a boss keep hitting the tank in
 * front of it while the same boss fires its ranged attack at whoever is hiding in the back.
 * Candidates are always restricted to targets the ability could actually reach, so
 * {@link #FARTHEST} means "the farthest one still inside this attack's range", not a player
 * on the other side of the world.</p>
 */
public final class BossTargetMode {
    /** Keep using the NPC's own combat target - the behaviour from before this setting existed. */
    public static final int MAIN = 0;
    public static final int NEAREST = 1;
    public static final int FARTHEST = 2;
    public static final int RANDOM = 3;

    public static final String[] LABELS = {
            "cnpcgeckoaddon.boss.target_mode_main",
            "cnpcgeckoaddon.boss.target_mode_nearest",
            "cnpcgeckoaddon.boss.target_mode_farthest",
            "cnpcgeckoaddon.boss.target_mode_random"
    };

    private BossTargetMode() {
    }

    public static int clamp(int mode) {
        return Mth.clamp(mode, MAIN, RANDOM);
    }
}
