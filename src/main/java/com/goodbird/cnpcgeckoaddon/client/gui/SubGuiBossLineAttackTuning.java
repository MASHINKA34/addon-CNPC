package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossLineAttackSettings;

/** The line strike's own roar as it goes off, and the puff at everyone in its lane; both off at first. */
public final class SubGuiBossLineAttackTuning extends SubGuiBossAbilityTuning {
    private static final int SWEEP_SOUND_BUTTON = 1;
    private static final int HIT_PARTICLES_BUTTON = 2;

    private final BossLineAttackSettings line;

    public SubGuiBossLineAttackTuning(BossLineAttackSettings line) {
        super("cnpcgeckoaddon.boss.line_tuning_title");
        this.line = line;
    }

    @Override
    protected int rows() {
        return 2;
    }

    @Override
    protected void addRows() {
        addCueButton(SWEEP_SOUND_BUTTON, "cnpcgeckoaddon.boss.line_cue_sweep", nextRow(), line.getSweepSound());
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.line_cue_hit_particles", nextRow(),
                line.getHitParticles());
    }
}
