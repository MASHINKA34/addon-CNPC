package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossGravitySettings;

/** How often the field doses and bites, how long it waits for a landing, how its wind blows, its noises. */
public final class SubGuiBossGravityTuning extends SubGuiBossAbilityTuning {
    private static final int EFFECT_INTERVAL_FIELD = 1;
    private static final int BITE_INTERVAL_FIELD = 2;
    private static final int VFX_FIELD = 3;
    private static final int PULL_SLACK_FIELD = 4;
    private static final int LANDING_TIMEOUT_FIELD = 5;
    private static final int STREAM_PARTICLES_FIELD = 6;
    private static final int STREAM_INNER_FIELD = 7;
    private static final int STREAM_HEIGHT_FIELD = 8;
    private static final int OPEN_SOUND_BUTTON = 9;
    private static final int PUSH_SOUND_BUTTON = 10;
    private static final int LAUNCH_SOUND_BUTTON = 11;
    private static final int LANDING_SOUND_BUTTON = 12;

    private final BossGravitySettings gravity;

    public SubGuiBossGravityTuning(BossGravitySettings gravity) {
        super("cnpcgeckoaddon.boss.gravity_tuning_title");
        this.gravity = gravity;
    }

    @Override
    protected int rows() {
        return 12;
    }

    @Override
    protected void addRows() {
        addNumberField(EFFECT_INTERVAL_FIELD, "cnpcgeckoaddon.boss.gravity_effect_interval", nextRow(),
                gravity.getEffectIntervalTicks(), BossGravitySettings.MIN_INTERVAL_TICKS,
                BossGravitySettings.MAX_INTERVAL_TICKS, 20);
        addNumberField(BITE_INTERVAL_FIELD, "cnpcgeckoaddon.boss.gravity_bite_interval", nextRow(),
                gravity.getBiteIntervalTicks(), BossGravitySettings.MIN_INTERVAL_TICKS,
                BossGravitySettings.MAX_INTERVAL_TICKS, 20);
        addNumberField(VFX_FIELD, "cnpcgeckoaddon.boss.gravity_vfx_ticks", nextRow(),
                gravity.getVfxTicks(), BossGravitySettings.MIN_INTERVAL_TICKS,
                BossGravitySettings.MAX_INTERVAL_TICKS, 20);
        addNumberField(PULL_SLACK_FIELD, "cnpcgeckoaddon.boss.gravity_pull_slack", nextRow(),
                gravity.getPullSlackTenths(), 0, BossGravitySettings.MAX_PULL_SLACK, 10);
        addNumberField(LANDING_TIMEOUT_FIELD, "cnpcgeckoaddon.boss.gravity_landing_timeout", nextRow(),
                gravity.getLandingTimeoutTicks(), BossGravitySettings.MIN_LANDING_TIMEOUT_TICKS,
                BossGravitySettings.MAX_LANDING_TIMEOUT_TICKS, 400);
        addNumberField(STREAM_PARTICLES_FIELD, "cnpcgeckoaddon.boss.gravity_stream_particles", nextRow(),
                gravity.getStreamParticles(), 0, BossGravitySettings.MAX_STREAM_PARTICLES, 3);
        addNumberField(STREAM_INNER_FIELD, "cnpcgeckoaddon.boss.gravity_stream_inner", nextRow(),
                gravity.getStreamInnerPercent(), 0, BossGravitySettings.MAX_STREAM_INNER_PERCENT, 35);
        addNumberField(STREAM_HEIGHT_FIELD, "cnpcgeckoaddon.boss.gravity_stream_height", nextRow(),
                gravity.getStreamHeightTenths(), BossGravitySettings.MIN_STREAM_HEIGHT,
                BossGravitySettings.MAX_STREAM_HEIGHT, 20);

        addCueButton(OPEN_SOUND_BUTTON, "cnpcgeckoaddon.boss.gravity_cue_open", nextRow(),
                gravity.getOpenSound());
        addCueButton(PUSH_SOUND_BUTTON, "cnpcgeckoaddon.boss.gravity_cue_push", nextRow(),
                gravity.getPushSound());
        addCueButton(LAUNCH_SOUND_BUTTON, "cnpcgeckoaddon.boss.gravity_cue_launch", nextRow(),
                gravity.getLaunchSound());
        addCueButton(LANDING_SOUND_BUTTON, "cnpcgeckoaddon.boss.gravity_cue_landing", nextRow(),
                gravity.getLandingSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(EFFECT_INTERVAL_FIELD, gravity::setEffectIntervalTicks);
        applyNumberField(BITE_INTERVAL_FIELD, gravity::setBiteIntervalTicks);
        applyNumberField(VFX_FIELD, gravity::setVfxTicks);
        applyNumberField(PULL_SLACK_FIELD, gravity::setPullSlackTenths);
        applyNumberField(LANDING_TIMEOUT_FIELD, gravity::setLandingTimeoutTicks);
        applyNumberField(STREAM_PARTICLES_FIELD, gravity::setStreamParticles);
        applyNumberField(STREAM_INNER_FIELD, gravity::setStreamInnerPercent);
        applyNumberField(STREAM_HEIGHT_FIELD, gravity::setStreamHeightTenths);
    }
}
