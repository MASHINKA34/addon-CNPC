package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossCoverSettings;

/** Where the second sight line runs, how the wave and the shelters are laid out, and the bang. */
public final class SubGuiBossCoverTuning extends SubGuiBossAbilityTuning {
    private static final int KNEE_HEIGHT_FIELD = 1;
    private static final int WAVE_SPEED_FIELD = 2;
    private static final int VFX_MIN_FIELD = 3;
    private static final int VFX_MAX_FIELD = 4;
    private static final int POST_HEIGHT_FIELD = 5;
    private static final int SHELTER_SPACING_FIELD = 6;
    private static final int BLAST_SOUND_BUTTON = 7;
    private static final int BLAST_PARTICLES_BUTTON = 8;

    private final BossCoverSettings cover;

    public SubGuiBossCoverTuning(BossCoverSettings cover) {
        super("cnpcgeckoaddon.boss.cover_tuning_title");
        this.cover = cover;
    }

    @Override
    protected int rows() {
        return 8;
    }

    @Override
    protected void addRows() {
        addNumberField(KNEE_HEIGHT_FIELD, "cnpcgeckoaddon.boss.cover_knee_height", nextRow(),
                cover.getKneeHeightHundredths(), 0, BossCoverSettings.MAX_KNEE_HEIGHT, 25);
        addNumberField(WAVE_SPEED_FIELD, "cnpcgeckoaddon.boss.cover_wave_speed", nextRow(),
                cover.getWaveSpeedTenths(), BossCoverSettings.MIN_WAVE_SPEED,
                BossCoverSettings.MAX_WAVE_SPEED, 10);
        addNumberField(VFX_MIN_FIELD, "cnpcgeckoaddon.boss.cover_vfx_min", nextRow(),
                cover.getVfxMinTicks(), BossCoverSettings.MIN_VFX_TICKS,
                BossCoverSettings.MAX_VFX_TICKS, 20);
        addNumberField(VFX_MAX_FIELD, "cnpcgeckoaddon.boss.cover_vfx_max", nextRow(),
                cover.getVfxMaxTicks(), BossCoverSettings.MIN_VFX_TICKS,
                BossCoverSettings.MAX_VFX_TICKS, 60);
        addNumberField(POST_HEIGHT_FIELD, "cnpcgeckoaddon.boss.cover_post_height", nextRow(),
                cover.getPostHeight(), 0, BossCoverSettings.MAX_POST_HEIGHT, 3);
        addNumberField(SHELTER_SPACING_FIELD, "cnpcgeckoaddon.boss.cover_shelter_spacing", nextRow(),
                cover.getShelterSpacingPercent(), BossCoverSettings.MIN_SHELTER_SPACING,
                BossCoverSettings.MAX_SHELTER_SPACING, 200);

        addCueButton(BLAST_SOUND_BUTTON, "cnpcgeckoaddon.boss.cover_cue_blast", nextRow(),
                cover.getBlastSound());
        addCueButton(BLAST_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.cover_cue_blast", nextRow(),
                cover.getBlastParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(KNEE_HEIGHT_FIELD, cover::setKneeHeightHundredths);
        applyNumberField(WAVE_SPEED_FIELD, cover::setWaveSpeedTenths);
        applyNumberField(VFX_MIN_FIELD, cover::setVfxMinTicks);
        applyNumberField(VFX_MAX_FIELD, cover::setVfxMaxTicks);
        applyNumberField(POST_HEIGHT_FIELD, cover::setPostHeight);
        applyNumberField(SHELTER_SPACING_FIELD, cover::setShelterSpacingPercent);
    }
}
