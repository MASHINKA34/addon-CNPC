package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;

/** The random series' size, where the rings stand and whether the boss does, the slam's landing, and the seven noises and puffs. */
public final class SubGuiBossSeismicTuning extends SubGuiBossAbilityTuning {
    private static final int RANDOM_PULSES_FIELD = 1;
    private static final int RANDOM_CORE_BUTTON = 2;
    private static final int FOLLOW_BUTTON = 3;
    private static final int ROOT_BUTTON = 4;
    private static final int SLAM_FALL_BUTTON = 5;
    private static final int SLAM_TIMEOUT_FIELD = 6;
    private static final int VFX_TICKS_FIELD = 7;
    private static final int PULSE_SOUND_BUTTON = 8;
    private static final int HIT_SOUND_BUTTON = 9;
    private static final int LAUNCH_SOUND_BUTTON = 10;
    private static final int SLAM_SOUND_BUTTON = 11;
    private static final int PULSE_PARTICLES_BUTTON = 12;
    private static final int HIT_PARTICLES_BUTTON = 13;
    private static final int SLAM_PARTICLES_BUTTON = 14;

    private final BossSeismicSettings seismic;

    public SubGuiBossSeismicTuning(BossSeismicSettings seismic) {
        super("cnpcgeckoaddon.boss.seismic_tuning_title");
        this.seismic = seismic;
    }

    @Override
    protected int rows() {
        return 14;
    }

    @Override
    protected void addRows() {
        addNumberField(RANDOM_PULSES_FIELD, "cnpcgeckoaddon.boss.seismic_random_pulses", nextRow(),
                seismic.getRandomPulses(), 0, BossSeismicSettings.MAX_RANDOM_PULSES, 0);
        addYesNo(RANDOM_CORE_BUTTON, "cnpcgeckoaddon.boss.seismic_random_core", nextRow(), seismic.isRandomCore());
        addYesNo(FOLLOW_BUTTON, "cnpcgeckoaddon.boss.seismic_follow", nextRow(), seismic.isFollowBoss());
        addYesNo(ROOT_BUTTON, "cnpcgeckoaddon.boss.seismic_root", nextRow(), seismic.isRootWhileRunning());
        addYesNo(SLAM_FALL_BUTTON, "cnpcgeckoaddon.boss.seismic_slam_fall", nextRow(), seismic.isSlamFallDamage());
        addNumberField(SLAM_TIMEOUT_FIELD, "cnpcgeckoaddon.boss.seismic_slam_timeout", nextRow(),
                seismic.getSlamTimeoutTicks(), BossSeismicSettings.MIN_SLAM_TIMEOUT_TICKS,
                BossSeismicSettings.MAX_SLAM_TIMEOUT_TICKS, 100);
        addNumberField(VFX_TICKS_FIELD, "cnpcgeckoaddon.boss.seismic_vfx_ticks", nextRow(),
                seismic.getVfxTicks(), BossSeismicSettings.MIN_VFX_TICKS, BossSeismicSettings.MAX_VFX_TICKS, 8);

        addCueButton(PULSE_SOUND_BUTTON, "cnpcgeckoaddon.boss.seismic_cue_pulse", nextRow(), seismic.getPulseSound());
        addCueButton(HIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.seismic_cue_hit", nextRow(), seismic.getHitSound());
        addCueButton(LAUNCH_SOUND_BUTTON, "cnpcgeckoaddon.boss.seismic_cue_launch", nextRow(),
                seismic.getLaunchSound());
        addCueButton(SLAM_SOUND_BUTTON, "cnpcgeckoaddon.boss.seismic_cue_slam", nextRow(), seismic.getSlamSound());
        addCueButton(PULSE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.seismic_cue_pulse_particles", nextRow(),
                seismic.getPulseParticles());
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.seismic_cue_hit_particles", nextRow(),
                seismic.getHitParticles());
        addCueButton(SLAM_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.seismic_cue_slam_particles", nextRow(),
                seismic.getSlamParticles());
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == RANDOM_CORE_BUTTON) {
            seismic.setRandomCore(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == FOLLOW_BUTTON) {
            seismic.setFollowBoss(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ROOT_BUTTON) {
            seismic.setRootWhileRunning(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SLAM_FALL_BUTTON) {
            seismic.setSlamFallDamage(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(RANDOM_PULSES_FIELD, seismic::setRandomPulses);
        applyNumberField(SLAM_TIMEOUT_FIELD, seismic::setSlamTimeoutTicks);
        applyNumberField(VFX_TICKS_FIELD, seismic::setVfxTicks);
    }
}
