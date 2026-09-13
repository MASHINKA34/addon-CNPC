package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossFluidSpitSettings;

/** The arc the glob leaves on, what it does in the air, and the noise the boss makes throwing it. */
public final class SubGuiBossFluidSpitTuning extends SubGuiBossAbilityTuning {
    private static final int ARC_LIFT_FIELD = 1;
    private static final int VELOCITY_FIELD = 2;
    private static final int INACCURACY_FIELD = 3;
    private static final int GRAVITY_FIELD = 4;
    private static final int LIFE_FIELD = 5;
    private static final int SPLASH_BASE_FIELD = 6;
    private static final int SPLASH_PER_RADIUS_FIELD = 7;
    private static final int SPIT_SOUND_BUTTON = 8;

    private final BossFluidSpitSettings spit;

    public SubGuiBossFluidSpitTuning(BossFluidSpitSettings spit) {
        super("cnpcgeckoaddon.boss.fluid_tuning_title");
        this.spit = spit;
    }

    @Override
    protected int rows() {
        return 8;
    }

    @Override
    protected void addRows() {
        addNumberField(ARC_LIFT_FIELD, "cnpcgeckoaddon.boss.fluid_arc_lift", nextRow(),
                spit.getArcLiftHundredths(), 0, BossFluidSpitSettings.MAX_ARC_LIFT, 20);
        addNumberField(VELOCITY_FIELD, "cnpcgeckoaddon.boss.fluid_velocity", nextRow(),
                spit.getVelocityTenths(), BossFluidSpitSettings.MIN_VELOCITY,
                BossFluidSpitSettings.MAX_VELOCITY, 12);
        addNumberField(INACCURACY_FIELD, "cnpcgeckoaddon.boss.fluid_inaccuracy", nextRow(),
                spit.getInaccuracyTenths(), 0, BossFluidSpitSettings.MAX_INACCURACY, 40);
        addNumberField(GRAVITY_FIELD, "cnpcgeckoaddon.boss.fluid_gravity", nextRow(),
                spit.getGravityThousandths(), 0, BossFluidSpitSettings.MAX_GRAVITY, 50);
        addNumberField(LIFE_FIELD, "cnpcgeckoaddon.boss.fluid_projectile_life", nextRow(),
                spit.getProjectileLifeTicks(), BossFluidSpitSettings.MIN_PROJECTILE_LIFE,
                BossFluidSpitSettings.MAX_PROJECTILE_LIFE, 200);
        addNumberField(SPLASH_BASE_FIELD, "cnpcgeckoaddon.boss.fluid_splash_base", nextRow(),
                spit.getSplashBase(), 0, BossFluidSpitSettings.MAX_SPLASH_BASE, 12);
        addNumberField(SPLASH_PER_RADIUS_FIELD, "cnpcgeckoaddon.boss.fluid_splash_per_radius", nextRow(),
                spit.getSplashPerRadius(), 0, BossFluidSpitSettings.MAX_SPLASH_PER_RADIUS, 8);

        addCueButton(SPIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.fluid_cue_spit", nextRow(),
                spit.getSpitSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(ARC_LIFT_FIELD, spit::setArcLiftHundredths);
        applyNumberField(VELOCITY_FIELD, spit::setVelocityTenths);
        applyNumberField(INACCURACY_FIELD, spit::setInaccuracyTenths);
        applyNumberField(GRAVITY_FIELD, spit::setGravityThousandths);
        applyNumberField(LIFE_FIELD, spit::setProjectileLifeTicks);
        applyNumberField(SPLASH_BASE_FIELD, spit::setSplashBase);
        applyNumberField(SPLASH_PER_RADIUS_FIELD, spit::setSplashPerRadius);
    }
}
