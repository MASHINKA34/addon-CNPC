package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The hit that goes off all round the boss, and the wave of floor it lifts.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#areaAttack()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossAreaAttackSettings {

    private boolean areaAttackEnabled;
    private String areaAttackAnimation = "";
    private int areaAttackActionDelayTicks = 12;
    private int areaAttackCooldownTicks = 100;
    private int areaAttackDamage = 8;
    private int areaAttackRadius = 5;
    private int areaAttackKnockback = 1;
    private String areaAttackVfx = AreaVfxStyles.NONE;
    private int areaAttackVfxDurationTicks = 20;
    private boolean areaAttackBlockWave;
    private final BossEffectSet areaAttackEffects = new BossEffectSet();

    public boolean isEnabled() { return areaAttackEnabled; }

    public void setEnabled(boolean value) { areaAttackEnabled = value; }

    public String getAnimation() { return areaAttackAnimation; }

    public void setAnimation(String value) { areaAttackAnimation = clean(value); }

    public int getActionDelayTicks() { return areaAttackActionDelayTicks; }

    public void setActionDelayTicks(int value) { areaAttackActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return areaAttackCooldownTicks; }

    public void setCooldownTicks(int value) { areaAttackCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getDamage() { return areaAttackDamage; }

    public void setDamage(int value) { areaAttackDamage = Mth.clamp(value, 1, 1000); }

    public int getRadius() { return areaAttackRadius; }

    public void setRadius(int value) { areaAttackRadius = Mth.clamp(value, 1, 32); }

    public int getKnockback() { return areaAttackKnockback; }

    public void setKnockback(int value) { areaAttackKnockback = Mth.clamp(value, 0, 10); }

    public String getVfx() { return areaAttackVfx; }

    public void setVfx(String value) { areaAttackVfx = AreaVfxStyles.normalize(value); }

    public int getVfxDurationTicks() { return areaAttackVfxDurationTicks; }

    public void setVfxDurationTicks(int value) { areaAttackVfxDurationTicks = Mth.clamp(value, 5, 100); }

    public boolean isBlockWave() { return areaAttackBlockWave; }

    public void setBlockWave(boolean value) { areaAttackBlockWave = value; }

    public BossEffectSet getEffects() { return areaAttackEffects; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("AreaAttackEnabled", areaAttackEnabled);
        tag.putString("AreaAttackAnimation", areaAttackAnimation);
        tag.putInt("AreaAttackActionDelayTicks", areaAttackActionDelayTicks);
        tag.putInt("AreaAttackCooldownTicks", areaAttackCooldownTicks);
        tag.putInt("AreaAttackDamage", areaAttackDamage);
        tag.putInt("AreaAttackRadius", areaAttackRadius);
        tag.putInt("AreaAttackKnockback", areaAttackKnockback);
        tag.putString("AreaAttackVfx", areaAttackVfx);
        tag.putInt("AreaAttackVfxDuration", areaAttackVfxDurationTicks);
        tag.putBoolean("AreaAttackBlockWave", areaAttackBlockWave);
        tag.put("AreaAttackEffects", areaAttackEffects.writeToNBT());
    }

    void readFromNBT(CompoundTag tag) {
        areaAttackEnabled = tag.getBoolean("AreaAttackEnabled");
        areaAttackAnimation = clean(tag.getString("AreaAttackAnimation"));
        areaAttackActionDelayTicks = value(tag, "AreaAttackActionDelayTicks", 12, 0, 1200);
        areaAttackCooldownTicks = value(tag, "AreaAttackCooldownTicks", 100, 1, 12000);
        areaAttackDamage = value(tag, "AreaAttackDamage", 8, 1, 1000);
        areaAttackRadius = value(tag, "AreaAttackRadius", 5, 1, 32);
        areaAttackKnockback = value(tag, "AreaAttackKnockback", 1, 0, 10);
        areaAttackVfx = AreaVfxStyles.normalize(tag.getString("AreaAttackVfx"));
        areaAttackVfxDurationTicks = value(tag, "AreaAttackVfxDuration", 20, 5, 100);
        areaAttackBlockWave = tag.getBoolean("AreaAttackBlockWave");
        areaAttackEffects.readFromNBT(tag, "AreaAttackEffects");
    }
}
