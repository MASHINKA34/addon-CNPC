package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTetherSettings;

/** How often the leash doses, how much slack its drag gives, how its beam hangs, its noises. */
public final class SubGuiBossTetherTuning extends SubGuiBossAbilityTuning {
    private static final int EFFECT_INTERVAL_FIELD = 1;
    private static final int PULL_SLACK_FIELD = 2;
    private static final int BEAM_SAG_FIELD = 3;
    private static final int PULL_PER_LEVEL_FIELD = 4;
    private static final int PLACE_SOUND_BUTTON = 5;
    private static final int PLACE_PARTICLES_BUTTON = 6;
    private static final int BREAK_SOUND_BUTTON = 7;
    private static final int BREAK_PARTICLES_BUTTON = 8;
    private static final int FAIL_SOUND_BUTTON = 9;
    private static final int FAIL_PARTICLES_BUTTON = 10;

    private final BossTetherSettings tether;

    public SubGuiBossTetherTuning(BossTetherSettings tether) {
        super("cnpcgeckoaddon.boss.tether_tuning_title");
        this.tether = tether;
    }

    @Override
    protected int rows() {
        return 10;
    }

    @Override
    protected void addRows() {
        addNumberField(EFFECT_INTERVAL_FIELD, "cnpcgeckoaddon.boss.tether_effect_interval", nextRow(),
                tether.getEffectIntervalTicks(), BossTetherSettings.MIN_EFFECT_INTERVAL_TICKS,
                BossTetherSettings.MAX_EFFECT_INTERVAL_TICKS, 20);
        addNumberField(PULL_SLACK_FIELD, "cnpcgeckoaddon.boss.tether_pull_slack", nextRow(),
                tether.getPullSlackTenths(), 0, BossTetherSettings.MAX_PULL_SLACK, 10);
        addNumberField(BEAM_SAG_FIELD, "cnpcgeckoaddon.boss.tether_beam_sag", nextRow(),
                tether.getBeamSagPercent(), 0, BossTetherSettings.MAX_BEAM_SAG_PERCENT, 100);
        addNumberField(PULL_PER_LEVEL_FIELD, "cnpcgeckoaddon.boss.tether_pull_per_level", nextRow(),
                tether.getPullPerLevelThousandths(), BossTetherSettings.MIN_PULL_PER_LEVEL,
                BossTetherSettings.MAX_PULL_PER_LEVEL, 20);

        addCueButton(PLACE_SOUND_BUTTON, "cnpcgeckoaddon.boss.tether_cue_place", nextRow(),
                tether.getPlaceSound());
        addCueButton(PLACE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tether_cue_place", nextRow(),
                tether.getPlaceParticles());
        addCueButton(BREAK_SOUND_BUTTON, "cnpcgeckoaddon.boss.tether_cue_break", nextRow(),
                tether.getBreakSound());
        addCueButton(BREAK_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tether_cue_break", nextRow(),
                tether.getBreakParticles());
        addCueButton(FAIL_SOUND_BUTTON, "cnpcgeckoaddon.boss.tether_cue_fail", nextRow(),
                tether.getFailSound());
        addCueButton(FAIL_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tether_cue_fail", nextRow(),
                tether.getFailParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(EFFECT_INTERVAL_FIELD, tether::setEffectIntervalTicks);
        applyNumberField(PULL_SLACK_FIELD, tether::setPullSlackTenths);
        applyNumberField(BEAM_SAG_FIELD, tether::setBeamSagPercent);
        applyNumberField(PULL_PER_LEVEL_FIELD, tether::setPullPerLevelThousandths);
    }
}
