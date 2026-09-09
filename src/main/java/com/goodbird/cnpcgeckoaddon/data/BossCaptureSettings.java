package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The grab, and the beam it holds its victim in.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#capture()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossCaptureSettings {

    private boolean captureEnabled;
    private String captureAnimation = "";
    private int captureActionDelayTicks = 10;
    private int captureCooldownTicks = 200;
    private int captureTargetMode = BossTargetMode.RANDOM;
    private int captureMinRange;
    private int captureMaxRange = 16;
    private int captureMode = BossPhaseData.CAPTURE_MODE_HOLD;
    private int captureDurationTicks = 60;
    private int captureLiftHeight = 5;
    private int captureLiftTicks = 40;
    private int captureEffectTarget = BossPhaseData.CAPTURE_EFFECT_PLAYER;
    private String captureBeamStyle = HookCordStyles.GHOST;
    private int captureBeamWidthPercent = 100;
    private int captureBeamSagPercent;
    private boolean captureAllowLook = true;
    private final BossEffectSet captureEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot captureCastSpot = new BossCastSpot();

    public boolean isEnabled() { return captureEnabled; }

    public void setEnabled(boolean value) { captureEnabled = value; }

    public String getAnimation() { return captureAnimation; }

    public void setAnimation(String value) { captureAnimation = clean(value); }

    public int getActionDelayTicks() { return captureActionDelayTicks; }

    public void setActionDelayTicks(int value) { captureActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return captureCooldownTicks; }

    public void setCooldownTicks(int value) { captureCooldownTicks = Mth.clamp(value, 20, 12000); }

    public int getTargetMode() { return captureTargetMode; }

    public void setTargetMode(int value) { captureTargetMode = BossTargetMode.clamp(value); }

    public int getMinRange() { return captureMinRange; }

    public int getMaxRange() { return captureMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        captureMinRange = Math.min(min, max);
        captureMaxRange = Math.max(min, max);
    }

    public int getMode() { return captureMode; }

    public void setMode(int value) { captureMode = Mth.clamp(value, BossPhaseData.CAPTURE_MODE_HOLD, BossPhaseData.CAPTURE_MODE_LIFT); }

    public int getDurationTicks() { return captureDurationTicks; }

    public void setDurationTicks(int value) { captureDurationTicks = Mth.clamp(value, 1, 1200); }

    public int getLiftHeight() { return captureLiftHeight; }

    public void setLiftHeight(int value) { captureLiftHeight = Mth.clamp(value, 0, 64); }

    public int getLiftTicks() { return captureLiftTicks; }

    public void setLiftTicks(int value) { captureLiftTicks = Mth.clamp(value, 1, 1200); }

    public int getEffectTarget() { return captureEffectTarget; }

    public void setEffectTarget(int value) {
        captureEffectTarget = Mth.clamp(value, BossPhaseData.CAPTURE_EFFECT_PLAYER, BossPhaseData.CAPTURE_EFFECT_BOTH);
    }

    public String getBeamStyle() { return captureBeamStyle; }

    public void setBeamStyle(String value) { captureBeamStyle = HookCordStyles.normalize(value); }

    public int getBeamWidthPercent() { return captureBeamWidthPercent; }

    public void setBeamWidthPercent(int value) { captureBeamWidthPercent = Mth.clamp(value, 25, 400); }

    public int getBeamSagPercent() { return captureBeamSagPercent; }

    public void setBeamSagPercent(int value) { captureBeamSagPercent = Mth.clamp(value, 0, 200); }

    public boolean isAllowLook() { return captureAllowLook; }

    public void setAllowLook(boolean value) { captureAllowLook = value; }

    public BossEffectSet getEffects() { return captureEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return captureCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("CaptureEnabled", captureEnabled);
        tag.putString("CaptureAnimation", captureAnimation);
        tag.putInt("CaptureActionDelayTicks", captureActionDelayTicks);
        tag.putInt("CaptureCooldownTicks", captureCooldownTicks);
        tag.putInt("CaptureTargetMode", captureTargetMode);
        tag.putInt("CaptureMinRange", captureMinRange);
        tag.putInt("CaptureMaxRange", captureMaxRange);
        tag.putInt("CaptureMode", captureMode);
        tag.putInt("CaptureDurationTicks", captureDurationTicks);
        tag.putInt("CaptureLiftHeight", captureLiftHeight);
        tag.putInt("CaptureLiftTicks", captureLiftTicks);
        tag.putInt("CaptureEffectTarget", captureEffectTarget);
        tag.putString("CaptureBeamStyle", captureBeamStyle);
        tag.putInt("CaptureBeamWidthPercent", captureBeamWidthPercent);
        tag.putInt("CaptureBeamSagPercent", captureBeamSagPercent);
        tag.putBoolean("CaptureAllowLook", captureAllowLook);
        tag.put("CaptureEffects", captureEffects.writeToNBT());
        captureCastSpot.writeToNBT(tag, "Capture");
    }

    void readFromNBT(CompoundTag tag) {
        captureEnabled = tag.getBoolean("CaptureEnabled");
        captureAnimation = clean(tag.getString("CaptureAnimation"));
        captureActionDelayTicks = value(tag, "CaptureActionDelayTicks", 10, 0, 1200);
        captureCooldownTicks = value(tag, "CaptureCooldownTicks", 200, 20, 12000);
        captureTargetMode = value(tag, "CaptureTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        setRange(
                value(tag, "CaptureMinRange", 0, 0, 64),
                value(tag, "CaptureMaxRange", 16, 1, 128));
        captureMode = value(tag, "CaptureMode", BossPhaseData.CAPTURE_MODE_HOLD, BossPhaseData.CAPTURE_MODE_HOLD, BossPhaseData.CAPTURE_MODE_LIFT);
        captureDurationTicks = value(tag, "CaptureDurationTicks", 60, 1, 1200);
        captureLiftHeight = value(tag, "CaptureLiftHeight", 5, 0, 64);
        captureLiftTicks = value(tag, "CaptureLiftTicks", 40, 1, 1200);
        captureEffectTarget = value(tag, "CaptureEffectTarget", BossPhaseData.CAPTURE_EFFECT_PLAYER,
                BossPhaseData.CAPTURE_EFFECT_PLAYER, BossPhaseData.CAPTURE_EFFECT_BOTH);
        captureBeamStyle = tag.contains("CaptureBeamStyle")
                ? HookCordStyles.normalize(tag.getString("CaptureBeamStyle")) : HookCordStyles.GHOST;
        captureBeamWidthPercent = value(tag, "CaptureBeamWidthPercent", 100, 25, 400);
        captureBeamSagPercent = value(tag, "CaptureBeamSagPercent", 0, 0, 200);
        captureAllowLook = !tag.contains("CaptureAllowLook") || tag.getBoolean("CaptureAllowLook");
        captureEffects.readFromNBT(tag, "CaptureEffects");
        captureCastSpot.readFromNBT(tag, "Capture");
    }
}
