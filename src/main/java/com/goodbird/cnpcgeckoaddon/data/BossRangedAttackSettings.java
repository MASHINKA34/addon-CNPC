package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The projectile the boss throws.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#rangedAttack()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossRangedAttackSettings {

    /** Degrees a tick the head turns while the strike aims; one is a boss that barely tracks. */
    public static final int MIN_AIM_TURN = 1;
    public static final int MAX_AIM_TURN = 180;
    /** A percentage of the squared range past which an arcing shot is used instead of a flat one. */
    public static final int MIN_LOB_SHARE = 10;
    public static final int MAX_LOB_SHARE = 100;

    private boolean rangedAttackEnabled;
    private String rangedAttackAnimation = "";
    private int rangedAttackActionDelayTicks = 12;
    private int rangedAttackCooldownTicks = 80;
    private int rangedAttackDamage = 6;
    private int rangedAttackMinRange = 4;
    private int rangedAttackMaxRange = 24;
    private int rangedAttackTargetMode = BossTargetMode.MAIN;
    private final BossEffectSet rangedAttackEffects = new BossEffectSet();
    /** How fast the head swings onto the victim as the shot goes off, in degrees a tick. */
    private int rangedAimTurnDegrees = 30;
    /** Past this share of the squared range an arcing shot is used instead of a flat one. */
    private int rangedLobSharePercent = 50;
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot rangedAttackCastSpot = new BossCastSpot();

    public boolean isEnabled() { return rangedAttackEnabled; }

    public void setEnabled(boolean value) { rangedAttackEnabled = value; }

    public String getAnimation() { return rangedAttackAnimation; }

    public void setAnimation(String value) { rangedAttackAnimation = clean(value); }

    public int getActionDelayTicks() { return rangedAttackActionDelayTicks; }

    public void setActionDelayTicks(int value) {
        rangedAttackActionDelayTicks = Mth.clamp(value, 0, 1200);
    }

    public int getCooldownTicks() { return rangedAttackCooldownTicks; }

    public void setCooldownTicks(int value) {
        rangedAttackCooldownTicks = Mth.clamp(value, 1, 12000);
    }

    public int getDamage() { return rangedAttackDamage; }

    public void setDamage(int value) { rangedAttackDamage = Mth.clamp(value, 1, 1000); }

    public int getMinRange() { return rangedAttackMinRange; }

    public int getMaxRange() { return rangedAttackMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        rangedAttackMinRange = Math.min(min, max);
        rangedAttackMaxRange = Math.max(min, max);
    }

    public int getTargetMode() { return rangedAttackTargetMode; }

    public void setTargetMode(int value) {
        rangedAttackTargetMode = BossTargetMode.clamp(value);
    }

    public BossEffectSet getEffects() { return rangedAttackEffects; }

    public int getAimTurnDegrees() { return rangedAimTurnDegrees; }

    public void setAimTurnDegrees(int value) {
        rangedAimTurnDegrees = Mth.clamp(value, MIN_AIM_TURN, MAX_AIM_TURN);
    }

    public int getLobSharePercent() { return rangedLobSharePercent; }

    public void setLobSharePercent(int value) {
        rangedLobSharePercent = Mth.clamp(value, MIN_LOB_SHARE, MAX_LOB_SHARE);
    }

    /**
     * How far, squared, a victim has to be before the shot is arced over rather than sent
     * flat. Measured off the squared range because that is what the shot itself compares.
     */
    public double lobBeyondSquared() {
        return rangedAttackMaxRange * (double) rangedAttackMaxRange * rangedLobSharePercent / 100.0D;
    }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return rangedAttackCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("RangedAttackEnabled", rangedAttackEnabled);
        tag.putString("RangedAttackAnimation", rangedAttackAnimation);
        tag.putInt("RangedAttackActionDelayTicks", rangedAttackActionDelayTicks);
        tag.putInt("RangedAttackCooldownTicks", rangedAttackCooldownTicks);
        tag.putInt("RangedAttackDamage", rangedAttackDamage);
        tag.putInt("RangedAttackMinRange", rangedAttackMinRange);
        tag.putInt("RangedAttackMaxRange", rangedAttackMaxRange);
        tag.putInt("RangedAttackTargetMode", rangedAttackTargetMode);
        tag.put("RangedAttackEffects", rangedAttackEffects.writeToNBT());
        tag.putInt("RangedAimTurn", rangedAimTurnDegrees);
        tag.putInt("RangedLobShare", rangedLobSharePercent);
        rangedAttackCastSpot.writeToNBT(tag, "RangedAttack");
    }

    void readFromNBT(CompoundTag tag) {
        rangedAttackEnabled = tag.getBoolean("RangedAttackEnabled");
        rangedAttackAnimation = clean(tag.getString("RangedAttackAnimation"));
        rangedAttackActionDelayTicks = value(tag, "RangedAttackActionDelayTicks", 12, 0, 1200);
        rangedAttackCooldownTicks = value(tag, "RangedAttackCooldownTicks", 80, 1, 12000);
        rangedAttackDamage = value(tag, "RangedAttackDamage", 6, 1, 1000);
        setRange(
                value(tag, "RangedAttackMinRange", 4, 0, 64),
                value(tag, "RangedAttackMaxRange", 24, 1, 128));
        rangedAttackTargetMode = value(tag, "RangedAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        rangedAttackEffects.readFromNBT(tag, "RangedAttackEffects");
        rangedAimTurnDegrees = value(tag, "RangedAimTurn", 30, MIN_AIM_TURN, MAX_AIM_TURN);
        // Half the squared range: what the shot arced past before it was a setting.
        rangedLobSharePercent = value(tag, "RangedLobShare", 50, MIN_LOB_SHARE, MAX_LOB_SHARE);
        rangedAttackCastSpot.readFromNBT(tag, "RangedAttack");
    }
}
