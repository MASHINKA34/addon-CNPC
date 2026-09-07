package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The swing the boss makes at whoever is in reach.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#meleeAttack()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossMeleeAttackSettings {

    private boolean meleeAttackEnabled;
    private String meleeAttackAnimation = "";
    private int meleeAttackActionDelayTicks = 8;
    private int meleeAttackCooldownTicks = 30;
    private int meleeAttackDamage = 6;
    private int meleeAttackRange = 3;
    private int meleeAttackKnockback = 1;
    private int meleeAttackTargetMode = BossTargetMode.MAIN;
    private final BossEffectSet meleeAttackEffects = new BossEffectSet();

    public boolean isEnabled() { return meleeAttackEnabled; }

    public void setEnabled(boolean value) { meleeAttackEnabled = value; }

    public String getAnimation() { return meleeAttackAnimation; }

    public void setAnimation(String value) { meleeAttackAnimation = clean(value); }

    public int getActionDelayTicks() { return meleeAttackActionDelayTicks; }

    public void setActionDelayTicks(int value) {
        meleeAttackActionDelayTicks = Mth.clamp(value, 0, 1200);
    }

    public int getCooldownTicks() { return meleeAttackCooldownTicks; }

    public void setCooldownTicks(int value) {
        meleeAttackCooldownTicks = Mth.clamp(value, 1, 12000);
    }

    public int getDamage() { return meleeAttackDamage; }

    public void setDamage(int value) { meleeAttackDamage = Mth.clamp(value, 1, 1000); }

    public int getRange() { return meleeAttackRange; }

    public void setRange(int value) { meleeAttackRange = Mth.clamp(value, 1, 32); }

    public int getKnockback() { return meleeAttackKnockback; }

    public void setKnockback(int value) { meleeAttackKnockback = Mth.clamp(value, 0, 10); }

    public int getTargetMode() { return meleeAttackTargetMode; }

    public void setTargetMode(int value) {
        meleeAttackTargetMode = BossTargetMode.clamp(value);
    }

    public BossEffectSet getEffects() { return meleeAttackEffects; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("MeleeAttackEnabled", meleeAttackEnabled);
        tag.putString("MeleeAttackAnimation", meleeAttackAnimation);
        tag.putInt("MeleeAttackActionDelayTicks", meleeAttackActionDelayTicks);
        tag.putInt("MeleeAttackCooldownTicks", meleeAttackCooldownTicks);
        tag.putInt("MeleeAttackDamage", meleeAttackDamage);
        tag.putInt("MeleeAttackRange", meleeAttackRange);
        tag.putInt("MeleeAttackKnockback", meleeAttackKnockback);
        tag.putInt("MeleeAttackTargetMode", meleeAttackTargetMode);
        tag.put("MeleeAttackEffects", meleeAttackEffects.writeToNBT());
    }

    void readFromNBT(CompoundTag tag) {
        meleeAttackEnabled = tag.getBoolean("MeleeAttackEnabled");
        meleeAttackAnimation = clean(tag.getString("MeleeAttackAnimation"));
        meleeAttackActionDelayTicks = value(tag, "MeleeAttackActionDelayTicks", 8, 0, 1200);
        meleeAttackCooldownTicks = value(tag, "MeleeAttackCooldownTicks", 30, 1, 12000);
        meleeAttackDamage = value(tag, "MeleeAttackDamage", 6, 1, 1000);
        meleeAttackRange = value(tag, "MeleeAttackRange", 3, 1, 32);
        meleeAttackKnockback = value(tag, "MeleeAttackKnockback", 1, 0, 10);
        meleeAttackTargetMode = value(tag, "MeleeAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        meleeAttackEffects.readFromNBT(tag, "MeleeAttackEffects");
    }
}
