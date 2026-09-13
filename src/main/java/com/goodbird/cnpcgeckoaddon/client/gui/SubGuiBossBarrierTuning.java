package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossBarrierSettings;

/** How fast the shield is painted, how long it holds between hits, how wide its ring is, and its noises. */
public final class SubGuiBossBarrierTuning extends SubGuiBossAbilityTuning {
    private static final int PAINT_INTERVAL_FIELD = 1;
    private static final int HURT_COOLDOWN_FIELD = 2;
    private static final int AURA_PERCENT_FIELD = 3;
    private static final int AURA_EXTRA_FIELD = 4;
    private static final int UP_SOUND_BUTTON = 5;
    private static final int UP_PARTICLES_BUTTON = 6;
    private static final int BROKEN_SOUND_BUTTON = 7;
    private static final int BROKEN_PARTICLES_BUTTON = 8;
    private static final int EXPIRED_SOUND_BUTTON = 9;
    private static final int FAIL_HEAL_SOUND_BUTTON = 10;
    private static final int FAIL_HEAL_PARTICLES_BUTTON = 11;
    private static final int FAIL_CURSE_SOUND_BUTTON = 12;
    private static final int HIT_SOUND_BUTTON = 13;
    private static final int HIT_PARTICLES_BUTTON = 14;

    private final BossBarrierSettings barrier;

    public SubGuiBossBarrierTuning(BossBarrierSettings barrier) {
        super("cnpcgeckoaddon.boss.barrier_tuning_title");
        this.barrier = barrier;
    }

    @Override
    protected int rows() {
        return 14;
    }

    @Override
    protected void addRows() {
        addNumberField(PAINT_INTERVAL_FIELD, "cnpcgeckoaddon.boss.barrier_paint_interval", nextRow(),
                barrier.getPaintIntervalTicks(), BossBarrierSettings.MIN_PAINT_INTERVAL_TICKS,
                BossBarrierSettings.MAX_PAINT_INTERVAL_TICKS, 5);
        addNumberField(HURT_COOLDOWN_FIELD, "cnpcgeckoaddon.boss.barrier_hurt_cooldown", nextRow(),
                barrier.getHurtCooldownTicks(), 0, BossBarrierSettings.MAX_HURT_COOLDOWN_TICKS, 10);
        addNumberField(AURA_PERCENT_FIELD, "cnpcgeckoaddon.boss.barrier_aura_percent", nextRow(),
                barrier.getAuraPercent(), BossBarrierSettings.MIN_AURA_PERCENT,
                BossBarrierSettings.MAX_AURA_PERCENT, 75);
        addNumberField(AURA_EXTRA_FIELD, "cnpcgeckoaddon.boss.barrier_aura_extra", nextRow(),
                barrier.getAuraExtraTenths(), 0, BossBarrierSettings.MAX_AURA_EXTRA, 3);

        addCueButton(UP_SOUND_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_up", nextRow(),
                barrier.getUpSound());
        addCueButton(UP_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_up", nextRow(),
                barrier.getUpParticles());
        addCueButton(BROKEN_SOUND_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_broken", nextRow(),
                barrier.getBrokenSound());
        addCueButton(BROKEN_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_broken", nextRow(),
                barrier.getBrokenParticles());
        addCueButton(EXPIRED_SOUND_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_expired", nextRow(),
                barrier.getExpiredSound());
        addCueButton(FAIL_HEAL_SOUND_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_fail_heal", nextRow(),
                barrier.getFailHealSound());
        addCueButton(FAIL_HEAL_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_fail_heal", nextRow(),
                barrier.getFailHealParticles());
        addCueButton(FAIL_CURSE_SOUND_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_fail_curse", nextRow(),
                barrier.getFailCurseSound());
        addCueButton(HIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_hit", nextRow(),
                barrier.getHitSound());
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.barrier_cue_hit", nextRow(),
                barrier.getHitParticles());
    }

    @Override
    protected void applyFields() {
        applyNumberField(PAINT_INTERVAL_FIELD, barrier::setPaintIntervalTicks);
        applyNumberField(HURT_COOLDOWN_FIELD, barrier::setHurtCooldownTicks);
        applyNumberField(AURA_PERCENT_FIELD, barrier::setAuraPercent);
        applyNumberField(AURA_EXTRA_FIELD, barrier::setAuraExtraTenths);
    }
}
