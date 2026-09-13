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

    /** The eruption's wave, its column and its boil, each in the unit its label names. */
    public static final int MIN_VFX_TICKS = 1;
    public static final int MAX_VFX_TICKS = 200;
    public static final int MAX_COLUMN_PER_RADIUS = 50;
    public static final int MAX_COLUMN_HEIGHT = 400;
    public static final int MAX_BOIL_SPEED = 100;

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
    /** How long the wave that runs out of the eruption is drawn for. */
    private int geyserVfxTicks = 20;
    /** Tenths of a block the column climbs per block of radius, and the ends it is held between. */
    private int geyserColumnPerRadius = 15;
    private int geyserColumnMin = 30;
    private int geyserColumnMax = 120;
    /** Hundredths of a block a tick the boil at the middle spits, from lit to the last tick. */
    private int geyserBoilMin = 2;
    private int geyserBoilMax = 12;
    private final BossSoundCue geyserLitSound = new BossSoundCue("minecraft:block.lava.pop", 1.6F, 0.5F);
    private final BossSoundCue geyserEruptSound =
            new BossSoundCue("minecraft:block.lava.extinguish", 3.0F, 0.5F);
    private final BossEffectSet geyserEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot geyserCastSpot = new BossCastSpot();

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

    /** Ticks the eruption's wave runs for. */
    public int getVfxTicks() { return geyserVfxTicks; }

    public void setVfxTicks(int value) {
        geyserVfxTicks = Mth.clamp(value, MIN_VFX_TICKS, MAX_VFX_TICKS);
    }

    /** Tenths of a block of column per block of radius. */
    public int getColumnPerRadiusTenths() { return geyserColumnPerRadius; }

    public void setColumnPerRadiusTenths(int value) {
        geyserColumnPerRadius = Mth.clamp(value, 0, MAX_COLUMN_PER_RADIUS);
    }

    /** The ends the column is held between, in tenths of a block. */
    public int getColumnMinTenths() { return geyserColumnMin; }

    public void setColumnMinTenths(int value) {
        geyserColumnMin = Mth.clamp(value, 0, MAX_COLUMN_HEIGHT);
    }

    public int getColumnMaxTenths() { return geyserColumnMax; }

    public void setColumnMaxTenths(int value) {
        geyserColumnMax = Mth.clamp(value, 0, MAX_COLUMN_HEIGHT);
    }

    /** Hundredths of a block a tick the boil spits on the tick the fuse was lit. */
    public int getBoilMinHundredths() { return geyserBoilMin; }

    public void setBoilMinHundredths(int value) {
        geyserBoilMin = Mth.clamp(value, 0, MAX_BOIL_SPEED);
    }

    /** And on its last tick, which is how the mark says how long is left. */
    public int getBoilMaxHundredths() { return geyserBoilMax; }

    public void setBoilMaxHundredths(int value) {
        geyserBoilMax = Mth.clamp(value, 0, MAX_BOIL_SPEED);
    }

    /** The hiss as the ground opens. */
    public BossSoundCue getLitSound() { return geyserLitSound; }

    public BossSoundCue getEruptSound() { return geyserEruptSound; }

    /** Whether the eruption pools anything, i.e. whether the fluid id is worth resolving. */
    public boolean leavesGeyserFluid() { return !geyserFluid.isEmpty(); }

    public BossEffectSet getEffects() { return geyserEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return geyserCastSpot; }

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
        tag.putInt("GeyserVfxTicks", geyserVfxTicks);
        tag.putInt("GeyserColumnPerRadius", geyserColumnPerRadius);
        tag.putInt("GeyserColumnMin", geyserColumnMin);
        tag.putInt("GeyserColumnMax", geyserColumnMax);
        tag.putInt("GeyserBoilMin", geyserBoilMin);
        tag.putInt("GeyserBoilMax", geyserBoilMax);
        geyserLitSound.writeToNBT(tag, "GeyserLitSound");
        geyserEruptSound.writeToNBT(tag, "GeyserEruptSound");
        geyserCastSpot.writeToNBT(tag, "Geyser");
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
        geyserVfxTicks = value(tag, "GeyserVfxTicks", 20, MIN_VFX_TICKS, MAX_VFX_TICKS);
        geyserColumnPerRadius = value(tag, "GeyserColumnPerRadius", 15, 0, MAX_COLUMN_PER_RADIUS);
        geyserColumnMin = value(tag, "GeyserColumnMin", 30, 0, MAX_COLUMN_HEIGHT);
        geyserColumnMax = value(tag, "GeyserColumnMax", 120, 0, MAX_COLUMN_HEIGHT);
        geyserBoilMin = value(tag, "GeyserBoilMin", 2, 0, MAX_BOIL_SPEED);
        geyserBoilMax = value(tag, "GeyserBoilMax", 12, 0, MAX_BOIL_SPEED);
        geyserLitSound.readFromNBT(tag, "GeyserLitSound");
        geyserEruptSound.readFromNBT(tag, "GeyserEruptSound");
        geyserCastSpot.readFromNBT(tag, "Geyser");
    }
}
