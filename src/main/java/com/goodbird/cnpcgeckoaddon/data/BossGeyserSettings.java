package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The fuse under a victim and the column that follows it.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#geyser()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossGeyserSettings {

    private boolean geyserEnabled;
    private String geyserAnimation = "";
    private int geyserActionDelayTicks = 12;
    private int geyserCooldownTicks = 160;
    private int geyserTargetMode = BossTargetMode.RANDOM;
    private int geyserTargetCount = 1;
    private int geyserMinRange = 3;
    private int geyserMaxRange = 24;
    /** How long the mark sits on the floor before the column comes up through it. */
    private int geyserFuseTicks = 25;
    private int geyserRadius = 3;
    private int geyserDamage = 8;
    /** Tenths of a block per tick, so 8 throws a victim up at 0.8 blocks a tick. */
    private int geyserLaunch = 8;
    private boolean geyserFollowTarget;
    /** Empty leaves nothing behind; anything else is a block id the eruption pools. */
    private String geyserFluid = "";
    private int geyserFluidLifetimeTicks = 60;
    private String geyserVfx = AreaVfxStyles.NONE;
    private boolean geyserBlockWave;
    private final BossEffectSet geyserEffects = new BossEffectSet();

    public boolean isEnabled() { return geyserEnabled; }

    public void setEnabled(boolean value) { geyserEnabled = value; }

    public String getAnimation() { return geyserAnimation; }

    public void setAnimation(String value) { geyserAnimation = clean(value); }

    public int getActionDelayTicks() { return geyserActionDelayTicks; }

    public void setActionDelayTicks(int value) { geyserActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return geyserCooldownTicks; }

    public void setCooldownTicks(int value) { geyserCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return geyserTargetMode; }

    public void setTargetMode(int value) { geyserTargetMode = BossTargetMode.clamp(value); }

    /** How many marks a single cast puts on the floor, one under each victim it picked. */
    public int getTargetCount() { return geyserTargetCount; }

    public void setTargetCount(int value) { geyserTargetCount = Mth.clamp(value, 1, 8); }

    public int getMinRange() { return geyserMinRange; }

    public int getMaxRange() { return geyserMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        geyserMinRange = Math.min(min, max);
        geyserMaxRange = Math.max(min, max);
    }

    /** The window a victim has to walk out of the circle, which is the whole mechanic. */
    public int getFuseTicks() { return geyserFuseTicks; }

    public void setFuseTicks(int value) { geyserFuseTicks = Mth.clamp(value, 5, 200); }

    public int getRadius() { return geyserRadius; }

    public void setRadius(int value) { geyserRadius = Mth.clamp(value, 1, 16); }

    public int getDamage() { return geyserDamage; }

    public void setDamage(int value) { geyserDamage = Mth.clamp(value, 0, 1000); }

    /** Upward throw in tenths of a block per tick; zero leaves the victim on the floor. */
    public int getLaunch() { return geyserLaunch; }

    public void setLaunch(int value) { geyserLaunch = Mth.clamp(value, 0, 20); }

    /** With this on the mark rides the victim, so there is nowhere to step out to. */
    public boolean isFollowTarget() { return geyserFollowTarget; }

    public void setFollowTarget(boolean value) { geyserFollowTarget = value; }

    public String getFluid() { return geyserFluid; }

    public void setFluid(String value) { geyserFluid = clean(value); }

    public int getFluidLifetimeTicks() { return geyserFluidLifetimeTicks; }

    public void setFluidLifetimeTicks(int value) {
        geyserFluidLifetimeTicks = Mth.clamp(value, 5, 1200);
    }

    public String getVfx() { return geyserVfx; }

    public void setVfx(String value) { geyserVfx = AreaVfxStyles.normalize(value); }

    public boolean isBlockWave() { return geyserBlockWave; }

    public void setBlockWave(boolean value) { geyserBlockWave = value; }

    /** Whether the eruption pools anything, i.e. whether the fluid id is worth resolving. */
    public boolean leavesGeyserFluid() { return !geyserFluid.isEmpty(); }

    public BossEffectSet getEffects() { return geyserEffects; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("GeyserEnabled", geyserEnabled);
        tag.putString("GeyserAnimation", geyserAnimation);
        tag.putInt("GeyserActionDelayTicks", geyserActionDelayTicks);
        tag.putInt("GeyserCooldownTicks", geyserCooldownTicks);
        tag.putInt("GeyserTargetMode", geyserTargetMode);
        tag.putInt("GeyserTargetCount", geyserTargetCount);
        tag.putInt("GeyserMinRange", geyserMinRange);
        tag.putInt("GeyserMaxRange", geyserMaxRange);
        tag.putInt("GeyserFuseTicks", geyserFuseTicks);
        tag.putInt("GeyserRadius", geyserRadius);
        tag.putInt("GeyserDamage", geyserDamage);
        tag.putInt("GeyserLaunch", geyserLaunch);
        tag.putBoolean("GeyserFollowTarget", geyserFollowTarget);
        tag.putString("GeyserFluid", geyserFluid);
        tag.putInt("GeyserFluidLifetime", geyserFluidLifetimeTicks);
        tag.putString("GeyserVfx", geyserVfx);
        tag.putBoolean("GeyserBlockWave", geyserBlockWave);
        tag.put("GeyserEffects", geyserEffects.writeToNBT());
    }

    void readFromNBT(CompoundTag tag) {
        geyserEnabled = tag.getBoolean("GeyserEnabled");
        geyserAnimation = clean(tag.getString("GeyserAnimation"));
        geyserActionDelayTicks = value(tag, "GeyserActionDelayTicks", 12, 0, 1200);
        geyserCooldownTicks = value(tag, "GeyserCooldownTicks", 160, 1, 12000);
        geyserTargetMode = value(tag, "GeyserTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        geyserTargetCount = value(tag, "GeyserTargetCount", 1, 1, 8);
        setRange(
                value(tag, "GeyserMinRange", 3, 0, 64),
                value(tag, "GeyserMaxRange", 24, 1, 128));
        geyserFuseTicks = value(tag, "GeyserFuseTicks", 25, 5, 200);
        geyserRadius = value(tag, "GeyserRadius", 3, 1, 16);
        geyserDamage = value(tag, "GeyserDamage", 8, 0, 1000);
        geyserLaunch = value(tag, "GeyserLaunch", 8, 0, 20);
        geyserFollowTarget = tag.getBoolean("GeyserFollowTarget");
        geyserFluid = clean(tag.getString("GeyserFluid"));
        geyserFluidLifetimeTicks = value(tag, "GeyserFluidLifetime", 60, 5, 1200);
        geyserVfx = AreaVfxStyles.normalize(tag.getString("GeyserVfx"));
        geyserBlockWave = tag.getBoolean("GeyserBlockWave");
        geyserEffects.readFromNBT(tag, "GeyserEffects");
    }
}
