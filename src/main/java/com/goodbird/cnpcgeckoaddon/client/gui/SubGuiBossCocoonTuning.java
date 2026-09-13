package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossCocoonSettings;

/** How often the shell doses and calls out, how far it reaches, where its guard stands, its noises. */
public final class SubGuiBossCocoonTuning extends SubGuiBossAbilityTuning {
    private static final int EFFECT_INTERVAL_FIELD = 1;
    private static final int ANNOUNCE_INTERVAL_FIELD = 2;
    private static final int ANNOUNCE_RANGE_FIELD = 3;
    private static final int REACH_FIELD = 4;
    private static final int GUARD_DISTANCE_FIELD = 5;
    private static final int WRAP_SOUND_BUTTON = 6;
    private static final int WRAP_PARTICLES_BUTTON = 7;
    private static final int FREED_SOUND_BUTTON = 8;
    private static final int FREED_PARTICLES_BUTTON = 9;
    private static final int TIMEOUT_SOUND_BUTTON = 10;
    private static final int TIMEOUT_PARTICLES_BUTTON = 11;

    private final BossCocoonSettings cocoon;

    public SubGuiBossCocoonTuning(BossCocoonSettings cocoon) {
        super("cnpcgeckoaddon.boss.cocoon_tuning_title");
        this.cocoon = cocoon;
    }

    @Override
    protected int rows() {
        return 11;
    }

    @Override
    protected void addRows() {
        addNumberField(EFFECT_INTERVAL_FIELD, "cnpcgeckoaddon.boss.cocoon_effect_interval", nextRow(),
                cocoon.getEffectIntervalTicks(), BossCocoonSettings.MIN_INTERVAL_TICKS,
                BossCocoonSettings.MAX_INTERVAL_TICKS, 20);
        addNumberField(ANNOUNCE_INTERVAL_FIELD, "cnpcgeckoaddon.boss.cocoon_announce_interval", nextRow(),
                cocoon.getAnnounceIntervalTicks(), BossCocoonSettings.MIN_INTERVAL_TICKS,
                BossCocoonSettings.MAX_INTERVAL_TICKS, 10);
        addNumberField(ANNOUNCE_RANGE_FIELD, "cnpcgeckoaddon.boss.cocoon_announce_range", nextRow(),
                cocoon.getAnnounceRange(), 0, BossCocoonSettings.MAX_ANNOUNCE_RANGE, 12);
        addNumberField(REACH_FIELD, "cnpcgeckoaddon.boss.cocoon_reach", nextRow(),
                cocoon.getReach(), BossCocoonSettings.MIN_REACH, BossCocoonSettings.MAX_REACH, 32);
        addNumberField(GUARD_DISTANCE_FIELD, "cnpcgeckoaddon.boss.cocoon_guard_distance", nextRow(),
                cocoon.getGuardDistanceTenths(), 0, BossCocoonSettings.MAX_GUARD_DISTANCE, 20);

        addCueButton(WRAP_SOUND_BUTTON, "cnpcgeckoaddon.boss.cocoon_cue_wrap", nextRow(),
                cocoon.getWrapSound());
        addCueButton(WRAP_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.cocoon_cue_wrap", nextRow(),
                cocoon.getWrapParticles());
        addCueButton(FREED_SOUND_BUTTON, "cnpcgeckoaddon.boss.cocoon_cue_freed", nextRow(),
                cocoon.getFreedSound());
        addCueButton(FREED_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.cocoon_cue_freed", nextRow(),
                cocoon.getFreedParticles());
        addCueButton(TIMEOUT_SOUND_BUTTON, "cnpcgeckoaddon.boss.cocoon_cue_timeout", nextRow(),
                cocoon.getTimeoutSound());
        addCueButton(TIMEOUT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.cocoon_cue_timeout", nextRow(),
                cocoon.getTimeoutParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(EFFECT_INTERVAL_FIELD, cocoon::setEffectIntervalTicks);
        applyNumberField(ANNOUNCE_INTERVAL_FIELD, cocoon::setAnnounceIntervalTicks);
        applyNumberField(ANNOUNCE_RANGE_FIELD, cocoon::setAnnounceRange);
        applyNumberField(REACH_FIELD, cocoon::setReach);
        applyNumberField(GUARD_DISTANCE_FIELD, cocoon::setGuardDistanceTenths);
    }
}
