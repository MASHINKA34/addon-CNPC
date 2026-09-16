package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;

/**
 * How long the eruption's wave runs, how tall its column comes up and what shape, how fast
 * it rises and out of what, how its fuse boils, and its noises.
 */
public final class SubGuiBossGeyserTuning extends SubGuiBossAbilityTuning {
    private static final int VFX_FIELD = 1;
    private static final int COLUMN_PER_RADIUS_FIELD = 2;
    private static final int COLUMN_MIN_FIELD = 3;
    private static final int COLUMN_MAX_FIELD = 4;
    private static final int BOIL_MIN_FIELD = 5;
    private static final int BOIL_MAX_FIELD = 6;
    private static final int LIT_SOUND_BUTTON = 7;
    private static final int ERUPT_SOUND_BUTTON = 8;
    private static final int COLUMN_SHAPE_BUTTON = 9;
    private static final int COLUMN_TOP_FIELD = 10;
    private static final int COLUMN_RISE_FIELD = 11;
    private static final int COLUMN_POINTS_FIELD = 12;
    private static final int COLUMN_PARTICLES_BUTTON = 13;
    private static final int COLUMN_SMOKE_BUTTON = 14;

    /** The shape's choice reads as a sentence, so its button takes most of the row. */
    private static final int SHAPE_BUTTON_X = 80;
    private static final int SHAPE_BUTTON_WIDTH = 162;

    private final BossGeyserSettings geyser;

    public SubGuiBossGeyserTuning(BossGeyserSettings geyser) {
        super("cnpcgeckoaddon.boss.geyser_tuning_title");
        this.geyser = geyser;
    }

    @Override
    protected int rows() {
        return 14;
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

        addCycle(COLUMN_SHAPE_BUTTON, "cnpcgeckoaddon.boss.geyser_column_shape", nextRow(),
                BossGeyserSettings.COLUMN_SHAPE_LABELS, geyser.getColumnShape());
        addNumberField(COLUMN_TOP_FIELD, "cnpcgeckoaddon.boss.geyser_column_top", nextRow(),
                geyser.getColumnTopRadiusTenths(), 0, BossGeyserSettings.MAX_COLUMN_TOP_RADIUS, 5);
        addNumberField(COLUMN_RISE_FIELD, "cnpcgeckoaddon.boss.geyser_column_rise", nextRow(),
                geyser.getColumnRiseTicks(), 0, BossGeyserSettings.MAX_COLUMN_RISE_TICKS, 0);
        addNumberField(COLUMN_POINTS_FIELD, "cnpcgeckoaddon.boss.geyser_column_points", nextRow(),
                geyser.getColumnPointsPerSlice(), BossGeyserSettings.MIN_COLUMN_POINTS,
                BossGeyserSettings.MAX_COLUMN_POINTS, 6);
        addCueButton(COLUMN_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_column", nextRow(),
                geyser.getColumnParticles());
        addCueButton(COLUMN_SMOKE_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_column_smoke", nextRow(),
                geyser.getColumnSmoke());

        addCueButton(LIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_lit", nextRow(),
                geyser.getLitSound());
        addCueButton(ERUPT_SOUND_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_erupt", nextRow(),
                geyser.getEruptSound());
    }

    @Override
    protected int cycleButtonX() {
        return SHAPE_BUTTON_X;
    }

    @Override
    protected int cycleButtonWidth() {
        return SHAPE_BUTTON_WIDTH;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == COLUMN_SHAPE_BUTTON) {
            geyser.setColumnShape(button.getValue());
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(VFX_FIELD, geyser::setVfxTicks);
        applyNumberField(COLUMN_PER_RADIUS_FIELD, geyser::setColumnPerRadiusTenths);
        applyNumberField(COLUMN_MIN_FIELD, geyser::setColumnMinTenths);
        applyNumberField(COLUMN_MAX_FIELD, geyser::setColumnMaxTenths);
        applyNumberField(BOIL_MIN_FIELD, geyser::setBoilMinHundredths);
        applyNumberField(BOIL_MAX_FIELD, geyser::setBoilMaxHundredths);
        applyNumberField(COLUMN_TOP_FIELD, geyser::setColumnTopRadiusTenths);
        applyNumberField(COLUMN_RISE_FIELD, geyser::setColumnRiseTicks);
        applyNumberField(COLUMN_POINTS_FIELD, geyser::setColumnPointsPerSlice);
    }
}
