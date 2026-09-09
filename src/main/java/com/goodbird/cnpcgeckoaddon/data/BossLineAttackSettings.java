package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The corridor struck straight out in front of the boss, and its flank waves.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#lineAttack()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossLineAttackSettings {

    private boolean lineAttackEnabled;
    private String lineAttackAnimation = "";
    private int lineAttackActionDelayTicks = 12;
    private int lineAttackCooldownTicks = 140;
    private int lineAttackDirection = BossPhaseData.LINE_DIRECTION_TARGET;
    /** Off for animations that must not turn with the strike, such as a full-circle swing. */
    private boolean lineAttackFaceAxis = true;
    private int lineAttackTargetMode = BossTargetMode.MAIN;
    private int lineAttackLength = 9;
    private int lineAttackWidth = 2;
    private int lineAttackHeight = 3;
    private int lineAttackDamage = 10;
    private int lineAttackKnockback = 2;
    /** How far past the corridor the weaker wave reaches; zero leaves the flanks alone. */
    private int lineAttackSideWidth = 2;
    private int lineAttackSidePercent = 50;
    private String lineAttackVfx = AreaVfxStyles.NONE;
    private boolean lineAttackBlockWave;
    private final BossEffectSet lineAttackEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot lineAttackCastSpot = new BossCastSpot();

    public boolean isEnabled() { return lineAttackEnabled; }

    public void setEnabled(boolean value) { lineAttackEnabled = value; }

    public String getAnimation() { return lineAttackAnimation; }

    public void setAnimation(String value) { lineAttackAnimation = clean(value); }

    public int getActionDelayTicks() { return lineAttackActionDelayTicks; }

    public void setActionDelayTicks(int value) { lineAttackActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return lineAttackCooldownTicks; }

    public void setCooldownTicks(int value) { lineAttackCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Whether the corridor is laid toward the chosen victim or along the boss' own gaze. */
    public int getDirection() { return lineAttackDirection; }

    public void setDirection(int value) {
        lineAttackDirection = Mth.clamp(value, BossPhaseData.LINE_DIRECTION_TARGET, BossPhaseData.LINE_DIRECTION_FACING);
    }

    /** Whether the wind-up turns the model onto the committed corridor instead of the target. */
    public boolean isFaceAxis() { return lineAttackFaceAxis; }

    public void setFaceAxis(boolean value) { lineAttackFaceAxis = value; }

    public int getTargetMode() { return lineAttackTargetMode; }

    public void setTargetMode(int value) { lineAttackTargetMode = BossTargetMode.clamp(value); }

    /** How far down the line the strike reaches, measured flat from the boss. */
    public int getLength() { return lineAttackLength; }

    public void setLength(int value) { lineAttackLength = Mth.clamp(value, 1, 64); }

    /** The full width of the corridor, so the strike reaches half of this to either side. */
    public int getWidth() { return lineAttackWidth; }

    public void setWidth(int value) { lineAttackWidth = Mth.clamp(value, 1, 8); }

    /** How far above and below the boss the strike still catches somebody. */
    public int getHeight() { return lineAttackHeight; }

    public void setHeight(int value) { lineAttackHeight = Mth.clamp(value, 1, 8); }

    public int getDamage() { return lineAttackDamage; }

    public void setDamage(int value) { lineAttackDamage = Mth.clamp(value, 1, 1000); }

    public int getKnockback() { return lineAttackKnockback; }

    public void setKnockback(int value) { lineAttackKnockback = Mth.clamp(value, 0, 10); }

    /** Width of the softer band running along each flank of the corridor. */
    public int getSideWidth() { return lineAttackSideWidth; }

    public void setSideWidth(int value) { lineAttackSideWidth = Mth.clamp(value, 0, 8); }

    /** What the flanks hit for, as a percentage of the corridor's own damage. */
    public int getSidePercent() { return lineAttackSidePercent; }

    public void setSidePercent(int value) { lineAttackSidePercent = Mth.clamp(value, 10, 100); }

    public String getVfx() { return lineAttackVfx; }

    public void setVfx(String value) { lineAttackVfx = AreaVfxStyles.normalize(value); }

    public boolean isBlockWave() { return lineAttackBlockWave; }

    public void setBlockWave(boolean value) { lineAttackBlockWave = value; }

    public BossEffectSet getEffects() { return lineAttackEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return lineAttackCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("LineAttackEnabled", lineAttackEnabled);
        tag.putString("LineAttackAnimation", lineAttackAnimation);
        tag.putInt("LineAttackActionDelayTicks", lineAttackActionDelayTicks);
        tag.putInt("LineAttackCooldownTicks", lineAttackCooldownTicks);
        tag.putInt("LineAttackDirection", lineAttackDirection);
        tag.putBoolean("LineAttackFaceAxis", lineAttackFaceAxis);
        tag.putInt("LineAttackTargetMode", lineAttackTargetMode);
        tag.putInt("LineAttackLength", lineAttackLength);
        tag.putInt("LineAttackWidth", lineAttackWidth);
        tag.putInt("LineAttackHeight", lineAttackHeight);
        tag.putInt("LineAttackDamage", lineAttackDamage);
        tag.putInt("LineAttackKnockback", lineAttackKnockback);
        tag.putInt("LineAttackSideWidth", lineAttackSideWidth);
        tag.putInt("LineAttackSidePercent", lineAttackSidePercent);
        tag.putString("LineAttackVfx", lineAttackVfx);
        tag.putBoolean("LineAttackBlockWave", lineAttackBlockWave);
        tag.put("LineAttackEffects", lineAttackEffects.writeToNBT());
        lineAttackCastSpot.writeToNBT(tag, "LineAttack");
    }

    void readFromNBT(CompoundTag tag) {
        lineAttackEnabled = tag.getBoolean("LineAttackEnabled");
        lineAttackAnimation = clean(tag.getString("LineAttackAnimation"));
        lineAttackActionDelayTicks = value(tag, "LineAttackActionDelayTicks", 12, 0, 1200);
        lineAttackCooldownTicks = value(tag, "LineAttackCooldownTicks", 140, 1, 12000);
        lineAttackDirection = value(tag, "LineAttackDirection", BossPhaseData.LINE_DIRECTION_TARGET,
                BossPhaseData.LINE_DIRECTION_TARGET, BossPhaseData.LINE_DIRECTION_FACING);
        lineAttackFaceAxis = !tag.contains("LineAttackFaceAxis") || tag.getBoolean("LineAttackFaceAxis");
        lineAttackTargetMode = value(tag, "LineAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        lineAttackLength = value(tag, "LineAttackLength", 9, 1, 64);
        lineAttackWidth = value(tag, "LineAttackWidth", 2, 1, 8);
        lineAttackHeight = value(tag, "LineAttackHeight", 3, 1, 8);
        lineAttackDamage = value(tag, "LineAttackDamage", 10, 1, 1000);
        lineAttackKnockback = value(tag, "LineAttackKnockback", 2, 0, 10);
        lineAttackSideWidth = value(tag, "LineAttackSideWidth", 2, 0, 8);
        lineAttackSidePercent = value(tag, "LineAttackSidePercent", 50, 10, 100);
        lineAttackVfx = AreaVfxStyles.normalize(tag.getString("LineAttackVfx"));
        lineAttackBlockWave = tag.getBoolean("LineAttackBlockWave");
        lineAttackEffects.readFromNBT(tag, "LineAttackEffects");
        lineAttackCastSpot.readFromNBT(tag, "LineAttack");
    }
}
