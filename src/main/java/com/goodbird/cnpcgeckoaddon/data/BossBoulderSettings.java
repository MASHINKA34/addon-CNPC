package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The stone rolled, thrown or dropped at one victim.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#boulder()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossBoulderSettings {

    private boolean boulderEnabled;
    private String boulderAnimation = "";
    private int boulderActionDelayTicks = 16;
    private int boulderCooldownTicks = 180;
    private int boulderMode = BossPhaseData.BOULDER_MODE_ROLL;
    private int boulderTargetMode = BossTargetMode.MAIN;
    /** Cosmetic only: what the stone is drawn as, never what it does to the arena. */
    private String boulderBlock = "minecraft:stone";
    /** The drawn skin. Default keeps the plain scaled block the boulder started life as. */
    private String boulderStyle = BoulderStyles.BLOCK;
    /** Diameter in tenths of a block, so 15 rolls a 1.5 block stone. */
    private int boulderScale = 15;
    /** Tenths of a block per tick, so 6 travels at 0.6 blocks a tick. */
    private int boulderSpeed = 6;
    private int boulderRange = 20;
    private int boulderDamage = 12;
    private int boulderKnockback = 3;
    /** Off, the boulder rolls through the whole line; on, it breaks on the first victim. */
    private boolean boulderStopsOnHit;
    private int boulderShatterRadius = 2;
    private int boulderShatterDamage = 4;
    private String boulderVfx = AreaVfxStyles.NONE;
    private final BossEffectSet boulderEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot boulderCastSpot = new BossCastSpot();

    public boolean isEnabled() { return boulderEnabled; }

    public void setEnabled(boolean value) { boulderEnabled = value; }

    public String getAnimation() { return boulderAnimation; }

    public void setAnimation(String value) { boulderAnimation = clean(value); }

    public int getActionDelayTicks() { return boulderActionDelayTicks; }

    public void setActionDelayTicks(int value) { boulderActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return boulderCooldownTicks; }

    public void setCooldownTicks(int value) { boulderCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Whether the stone rolls along the floor or is thrown in an arc. */
    public int getMode() { return boulderMode; }

    public void setMode(int value) {
        boulderMode = Mth.clamp(value, BossPhaseData.BOULDER_MODE_ROLL, BossPhaseData.BOULDER_MODE_THROW);
    }

    public int getTargetMode() { return boulderTargetMode; }

    public void setTargetMode(int value) { boulderTargetMode = BossTargetMode.clamp(value); }

    /** Block id the stone is drawn as, for example {@code minecraft:deepslate}. */
    public String getBlock() { return boulderBlock; }

    public void setBlock(String value) { boulderBlock = clean(value); }

    public String getStyle() { return boulderStyle; }

    public void setStyle(String value) { boulderStyle = BoulderStyles.normalize(value); }

    /** Diameter in tenths of a block. */
    public int getScale() { return boulderScale; }

    public void setScale(int value) { boulderScale = Mth.clamp(value, 5, 40); }

    /** Travel speed in tenths of a block per tick. */
    public int getSpeed() { return boulderSpeed; }

    public void setSpeed(int value) { boulderSpeed = Mth.clamp(value, 1, 20); }

    /** How far down the corridor the stone travels before breaking apart on its own. */
    public int getRange() { return boulderRange; }

    public void setRange(int value) { boulderRange = Mth.clamp(value, 4, 64); }

    public int getDamage() { return boulderDamage; }

    public void setDamage(int value) { boulderDamage = Mth.clamp(value, 0, 1000); }

    public int getKnockback() { return boulderKnockback; }

    public void setKnockback(int value) { boulderKnockback = Mth.clamp(value, 0, 10); }

    /** Off rolls through the whole line; on breaks the stone on the first victim it hits. */
    public boolean isStopsOnHit() { return boulderStopsOnHit; }

    public void setStopsOnHit(boolean value) { boulderStopsOnHit = value; }

    public int getShatterRadius() { return boulderShatterRadius; }

    public void setShatterRadius(int value) { boulderShatterRadius = Mth.clamp(value, 0, 16); }

    public int getShatterDamage() { return boulderShatterDamage; }

    public void setShatterDamage(int value) { boulderShatterDamage = Mth.clamp(value, 0, 1000); }

    public String getVfx() { return boulderVfx; }

    public void setVfx(String value) { boulderVfx = AreaVfxStyles.normalize(value); }

    /** Whether the ability is worth scheduling: on, and with a block to be made of. */
    public boolean canLaunch() { return boulderEnabled && !boulderBlock.isEmpty(); }

    public BossEffectSet getEffects() { return boulderEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return boulderCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("BoulderEnabled", boulderEnabled);
        tag.putString("BoulderAnimation", boulderAnimation);
        tag.putInt("BoulderActionDelayTicks", boulderActionDelayTicks);
        tag.putInt("BoulderCooldownTicks", boulderCooldownTicks);
        tag.putInt("BoulderMode", boulderMode);
        tag.putInt("BoulderTargetMode", boulderTargetMode);
        tag.putString("BoulderBlock", boulderBlock);
        tag.putString("BoulderStyle", boulderStyle);
        tag.putInt("BoulderScale", boulderScale);
        tag.putInt("BoulderSpeed", boulderSpeed);
        tag.putInt("BoulderRange", boulderRange);
        tag.putInt("BoulderDamage", boulderDamage);
        tag.putInt("BoulderKnockback", boulderKnockback);
        tag.putBoolean("BoulderStopsOnHit", boulderStopsOnHit);
        tag.putInt("BoulderShatterRadius", boulderShatterRadius);
        tag.putInt("BoulderShatterDamage", boulderShatterDamage);
        tag.putString("BoulderVfx", boulderVfx);
        tag.put("BoulderEffects", boulderEffects.writeToNBT());
        boulderCastSpot.writeToNBT(tag, "Boulder");
    }

    void readFromNBT(CompoundTag tag) {
        boulderEnabled = tag.getBoolean("BoulderEnabled");
        boulderAnimation = clean(tag.getString("BoulderAnimation"));
        boulderActionDelayTicks = value(tag, "BoulderActionDelayTicks", 16, 0, 1200);
        boulderCooldownTicks = value(tag, "BoulderCooldownTicks", 180, 1, 12000);
        boulderMode = value(tag, "BoulderMode", BossPhaseData.BOULDER_MODE_ROLL, BossPhaseData.BOULDER_MODE_ROLL, BossPhaseData.BOULDER_MODE_THROW);
        boulderTargetMode = value(tag, "BoulderTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        boulderBlock = tag.contains("BoulderBlock")
                ? clean(tag.getString("BoulderBlock")) : "minecraft:stone";
        // An absent key is a boss saved before the skins existed, and normalize maps it to
        // the plain block - so nobody's boulder changes its look under them.
        boulderStyle = BoulderStyles.normalize(tag.getString("BoulderStyle"));
        boulderScale = value(tag, "BoulderScale", 15, 5, 40);
        boulderSpeed = value(tag, "BoulderSpeed", 6, 1, 20);
        boulderRange = value(tag, "BoulderRange", 20, 4, 64);
        boulderDamage = value(tag, "BoulderDamage", 12, 0, 1000);
        boulderKnockback = value(tag, "BoulderKnockback", 3, 0, 10);
        boulderStopsOnHit = tag.getBoolean("BoulderStopsOnHit");
        boulderShatterRadius = value(tag, "BoulderShatterRadius", 2, 0, 16);
        boulderShatterDamage = value(tag, "BoulderShatterDamage", 4, 0, 1000);
        boulderVfx = AreaVfxStyles.normalize(tag.getString("BoulderVfx"));
        boulderEffects.readFromNBT(tag, "BoulderEffects");
        boulderCastSpot.readFromNBT(tag, "Boulder");
    }
}
