package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossHookSettings;

/** How much the yank lifts its victim, and what the cord is heard and drawn as. */
public final class SubGuiBossHookTuning extends SubGuiBossAbilityTuning {
    private static final int LIFT_MAX_FIELD = 1;
    private static final int LIFT_PER_BLOCK_FIELD = 2;
    private static final int CORD_SOUND_BUTTON = 3;
    private static final int CORD_PARTICLES_BUTTON = 4;

    private final BossHookSettings hook;

    public SubGuiBossHookTuning(BossHookSettings hook) {
        super("cnpcgeckoaddon.boss.hook_tuning_title");
        this.hook = hook;
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void addRows() {
        addNumberField(LIFT_MAX_FIELD, "cnpcgeckoaddon.boss.hook_lift_max", nextRow(),
                hook.getLiftMaxHundredths(), 0, BossHookSettings.MAX_LIFT, 35);
        addNumberField(LIFT_PER_BLOCK_FIELD, "cnpcgeckoaddon.boss.hook_lift_per_block", nextRow(),
                hook.getLiftPerBlockHundredths(), 0, BossHookSettings.MAX_LIFT_PER_BLOCK, 3);

        addCueButton(CORD_SOUND_BUTTON, "cnpcgeckoaddon.boss.hook_cue_cord", nextRow(),
                hook.getCordSound());
        addCueButton(CORD_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.hook_cue_cord_particles", nextRow(),
                hook.getCordParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(LIFT_MAX_FIELD, hook::setLiftMaxHundredths);
        applyNumberField(LIFT_PER_BLOCK_FIELD, hook::setLiftPerBlockHundredths);
    }
}
