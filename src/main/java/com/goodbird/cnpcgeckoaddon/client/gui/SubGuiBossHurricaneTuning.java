package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossHurricaneSettings;

/** The ride's smaller numbers, the storm's column, and the eight noises and puffs it makes. */
public final class SubGuiBossHurricaneTuning extends SubGuiBossAbilityTuning {
    private static final int ORBIT_FIELD = 1;
    private static final int MAX_VICTIMS_FIELD = 2;
    private static final int GRACE_FIELD = 3;
    private static final int FLOOR_SEARCH_FIELD = 4;
    private static final int COLUMN_HEIGHT_FIELD = 5;
    private static final int COLUMN_DENSITY_FIELD = 6;
    private static final int LOOP_INTERVAL_FIELD = 7;
    private static final int LAUNCH_SOUND_BUTTON = 8;
    private static final int LOOP_SOUND_BUTTON = 9;
    private static final int CATCH_SOUND_BUTTON = 10;
    private static final int RELEASE_SOUND_BUTTON = 11;
    private static final int COLUMN_PARTICLES_BUTTON = 12;
    private static final int BASE_PARTICLES_BUTTON = 13;
    private static final int TRAIL_PARTICLES_BUTTON = 14;
    private static final int CATCH_PARTICLES_BUTTON = 15;

    private final BossHurricaneSettings hurricane;

    public SubGuiBossHurricaneTuning(BossHurricaneSettings hurricane) {
        super("cnpcgeckoaddon.boss.hurricane_tuning_title");
        this.hurricane = hurricane;
    }

    @Override
    protected int rows() {
        return 15;
    }

    @Override
    protected void addRows() {
        addNumberField(ORBIT_FIELD, "cnpcgeckoaddon.boss.hurricane_orbit", nextRow(),
                hurricane.getOrbitRadiusTenths(), 0, BossHurricaneSettings.MAX_ORBIT_RADIUS, 8);
        addNumberField(MAX_VICTIMS_FIELD, "cnpcgeckoaddon.boss.hurricane_max_victims", nextRow(),
                hurricane.getMaxVictims(), 1, BossHurricaneSettings.MAX_VICTIMS, 4);
        addNumberField(GRACE_FIELD, "cnpcgeckoaddon.boss.hurricane_grace", nextRow(),
                hurricane.getGraceTicks(), 0, BossHurricaneSettings.MAX_GRACE_TICKS, 40);
        addNumberField(FLOOR_SEARCH_FIELD, "cnpcgeckoaddon.boss.hurricane_floor", nextRow(),
                hurricane.getFloorSearch(), BossHurricaneSettings.MIN_FLOOR_SEARCH,
                BossHurricaneSettings.MAX_FLOOR_SEARCH, 4);
        addNumberField(COLUMN_HEIGHT_FIELD, "cnpcgeckoaddon.boss.hurricane_column", nextRow(),
                hurricane.getColumnHeightTenths(), BossHurricaneSettings.MIN_COLUMN_HEIGHT,
                BossHurricaneSettings.MAX_COLUMN_HEIGHT, 60);
        addNumberField(COLUMN_DENSITY_FIELD, "cnpcgeckoaddon.boss.hurricane_column", nextRow(),
                hurricane.getColumnDensity(), 0, BossHurricaneSettings.MAX_COLUMN_DENSITY, 6);
        addNumberField(LOOP_INTERVAL_FIELD, "cnpcgeckoaddon.boss.hurricane_loop_interval", nextRow(),
                hurricane.getLoopIntervalTicks(), BossHurricaneSettings.MIN_LOOP_INTERVAL,
                BossHurricaneSettings.MAX_LOOP_INTERVAL, 20);

        addCueButton(LAUNCH_SOUND_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_launch", nextRow(),
                hurricane.getLaunchSound());
        addCueButton(LOOP_SOUND_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_loop", nextRow(),
                hurricane.getLoopSound());
        addCueButton(CATCH_SOUND_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_catch", nextRow(),
                hurricane.getCatchSound());
        addCueButton(RELEASE_SOUND_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_release", nextRow(),
                hurricane.getReleaseSound());
        addCueButton(COLUMN_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_column", nextRow(),
                hurricane.getColumnParticles());
        addCueButton(BASE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_base", nextRow(),
                hurricane.getBaseParticles());
        addCueButton(TRAIL_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_trail", nextRow(),
                hurricane.getTrailParticles());
        addCueButton(CATCH_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.hurricane_cue_catch_particles", nextRow(),
                hurricane.getCatchParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(ORBIT_FIELD, hurricane::setOrbitRadiusTenths);
        applyNumberField(MAX_VICTIMS_FIELD, hurricane::setMaxVictims);
        applyNumberField(GRACE_FIELD, hurricane::setGraceTicks);
        applyNumberField(FLOOR_SEARCH_FIELD, hurricane::setFloorSearch);
        applyNumberField(COLUMN_HEIGHT_FIELD, hurricane::setColumnHeightTenths);
        applyNumberField(COLUMN_DENSITY_FIELD, hurricane::setColumnDensity);
        applyNumberField(LOOP_INTERVAL_FIELD, hurricane::setLoopIntervalTicks);
    }
}
