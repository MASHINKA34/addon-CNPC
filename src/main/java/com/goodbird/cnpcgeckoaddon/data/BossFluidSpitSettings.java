package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The lobbed ball of fluid and the puddle it leaves.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#fluidSpit()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossFluidSpitSettings {

    private boolean fluidSpitEnabled;
    private String fluidSpitAnimation = "";
    private int fluidSpitActionDelayTicks = 12;
    private int fluidSpitCooldownTicks = 120;
    private String fluidSpitBlock = "minecraft:lava";
    private int fluidSpitLifetimeTicks = 60;
    private int fluidSpitRadius = 1;
    private int fluidSpitDamage = 0;
    private int fluidSpitMinRange = 2;
    private int fluidSpitMaxRange = 24;
    private int fluidSpitTargetMode = BossTargetMode.MAIN;
    private final BossEffectSet fluidSpitEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot fluidSpitCastSpot = new BossCastSpot();

    public boolean isEnabled() { return fluidSpitEnabled; }

    public void setEnabled(boolean value) { fluidSpitEnabled = value; }

    public String getAnimation() { return fluidSpitAnimation; }

    public void setAnimation(String value) { fluidSpitAnimation = clean(value); }

    public int getActionDelayTicks() { return fluidSpitActionDelayTicks; }

    public void setActionDelayTicks(int value) {
        fluidSpitActionDelayTicks = Mth.clamp(value, 0, 1200);
    }

    public int getCooldownTicks() { return fluidSpitCooldownTicks; }

    public void setCooldownTicks(int value) {
        fluidSpitCooldownTicks = Mth.clamp(value, 1, 12000);
    }

    /** Block id of the fluid to spit, for example {@code minecraft:lava}. */
    public String getBlock() { return fluidSpitBlock; }

    public void setBlock(String value) { fluidSpitBlock = clean(value); }

    public int getLifetimeTicks() { return fluidSpitLifetimeTicks; }

    public void setLifetimeTicks(int value) {
        fluidSpitLifetimeTicks = Mth.clamp(value, 5, 1200);
    }

    public int getRadius() { return fluidSpitRadius; }

    public void setRadius(int value) { fluidSpitRadius = Mth.clamp(value, 0, 4); }

    public int getDamage() { return fluidSpitDamage; }

    public void setDamage(int value) { fluidSpitDamage = Mth.clamp(value, 0, 1000); }

    public int getMinRange() { return fluidSpitMinRange; }

    public int getMaxRange() { return fluidSpitMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        fluidSpitMinRange = Math.min(min, max);
        fluidSpitMaxRange = Math.max(min, max);
    }

    public int getTargetMode() { return fluidSpitTargetMode; }

    public void setTargetMode(int value) {
        fluidSpitTargetMode = BossTargetMode.clamp(value);
    }

    public boolean canSpit() { return fluidSpitEnabled && !fluidSpitBlock.isEmpty(); }

    public BossEffectSet getEffects() { return fluidSpitEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return fluidSpitCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("FluidSpitEnabled", fluidSpitEnabled);
        tag.putString("FluidSpitAnimation", fluidSpitAnimation);
        tag.putInt("FluidSpitActionDelayTicks", fluidSpitActionDelayTicks);
        tag.putInt("FluidSpitCooldownTicks", fluidSpitCooldownTicks);
        tag.putString("FluidSpitBlock", fluidSpitBlock);
        tag.putInt("FluidSpitLifetimeTicks", fluidSpitLifetimeTicks);
        tag.putInt("FluidSpitRadius", fluidSpitRadius);
        tag.putInt("FluidSpitDamage", fluidSpitDamage);
        tag.putInt("FluidSpitMinRange", fluidSpitMinRange);
        tag.putInt("FluidSpitMaxRange", fluidSpitMaxRange);
        tag.putInt("FluidSpitTargetMode", fluidSpitTargetMode);
        tag.put("FluidSpitEffects", fluidSpitEffects.writeToNBT());
        fluidSpitCastSpot.writeToNBT(tag, "FluidSpit");
    }

    void readFromNBT(CompoundTag tag) {
        fluidSpitEnabled = tag.getBoolean("FluidSpitEnabled");
        fluidSpitAnimation = clean(tag.getString("FluidSpitAnimation"));
        fluidSpitActionDelayTicks = value(tag, "FluidSpitActionDelayTicks", 12, 0, 1200);
        fluidSpitCooldownTicks = value(tag, "FluidSpitCooldownTicks", 120, 1, 12000);
        fluidSpitBlock = tag.contains("FluidSpitBlock")
                ? clean(tag.getString("FluidSpitBlock")) : "minecraft:lava";
        fluidSpitLifetimeTicks = value(tag, "FluidSpitLifetimeTicks", 60, 5, 1200);
        fluidSpitRadius = value(tag, "FluidSpitRadius", 1, 0, 4);
        fluidSpitDamage = value(tag, "FluidSpitDamage", 0, 0, 1000);
        setRange(
                value(tag, "FluidSpitMinRange", 2, 0, 64),
                value(tag, "FluidSpitMaxRange", 24, 1, 128));
        fluidSpitTargetMode = value(tag, "FluidSpitTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        fluidSpitEffects.readFromNBT(tag, "FluidSpitEffects");
        fluidSpitCastSpot.readFromNBT(tag, "FluidSpit");
    }
}
