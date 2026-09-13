package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossBoulderSettings;

/** How the stone rolls, how long it lives, how it breaks, and the two noises it makes. */
public final class SubGuiBossBoulderTuning extends SubGuiBossAbilityTuning {
    private static final int STEP_HEIGHT_FIELD = 1;
    private static final int FALL_SPEED_FIELD = 2;
    private static final int PIT_DEPTH_FIELD = 3;
    private static final int THROW_GRAVITY_FIELD = 4;
    private static final int LIFETIME_MARGIN_FIELD = 5;
    private static final int LIFETIME_MAX_FIELD = 6;
    private static final int DEBRIS_BASE_FIELD = 7;
    private static final int DEBRIS_PER_SIZE_FIELD = 8;
    private static final int SHATTER_VFX_FIELD = 9;
    private static final int MUZZLE_FIELD = 10;
    private static final int THROW_SOUND_BUTTON = 11;
    private static final int BREAK_SOUND_BUTTON = 12;

    private final BossBoulderSettings boulder;

    public SubGuiBossBoulderTuning(BossBoulderSettings boulder) {
        super("cnpcgeckoaddon.boss.boulder_tuning_title");
        this.boulder = boulder;
    }

    @Override
    protected int rows() {
        return 12;
    }

    @Override
    protected void addRows() {
        addNumberField(STEP_HEIGHT_FIELD, "cnpcgeckoaddon.boss.boulder_step_height", nextRow(),
                boulder.getStepHeightTenths(), 0, BossBoulderSettings.MAX_STEP_HEIGHT, 10);
        addNumberField(FALL_SPEED_FIELD, "cnpcgeckoaddon.boss.boulder_max_fall_speed", nextRow(),
                boulder.getMaxFallSpeedTenths(), BossBoulderSettings.MIN_FALL_SPEED,
                BossBoulderSettings.MAX_FALL_SPEED, 15);
        addNumberField(PIT_DEPTH_FIELD, "cnpcgeckoaddon.boss.boulder_max_pit_depth", nextRow(),
                boulder.getMaxPitDepth(), BossBoulderSettings.MIN_PIT_DEPTH,
                BossBoulderSettings.MAX_PIT_DEPTH, 16);
        addNumberField(THROW_GRAVITY_FIELD, "cnpcgeckoaddon.boss.boulder_throw_gravity", nextRow(),
                boulder.getThrowGravityThousandths(), BossBoulderSettings.MIN_THROW_GRAVITY,
                BossBoulderSettings.MAX_THROW_GRAVITY, 50);
        addNumberField(LIFETIME_MARGIN_FIELD, "cnpcgeckoaddon.boss.boulder_lifetime_margin", nextRow(),
                boulder.getLifetimeMarginTicks(), 0, BossBoulderSettings.MAX_LIFETIME_MARGIN, 60);
        addNumberField(LIFETIME_MAX_FIELD, "cnpcgeckoaddon.boss.boulder_lifetime_max", nextRow(),
                boulder.getLifetimeMaxTicks(), BossBoulderSettings.MIN_LIFETIME_MAX,
                BossBoulderSettings.MAX_LIFETIME_MAX, 1500);
        addNumberField(DEBRIS_BASE_FIELD, "cnpcgeckoaddon.boss.boulder_debris_base", nextRow(),
                boulder.getDebrisBase(), 0, BossBoulderSettings.MAX_DEBRIS, 20);
        addNumberField(DEBRIS_PER_SIZE_FIELD, "cnpcgeckoaddon.boss.boulder_debris_per_size", nextRow(),
                boulder.getDebrisPerSize(), 0, BossBoulderSettings.MAX_DEBRIS, 15);
        addNumberField(SHATTER_VFX_FIELD, "cnpcgeckoaddon.boss.boulder_shatter_vfx", nextRow(),
                boulder.getShatterVfxTicks(), BossBoulderSettings.MIN_SHATTER_VFX_TICKS,
                BossBoulderSettings.MAX_SHATTER_VFX_TICKS, 20);
        addNumberField(MUZZLE_FIELD, "cnpcgeckoaddon.boss.boulder_muzzle", nextRow(),
                boulder.getMuzzleOffsetHundredths(), 0, BossBoulderSettings.MAX_MUZZLE_OFFSET, 25);

        addCueButton(THROW_SOUND_BUTTON, "cnpcgeckoaddon.boss.boulder_cue_throw", nextRow(),
                boulder.getThrowSound());
        addCueButton(BREAK_SOUND_BUTTON, "cnpcgeckoaddon.boss.boulder_cue_break", nextRow(),
                boulder.getBreakSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(STEP_HEIGHT_FIELD, boulder::setStepHeightTenths);
        applyNumberField(FALL_SPEED_FIELD, boulder::setMaxFallSpeedTenths);
        applyNumberField(PIT_DEPTH_FIELD, boulder::setMaxPitDepth);
        applyNumberField(THROW_GRAVITY_FIELD, boulder::setThrowGravityThousandths);
        applyNumberField(LIFETIME_MARGIN_FIELD, boulder::setLifetimeMarginTicks);
        applyNumberField(LIFETIME_MAX_FIELD, boulder::setLifetimeMaxTicks);
        applyNumberField(DEBRIS_BASE_FIELD, boulder::setDebrisBase);
        applyNumberField(DEBRIS_PER_SIZE_FIELD, boulder::setDebrisPerSize);
        applyNumberField(SHATTER_VFX_FIELD, boulder::setShatterVfxTicks);
        applyNumberField(MUZZLE_FIELD, boulder::setMuzzleOffsetHundredths);
    }
}
