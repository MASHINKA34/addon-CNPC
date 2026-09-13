package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;

/** The totems' own clocks and box, and everything a hit that bounced off the boss sounds like. */
public final class SubGuiBossTuningTotems extends SubGuiBossTuningTopic {
    private static final int RETRY_FIELD = 1;
    private static final int LINK_DURATION_FIELD = 2;
    private static final int LINK_REFRESH_FIELD = 3;
    private static final int FIT_HEIGHT_FIELD = 4;
    private static final int FIT_HALF_WIDTH_FIELD = 5;
    private static final int REPORT_RANGE_FIELD = 6;
    private static final int HIT_SOUND_BUTTON = 7;
    private static final int HIT_PARTICLES_BUTTON = 8;
    private static final int LINK_SOUND_BUTTON = 9;
    private static final int BLOCKED_SOUND_BUTTON = 10;
    private static final int BLOCKED_PARTICLES_BUTTON = 11;

    public SubGuiBossTuningTotems(BossTuningSettings tuning) {
        super("cnpcgeckoaddon.boss.tuning.totems", tuning);
    }

    @Override
    protected int rows() {
        return 11;
    }

    @Override
    protected void addRows() {
        addNumberField(RETRY_FIELD, "cnpcgeckoaddon.boss.tuning.totem_retry", nextRow(),
                tuning.totemRetryIntervalTicks(), BossTuningSettings.MIN_RETRY,
                BossTuningSettings.MAX_RETRY, 20);
        addNumberField(LINK_DURATION_FIELD, "cnpcgeckoaddon.boss.tuning.totem_link_duration", nextRow(),
                tuning.totemLinkDurationTicks(), BossTuningSettings.MIN_TOTEM_LINK_DURATION,
                BossTuningSettings.MAX_TOTEM_LINK, 200);
        addNumberField(LINK_REFRESH_FIELD, "cnpcgeckoaddon.boss.tuning.totem_link_refresh", nextRow(),
                tuning.totemLinkRefreshTicks(), BossTuningSettings.MIN_TOTEM_LINK_REFRESH,
                BossTuningSettings.MAX_TOTEM_LINK, 160);
        addNumberField(FIT_HEIGHT_FIELD, "cnpcgeckoaddon.boss.tuning.totem_fit_height", nextRow(),
                tuning.totemFitHeightTenths(), BossTuningSettings.MIN_TOTEM_FIT_HEIGHT,
                BossTuningSettings.MAX_TOTEM_FIT_HEIGHT, 18);
        addNumberField(FIT_HALF_WIDTH_FIELD, "cnpcgeckoaddon.boss.tuning.totem_fit_half_width", nextRow(),
                tuning.totemFitHalfWidthTenths(), BossTuningSettings.MIN_TOTEM_FIT_HALF_WIDTH,
                BossTuningSettings.MAX_TOTEM_FIT_HALF_WIDTH, 3);
        addNumberField(REPORT_RANGE_FIELD, "cnpcgeckoaddon.boss.tuning.totem_report_range", nextRow(),
                (int) tuning.totemReportRange(), 0, BossTuningSettings.MAX_TOTEM_REPORT_RANGE, 48);
        addCueButton(HIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.totem_hit_sound", nextRow(),
                tuning.totemHitSound());
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tuning.totem_hit_particles", nextRow(),
                tuning.totemHitParticles());
        addCueButton(LINK_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.totem_link_sound", nextRow(),
                tuning.totemLinkSound());
        addCueButton(BLOCKED_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.blocked_hit_sound", nextRow(),
                tuning.blockedHitSound());
        addCueButton(BLOCKED_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tuning.blocked_hit_particles", nextRow(),
                tuning.blockedHitParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(RETRY_FIELD, tuning::setTotemRetryIntervalTicks);
        applyNumberField(LINK_DURATION_FIELD, tuning::setTotemLinkDurationTicks);
        applyNumberField(LINK_REFRESH_FIELD, tuning::setTotemLinkRefreshTicks);
        applyNumberField(FIT_HEIGHT_FIELD, tuning::setTotemFitHeightTenths);
        applyNumberField(FIT_HALF_WIDTH_FIELD, tuning::setTotemFitHalfWidthTenths);
        applyNumberField(REPORT_RANGE_FIELD, tuning::setTotemReportRange);
    }
}
