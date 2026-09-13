package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossHazardSettings;

/** How far past the safe circle the arena burns, and how fast its warning edge flashes. */
public final class SubGuiBossHazardTuning extends SubGuiBossAbilityTuning {
    private static final int RING_REACH_FIELD = 1;
    private static final int BLINK_FIELD = 2;

    private final BossHazardSettings hazard;

    public SubGuiBossHazardTuning(BossHazardSettings hazard) {
        super("cnpcgeckoaddon.boss.hazard_tuning_title");
        this.hazard = hazard;
    }

    @Override
    protected int rows() {
        return 2;
    }

    @Override
    protected void addRows() {
        addNumberField(RING_REACH_FIELD, "cnpcgeckoaddon.boss.hazard_ring_reach", nextRow(),
                hazard.getRingReach(), BossHazardSettings.MIN_RING_REACH,
                BossHazardSettings.MAX_RING_REACH, 32);
        addNumberField(BLINK_FIELD, "cnpcgeckoaddon.boss.hazard_blink", nextRow(),
                hazard.getBlinkTicks(), BossHazardSettings.MIN_BLINK_TICKS,
                BossHazardSettings.MAX_BLINK_TICKS, 4);
    }

    @Override
    protected void applyFields() {
        applyNumberField(RING_REACH_FIELD, hazard::setRingReach);
        applyNumberField(BLINK_FIELD, hazard::setBlinkTicks);
    }
}
