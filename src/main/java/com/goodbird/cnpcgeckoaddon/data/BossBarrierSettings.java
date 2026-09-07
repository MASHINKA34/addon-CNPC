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
    }
}
