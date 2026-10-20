package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossMeleeAttackSettings;

/** The swing's own whoosh as it goes off, and the crack and the puff at whoever it lands on; all off at first. */
public final class SubGuiBossMeleeAttackTuning extends SubGuiBossAbilityTuning {
    private static final int SWING_SOUND_BUTTON = 1;
    private static final int HIT_SOUND_BUTTON = 2;
    private static final int HIT_PARTICLES_BUTTON = 3;

    private final BossMeleeAttackSettings melee;

    public SubGuiBossMeleeAttackTuning(BossMeleeAttackSettings melee) {
        super("cnpcgeckoaddon.boss.melee_tuning_title");
        this.melee = melee;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void addRows() {
        addCueButton(SWING_SOUND_BUTTON, "cnpcgeckoaddon.boss.melee_cue_swing", nextRow(), melee.getSwingSound());
        addCueButton(HIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.melee_cue_hit", nextRow(), melee.getHitSound());
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.melee_cue_hit_particles", nextRow(),
                melee.getHitParticles());
    }
}
