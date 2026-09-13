package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossBoulderRainSettings;

/** How small a mark still counts, how low a roof still allows a drop, and the mark's own noise. */
public final class SubGuiBossBoulderRainTuning extends SubGuiBossAbilityTuning {
    private static final int MIN_MARK_FIELD = 1;
    private static final int MIN_DROP_FIELD = 2;
    private static final int MARK_SOUND_BUTTON = 3;

    private final BossBoulderRainSettings rain;

    public SubGuiBossBoulderRainTuning(BossBoulderRainSettings rain) {
        super("cnpcgeckoaddon.boss.boulder_rain_tuning_title");
        this.rain = rain;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void addRows() {
        addNumberField(MIN_MARK_FIELD, "cnpcgeckoaddon.boss.boulder_rain_min_mark", nextRow(),
                rain.getMinMarkRadiusTenths(), BossBoulderRainSettings.MIN_MARK,
                BossBoulderRainSettings.MAX_MARK, 10);
        addNumberField(MIN_DROP_FIELD, "cnpcgeckoaddon.boss.boulder_rain_min_drop", nextRow(),
                rain.getMinDropTenths(), 0, BossBoulderRainSettings.MAX_MIN_DROP, 10);

        addCueButton(MARK_SOUND_BUTTON, "cnpcgeckoaddon.boss.boulder_rain_cue_mark", nextRow(),
                rain.getMarkSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(MIN_MARK_FIELD, rain::setMinMarkRadiusTenths);
        applyNumberField(MIN_DROP_FIELD, rain::setMinDropTenths);
    }
}
