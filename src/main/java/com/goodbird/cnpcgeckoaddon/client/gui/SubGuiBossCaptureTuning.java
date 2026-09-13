package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossCaptureSettings;

/** The noise and the puff the grab lands with. */
public final class SubGuiBossCaptureTuning extends SubGuiBossAbilityTuning {
    private static final int CAPTURE_SOUND_BUTTON = 1;
    private static final int CAPTURE_PARTICLES_BUTTON = 2;

    private final BossCaptureSettings capture;

    public SubGuiBossCaptureTuning(BossCaptureSettings capture) {
        super("cnpcgeckoaddon.boss.capture_tuning_title");
        this.capture = capture;
    }

    @Override
    protected int rows() {
        return 2;
    }

    @Override
    protected void addRows() {
        addCueButton(CAPTURE_SOUND_BUTTON, "cnpcgeckoaddon.boss.capture_cue_capture", nextRow(),
                capture.getCaptureSound());
        addCueButton(CAPTURE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.capture_cue_capture", nextRow(),
                capture.getCaptureParticles());
    }
}
