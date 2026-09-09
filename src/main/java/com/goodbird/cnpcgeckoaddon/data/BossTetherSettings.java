package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The leash tied to the boss, to a spot or between two victims.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#tether()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossTetherSettings {

    private boolean tetherEnabled;
    private String tetherAnimation = "";
    private int tetherActionDelayTicks = 16;
    private int tetherCooldownTicks = 300;
    private int tetherTargetMode = BossTargetMode.RANDOM;
    private int tetherTargetCount = 2;
    private int tetherAnchor = BossPhaseData.TETHER_ANCHOR_BOSS;
    /** How far from its anchor a victim has to get for the leash to snap. */
    private int tetherBreakDistance = 10;
    /** How long they get to do it before the leash punishes them instead. */
    private int tetherDurationTicks = 120;
    /** Drag toward the anchor, 0 to 10; zero leaves the victim free to walk until the timer runs out. */
    private int tetherPull;
    private int tetherFailDamage = 12;
    private String tetherStyle = HookCordStyles.PARTICLES;
    private int tetherWidthPercent = 100;
    /** Dosed every second for as long as the leash holds. */
    private final BossEffectSet tetherEffects = new BossEffectSet();
    /** Landed once, on whoever was still leashed when the time ran out. */
    private final BossEffectSet tetherFailEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot tetherCastSpot = new BossCastSpot();

    public boolean isEnabled() { return tetherEnabled; }

    public void setEnabled(boolean value) { tetherEnabled = value; }

    public String getAnimation() { return tetherAnimation; }

    public void setAnimation(String value) { tetherAnimation = clean(value); }

    public int getActionDelayTicks() { return tetherActionDelayTicks; }

    public void setActionDelayTicks(int value) { tetherActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return tetherCooldownTicks; }

    public void setCooldownTicks(int value) { tetherCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return tetherTargetMode; }

    public void setTargetMode(int value) { tetherTargetMode = BossTargetMode.clamp(value); }

    /** How many victims one cast leashes; in the pair mode they are leashed two by two. */
    public int getTargetCount() { return tetherTargetCount; }

    public void setTargetCount(int value) { tetherTargetCount = Mth.clamp(value, 1, 8); }

    /** What the leash is tied to: the boss, the ground under the victim, or another victim. */
    public int getAnchor() { return tetherAnchor; }

    public void setAnchor(int value) {
        tetherAnchor = Mth.clamp(value, BossPhaseData.TETHER_ANCHOR_BOSS, BossPhaseData.TETHER_ANCHOR_PAIR);
    }

    public int getBreakDistance() { return tetherBreakDistance; }

    public void setBreakDistance(int value) { tetherBreakDistance = Mth.clamp(value, 3, 48); }

    public int getDurationTicks() { return tetherDurationTicks; }

    public void setDurationTicks(int value) { tetherDurationTicks = Mth.clamp(value, 20, 1200); }

    /** Drag toward the anchor, 0 to 10. Zero only times the victim out; it never moves them. */
    public int getPull() { return tetherPull; }

    public void setPull(int value) { tetherPull = Mth.clamp(value, 0, 10); }

    public int getFailDamage() { return tetherFailDamage; }

    public void setFailDamage(int value) { tetherFailDamage = Mth.clamp(value, 0, 1000); }

    public String getStyle() { return tetherStyle; }

    public void setStyle(String value) { tetherStyle = HookCordStyles.normalize(value); }

    public int getWidthPercent() { return tetherWidthPercent; }

    public void setWidthPercent(int value) { tetherWidthPercent = Mth.clamp(value, 25, 400); }

    public BossEffectSet getEffects() { return tetherEffects; }

    public BossEffectSet getFailEffects() { return tetherFailEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return tetherCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("TetherEnabled", tetherEnabled);
        tag.putString("TetherAnimation", tetherAnimation);
        tag.putInt("TetherActionDelayTicks", tetherActionDelayTicks);
        tag.putInt("TetherCooldownTicks", tetherCooldownTicks);
        tag.putInt("TetherTargetMode", tetherTargetMode);
        tag.putInt("TetherTargetCount", tetherTargetCount);
        tag.putInt("TetherAnchor", tetherAnchor);
        tag.putInt("TetherBreakDistance", tetherBreakDistance);
        tag.putInt("TetherDurationTicks", tetherDurationTicks);
        tag.putInt("TetherPull", tetherPull);
        tag.putInt("TetherFailDamage", tetherFailDamage);
        tag.putString("TetherStyle", tetherStyle);
        tag.putInt("TetherWidthPercent", tetherWidthPercent);
        tag.put("TetherEffects", tetherEffects.writeToNBT());
        tag.put("TetherFailEffects", tetherFailEffects.writeToNBT());
        tetherCastSpot.writeToNBT(tag, "Tether");
    }

    void readFromNBT(CompoundTag tag) {
        tetherEnabled = tag.getBoolean("TetherEnabled");
        tetherAnimation = clean(tag.getString("TetherAnimation"));
        tetherActionDelayTicks = value(tag, "TetherActionDelayTicks", 16, 0, 1200);
        tetherCooldownTicks = value(tag, "TetherCooldownTicks", 300, 1, 12000);
        tetherTargetMode = value(tag, "TetherTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        tetherTargetCount = value(tag, "TetherTargetCount", 2, 1, 8);
        tetherAnchor = value(tag, "TetherAnchor", BossPhaseData.TETHER_ANCHOR_BOSS, BossPhaseData.TETHER_ANCHOR_BOSS, BossPhaseData.TETHER_ANCHOR_PAIR);
        tetherBreakDistance = value(tag, "TetherBreakDistance", 10, 3, 48);
        tetherDurationTicks = value(tag, "TetherDurationTicks", 120, 20, 1200);
        tetherPull = value(tag, "TetherPull", 0, 0, 10);
        tetherFailDamage = value(tag, "TetherFailDamage", 12, 0, 1000);
        // An absent key reads as an empty string, which normalizes back to the plain sparks.
        tetherStyle = HookCordStyles.normalize(tag.getString("TetherStyle"));
        tetherWidthPercent = value(tag, "TetherWidthPercent", 100, 25, 400);
        tetherEffects.readFromNBT(tag, "TetherEffects");
        tetherFailEffects.readFromNBT(tag, "TetherFailEffects");
        tetherCastSpot.readFromNBT(tag, "Tether");
    }
}
