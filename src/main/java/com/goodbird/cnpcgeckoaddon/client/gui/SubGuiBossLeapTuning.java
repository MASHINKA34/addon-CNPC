package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossLeapSettings;

/** What the flight is driven by, how far inside the leash it lands, and the noises it makes. */
public final class SubGuiBossLeapTuning extends SubGuiBossAbilityTuning {
    private static final int REACH_PERCENT_FIELD = 1;
    private static final int MAX_SPEED_FIELD = 2;
    private static final int LAUNCH_GRACE_FIELD = 3;
    private static final int VFX_TICKS_FIELD = 4;
    private static final int LEASH_MARGIN_FIELD = 5;
    private static final int DEBRIS_FIELD = 6;
    private static final int TAKEOFF_SOUND_BUTTON = 7;
    private static final int TAKEOFF_PARTICLES_BUTTON = 8;
    private static final int LANDING_SOUND_BUTTON = 9;
    private static final int LANDING_PARTICLES_BUTTON = 10;
    private static final int TRAIL_PARTICLES_BUTTON = 11;

    private final BossLeapSettings leap;

    public SubGuiBossLeapTuning(BossLeapSettings leap) {
        super("cnpcgeckoaddon.boss.leap_tuning_title");
        this.leap = leap;
    }

    @Override
    protected int rows() {
        return 11;
    }

    @Override
    protected void addRows() {
        addNumberField(REACH_PERCENT_FIELD, "cnpcgeckoaddon.boss.leap_reach_percent", nextRow(),
                leap.getReachPercent(), BossLeapSettings.MIN_REACH_PERCENT,
                BossLeapSettings.MAX_REACH_PERCENT, 103);
        addNumberField(MAX_SPEED_FIELD, "cnpcgeckoaddon.boss.leap_max_speed", nextRow(),
                leap.getMaxSpeedTenths(), BossLeapSettings.MIN_MAX_SPEED,
                BossLeapSettings.MAX_MAX_SPEED, 40);
        addNumberField(LAUNCH_GRACE_FIELD, "cnpcgeckoaddon.boss.leap_launch_grace", nextRow(),
                leap.getLaunchGraceTicks(), BossLeapSettings.MIN_LAUNCH_GRACE,
                BossLeapSettings.MAX_LAUNCH_GRACE, 5);
        addNumberField(VFX_TICKS_FIELD, "cnpcgeckoaddon.boss.leap_vfx_ticks", nextRow(),
                leap.getVfxTicks(), BossLeapSettings.MIN_VFX_TICKS, BossLeapSettings.MAX_VFX_TICKS, 20);
        addNumberField(LEASH_MARGIN_FIELD, "cnpcgeckoaddon.boss.leap_leash_margin", nextRow(),
                leap.getLeashMarginTenths(), 0, BossLeapSettings.MAX_LEASH_MARGIN, 15);
        addNumberField(DEBRIS_FIELD, "cnpcgeckoaddon.boss.leap_debris", nextRow(),
                leap.getDebrisCount(), 0, BossLeapSettings.MAX_DEBRIS, 30);

        addCueButton(TAKEOFF_SOUND_BUTTON, "cnpcgeckoaddon.boss.leap_cue_takeoff", nextRow(),
                leap.getTakeoffSound());
        addCueButton(TAKEOFF_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.leap_cue_takeoff", nextRow(),
                leap.getTakeoffParticles());
        addCueButton(LANDING_SOUND_BUTTON, "cnpcgeckoaddon.boss.leap_cue_landing", nextRow(),
                leap.getLandingSound());
        addCueButton(LANDING_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.leap_cue_landing", nextRow(),
                leap.getLandingParticles());
        addCueButton(TRAIL_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.leap_cue_trail", nextRow(),
                leap.getTrailParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(REACH_PERCENT_FIELD, leap::setReachPercent);
        applyNumberField(MAX_SPEED_FIELD, leap::setMaxSpeedTenths);
        applyNumberField(LAUNCH_GRACE_FIELD, leap::setLaunchGraceTicks);
        applyNumberField(VFX_TICKS_FIELD, leap::setVfxTicks);
        applyNumberField(LEASH_MARGIN_FIELD, leap::setLeashMarginTenths);
        applyNumberField(DEBRIS_FIELD, leap::setDebrisCount);
    }
}
