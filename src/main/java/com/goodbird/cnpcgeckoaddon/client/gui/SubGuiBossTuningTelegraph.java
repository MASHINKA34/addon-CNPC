package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;

/**
 * How the boss draws and sounds its warnings: who is near enough to see one, how often it is
 * repainted, how dim its faded half is, and the noise it makes.
 *
 * <p>Which abilities warn at all, and with what shape, is the warning screen's business; this
 * is only the trim round it.</p>
 */
public final class SubGuiBossTuningTelegraph extends SubGuiBossTuningTopic {
    private static final int AUDIENCE_FIELD = 1;
    private static final int INTERVAL_FIELD = 2;
    private static final int AURA_FIELD = 3;
    private static final int FADED_FIELD = 4;
    private static final int MELEE_ANGLE_FIELD = 5;
    private static final int SPAWN_RING_RADIUS_FIELD = 6;
    private static final int SPAWN_RINGS_FIELD = 7;
    private static final int SOUND_BUTTON = 8;

    public SubGuiBossTuningTelegraph(BossTuningSettings tuning) {
        super("cnpcgeckoaddon.boss.tuning.telegraph", tuning);
    }

    @Override
    protected int rows() {
        return 8;
    }

    @Override
    protected void addRows() {
        addNumberField(AUDIENCE_FIELD, "cnpcgeckoaddon.boss.tuning.audience", nextRow(),
                (int) tuning.telegraphAudienceRange(), BossTuningSettings.MIN_TELEGRAPH_AUDIENCE,
                BossTuningSettings.MAX_TELEGRAPH_AUDIENCE, 64);
        addNumberField(INTERVAL_FIELD, "cnpcgeckoaddon.boss.tuning.interval", nextRow(),
                tuning.telegraphIntervalTicks(), BossTuningSettings.MIN_TELEGRAPH_INTERVAL,
                BossTuningSettings.MAX_TELEGRAPH_INTERVAL, 2);
        addNumberField(AURA_FIELD, "cnpcgeckoaddon.boss.tuning.aura_particles", nextRow(),
                tuning.telegraphAuraParticles(), 0, BossTuningSettings.MAX_AURA_PARTICLES, 6);
        addNumberField(FADED_FIELD, "cnpcgeckoaddon.boss.tuning.faded_percent", nextRow(),
                tuning.telegraphFadedPercent(), BossTuningSettings.MIN_FADED_PERCENT,
                BossTuningSettings.MAX_FADED_PERCENT, 55);
        addNumberField(MELEE_ANGLE_FIELD, "cnpcgeckoaddon.boss.tuning.melee_half_angle", nextRow(),
                (int) tuning.telegraphMeleeHalfAngle(), BossTuningSettings.MIN_MELEE_HALF_ANGLE,
                BossTuningSettings.MAX_MELEE_HALF_ANGLE, 60);
        addNumberField(SPAWN_RING_RADIUS_FIELD, "cnpcgeckoaddon.boss.tuning.spawn_ring_radius", nextRow(),
                tuning.telegraphSpawnRingRadiusTenths(), BossTuningSettings.MIN_SPAWN_RING_RADIUS,
                BossTuningSettings.MAX_SPAWN_RING_RADIUS, 10);
        addNumberField(SPAWN_RINGS_FIELD, "cnpcgeckoaddon.boss.tuning.spawn_rings", nextRow(),
                tuning.telegraphSpawnRings(), 0, BossTuningSettings.MAX_SPAWN_RINGS, 8);
        addCueButton(SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.warning_sound", nextRow(),
                tuning.telegraphSound());
    }

    @Override
    protected void applyFields() {
        applyNumberField(AUDIENCE_FIELD, tuning::setTelegraphAudienceRange);
        applyNumberField(INTERVAL_FIELD, tuning::setTelegraphIntervalTicks);
        applyNumberField(AURA_FIELD, tuning::setTelegraphAuraParticles);
        applyNumberField(FADED_FIELD, tuning::setTelegraphFadedPercent);
        applyNumberField(MELEE_ANGLE_FIELD, tuning::setTelegraphMeleeHalfAngle);
        applyNumberField(SPAWN_RING_RADIUS_FIELD, tuning::setTelegraphSpawnRingRadiusTenths);
        applyNumberField(SPAWN_RINGS_FIELD, tuning::setTelegraphSpawnRings);
    }
}
