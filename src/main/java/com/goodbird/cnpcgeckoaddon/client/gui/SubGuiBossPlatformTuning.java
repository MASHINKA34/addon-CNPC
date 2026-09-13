package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;

/** How a platform flashes and counts down, how thickly it pops, and the two noises it makes. */
public final class SubGuiBossPlatformTuning extends SubGuiBossAbilityTuning {
    private static final int BLINK_FIELD = 1;
    private static final int COUNTDOWN_FIELD = 2;
    private static final int FLARE_MAX_FIELD = 3;
    private static final int FLARE_AREA_FIELD = 4;
    private static final int LIT_SOUND_BUTTON = 5;
    private static final int OUTLINE_PARTICLES_BUTTON = 6;
    private static final int BLAST_PARTICLES_BUTTON = 7;
    private static final int BLAST_SOUND_BUTTON = 8;

    private final BossPlatformSettings platform;

    public SubGuiBossPlatformTuning(BossPlatformSettings platform) {
        super("cnpcgeckoaddon.boss.platform_tuning_title");
        this.platform = platform;
    }

    @Override
    protected int rows() {
        return 8;
    }

    @Override
    protected void addRows() {
        addNumberField(BLINK_FIELD, "cnpcgeckoaddon.boss.platform_blink", nextRow(),
                platform.getBlinkTicks(), BossPlatformSettings.MIN_BLINK_TICKS,
                BossPlatformSettings.MAX_BLINK_TICKS, 4);
        addNumberField(COUNTDOWN_FIELD, "cnpcgeckoaddon.boss.platform_countdown_interval", nextRow(),
                platform.getCountdownIntervalTicks(), BossPlatformSettings.MIN_COUNTDOWN_INTERVAL_TICKS,
                BossPlatformSettings.MAX_COUNTDOWN_INTERVAL_TICKS, 20);
        addNumberField(FLARE_MAX_FIELD, "cnpcgeckoaddon.boss.platform_flare_max", nextRow(),
                platform.getFlareMax(), 0, BossPlatformSettings.MAX_FLARE, 24);
        addNumberField(FLARE_AREA_FIELD, "cnpcgeckoaddon.boss.platform_flare_area", nextRow(),
                platform.getFlareArea(), BossPlatformSettings.MIN_FLARE_AREA,
                BossPlatformSettings.MAX_FLARE_AREA, 40);

        addCueButton(LIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.platform_cue_lit", nextRow(),
                platform.getLitSound());
        addCueButton(OUTLINE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.platform_cue_outline", nextRow(),
                platform.getOutlineParticles());
        addCueButton(BLAST_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.platform_cue_blast_particle", nextRow(),
                platform.getBlastParticles());
        addCueButton(BLAST_SOUND_BUTTON, "cnpcgeckoaddon.boss.platform_cue_blast", nextRow(),
                platform.getBlastSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(BLINK_FIELD, platform::setBlinkTicks);
        applyNumberField(COUNTDOWN_FIELD, platform::setCountdownIntervalTicks);
        applyNumberField(FLARE_MAX_FIELD, platform::setFlareMax);
        applyNumberField(FLARE_AREA_FIELD, platform::setFlareArea);
    }
}
