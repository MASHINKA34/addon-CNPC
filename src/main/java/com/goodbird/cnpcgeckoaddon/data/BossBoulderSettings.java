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

    /**
     * The ranges the stone itself is held to, so the entity and the screen cannot drift apart:
     * a size the GUI offers and the entity then clamps away is a setting that lies.
     */
    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 20;
    public static final int MIN_SCALE = 5;
    public static final int MAX_SCALE = 40;

    // The roll, the arc and the break, each in the unit its label names.
    public static final int MAX_STEP_HEIGHT = 30;
    public static final int MIN_FALL_SPEED = 5;
    public static final int MAX_FALL_SPEED = 50;
    public static final int MIN_PIT_DEPTH = 1;
    public static final int MAX_PIT_DEPTH = 64;
    public static final int MIN_THROW_GRAVITY = 5;
    public static final int MAX_THROW_GRAVITY = 300;
    public static final int MAX_LIFETIME_MARGIN = 600;
    public static final int MIN_LIFETIME_MAX = 60;
    public static final int MAX_LIFETIME_MAX = 6000;
    public static final int MAX_DEBRIS = 100;
    public static final int MIN_SHATTER_VFX_TICKS = 1;
    public static final int MAX_SHATTER_VFX_TICKS = 200;
    public static final int MAX_MUZZLE_OFFSET = 300;

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
    /** Tenths of a block a rolling stone climbs; anything taller is a wall to it. */
    private int boulderStepHeight = 10;
    /** Tenths of a block a tick a falling stone may reach. */
    private int boulderMaxFallSpeed = 15;
    /** Blocks of floorless drop after which the stone is lost rather than still rolling. */
    private int boulderMaxPitDepth = 16;
    /** Thousandths of a block a tick a thrown stone loses to gravity. */
    private int boulderThrowGravity = 50;
    /** Slack on top of the flight, and the ceiling over the whole life, both in ticks. */
    private int boulderLifetimeMarginTicks = 60;
    private int boulderLifetimeMaxTicks = 1500;
    /** Debris at size zero, and how much each block of diameter adds. */
    private int boulderDebrisBase = 20;
    private int boulderDebrisPerSize = 15;
    /** How long the wave a shattering stone sends out travels for. */
    private int boulderShatterVfxTicks = 20;
    /** Hundredths of a block the stone clears the boss' own edge by as it leaves. */
    private int boulderMuzzleOffset = 25;
    /** Empty id: the stone is heard as the block it is made of, the way it always was. */
    private final BossSoundCue boulderThrowSound = new BossSoundCue("", 1.5F, 0.6F);
    private final BossSoundCue boulderBreakSound = new BossSoundCue("", 2.0F, 0.7F);
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

    public void setScale(int value) { boulderScale = Mth.clamp(value, MIN_SCALE, MAX_SCALE); }

    /** Travel speed in tenths of a block per tick. */
    public int getSpeed() { return boulderSpeed; }

    public void setSpeed(int value) { boulderSpeed = Mth.clamp(value, MIN_SPEED, MAX_SPEED); }

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
    public boolean canLaunch() { return boulderEnabled && isConfigured(); }

    /** Whether there is a block to make the stone of, whatever the switch says: all a chained start still needs. */
    public boolean isConfigured() { return !boulderBlock.isEmpty(); }

    /** Tenths of a block: 10 is the single stair the roll always climbed. */
    public int getStepHeightTenths() { return boulderStepHeight; }

    public void setStepHeightTenths(int value) {
        boulderStepHeight = Mth.clamp(value, 0, MAX_STEP_HEIGHT);
    }

    public double getStepHeight() { return boulderStepHeight / 10.0D; }

    /** Tenths of a block a tick. */
    public int getMaxFallSpeedTenths() { return boulderMaxFallSpeed; }

    public void setMaxFallSpeedTenths(int value) {
        boulderMaxFallSpeed = Mth.clamp(value, MIN_FALL_SPEED, MAX_FALL_SPEED);
    }

    public double getMaxFallSpeed() { return boulderMaxFallSpeed / 10.0D; }

    /** Blocks: a hole deeper than this is not a floor the stone is still rolling on. */
    public int getMaxPitDepth() { return boulderMaxPitDepth; }

    public void setMaxPitDepth(int value) {
        boulderMaxPitDepth = Mth.clamp(value, MIN_PIT_DEPTH, MAX_PIT_DEPTH);
    }

    /** Thousandths of a block a tick. */
    public int getThrowGravityThousandths() { return boulderThrowGravity; }

    public void setThrowGravityThousandths(int value) {
        boulderThrowGravity = Mth.clamp(value, MIN_THROW_GRAVITY, MAX_THROW_GRAVITY);
    }

    public double getThrowGravity() { return boulderThrowGravity / 1000.0D; }

    /** Ticks added on top of the flight the stone was launched for. */
    public int getLifetimeMarginTicks() { return boulderLifetimeMarginTicks; }

    public void setLifetimeMarginTicks(int value) {
        boulderLifetimeMarginTicks = Mth.clamp(value, 0, MAX_LIFETIME_MARGIN);
    }

    /** The ceiling on a stone's whole life, however long its flight was measured at. */
    public int getLifetimeMaxTicks() { return boulderLifetimeMaxTicks; }

    public void setLifetimeMaxTicks(int value) {
        boulderLifetimeMaxTicks = Mth.clamp(value, MIN_LIFETIME_MAX, MAX_LIFETIME_MAX);
    }

    public int getDebrisBase() { return boulderDebrisBase; }

    public void setDebrisBase(int value) { boulderDebrisBase = Mth.clamp(value, 0, MAX_DEBRIS); }

    public int getDebrisPerSize() { return boulderDebrisPerSize; }

    public void setDebrisPerSize(int value) {
        boulderDebrisPerSize = Mth.clamp(value, 0, MAX_DEBRIS);
    }

    public int getShatterVfxTicks() { return boulderShatterVfxTicks; }

    public void setShatterVfxTicks(int value) {
        boulderShatterVfxTicks = Mth.clamp(value, MIN_SHATTER_VFX_TICKS, MAX_SHATTER_VFX_TICKS);
    }

    /** Hundredths of a block past the boss' own edge and the stone's radius. */
    public int getMuzzleOffsetHundredths() { return boulderMuzzleOffset; }

    public void setMuzzleOffsetHundredths(int value) {
        boulderMuzzleOffset = Mth.clamp(value, 0, MAX_MUZZLE_OFFSET);
    }

    public double getMuzzleOffset() { return boulderMuzzleOffset / 100.0D; }

    /** With no id of its own, the place sound of the block the stone is made of. */
    public BossSoundCue getThrowSound() { return boulderThrowSound; }

    /** With no id of its own, the break sound of that same block. */
    public BossSoundCue getBreakSound() { return boulderBreakSound; }

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
        tag.putInt("BoulderStepHeight", boulderStepHeight);
        tag.putInt("BoulderMaxFallSpeed", boulderMaxFallSpeed);
        tag.putInt("BoulderMaxPitDepth", boulderMaxPitDepth);
        tag.putInt("BoulderThrowGravity", boulderThrowGravity);
        tag.putInt("BoulderLifetimeMargin", boulderLifetimeMarginTicks);
        tag.putInt("BoulderLifetimeMax", boulderLifetimeMaxTicks);
        tag.putInt("BoulderDebrisBase", boulderDebrisBase);
        tag.putInt("BoulderDebrisPerSize", boulderDebrisPerSize);
        tag.putInt("BoulderShatterVfx", boulderShatterVfxTicks);
        tag.putInt("BoulderMuzzle", boulderMuzzleOffset);
        boulderThrowSound.writeToNBT(tag, "BoulderThrowSound");
        boulderBreakSound.writeToNBT(tag, "BoulderBreakSound");
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
        boulderScale = value(tag, "BoulderScale", 15, MIN_SCALE, MAX_SCALE);
        boulderSpeed = value(tag, "BoulderSpeed", 6, MIN_SPEED, MAX_SPEED);
        boulderRange = value(tag, "BoulderRange", 20, 4, 64);
        boulderDamage = value(tag, "BoulderDamage", 12, 0, 1000);
        boulderKnockback = value(tag, "BoulderKnockback", 3, 0, 10);
        boulderStopsOnHit = tag.getBoolean("BoulderStopsOnHit");
        boulderShatterRadius = value(tag, "BoulderShatterRadius", 2, 0, 16);
        boulderShatterDamage = value(tag, "BoulderShatterDamage", 4, 0, 1000);
        boulderVfx = AreaVfxStyles.normalize(tag.getString("BoulderVfx"));
        // A boss saved before these were settings carries none of them and rolls on the
        // numbers that used to be literals in the stone itself.
        boulderStepHeight = value(tag, "BoulderStepHeight", 10, 0, MAX_STEP_HEIGHT);
        boulderMaxFallSpeed = value(tag, "BoulderMaxFallSpeed", 15, MIN_FALL_SPEED, MAX_FALL_SPEED);
        boulderMaxPitDepth = value(tag, "BoulderMaxPitDepth", 16, MIN_PIT_DEPTH, MAX_PIT_DEPTH);
        boulderThrowGravity = value(tag, "BoulderThrowGravity", 50,
                MIN_THROW_GRAVITY, MAX_THROW_GRAVITY);
        boulderLifetimeMarginTicks = value(tag, "BoulderLifetimeMargin", 60, 0, MAX_LIFETIME_MARGIN);
        boulderLifetimeMaxTicks = value(tag, "BoulderLifetimeMax", 1500,
                MIN_LIFETIME_MAX, MAX_LIFETIME_MAX);
        boulderDebrisBase = value(tag, "BoulderDebrisBase", 20, 0, MAX_DEBRIS);
        boulderDebrisPerSize = value(tag, "BoulderDebrisPerSize", 15, 0, MAX_DEBRIS);
        boulderShatterVfxTicks = value(tag, "BoulderShatterVfx", 20,
                MIN_SHATTER_VFX_TICKS, MAX_SHATTER_VFX_TICKS);
        boulderMuzzleOffset = value(tag, "BoulderMuzzle", 25, 0, MAX_MUZZLE_OFFSET);
        boulderThrowSound.readFromNBT(tag, "BoulderThrowSound");
        boulderBreakSound.readFromNBT(tag, "BoulderBreakSound");
        boulderEffects.readFromNBT(tag, "BoulderEffects");
        boulderCastSpot.readFromNBT(tag, "Boulder");
    }
}
