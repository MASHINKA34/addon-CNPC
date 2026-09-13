package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The chain the boss throws, and the drag that keeps hold of whoever it caught.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#hook()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossHookSettings {

    /** Both in hundredths of a block: the lift is far too small a number for tenths. */
    public static final int MAX_LIFT = 200;
    public static final int MAX_LIFT_PER_BLOCK = 50;

    private boolean hookEnabled;
    private String hookAnimation = "";
    private int hookActionDelayTicks = 10;
    private int hookCooldownTicks = 160;
    private int hookTargetMode = BossTargetMode.FARTHEST;
    private int hookTargetCount = 1;
    private int hookDamage = 4;
    /** Tenths of a block per tick, so 8 pulls at 0.4 blocks/tick. */
    private int hookPullStrength = 8;
    private int hookPullDurationTicks = 20;
    private int hookStopDistance = 2;
    private int hookMinRange = 4;
    private int hookMaxRange = 24;
    private int hookMode = BossPhaseData.HOOK_MODE_PULL;
    private String hookCordStyle = HookCordStyles.PARTICLES;
    /** Hundredths of a block a tick of lift: the cap, and what one block of distance adds. */
    private int hookLiftMax = 35;
    private int hookLiftPerBlock = 3;
    /** Empty id: the cord is heard as the style it is drawn in, at that style's own pitch. */
    private final BossSoundCue hookCordSound = new BossSoundCue("", 2.0F, 0.6F);
    private final BossParticleCue hookCordParticles = new BossParticleCue("minecraft:crit", 1);
    private final BossEffectSet hookEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot hookCastSpot = new BossCastSpot();

    public boolean isEnabled() { return hookEnabled; }

    public void setEnabled(boolean value) { hookEnabled = value; }

    public String getAnimation() { return hookAnimation; }

    public void setAnimation(String value) { hookAnimation = clean(value); }

    public int getActionDelayTicks() { return hookActionDelayTicks; }

    public void setActionDelayTicks(int value) { hookActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return hookCooldownTicks; }

    public void setCooldownTicks(int value) { hookCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return hookTargetMode; }

    public void setTargetMode(int value) { hookTargetMode = BossTargetMode.clamp(value); }

    /** How many victims a single cast grabs. */
    public int getTargetCount() { return hookTargetCount; }

    public void setTargetCount(int value) { hookTargetCount = Mth.clamp(value, 1, 8); }

    public int getDamage() { return hookDamage; }

    public void setDamage(int value) { hookDamage = Mth.clamp(value, 0, 1000); }

    /** Pull speed in tenths of a block per tick. */
    public int getPullStrength() { return hookPullStrength; }

    public void setPullStrength(int value) { hookPullStrength = Mth.clamp(value, 1, 20); }

    public int getPullDurationTicks() { return hookPullDurationTicks; }

    public void setPullDurationTicks(int value) { hookPullDurationTicks = Mth.clamp(value, 1, 200); }

    /** The pull releases once the victim is this close, so they are not ground into the boss. */
    public int getStopDistance() { return hookStopDistance; }

    public void setStopDistance(int value) { hookStopDistance = Mth.clamp(value, 0, 32); }

    public int getMinRange() { return hookMinRange; }

    public int getMaxRange() { return hookMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        hookMinRange = Math.min(min, max);
        hookMaxRange = Math.max(min, max);
    }

    public int getMode() { return hookMode; }

    public void setMode(int value) { hookMode = Mth.clamp(value, BossPhaseData.HOOK_MODE_PULL, BossPhaseData.HOOK_MODE_CINCH); }

    public String getCordStyle() { return hookCordStyle; }

    public void setCordStyle(String value) { hookCordStyle = HookCordStyles.normalize(value); }

    /** Hundredths of a block a tick: the most lift a yank ever adds, however far the victim is. */
    public int getLiftMaxHundredths() { return hookLiftMax; }

    public void setLiftMaxHundredths(int value) { hookLiftMax = Mth.clamp(value, 0, MAX_LIFT); }

    public double getLiftMax() { return hookLiftMax / 100.0D; }

    /** Hundredths of a block a tick of lift each block of distance is worth. */
    public int getLiftPerBlockHundredths() { return hookLiftPerBlock; }

    public void setLiftPerBlockHundredths(int value) {
        hookLiftPerBlock = Mth.clamp(value, 0, MAX_LIFT_PER_BLOCK);
    }

    public double getLiftPerBlock() { return hookLiftPerBlock / 100.0D; }

    /** With no id of its own, the voice the cord style was always given. */
    public BossSoundCue getCordSound() { return hookCordSound; }

    /** The particle a drawn cord is made of, one per point along it. */
    public BossParticleCue getCordParticles() { return hookCordParticles; }

    public BossEffectSet getEffects() { return hookEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return hookCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("HookEnabled", hookEnabled);
        tag.putString("HookAnimation", hookAnimation);
        tag.putInt("HookActionDelayTicks", hookActionDelayTicks);
        tag.putInt("HookCooldownTicks", hookCooldownTicks);
        tag.putInt("HookTargetMode", hookTargetMode);
        tag.putInt("HookTargetCount", hookTargetCount);
        tag.putInt("HookDamage", hookDamage);
        tag.putInt("HookPullStrength", hookPullStrength);
        tag.putInt("HookPullDurationTicks", hookPullDurationTicks);
        tag.putInt("HookStopDistance", hookStopDistance);
        tag.putInt("HookMinRange", hookMinRange);
        tag.putInt("HookMaxRange", hookMaxRange);
        tag.putInt("HookMode", hookMode);
        tag.putString("HookCordStyle", hookCordStyle);
        tag.putInt("HookLiftMax", hookLiftMax);
        tag.putInt("HookLiftPerBlock", hookLiftPerBlock);
        hookCordSound.writeToNBT(tag, "HookCordSound");
        hookCordParticles.writeToNBT(tag, "HookCordParticles");
        tag.put("HookEffects", hookEffects.writeToNBT());
        hookCastSpot.writeToNBT(tag, "Hook");
    }

    void readFromNBT(CompoundTag tag) {
        hookEnabled = tag.getBoolean("HookEnabled");
        hookAnimation = clean(tag.getString("HookAnimation"));
        hookActionDelayTicks = value(tag, "HookActionDelayTicks", 10, 0, 1200);
        hookCooldownTicks = value(tag, "HookCooldownTicks", 160, 1, 12000);
        hookTargetMode = value(tag, "HookTargetMode",
                BossTargetMode.FARTHEST, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        hookTargetCount = value(tag, "HookTargetCount", 1, 1, 8);
        hookDamage = value(tag, "HookDamage", 4, 0, 1000);
        hookPullStrength = value(tag, "HookPullStrength", 8, 1, 20);
        hookPullDurationTicks = value(tag, "HookPullDurationTicks", 20, 1, 200);
        hookStopDistance = value(tag, "HookStopDistance", 2, 0, 32);
        setRange(
                value(tag, "HookMinRange", 4, 0, 64),
                value(tag, "HookMaxRange", 24, 1, 128));
        hookMode = value(tag, "HookMode", BossPhaseData.HOOK_MODE_PULL, BossPhaseData.HOOK_MODE_PULL, BossPhaseData.HOOK_MODE_CINCH);
        // An absent key reads as an empty string, which normalizes back to the plain sparks.
        hookCordStyle = HookCordStyles.normalize(tag.getString("HookCordStyle"));
        // A boss saved before these were settings carries none of them and yanks on the
        // numbers that used to be literals in the drag.
        hookLiftMax = value(tag, "HookLiftMax", 35, 0, MAX_LIFT);
        hookLiftPerBlock = value(tag, "HookLiftPerBlock", 3, 0, MAX_LIFT_PER_BLOCK);
        hookCordSound.readFromNBT(tag, "HookCordSound");
        hookCordParticles.readFromNBT(tag, "HookCordParticles");
        hookEffects.readFromNBT(tag, "HookEffects");
        hookCastSpot.readFromNBT(tag, "Hook");
    }
}
