package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossBeamSettings;

/** Where the lines sit, how far down they still catch, what they spark with, and their start. */
public final class SubGuiBossBeamTuning extends SubGuiBossAbilityTuning {
    private static final int MAX_HEIGHT_FIELD = 1;
    private static final int VICTIM_SLACK_FIELD = 2;
    private static final int WALL_SPARKS_FIELD = 3;
    private static final int ACCENT_FIELD = 4;
    private static final int RARE_ACCENT_FIELD = 5;
    private static final int START_SOUND_BUTTON = 6;

    private final BossBeamSettings beam;

    public SubGuiBossBeamTuning(BossBeamSettings beam) {
        super("cnpcgeckoaddon.boss.beam_tuning_title");
        this.beam = beam;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void addRows() {
        addNumberField(MAX_HEIGHT_FIELD, "cnpcgeckoaddon.boss.beam_max_height", nextRow(),
                beam.getMaxHeightTenths(), BossBeamSettings.MIN_HEIGHT,
                BossBeamSettings.MAX_HEIGHT, 10);
        addNumberField(VICTIM_SLACK_FIELD, "cnpcgeckoaddon.boss.beam_victim_slack", nextRow(),
                beam.getVictimSlackTenths(), 0, BossBeamSettings.MAX_VICTIM_SLACK, 20);
        addNumberField(WALL_SPARKS_FIELD, "cnpcgeckoaddon.boss.beam_wall_sparks", nextRow(),
                beam.getWallSparks(), 0, BossBeamSettings.MAX_WALL_SPARKS, 2);
        addNumberField(ACCENT_FIELD, "cnpcgeckoaddon.boss.beam_accent", nextRow(),
                beam.getAccentOneIn(), BossBeamSettings.MIN_ACCENT, BossBeamSettings.MAX_ACCENT, 6);
        addNumberField(RARE_ACCENT_FIELD, "cnpcgeckoaddon.boss.beam_rare_accent", nextRow(),
                beam.getRareAccentOneIn(), BossBeamSettings.MIN_ACCENT,
                BossBeamSettings.MAX_RARE_ACCENT, 12);

        addCueButton(START_SOUND_BUTTON, "cnpcgeckoaddon.boss.beam_cue_start", nextRow(),
                beam.getStartSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(MAX_HEIGHT_FIELD, beam::setMaxHeightTenths);
        applyNumberField(VICTIM_SLACK_FIELD, beam::setVictimSlackTenths);
        applyNumberField(WALL_SPARKS_FIELD, beam::setWallSparks);
        applyNumberField(ACCENT_FIELD, beam::setAccentOneIn);
        applyNumberField(RARE_ACCENT_FIELD, beam::setRareAccentOneIn);
    }
}
