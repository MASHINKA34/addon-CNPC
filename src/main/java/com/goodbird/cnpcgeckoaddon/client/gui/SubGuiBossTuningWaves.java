package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;

/** How a wave travels and how long it lasts, and the shout each of its looks goes out with. */
public final class SubGuiBossTuningWaves extends SubGuiBossTuningTopic {
    private static final int CORRIDOR_SPEED_FIELD = 1;
    private static final int MIN_TICKS_FIELD = 2;
    private static final int MAX_TICKS_FIELD = 3;
    private static final int FLOOR_DEPTH_FIELD = 4;
    private static final int BLOCK_LIFETIME_FIELD = 5;
    private static final int FIRST_STYLE_BUTTON = 6;

    public SubGuiBossTuningWaves(BossTuningSettings tuning) {
        super("cnpcgeckoaddon.boss.tuning.waves", tuning);
    }

    @Override
    protected int rows() {
        // Five numbers, then one button per style that makes a noise at all.
        return 5 + AreaVfxStyles.values().size() - 1;
    }

    @Override
    protected void addRows() {
        addNumberField(CORRIDOR_SPEED_FIELD, "cnpcgeckoaddon.boss.tuning.corridor_speed", nextRow(),
                tuning.waveCorridorSpeedTenths(), BossTuningSettings.MIN_WAVE_CORRIDOR_SPEED,
                BossTuningSettings.MAX_WAVE_CORRIDOR_SPEED, 5);
        addNumberField(MIN_TICKS_FIELD, "cnpcgeckoaddon.boss.tuning.wave_min", nextRow(),
                tuning.waveMinTicks(), BossTuningSettings.MIN_WAVE_TICKS,
                BossTuningSettings.MAX_WAVE_TICKS, 10);
        addNumberField(MAX_TICKS_FIELD, "cnpcgeckoaddon.boss.tuning.wave_max", nextRow(),
                tuning.waveMaxTicks(), BossTuningSettings.MIN_WAVE_TICKS,
                BossTuningSettings.MAX_WAVE_TICKS, 60);
        addNumberField(FLOOR_DEPTH_FIELD, "cnpcgeckoaddon.boss.tuning.floor_depth", nextRow(),
                tuning.waveFloorSearchDepth(), BossTuningSettings.MIN_WAVE_FLOOR_DEPTH,
                BossTuningSettings.MAX_WAVE_FLOOR_DEPTH, 4);
        addNumberField(BLOCK_LIFETIME_FIELD, "cnpcgeckoaddon.boss.tuning.block_lifetime", nextRow(),
                tuning.waveBlockLifetimeTicks(), BossTuningSettings.MIN_WAVE_BLOCK_LIFETIME,
                BossTuningSettings.MAX_WAVE_BLOCK_LIFETIME, 40);

        // Named by the style itself, so a look added later brings its own label along.
        int id = FIRST_STYLE_BUTTON;
        for (AreaVfxStyles.Style style : AreaVfxStyles.values()) {
            if (tuning.waveSound(style.id()) == null) {
                continue;
            }
            addCueButton(id++, style.translationKey(), nextRow(), tuning.waveSound(style.id()));
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(CORRIDOR_SPEED_FIELD, tuning::setWaveCorridorSpeedTenths);
        applyNumberField(MIN_TICKS_FIELD, tuning::setWaveMinTicks);
        applyNumberField(MAX_TICKS_FIELD, tuning::setWaveMaxTicks);
        applyNumberField(FLOOR_DEPTH_FIELD, tuning::setWaveFloorSearchDepth);
        applyNumberField(BLOCK_LIFETIME_FIELD, tuning::setWaveBlockLifetimeTicks);
    }
}
