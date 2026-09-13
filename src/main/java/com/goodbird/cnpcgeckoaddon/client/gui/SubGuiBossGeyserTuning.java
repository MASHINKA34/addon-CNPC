package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;

/** How long the eruption's wave runs, how tall its column comes up, how its fuse boils, and its noises. */
public final class SubGuiBossGeyserTuning extends SubGuiBossAbilityTuning {
    private static final int VFX_FIELD = 1;
    private static final int COLUMN_PER_RADIUS_FIELD = 2;
    private static final int COLUMN_MIN_FIELD = 3;
    private static final int COLUMN_MAX_FIELD = 4;
    private static final int BOIL_MIN_FIELD = 5;
    private static final int BOIL_MAX_FIELD = 6;
    private static final int LIT_SOUND_BUTTON = 7;
    private static final int ERUPT_SOUND_BUTTON = 8;

    private final BossGeyserSettings geyser;

    public SubGuiBossGeyserTuning(BossGeyserSettings geyser) {
        super("cnpcgeckoaddon.boss.geyser_tuning_title");
        this.geyser = geyser;
    }

    @Override
    protected int rows() {
        return 8;
    }

    @Override
    protected void addRows() {
        addNumberField(VFX_FIELD, "cnpcgeckoaddon.boss.geyser_vfx_ticks", nextRow(),
                geyser.getVfxTicks(), BossGeyserSettings.MIN_VFX_TICKS,
                BossGeyserSettings.MAX_VFX_TICKS, 20);
        addNumberField(COLUMN_PER_RADIUS_FIELD, "cnpcgeckoaddon.boss.geyser_column_per_radius", nextRow(),
                geyser.getColumnPerRadiusTenths(), 0, BossGeyserSettings.MAX_COLUMN_PER_RADIUS, 15);
        addNumberField(COLUMN_MIN_FIELD, "cnpcgeckoaddon.boss.geyser_column_min", nextRow(),
                geyser.getColumnMinTenths(), 0, BossGeyserSettings.MAX_COLUMN_HEIGHT, 30);
        addNumberField(COLUMN_MAX_FIELD, "cnpcgeckoaddon.boss.geyser_column_max", nextRow(),
                geyser.getColumnMaxTenths(), 0, BossGeyserSettings.MAX_COLUMN_HEIGHT, 120);
        addNumberField(BOIL_MIN_FIELD, "cnpcgeckoaddon.boss.geyser_boil_min", nextRow(),
                geyser.getBoilMinHundredths(), 0, BossGeyserSettings.MAX_BOIL_SPEED, 2);
        addNumberField(BOIL_MAX_FIELD, "cnpcgeckoaddon.boss.geyser_boil_max", nextRow(),
                geyser.getBoilMaxHundredths(), 0, BossGeyserSettings.MAX_BOIL_SPEED, 12);

        addCueButton(LIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_lit", nextRow(),
                geyser.getLitSound());
        addCueButton(ERUPT_SOUND_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_erupt", nextRow(),
                geyser.getEruptSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(VFX_FIELD, geyser::setVfxTicks);
        applyNumberField(COLUMN_PER_RADIUS_FIELD, geyser::setColumnPerRadiusTenths);
        applyNumberField(COLUMN_MIN_FIELD, geyser::setColumnMinTenths);
        applyNumberField(COLUMN_MAX_FIELD, geyser::setColumnMaxTenths);
        applyNumberField(BOIL_MIN_FIELD, geyser::setBoilMinHundredths);
        applyNumberField(BOIL_MAX_FIELD, geyser::setBoilMaxHundredths);
    }
}
