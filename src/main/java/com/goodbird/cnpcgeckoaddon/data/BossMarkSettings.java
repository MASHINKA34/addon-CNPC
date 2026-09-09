package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The circle handed to a victim that goes off wherever they take it.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#mark()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossMarkSettings {

    private boolean markEnabled;
    private String markAnimation = "";
    private int markActionDelayTicks = 12;
    private int markCooldownTicks = 240;
    private int markMode = BossPhaseData.MARK_MODE_SOAK;
    private int markTargetMode = BossTargetMode.RANDOM;
    private int markTargetCount = 1;
    /** How long the mark burns on its carrier before it goes off. */
    private int markFuseTicks = 60;
    private int markRadius = 4;
    /** On, the circle rides its carrier; off, it stays on the ground they were called out on. */
    private boolean markFollow;
    /** Gather up: how many of the fight have to be standing inside when it goes off. */
    private int markMinPlayers = 2;
    /** Gather up: shared out between everyone inside. Spread out: dealt to each neighbour. */
    private int markDamage = 30;
    /** Gather up: what everyone inside takes instead when there were not enough of them. */
    private int markFailDamage = 60;
    /** Spread out: what the carrier pays for carrying it, wherever they took it. */
    private int markSelfDamage;
    private String markVfx = AreaVfxStyles.NONE;
    /** Landed on whoever took the mark's own damage, in either of its two rules. */
    private final BossEffectSet markEffects = new BossEffectSet();
    /** Gather up: landed instead on everyone inside when there were not enough of them. */
    private final BossEffectSet markFailEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot markCastSpot = new BossCastSpot();

    public boolean isEnabled() { return markEnabled; }

    public void setEnabled(boolean value) { markEnabled = value; }

    public String getAnimation() { return markAnimation; }

    public void setAnimation(String value) { markAnimation = clean(value); }

    public int getActionDelayTicks() { return markActionDelayTicks; }

    public void setActionDelayTicks(int value) { markActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return markCooldownTicks; }

    public void setCooldownTicks(int value) { markCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Which of the two rules the mark goes off by: gather up, or spread out. */
    public int getMode() { return markMode; }

    public void setMode(int value) {
        markMode = Mth.clamp(value, BossPhaseData.MARK_MODE_SOAK, BossPhaseData.MARK_MODE_SPREAD);
    }

    public int getTargetMode() { return markTargetMode; }

    public void setTargetMode(int value) { markTargetMode = BossTargetMode.clamp(value); }

    /** How many marks one cast hands out. */
    public int getTargetCount() { return markTargetCount; }

    public void setTargetCount(int value) { markTargetCount = Mth.clamp(value, 1, 8); }

    public int getFuseTicks() { return markFuseTicks; }

    public void setFuseTicks(int value) { markFuseTicks = Mth.clamp(value, 10, 400); }

    public int getRadius() { return markRadius; }

    public void setRadius(int value) { markRadius = Mth.clamp(value, 1, 16); }

    /** Whether the circle rides its carrier or stays where they were standing at the cast. */
    public boolean isFollow() { return markFollow; }

    public void setFollow(boolean value) { markFollow = value; }

    public int getMinPlayers() { return markMinPlayers; }

    public void setMinPlayers(int value) { markMinPlayers = Mth.clamp(value, 1, 10); }

    public int getDamage() { return markDamage; }

    public void setDamage(int value) { markDamage = Mth.clamp(value, 0, 1000); }

    public int getFailDamage() { return markFailDamage; }

    public void setFailDamage(int value) { markFailDamage = Mth.clamp(value, 0, 1000); }

    public int getSelfDamage() { return markSelfDamage; }

    public void setSelfDamage(int value) { markSelfDamage = Mth.clamp(value, 0, 1000); }

    public String getVfx() { return markVfx; }

    public void setVfx(String value) { markVfx = AreaVfxStyles.normalize(value); }

    public BossEffectSet getEffects() { return markEffects; }

    public BossEffectSet getFailEffects() { return markFailEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return markCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("MarkEnabled", markEnabled);
        tag.putString("MarkAnimation", markAnimation);
        tag.putInt("MarkActionDelayTicks", markActionDelayTicks);
        tag.putInt("MarkCooldownTicks", markCooldownTicks);
        tag.putInt("MarkMode", markMode);
        tag.putInt("MarkTargetMode", markTargetMode);
        tag.putInt("MarkTargetCount", markTargetCount);
        tag.putInt("MarkFuseTicks", markFuseTicks);
        tag.putInt("MarkRadius", markRadius);
        tag.putBoolean("MarkFollow", markFollow);
        tag.putInt("MarkMinPlayers", markMinPlayers);
        tag.putInt("MarkDamage", markDamage);
        tag.putInt("MarkFailDamage", markFailDamage);
        tag.putInt("MarkSelfDamage", markSelfDamage);
        tag.putString("MarkVfx", markVfx);
        tag.put("MarkEffects", markEffects.writeToNBT());
        tag.put("MarkFailEffects", markFailEffects.writeToNBT());
        markCastSpot.writeToNBT(tag, "Mark");
    }

    void readFromNBT(CompoundTag tag) {
        markEnabled = tag.getBoolean("MarkEnabled");
        markAnimation = clean(tag.getString("MarkAnimation"));
        markActionDelayTicks = value(tag, "MarkActionDelayTicks", 12, 0, 1200);
        markCooldownTicks = value(tag, "MarkCooldownTicks", 240, 1, 12000);
        markMode = value(tag, "MarkMode", BossPhaseData.MARK_MODE_SOAK, BossPhaseData.MARK_MODE_SOAK, BossPhaseData.MARK_MODE_SPREAD);
        markTargetMode = value(tag, "MarkTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        markTargetCount = value(tag, "MarkTargetCount", 1, 1, 8);
        markFuseTicks = value(tag, "MarkFuseTicks", 60, 10, 400);
        markRadius = value(tag, "MarkRadius", 4, 1, 16);
        markFollow = tag.getBoolean("MarkFollow");
        markMinPlayers = value(tag, "MarkMinPlayers", 2, 1, 10);
        markDamage = value(tag, "MarkDamage", 30, 0, 1000);
        markFailDamage = value(tag, "MarkFailDamage", 60, 0, 1000);
        markSelfDamage = value(tag, "MarkSelfDamage", 0, 0, 1000);
        markVfx = AreaVfxStyles.normalize(tag.getString("MarkVfx"));
        markEffects.readFromNBT(tag, "MarkEffects");
        markFailEffects.readFromNBT(tag, "MarkFailEffects");
        markCastSpot.readFromNBT(tag, "Mark");
    }
}
