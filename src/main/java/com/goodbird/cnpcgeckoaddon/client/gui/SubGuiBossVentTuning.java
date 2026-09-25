package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossVentSettings;

/**
 * How many particles a vent may spend a tick, how often a flame roars, and the noises and the
 * particles of the warning, the blast, the flame and the wall.
 */
public final class SubGuiBossVentTuning extends SubGuiBossAbilityTuning {
    private static final int BUDGET_FIELD = 1;
    private static final int FLAME_SOUND_INTERVAL_FIELD = 2;
    private static final int HISS_SOUND_BUTTON = 3;
    private static final int WARN_PARTICLES_BUTTON = 4;
    private static final int BURST_SOUND_BUTTON = 5;
    private static final int BURST_PARTICLES_BUTTON = 6;
    private static final int FLAME_SOUND_BUTTON = 7;
    private static final int FLAME_PARTICLES_BUTTON = 8;
    private static final int SMOKE_PARTICLES_BUTTON = 9;
    private static final int WALL_SOUND_BUTTON = 10;
    private static final int WALL_PARTICLES_BUTTON = 11;

    private final BossVentSettings vent;

    public SubGuiBossVentTuning(BossVentSettings vent) {
        super("cnpcgeckoaddon.boss.vent_tuning_title");
        this.vent = vent;
    }

    @Override
    protected int rows() {
        return 11;
    }

    @Override
    protected void addRows() {
        addNumberField(BUDGET_FIELD, "cnpcgeckoaddon.boss.vent_budget", nextRow(), vent.getParticleBudget(),
                BossVentSettings.MIN_PARTICLE_BUDGET, BossVentSettings.MAX_PARTICLE_BUDGET, 48);
        addNumberField(FLAME_SOUND_INTERVAL_FIELD, "cnpcgeckoaddon.boss.vent_flame_sound_interval", nextRow(),
                vent.getFlameSoundIntervalTicks(), BossVentSettings.MIN_FLAME_SOUND_INTERVAL_TICKS,
                BossVentSettings.MAX_FLAME_SOUND_INTERVAL_TICKS, 10);

        // In the order they happen to a vent: the warning, then whichever of the three it does.
        addCueButton(HISS_SOUND_BUTTON, "cnpcgeckoaddon.boss.vent_cue_hiss", nextRow(), vent.getHissSound());
        addCueButton(WARN_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.vent_cue_warn_particles", nextRow(),
                vent.getWarnParticles());
        addCueButton(BURST_SOUND_BUTTON, "cnpcgeckoaddon.boss.vent_cue_burst", nextRow(), vent.getBurstSound());
        addCueButton(BURST_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.vent_cue_burst_particles", nextRow(),
                vent.getBurstParticles());
        addCueButton(FLAME_SOUND_BUTTON, "cnpcgeckoaddon.boss.vent_cue_flame", nextRow(), vent.getFlameSound());
        addCueButton(FLAME_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.vent_cue_flame_particles", nextRow(),
                vent.getFlameParticles());
        addCueButton(SMOKE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.vent_cue_smoke", nextRow(),
                vent.getSmokeParticles());
        addCueButton(WALL_SOUND_BUTTON, "cnpcgeckoaddon.boss.vent_cue_wall", nextRow(), vent.getWallSound());
        addCueButton(WALL_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.vent_cue_wall_particles", nextRow(),
                vent.getWallParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(BUDGET_FIELD, vent::setParticleBudget);
        applyNumberField(FLAME_SOUND_INTERVAL_FIELD, vent::setFlameSoundIntervalTicks);
    }
}
