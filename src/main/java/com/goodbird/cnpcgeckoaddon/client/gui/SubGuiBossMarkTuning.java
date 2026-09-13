package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossMarkSettings;

/** How far a mark is handed out, how its fuse and its sparks burn, and the three noises it makes. */
public final class SubGuiBossMarkTuning extends SubGuiBossAbilityTuning {
    private static final int VFX_FIELD = 1;
    private static final int FUSE_MIN_FIELD = 2;
    private static final int FUSE_MAX_FIELD = 3;
    private static final int CARRIER_PARTICLES_FIELD = 4;
    private static final int REACH_FIELD = 5;
    private static final int MARKED_SOUND_BUTTON = 6;
    private static final int DEFUSED_SOUND_BUTTON = 7;
    private static final int BLAST_SOUND_BUTTON = 8;

    private final BossMarkSettings mark;

    public SubGuiBossMarkTuning(BossMarkSettings mark) {
        super("cnpcgeckoaddon.boss.mark_tuning_title");
        this.mark = mark;
    }

    @Override
    protected int rows() {
        return 8;
    }

    @Override
    protected void addRows() {
        addNumberField(VFX_FIELD, "cnpcgeckoaddon.boss.mark_vfx_ticks", nextRow(),
                mark.getVfxTicks(), BossMarkSettings.MIN_VFX_TICKS, BossMarkSettings.MAX_VFX_TICKS, 20);
        addNumberField(FUSE_MIN_FIELD, "cnpcgeckoaddon.boss.mark_fuse_min", nextRow(),
                mark.getFuseMinHundredths(), 0, BossMarkSettings.MAX_FUSE_SPEED, 2);
        addNumberField(FUSE_MAX_FIELD, "cnpcgeckoaddon.boss.mark_fuse_max", nextRow(),
                mark.getFuseMaxHundredths(), 0, BossMarkSettings.MAX_FUSE_SPEED, 12);
        addNumberField(CARRIER_PARTICLES_FIELD, "cnpcgeckoaddon.boss.mark_carrier_particles", nextRow(),
                mark.getCarrierParticles(), 0, BossMarkSettings.MAX_CARRIER_PARTICLES, 2);
        addNumberField(REACH_FIELD, "cnpcgeckoaddon.boss.mark_reach", nextRow(),
                mark.getReach(), BossMarkSettings.MIN_REACH, BossMarkSettings.MAX_REACH, 32);

        addCueButton(MARKED_SOUND_BUTTON, "cnpcgeckoaddon.boss.mark_cue_marked", nextRow(),
                mark.getMarkedSound());
        addCueButton(DEFUSED_SOUND_BUTTON, "cnpcgeckoaddon.boss.mark_cue_defused", nextRow(),
                mark.getDefusedSound());
        addCueButton(BLAST_SOUND_BUTTON, "cnpcgeckoaddon.boss.mark_cue_blast", nextRow(),
                mark.getBlastSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(VFX_FIELD, mark::setVfxTicks);
        applyNumberField(FUSE_MIN_FIELD, mark::setFuseMinHundredths);
        applyNumberField(FUSE_MAX_FIELD, mark::setFuseMaxHundredths);
        applyNumberField(CARRIER_PARTICLES_FIELD, mark::setCarrierParticles);
        applyNumberField(REACH_FIELD, mark::setReach);
    }
}
