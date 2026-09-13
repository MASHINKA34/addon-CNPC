package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;

/** How long a chained follow-up is owed, how long it is retried for, and how long a chain runs. */
public final class SubGuiBossTuningCombos extends SubGuiBossTuningTopic {
    private static final int STALE_FIELD = 1;
    private static final int RETRY_WINDOW_FIELD = 2;
    private static final int MAX_LINKS_FIELD = 3;

    public SubGuiBossTuningCombos(BossTuningSettings tuning) {
        super("cnpcgeckoaddon.boss.tuning.combos", tuning);
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void addRows() {
        addNumberField(STALE_FIELD, "cnpcgeckoaddon.boss.tuning.combo_stale", nextRow(),
                tuning.comboStaleTicks(), 0, BossTuningSettings.MAX_COMBO_STALE, 200);
        addNumberField(RETRY_WINDOW_FIELD, "cnpcgeckoaddon.boss.tuning.combo_retry_window", nextRow(),
                tuning.comboRetryWindowTicks(), 0, BossTuningSettings.MAX_COMBO_RETRY_WINDOW, 60);
        addNumberField(MAX_LINKS_FIELD, "cnpcgeckoaddon.boss.tuning.combo_max_links", nextRow(),
                tuning.comboMaxLinks(), BossTuningSettings.MIN_COMBO_LINKS,
                BossTuningSettings.COMBO_LINK_CEILING, 24);
    }

    @Override
    protected void applyFields() {
        applyNumberField(STALE_FIELD, tuning::setComboStaleTicks);
        applyNumberField(RETRY_WINDOW_FIELD, tuning::setComboRetryWindowTicks);
        applyNumberField(MAX_LINKS_FIELD, tuning::setComboMaxLinks);
    }
}
