package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossRangedAttackSettings;

/**
 * The ranged attack's own noise and puff at the boss' eyes on every shot of a cast, both off until
 * a builder turns them on: the projectile itself is CustomNPCs', and makes whatever noise it makes.
 */
public final class SubGuiBossRangedAttackTuning extends SubGuiBossAbilityTuning {
    private static final int SHOT_SOUND_BUTTON = 1;
    private static final int MUZZLE_PARTICLES_BUTTON = 2;

    private final BossRangedAttackSettings ranged;

    public SubGuiBossRangedAttackTuning(BossRangedAttackSettings ranged) {
        super("cnpcgeckoaddon.boss.ranged_tuning_title");
        this.ranged = ranged;
    }

    @Override
    protected int rows() {
        return 2;
    }

    @Override
    protected void addRows() {
        addCueButton(SHOT_SOUND_BUTTON, "cnpcgeckoaddon.boss.ranged_cue_shot", nextRow(), ranged.getShotSound());
        addCueButton(MUZZLE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.ranged_cue_muzzle", nextRow(),
                ranged.getMuzzleParticles());
    }
}
