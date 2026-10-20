package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossAreaAttackSettings;

/** The ground attack's own noise and puff at every victim, both off until a builder turns them on. */
public final class SubGuiBossAreaAttackTuning extends SubGuiBossAbilityTuning {
    private static final int HIT_SOUND_BUTTON = 1;
    private static final int HIT_PARTICLES_BUTTON = 2;

    private final BossAreaAttackSettings area;

    public SubGuiBossAreaAttackTuning(BossAreaAttackSettings area) {
        super("cnpcgeckoaddon.boss.area_tuning_title");
        this.area = area;
    }

    @Override
    protected int rows() {
        return 2;
    }

    @Override
    protected void addRows() {
        addCueButton(HIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.area_cue_hit", nextRow(), area.getHitSound());
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.area_cue_hit_particles", nextRow(),
                area.getHitParticles());
    }
}
