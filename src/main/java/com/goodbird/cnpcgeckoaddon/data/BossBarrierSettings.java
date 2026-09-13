package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The shield with a timer, and what the arena owes when it is not broken in time.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#barrier()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossBarrierSettings {

    /** The shield's own pace and the ring it is drawn as, each in the unit its label names. */
    public static final int MIN_PAINT_INTERVAL_TICKS = 1;
    public static final int MAX_PAINT_INTERVAL_TICKS = 40;
    public static final int MAX_HURT_COOLDOWN_TICKS = 100;
    public static final int MIN_AURA_PERCENT = 10;
    public static final int MAX_AURA_PERCENT = 300;
    public static final int MAX_AURA_EXTRA = 50;

    /**
     * The barrier: a damage check rather than a cast. While it stands the boss' health does
     * not move - every hit is paid out of the barrier instead - and what happens next depends
     * on whether the party burned through it before the time ran out.
     */
    private boolean barrierEnabled;
    private int barrierTrigger = BossPhaseData.BARRIER_TRIGGER_ENTER;
    /** Timer rule: how long after one barrier's outcome the next one goes up. */
    private int barrierIntervalTicks = 600;
    /** What the barrier absorbs, in damage points. */
    private int barrierAmount = 200;
    /** Above zero, the absorb is this share of the boss' maximum health instead of the points. */
    private int barrierPercent;
    /** How long the party gets to break it; zero leaves it standing until they do. */
    private int barrierTimeoutTicks = 300;
    /** Broken in time: how long the boss stands stunned for, or zero for no window at all. */
    private int barrierBreakWindowTicks = 60;
    /** Broken in time: what the boss takes for the length of the window, as a percentage. */
    private int barrierBreakDamageTakenPercent = 150;
    private int barrierFailMode = BossPhaseData.BARRIER_FAIL_DAMAGE;
    private int barrierFailDamage = 20;
    /** Heal rule: the share of the maximum health the boss gets back. */
    private int barrierFailHealPercent = 25;
    private String barrierAnimation = "";
    private String barrierBreakAnimation = "";
    /** Landed on everyone in the fight when a barrier is not broken in time, whatever the rule. */
    private final BossEffectSet barrierFailEffects = new BossEffectSet();
    /** How often a standing barrier's aura is painted and its count told to the party. */
    private int barrierPaintIntervalTicks = 5;
    /**
     * Vanilla's hurt cooldown, kept by the barrier for itself.
     *
     * <p>A hit the barrier pays for is cancelled before vanilla sees it, so vanilla never arms
     * the ten ticks in which only the excess over the last hit lands. Nought here is a shield
     * that takes every click of a held button in full.</p>
     */
    private int barrierHurtCooldownTicks = 10;
    /** The aura ring's radius: this share of the boss' width, plus the tenths below. */
    private int barrierAuraPercent = 75;
    private int barrierAuraExtra = 3;
    private final BossSoundCue barrierUpSound =
            new BossSoundCue("minecraft:block.beacon.activate", 1.0F, 1.3F);
    private final BossParticleCue barrierUpParticles = new BossParticleCue(BossParticleCue.DUST_ID, 40);
    private final BossSoundCue barrierBrokenSound =
            new BossSoundCue("minecraft:item.shield.break", 1.5F, 0.6F);
    private final BossParticleCue barrierBrokenParticles = new BossParticleCue("minecraft:end_rod", 40);
    private final BossSoundCue barrierExpiredSound =
            new BossSoundCue("minecraft:block.beacon.deactivate", 1.5F, 0.6F);
    private final BossSoundCue barrierFailHealSound =
            new BossSoundCue("minecraft:item.totem.use", 1.0F, 1.0F);
    private final BossParticleCue barrierFailHealParticles = new BossParticleCue("minecraft:heart", 20);
    private final BossSoundCue barrierFailCurseSound =
            new BossSoundCue("minecraft:entity.elder_guardian.curse", 1.0F, 0.8F);
    private final BossSoundCue barrierHitSound =
            new BossSoundCue("minecraft:block.amethyst_block.chime", 1.0F, 1.0F);
    private final BossParticleCue barrierHitParticles = new BossParticleCue(BossParticleCue.DUST_ID, 8);

    /** While a barrier stands the boss' health does not move; breaking it in time is the check. */
    public boolean isEnabled() { return barrierEnabled; }

    public void setEnabled(boolean value) { barrierEnabled = value; }

    public int getTrigger() { return barrierTrigger; }

    public void setTrigger(int value) {
        barrierTrigger = Mth.clamp(value, BossPhaseData.BARRIER_TRIGGER_ENTER, BossPhaseData.BARRIER_TRIGGER_TIMER);
    }

    public int getIntervalTicks() { return barrierIntervalTicks; }

    public void setIntervalTicks(int value) { barrierIntervalTicks = Mth.clamp(value, 20, 24000); }

    public int getAmount() { return barrierAmount; }

    public void setAmount(int value) { barrierAmount = Mth.clamp(value, 1, 1000000); }

    public int getPercent() { return barrierPercent; }

    public void setPercent(int value) { barrierPercent = Mth.clamp(value, 0, 100); }

    public int getTimeoutTicks() { return barrierTimeoutTicks; }

    public void setTimeoutTicks(int value) { barrierTimeoutTicks = Mth.clamp(value, 0, 24000); }

    public int getBreakWindowTicks() { return barrierBreakWindowTicks; }

    public void setBreakWindowTicks(int value) { barrierBreakWindowTicks = Mth.clamp(value, 0, 1200); }

    public int getBreakDamageTakenPercent() { return barrierBreakDamageTakenPercent; }

    public void setBreakDamageTakenPercent(int value) {
        barrierBreakDamageTakenPercent = Mth.clamp(value, 100, 500);
    }

    public int getFailMode() { return barrierFailMode; }

    public void setFailMode(int value) {
        barrierFailMode = Mth.clamp(value, BossPhaseData.BARRIER_FAIL_DAMAGE, BossPhaseData.BARRIER_FAIL_RAGE);
    }

    public int getFailDamage() { return barrierFailDamage; }

    public void setFailDamage(int value) { barrierFailDamage = Mth.clamp(value, 0, 1000); }

    public int getFailHealPercent() { return barrierFailHealPercent; }

    public void setFailHealPercent(int value) { barrierFailHealPercent = Mth.clamp(value, 1, 100); }

    public String getAnimation() { return barrierAnimation; }

    public void setAnimation(String value) { barrierAnimation = clean(value); }

    public String getBreakAnimation() { return barrierBreakAnimation; }

    public void setBreakAnimation(String value) { barrierBreakAnimation = clean(value); }

    /**
     * What one barrier absorbs on this boss: the share of its maximum health when one is
     * set, the plain points otherwise. Asked when the barrier goes up, so party health
     * scaling is already in the maximum.
     */
    public float barrierAbsorb(float maxHealth) {
        if (barrierPercent > 0) {
            return Math.max(1.0F, maxHealth * barrierPercent / 100.0F);
        }
        return barrierAmount;
    }

    /** Ticks between two paintings of the aura, which is also how often the count is told. */
    public int getPaintIntervalTicks() { return barrierPaintIntervalTicks; }

    public void setPaintIntervalTicks(int value) {
        barrierPaintIntervalTicks = Mth.clamp(value, MIN_PAINT_INTERVAL_TICKS, MAX_PAINT_INTERVAL_TICKS);
    }

    /** Ticks the shield keeps between two hits it pays in full; nought lets a spam click through. */
    public int getHurtCooldownTicks() { return barrierHurtCooldownTicks; }

    public void setHurtCooldownTicks(int value) {
        barrierHurtCooldownTicks = Mth.clamp(value, 0, MAX_HURT_COOLDOWN_TICKS);
    }

    /** The aura ring's radius as a percentage of the boss' width. */
    public int getAuraPercent() { return barrierAuraPercent; }

    public void setAuraPercent(int value) {
        barrierAuraPercent = Mth.clamp(value, MIN_AURA_PERCENT, MAX_AURA_PERCENT);
    }

    /** Tenths of a block added on top of that, so the ring clears a narrow boss' shoulders. */
    public int getAuraExtraTenths() { return barrierAuraExtra; }

    public void setAuraExtraTenths(int value) {
        barrierAuraExtra = Mth.clamp(value, 0, MAX_AURA_EXTRA);
    }

    public BossSoundCue getUpSound() { return barrierUpSound; }

    public BossParticleCue getUpParticles() { return barrierUpParticles; }

    public BossSoundCue getBrokenSound() { return barrierBrokenSound; }

    public BossParticleCue getBrokenParticles() { return barrierBrokenParticles; }

    public BossSoundCue getExpiredSound() { return barrierExpiredSound; }

    public BossSoundCue getFailHealSound() { return barrierFailHealSound; }

    public BossParticleCue getFailHealParticles() { return barrierFailHealParticles; }

    public BossSoundCue getFailCurseSound() { return barrierFailCurseSound; }

    /** The chime and the sparks of a hit the shield took, throttled the way the immune clang is. */
    public BossSoundCue getHitSound() { return barrierHitSound; }

    public BossParticleCue getHitParticles() { return barrierHitParticles; }

    public BossEffectSet getFailEffects() { return barrierFailEffects; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("BarrierEnabled", barrierEnabled);
        tag.putInt("BarrierTrigger", barrierTrigger);
        tag.putInt("BarrierIntervalTicks", barrierIntervalTicks);
        tag.putInt("BarrierAmount", barrierAmount);
        tag.putInt("BarrierPercent", barrierPercent);
        tag.putInt("BarrierTimeoutTicks", barrierTimeoutTicks);
        tag.putInt("BarrierBreakWindowTicks", barrierBreakWindowTicks);
        tag.putInt("BarrierBreakDamageTakenPercent", barrierBreakDamageTakenPercent);
        tag.putInt("BarrierFailMode", barrierFailMode);
        tag.putInt("BarrierFailDamage", barrierFailDamage);
        tag.putInt("BarrierFailHealPercent", barrierFailHealPercent);
        tag.putString("BarrierAnimation", barrierAnimation);
        tag.putString("BarrierBreakAnimation", barrierBreakAnimation);
        tag.put("BarrierFailEffects", barrierFailEffects.writeToNBT());
        tag.putInt("BarrierPaintInterval", barrierPaintIntervalTicks);
        tag.putInt("BarrierHurtCooldown", barrierHurtCooldownTicks);
        tag.putInt("BarrierAuraPercent", barrierAuraPercent);
        tag.putInt("BarrierAuraExtra", barrierAuraExtra);
        barrierUpSound.writeToNBT(tag, "BarrierUpSound");
        barrierUpParticles.writeToNBT(tag, "BarrierUpParticles");
        barrierBrokenSound.writeToNBT(tag, "BarrierBrokenSound");
        barrierBrokenParticles.writeToNBT(tag, "BarrierBrokenParticles");
        barrierExpiredSound.writeToNBT(tag, "BarrierExpiredSound");
        barrierFailHealSound.writeToNBT(tag, "BarrierFailHealSound");
        barrierFailHealParticles.writeToNBT(tag, "BarrierFailHealParticles");
        barrierFailCurseSound.writeToNBT(tag, "BarrierFailCurseSound");
        barrierHitSound.writeToNBT(tag, "BarrierHitSound");
        barrierHitParticles.writeToNBT(tag, "BarrierHitParticles");
    }

    void readFromNBT(CompoundTag tag) {
        // A boss saved before the barrier existed carries no key and reads as off.
        barrierEnabled = tag.getBoolean("BarrierEnabled");
        barrierTrigger = value(tag, "BarrierTrigger", BossPhaseData.BARRIER_TRIGGER_ENTER,
                BossPhaseData.BARRIER_TRIGGER_ENTER, BossPhaseData.BARRIER_TRIGGER_TIMER);
        barrierIntervalTicks = value(tag, "BarrierIntervalTicks", 600, 20, 24000);
        barrierAmount = value(tag, "BarrierAmount", 200, 1, 1000000);
        barrierPercent = value(tag, "BarrierPercent", 0, 0, 100);
        barrierTimeoutTicks = value(tag, "BarrierTimeoutTicks", 300, 0, 24000);
        barrierBreakWindowTicks = value(tag, "BarrierBreakWindowTicks", 60, 0, 1200);
        barrierBreakDamageTakenPercent = value(tag, "BarrierBreakDamageTakenPercent", 150, 100, 500);
        barrierFailMode = value(tag, "BarrierFailMode", BossPhaseData.BARRIER_FAIL_DAMAGE,
                BossPhaseData.BARRIER_FAIL_DAMAGE, BossPhaseData.BARRIER_FAIL_RAGE);
        barrierFailDamage = value(tag, "BarrierFailDamage", 20, 0, 1000);
        barrierFailHealPercent = value(tag, "BarrierFailHealPercent", 25, 1, 100);
        barrierAnimation = clean(tag.getString("BarrierAnimation"));
        barrierBreakAnimation = clean(tag.getString("BarrierBreakAnimation"));
        barrierFailEffects.readFromNBT(tag, "BarrierFailEffects");
        barrierPaintIntervalTicks = value(tag, "BarrierPaintInterval", 5,
                MIN_PAINT_INTERVAL_TICKS, MAX_PAINT_INTERVAL_TICKS);
        barrierHurtCooldownTicks = value(tag, "BarrierHurtCooldown", 10, 0, MAX_HURT_COOLDOWN_TICKS);
        barrierAuraPercent = value(tag, "BarrierAuraPercent", 75, MIN_AURA_PERCENT, MAX_AURA_PERCENT);
        barrierAuraExtra = value(tag, "BarrierAuraExtra", 3, 0, MAX_AURA_EXTRA);
        barrierUpSound.readFromNBT(tag, "BarrierUpSound");
        barrierUpParticles.readFromNBT(tag, "BarrierUpParticles");
        barrierBrokenSound.readFromNBT(tag, "BarrierBrokenSound");
        barrierBrokenParticles.readFromNBT(tag, "BarrierBrokenParticles");
        barrierExpiredSound.readFromNBT(tag, "BarrierExpiredSound");
        barrierFailHealSound.readFromNBT(tag, "BarrierFailHealSound");
        barrierFailHealParticles.readFromNBT(tag, "BarrierFailHealParticles");
        barrierFailCurseSound.readFromNBT(tag, "BarrierFailCurseSound");
        barrierHitSound.readFromNBT(tag, "BarrierHitSound");
        barrierHitParticles.readFromNBT(tag, "BarrierHitParticles");
    }
}
