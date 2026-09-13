package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The ring of stones dropped out of the sky.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#boulderRain()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossBoulderRainSettings {

    /** Both in tenths of a block, the way the mark and the headroom are measured. */
    public static final int MIN_MARK = 1;
    public static final int MAX_MARK = 100;
    public static final int MAX_MIN_DROP = 100;

    private boolean boulderRainEnabled;
    private String boulderRainAnimation = "";
    private int boulderRainActionDelayTicks = 16;
    private int boulderRainCooldownTicks = 240;
    /** Outer edge of the ring the volley falls in, measured from where the boss cast it. */
    private int boulderRainRadius = 12;
    /** Inner edge, the dead zone at the boss' own feet. Held under the outer one. */
    private int boulderRainMinRadius;
    private int boulderRainCount = 8;
    /** Ticks between one stone and the next; 0 drops the whole volley on one tick. */
    private int boulderRainIntervalTicks = 4;
    /** How high above the floor a stone starts, which is also how long its mark burns. */
    private int boulderRainFallHeight = 16;
    /** Cosmetic only, exactly as the corridor boulder's block is. */
    private String boulderRainBlock = "minecraft:stone";
    private String boulderRainStyle = BoulderStyles.BLOCK;
    /** Diameter in tenths of a block, so 12 drops a 1.2 block stone. */
    private int boulderRainScale = 12;
    private int boulderRainDamage = 10;
    private int boulderRainKnockback = 2;
    private int boulderRainShatterRadius = 2;
    private int boulderRainShatterDamage = 4;
    private String boulderRainVfx = AreaVfxStyles.NONE;
    /** Tenths of a block: the smallest circle a stone with no shards still burns. */
    private int rainMinMarkRadius = 10;
    /** Tenths of a block of headroom below which a point is not worth dropping into. */
    private int rainMinDropBlocks = 10;
    /** Empty id: the mark is heard as the block the stones are made of. */
    private final BossSoundCue rainMarkSound = new BossSoundCue("", 1.2F, 0.5F);
    private final BossEffectSet boulderRainEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot boulderRainCastSpot = new BossCastSpot();

    public boolean isEnabled() { return boulderRainEnabled; }

    public void setEnabled(boolean value) { boulderRainEnabled = value; }

    public String getAnimation() { return boulderRainAnimation; }

    public void setAnimation(String value) { boulderRainAnimation = clean(value); }

    public int getActionDelayTicks() { return boulderRainActionDelayTicks; }

    public void setActionDelayTicks(int value) {
        boulderRainActionDelayTicks = Mth.clamp(value, 0, 1200);
    }

    public int getCooldownTicks() { return boulderRainCooldownTicks; }

    public void setCooldownTicks(int value) {
        boulderRainCooldownTicks = Mth.clamp(value, 1, 12000);
    }

    public int getRadius() { return boulderRainRadius; }

    public int getMinRadius() { return boulderRainMinRadius; }

    /**
     * The ring the volley falls in, set as the pair it is read as.
     *
     * <p>The inner edge is held under the outer one: a dead zone as wide as the ring would
     * leave the cast with nowhere left to drop a stone.</p>
     */
    public void setRing(int radius, int minRadius) {
        boulderRainRadius = Mth.clamp(radius, 2, 48);
        boulderRainMinRadius = Mth.clamp(minRadius, 0, boulderRainRadius - 1);
    }

    public int getCount() { return boulderRainCount; }

    public void setCount(int value) { boulderRainCount = Mth.clamp(value, 1, 32); }

    public int getIntervalTicks() { return boulderRainIntervalTicks; }

    public void setIntervalTicks(int value) {
        boulderRainIntervalTicks = Mth.clamp(value, 0, 100);
    }

    public int getFallHeight() { return boulderRainFallHeight; }

    public void setFallHeight(int value) { boulderRainFallHeight = Mth.clamp(value, 4, 48); }

    public String getBlock() { return boulderRainBlock; }

    public void setBlock(String value) { boulderRainBlock = clean(value); }

    public String getStyle() { return boulderRainStyle; }

    public void setStyle(String value) { boulderRainStyle = BoulderStyles.normalize(value); }

    public int getScale() { return boulderRainScale; }

    public void setScale(int value) {
        boulderRainScale = Mth.clamp(value, BossBoulderSettings.MIN_SCALE, BossBoulderSettings.MAX_SCALE);
    }

    public int getDamage() { return boulderRainDamage; }

    public void setDamage(int value) { boulderRainDamage = Mth.clamp(value, 0, 1000); }

    public int getKnockback() { return boulderRainKnockback; }

    public void setKnockback(int value) { boulderRainKnockback = Mth.clamp(value, 0, 10); }

    public int getShatterRadius() { return boulderRainShatterRadius; }

    public void setShatterRadius(int value) {
        boulderRainShatterRadius = Mth.clamp(value, 0, 16);
    }

    public int getShatterDamage() { return boulderRainShatterDamage; }

    public void setShatterDamage(int value) {
        boulderRainShatterDamage = Mth.clamp(value, 0, 1000);
    }

    public String getVfx() { return boulderRainVfx; }

    public void setVfx(String value) { boulderRainVfx = AreaVfxStyles.normalize(value); }

    public boolean canLaunch() { return boulderRainEnabled && isConfigured(); }

    /** Whether there is a block to make the stones of, whatever the switch says: all a chained start still needs. */
    public boolean isConfigured() { return !boulderRainBlock.isEmpty(); }

    /** Tenths of a block: 10 is the one block circle every drop was always announced with. */
    public int getMinMarkRadiusTenths() { return rainMinMarkRadius; }

    public void setMinMarkRadiusTenths(int value) {
        rainMinMarkRadius = Mth.clamp(value, MIN_MARK, MAX_MARK);
    }

    public double getMinMarkRadius() { return rainMinMarkRadius / 10.0D; }

    /** Tenths of a block: a roof closer than this leaves nothing worth calling a drop. */
    public int getMinDropTenths() { return rainMinDropBlocks; }

    public void setMinDropTenths(int value) {
        rainMinDropBlocks = Mth.clamp(value, 0, MAX_MIN_DROP);
    }

    public double getMinDrop() { return rainMinDropBlocks / 10.0D; }

    /** With no id of its own, the place sound of the block the stones are made of. */
    public BossSoundCue getMarkSound() { return rainMarkSound; }

    public BossEffectSet getEffects() { return boulderRainEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return boulderRainCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("BoulderRainEnabled", boulderRainEnabled);
        tag.putString("BoulderRainAnimation", boulderRainAnimation);
        tag.putInt("BoulderRainActionDelayTicks", boulderRainActionDelayTicks);
        tag.putInt("BoulderRainCooldownTicks", boulderRainCooldownTicks);
        tag.putInt("BoulderRainRadius", boulderRainRadius);
        tag.putInt("BoulderRainMinRadius", boulderRainMinRadius);
        tag.putInt("BoulderRainCount", boulderRainCount);
        tag.putInt("BoulderRainIntervalTicks", boulderRainIntervalTicks);
        tag.putInt("BoulderRainFallHeight", boulderRainFallHeight);
        tag.putString("BoulderRainBlock", boulderRainBlock);
        tag.putString("BoulderRainStyle", boulderRainStyle);
        tag.putInt("BoulderRainScale", boulderRainScale);
        tag.putInt("BoulderRainDamage", boulderRainDamage);
        tag.putInt("BoulderRainKnockback", boulderRainKnockback);
        tag.putInt("BoulderRainShatterRadius", boulderRainShatterRadius);
        tag.putInt("BoulderRainShatterDamage", boulderRainShatterDamage);
        tag.putString("BoulderRainVfx", boulderRainVfx);
        tag.putInt("BoulderRainMinMark", rainMinMarkRadius);
        tag.putInt("BoulderRainMinDrop", rainMinDropBlocks);
        rainMarkSound.writeToNBT(tag, "BoulderRainMarkSound");
        tag.put("BoulderRainEffects", boulderRainEffects.writeToNBT());
        boulderRainCastSpot.writeToNBT(tag, "BoulderRain");
    }

    void readFromNBT(CompoundTag tag) {
        boulderRainEnabled = tag.getBoolean("BoulderRainEnabled");
        boulderRainAnimation = clean(tag.getString("BoulderRainAnimation"));
        boulderRainActionDelayTicks = value(tag, "BoulderRainActionDelayTicks", 16, 0, 1200);
        boulderRainCooldownTicks = value(tag, "BoulderRainCooldownTicks", 240, 1, 12000);
        setRing(
                value(tag, "BoulderRainRadius", 12, 2, 48),
                value(tag, "BoulderRainMinRadius", 0, 0, 47));
        boulderRainCount = value(tag, "BoulderRainCount", 8, 1, 32);
        boulderRainIntervalTicks = value(tag, "BoulderRainIntervalTicks", 4, 0, 100);
        boulderRainFallHeight = value(tag, "BoulderRainFallHeight", 16, 4, 48);
        boulderRainBlock = tag.contains("BoulderRainBlock")
                ? clean(tag.getString("BoulderRainBlock")) : "minecraft:stone";
        boulderRainStyle = BoulderStyles.normalize(tag.getString("BoulderRainStyle"));
        boulderRainScale = value(tag, "BoulderRainScale", 12,
                BossBoulderSettings.MIN_SCALE, BossBoulderSettings.MAX_SCALE);
        boulderRainDamage = value(tag, "BoulderRainDamage", 10, 0, 1000);
        boulderRainKnockback = value(tag, "BoulderRainKnockback", 2, 0, 10);
        boulderRainShatterRadius = value(tag, "BoulderRainShatterRadius", 2, 0, 16);
        boulderRainShatterDamage = value(tag, "BoulderRainShatterDamage", 4, 0, 1000);
        boulderRainVfx = AreaVfxStyles.normalize(tag.getString("BoulderRainVfx"));
        // A boss saved before these were settings carries neither, and rains on the numbers
        // that used to be literals in the scheduler.
        rainMinMarkRadius = value(tag, "BoulderRainMinMark", 10, MIN_MARK, MAX_MARK);
        rainMinDropBlocks = value(tag, "BoulderRainMinDrop", 10, 0, MAX_MIN_DROP);
        rainMarkSound.readFromNBT(tag, "BoulderRainMarkSound");
        boulderRainEffects.readFromNBT(tag, "BoulderRainEffects");
        boulderRainCastSpot.readFromNBT(tag, "BoulderRain");
    }
}
