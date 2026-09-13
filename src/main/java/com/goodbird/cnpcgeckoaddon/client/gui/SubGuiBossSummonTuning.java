package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossSummonSettings;

/** How much room a minion needs to stand, and how big a hole the fallback ring leaves. */
public final class SubGuiBossSummonTuning extends SubGuiBossAbilityTuning {
    private static final int FIT_HALF_WIDTH_FIELD = 1;
    private static final int FIT_HEIGHT_FIELD = 2;
    private static final int RING_INNER_FIELD = 3;

    private final BossSummonSettings summon;

    public SubGuiBossSummonTuning(BossSummonSettings summon) {
        super("cnpcgeckoaddon.boss.summon_tuning_title");
        this.summon = summon;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void addRows() {
        addNumberField(FIT_HALF_WIDTH_FIELD, "cnpcgeckoaddon.boss.summon_fit_half_width", nextRow(),
                summon.getFitHalfWidthHundredths(), BossSummonSettings.MIN_FIT_HALF_WIDTH,
                BossSummonSettings.MAX_FIT_HALF_WIDTH, 35);
        addNumberField(FIT_HEIGHT_FIELD, "cnpcgeckoaddon.boss.summon_fit_height", nextRow(),
                summon.getFitHeightTenths(), BossSummonSettings.MIN_FIT_HEIGHT,
                BossSummonSettings.MAX_FIT_HEIGHT, 18);
        addNumberField(RING_INNER_FIELD, "cnpcgeckoaddon.boss.summon_ring_inner", nextRow(),
                summon.getRingInnerRadiusTenths(), 0, BossSummonSettings.MAX_RING_INNER_RADIUS, 10);
    }

    @Override
    protected void applyFields() {
        applyNumberField(FIT_HALF_WIDTH_FIELD, summon::setFitHalfWidthHundredths);
        applyNumberField(FIT_HEIGHT_FIELD, summon::setFitHeightTenths);
        applyNumberField(RING_INNER_FIELD, summon::setRingInnerRadiusTenths);
    }
}
