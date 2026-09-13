package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;

/** Where the loot chest is looked for, and what the blast, the rage, the hop and a minion sound like. */
public final class SubGuiBossTuningDeath extends SubGuiBossTuningTopic {
    private static final int SEARCH_RADIUS_FIELD = 1;
    private static final int SEARCH_HEIGHT_FIELD = 2;
    private static final int DROP_HEIGHT_FIELD = 3;
    private static final int STAGED_TIMEOUT_FIELD = 4;
    private static final int AFTER_EXPLOSION_FIELD = 5;
    private static final int EXPLOSION_PARTICLES_FIELD = 6;
    private static final int EXPLOSION_SOUND_BUTTON = 7;
    private static final int RAGE_SOUND_BUTTON = 8;
    private static final int RAGE_PARTICLES_BUTTON = 9;
    private static final int RAGE_SMOKE_BUTTON = 10;
    private static final int TELEPORT_SOUND_BUTTON = 11;
    private static final int MINION_DESPAWN_BUTTON = 12;

    public SubGuiBossTuningDeath(BossTuningSettings tuning) {
        super("cnpcgeckoaddon.boss.tuning.death", tuning);
    }

    @Override
    protected int rows() {
        return 12;
    }

    @Override
    protected void addRows() {
        addNumberField(SEARCH_RADIUS_FIELD, "cnpcgeckoaddon.boss.tuning.chest_search_radius", nextRow(),
                tuning.chestSearchRadius(), 0, BossTuningSettings.MAX_CHEST_SEARCH, 2);
        addNumberField(SEARCH_HEIGHT_FIELD, "cnpcgeckoaddon.boss.tuning.chest_search_height", nextRow(),
                tuning.chestSearchHeight(), 0, BossTuningSettings.MAX_CHEST_SEARCH, 2);
        addNumberField(DROP_HEIGHT_FIELD, "cnpcgeckoaddon.boss.tuning.chest_drop_height", nextRow(),
                tuning.chestMaxDropHeight(), 0, BossTuningSettings.MAX_CHEST_DROP_HEIGHT, 8);
        addNumberField(STAGED_TIMEOUT_FIELD, "cnpcgeckoaddon.boss.tuning.chest_staged_timeout", nextRow(),
                tuning.chestStagedDropsTimeoutTicks(), BossTuningSettings.MIN_CHEST_STAGED_TIMEOUT,
                BossTuningSettings.MAX_CHEST_STAGED_TIMEOUT, 100);
        addNumberField(AFTER_EXPLOSION_FIELD, "cnpcgeckoaddon.boss.tuning.chest_after_explosion", nextRow(),
                tuning.chestAfterExplosionTicks(), 0, BossTuningSettings.MAX_CHEST_AFTER_EXPLOSION, 2);
        addNumberField(EXPLOSION_PARTICLES_FIELD, "cnpcgeckoaddon.boss.tuning.explosion_particles", nextRow(),
                tuning.explosionParticlePercent(), 0,
                BossTuningSettings.MAX_EXPLOSION_PARTICLE_PERCENT, 100);
        addCueButton(EXPLOSION_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.explosion_sound", nextRow(),
                tuning.explosionSound());
        addCueButton(RAGE_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.rage_sound", nextRow(),
                tuning.rageSound());
        addCueButton(RAGE_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.tuning.rage_particles", nextRow(),
                tuning.rageParticles());
        addCueButton(RAGE_SMOKE_BUTTON, "cnpcgeckoaddon.boss.tuning.rage_smoke", nextRow(),
                tuning.rageSmoke());
        addCueButton(TELEPORT_SOUND_BUTTON, "cnpcgeckoaddon.boss.tuning.teleport_sound", nextRow(),
                tuning.teleportSound());
        addCueButton(MINION_DESPAWN_BUTTON, "cnpcgeckoaddon.boss.tuning.minion_despawn", nextRow(),
                tuning.minionDespawnParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(SEARCH_RADIUS_FIELD, tuning::setChestSearchRadius);
        applyNumberField(SEARCH_HEIGHT_FIELD, tuning::setChestSearchHeight);
        applyNumberField(DROP_HEIGHT_FIELD, tuning::setChestMaxDropHeight);
        applyNumberField(STAGED_TIMEOUT_FIELD, tuning::setChestStagedDropsTimeoutTicks);
        applyNumberField(AFTER_EXPLOSION_FIELD, tuning::setChestAfterExplosionTicks);
        applyNumberField(EXPLOSION_PARTICLES_FIELD, tuning::setExplosionParticlePercent);
    }
}
