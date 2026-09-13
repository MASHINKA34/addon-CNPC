package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossDashSettings;

/** The slacks the run is judged by, and the five noises it makes. */
public final class SubGuiBossDashTuning extends SubGuiBossAbilityTuning {
    private static final int MIN_REACH_FIELD = 1;
    private static final int WALL_SHARE_FIELD = 2;
    private static final int CONTACT_SLICE_FIELD = 3;
    private static final int MAX_STEER_FIELD = 4;
    private static final int SWEEP_SLACK_FIELD = 5;
    private static final int TELEPORT_SLACK_FIELD = 6;
    private static final int CHAIN_HEIGHT_FIELD = 7;
    private static final int SLAM_VFX_FIELD = 8;
    private static final int START_SOUND_BUTTON = 9;
    private static final int START_PARTICLES_BUTTON = 10;
    private static final int HIT_SOUND_BUTTON = 11;
    private static final int SLAM_SOUND_BUTTON = 12;
    private static final int SLAM_PARTICLES_BUTTON = 13;
    private static final int CHAIN_SOUND_BUTTON = 14;
    private static final int CHAIN_PARTICLES_BUTTON = 15;
    private static final int WALL_SOUND_BUTTON = 16;

    private final BossDashSettings dash;

    public SubGuiBossDashTuning(BossDashSettings dash) {
        super("cnpcgeckoaddon.boss.dash_tuning_title");
        this.dash = dash;
    }

    @Override
    protected int rows() {
        return 16;
    }

    @Override
    protected void addRows() {
        addNumberField(MIN_REACH_FIELD, "cnpcgeckoaddon.boss.dash_min_reach", nextRow(),
                dash.getMinReachTenths(), 0, BossDashSettings.MAX_MIN_REACH, 10);
        addNumberField(WALL_SHARE_FIELD, "cnpcgeckoaddon.boss.dash_wall_share", nextRow(),
                dash.getWallSharePercent(), BossDashSettings.MIN_WALL_SHARE,
                BossDashSettings.MAX_WALL_SHARE, 25);
        addNumberField(CONTACT_SLICE_FIELD, "cnpcgeckoaddon.boss.dash_contact_slice", nextRow(),
                dash.getContactSliceTenths(), BossDashSettings.MIN_CONTACT_SLICE,
                BossDashSettings.MAX_CONTACT_SLICE, 4);
        addNumberField(MAX_STEER_FIELD, "cnpcgeckoaddon.boss.dash_max_steer", nextRow(),
                dash.getMaxSteerTenths(), 0, BossDashSettings.MAX_STEER, 5);
        addNumberField(SWEEP_SLACK_FIELD, "cnpcgeckoaddon.boss.dash_sweep_slack", nextRow(),
                dash.getSweepSlackTenths(), 0, BossDashSettings.MAX_SWEEP_SLACK, 10);
        addNumberField(TELEPORT_SLACK_FIELD, "cnpcgeckoaddon.boss.dash_teleport_slack", nextRow(),
                dash.getTeleportSlackTenths(), BossDashSettings.MIN_TELEPORT_SLACK,
                BossDashSettings.MAX_TELEPORT_SLACK, 20);
        addNumberField(CHAIN_HEIGHT_FIELD, "cnpcgeckoaddon.boss.dash_chain_height_slack", nextRow(),
                dash.getChainHeightSlackTenths(), 0, BossDashSettings.MAX_CHAIN_HEIGHT_SLACK, 5);
        addNumberField(SLAM_VFX_FIELD, "cnpcgeckoaddon.boss.dash_slam_vfx_ticks", nextRow(),
                dash.getSlamVfxTicks(), BossDashSettings.MIN_SLAM_VFX_TICKS,
                BossDashSettings.MAX_SLAM_VFX_TICKS, 20);

        addCueButton(START_SOUND_BUTTON, "cnpcgeckoaddon.boss.dash_cue_start", nextRow(), dash.getStartSound());
        addCueButton(START_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.dash_cue_start", nextRow(),
                dash.getStartParticles());
        addCueButton(HIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.dash_cue_hit", nextRow(), dash.getHitSound());
        addCueButton(SLAM_SOUND_BUTTON, "cnpcgeckoaddon.boss.dash_cue_slam", nextRow(), dash.getSlamSound());
        addCueButton(SLAM_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.dash_cue_slam", nextRow(),
                dash.getSlamParticles());
        addCueButton(CHAIN_SOUND_BUTTON, "cnpcgeckoaddon.boss.dash_cue_chain", nextRow(), dash.getChainSound());
        addCueButton(CHAIN_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.dash_cue_chain", nextRow(),
                dash.getChainParticles());
        addCueButton(WALL_SOUND_BUTTON, "cnpcgeckoaddon.boss.dash_cue_wall", nextRow(), dash.getWallSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(MIN_REACH_FIELD, dash::setMinReachTenths);
        applyNumberField(WALL_SHARE_FIELD, dash::setWallSharePercent);
        applyNumberField(CONTACT_SLICE_FIELD, dash::setContactSliceTenths);
        applyNumberField(MAX_STEER_FIELD, dash::setMaxSteerTenths);
        applyNumberField(SWEEP_SLACK_FIELD, dash::setSweepSlackTenths);
        applyNumberField(TELEPORT_SLACK_FIELD, dash::setTeleportSlackTenths);
        applyNumberField(CHAIN_HEIGHT_FIELD, dash::setChainHeightSlackTenths);
        applyNumberField(SLAM_VFX_FIELD, dash::setSlamVfxTicks);
    }
}
