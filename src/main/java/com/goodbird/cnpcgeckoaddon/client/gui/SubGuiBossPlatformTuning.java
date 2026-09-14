package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;

/**
 * How a platform flashes and counts down, how thickly it pops, how closely its outline is
 * dotted, how much fire it sows and how tall its pillars stand, what it smoulders and smokes
 * with, and the noises and the flash it makes.
 */
public final class SubGuiBossPlatformTuning extends SubGuiBossAbilityTuning {
    private static final int BLINK_FIELD = 1;
    private static final int COUNTDOWN_FIELD = 2;
    private static final int FLARE_MAX_FIELD = 3;
    private static final int FLARE_AREA_FIELD = 4;
    private static final int LIT_SOUND_BUTTON = 5;
    private static final int OUTLINE_PARTICLES_BUTTON = 6;
    private static final int BLAST_PARTICLES_BUTTON = 7;
    private static final int BLAST_SOUND_BUTTON = 8;
    private static final int EDGE_SPACING_FIELD = 9;
    private static final int FUSE_FILL_FIELD = 10;
    private static final int FUSE_RAMP_FIELD = 11;
    private static final int FUSE_PARTICLES_BUTTON = 12;
    private static final int PILLAR_HEIGHT_FIELD = 13;
    private static final int PILLAR_PARTICLES_BUTTON = 14;
    private static final int SMOULDER_FIELD = 15;
    private static final int SMOKE_PARTICLES_BUTTON = 16;
    private static final int BLAST_FLASH_BUTTON = 17;

    private final BossPlatformSettings platform;

    public SubGuiBossPlatformTuning(BossPlatformSettings platform) {
        super("cnpcgeckoaddon.boss.platform_tuning_title");
        this.platform = platform;
    }

    @Override
    protected int rows() {
        return 17;
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

        // The look of the fuse, the pillars and the smoulder, in the order they happen to a
        // platform: what the outline is dotted at, then what burns over the floor while the fuse
        // runs, then the corners, then what is left once it went off.
        addNumberField(EDGE_SPACING_FIELD, "cnpcgeckoaddon.boss.platform_edge_spacing", nextRow(),
                platform.getEdgeSpacing(), BossPlatformSettings.MIN_EDGE_SPACING,
                BossPlatformSettings.MAX_EDGE_SPACING, 50);
        addNumberField(FUSE_FILL_FIELD, "cnpcgeckoaddon.boss.platform_fuse_fill", nextRow(),
                platform.getFuseFillDensity(), 0, BossPlatformSettings.MAX_FILL_DENSITY, 6);
        addNumberField(FUSE_RAMP_FIELD, "cnpcgeckoaddon.boss.platform_fuse_ramp", nextRow(),
                platform.getFuseRampPercent(), 0, BossPlatformSettings.MAX_FUSE_RAMP, 200);
        addCueButton(FUSE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.platform_cue_fuse", nextRow(),
                platform.getFuseParticles());
        addNumberField(PILLAR_HEIGHT_FIELD, "cnpcgeckoaddon.boss.platform_pillar_height", nextRow(),
                platform.getPillarHeight(), 0, BossPlatformSettings.MAX_PILLAR_HEIGHT, 20);
        addCueButton(PILLAR_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.platform_cue_pillar", nextRow(),
                platform.getPillarParticles());
        addNumberField(SMOULDER_FIELD, "cnpcgeckoaddon.boss.platform_smoulder", nextRow(),
                platform.getSmoulderDensity(), 0, BossPlatformSettings.MAX_FILL_DENSITY, 4);
        addCueButton(OUTLINE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.platform_cue_outline", nextRow(),
                platform.getOutlineParticles());
        addCueButton(SMOKE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.platform_cue_smoke", nextRow(),
                platform.getSmokeParticles());

        addCueButton(LIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.platform_cue_lit", nextRow(),
                platform.getLitSound());
        addCueButton(BLAST_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.platform_cue_blast_particle", nextRow(),
                platform.getBlastParticles());
        addCueButton(BLAST_FLASH_BUTTON, "cnpcgeckoaddon.boss.platform_cue_flash", nextRow(),
                platform.getBlastFlash());
        addCueButton(BLAST_SOUND_BUTTON, "cnpcgeckoaddon.boss.platform_cue_blast", nextRow(),
                platform.getBlastSound());
    }

    /** Not "the defaults are the old behaviour": on this page, on purpose, they are not. */
    @Override
    protected String hintKey() {
        return "cnpcgeckoaddon.boss.platform_tuning_hint";
    }

    @Override
    protected void applyFields() {
        applyNumberField(BLINK_FIELD, platform::setBlinkTicks);
        applyNumberField(COUNTDOWN_FIELD, platform::setCountdownIntervalTicks);
        applyNumberField(FLARE_MAX_FIELD, platform::setFlareMax);
        applyNumberField(FLARE_AREA_FIELD, platform::setFlareArea);
        applyNumberField(EDGE_SPACING_FIELD, platform::setEdgeSpacing);
        applyNumberField(FUSE_FILL_FIELD, platform::setFuseFillDensity);
        applyNumberField(FUSE_RAMP_FIELD, platform::setFuseRampPercent);
        applyNumberField(PILLAR_HEIGHT_FIELD, platform::setPillarHeight);
        applyNumberField(SMOULDER_FIELD, platform::setSmoulderDensity);
    }
}
