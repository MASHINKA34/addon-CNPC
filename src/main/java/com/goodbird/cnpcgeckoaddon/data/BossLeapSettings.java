package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The jump, its flight and the slam it lands with.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#leap()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossLeapSettings {

    /** A percentage: how much further than the gap the flight is aimed, to cover vanilla's drag. */
    public static final int MIN_REACH_PERCENT = 90;
    public static final int MAX_REACH_PERCENT = 130;
    /** Tenths of a block a tick. */
    public static final int MIN_MAX_SPEED = 5;
    public static final int MAX_MAX_SPEED = 100;
    public static final int MIN_LAUNCH_GRACE = 1;
    public static final int MAX_LAUNCH_GRACE = 40;
    public static final int MIN_VFX_TICKS = 1;
    public static final int MAX_VFX_TICKS = 200;
    /** Tenths of a block kept inside the home leash, so a landing does not reset the fight. */
    public static final int MAX_LEASH_MARGIN = 100;
    public static final int MAX_DEBRIS = 200;

    private boolean leapEnabled;
    private String leapAnimation = "";
    private String leapLandAnimation = "";
    private int leapActionDelayTicks = 12;
    private int leapCooldownTicks = 200;
    private int leapMode = BossPhaseData.LEAP_MODE_UP;
    private int leapTargetMode = BossTargetMode.MAIN;
    private int leapHeight = 8;
    private int leapMinRange = 4;
    private int leapMaxRange = 24;
    private int leapOffsetX;
    private int leapOffsetY;
    private int leapOffsetZ;
    private int leapFixedX;
    private int leapFixedY;
    private int leapFixedZ;
    private int leapImpactDamage = 10;
    private int leapImpactRadius = 4;
    private int leapImpactKnockback = 2;
    /** Ends a leap that never lands, so a jump into a pit cannot strand the boss in mid air. */
    private int leapMaxAirTicks = 100;
    private boolean leapTelegraph = true;
    private String leapVfx = AreaVfxStyles.NONE;
    private boolean leapBlockWave;
    private final BossEffectSet leapEffects = new BossEffectSet();
    /** Fudge on the horizontal reach, so drag does not leave the boss just short of the ring. */
    private int leapReachPercent = 103;
    /** The fastest the flight is ever driven, in tenths of a block a tick. */
    private int leapMaxSpeed = 40;
    /** Ticks after the push before a boss still on the floor counts as a leap that never left. */
    private int leapLaunchGraceTicks = 5;
    private int leapVfxTicks = 20;
    /** Tenths of a block: how far inside the home leash a landing is pulled. */
    private int leapLeashMargin = 15;
    /** How much of the floor it landed on is kicked up around its feet. */
    private int leapDebrisCount = 30;
    private final BossSoundCue leapTakeoffSound =
            new BossSoundCue("minecraft:entity.ravager.roar", 1.5F, 1.2F);
    private final BossParticleCue leapTakeoffParticles = new BossParticleCue("minecraft:cloud", 20);
    private final BossSoundCue leapLandingSound = new BossSoundCue("minecraft:block.anvil.land", 2.0F, 0.5F);
    private final BossParticleCue leapLandingParticles = new BossParticleCue("minecraft:explosion", 1);
    /** One per point of the ring the landing is marked with, drawn every few ticks. */
    private final BossParticleCue leapTrailParticles = new BossParticleCue("minecraft:small_flame", 1);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot leapCastSpot = new BossCastSpot();

    public boolean isEnabled() { return leapEnabled; }

    public void setEnabled(boolean value) { leapEnabled = value; }

    public String getAnimation() { return leapAnimation; }

    public void setAnimation(String value) { leapAnimation = clean(value); }

    /** Played the moment the boss touches down, alongside the slam itself. */
    public String getLandAnimation() { return leapLandAnimation; }

    public void setLandAnimation(String value) { leapLandAnimation = clean(value); }

    public int getActionDelayTicks() { return leapActionDelayTicks; }

    public void setActionDelayTicks(int value) { leapActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return leapCooldownTicks; }

    public void setCooldownTicks(int value) { leapCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getMode() { return leapMode; }

    public void setMode(int value) { leapMode = Mth.clamp(value, BossPhaseData.LEAP_MODE_UP, BossPhaseData.LEAP_MODE_ARENA_OFFSET); }

    public int getTargetMode() { return leapTargetMode; }

    public void setTargetMode(int value) { leapTargetMode = BossTargetMode.clamp(value); }

    /** How high the arc climbs above the spot the boss pushed off from. */
    public int getHeight() { return leapHeight; }

    public void setHeight(int value) { leapHeight = Mth.clamp(value, 1, BossPhaseData.MAX_LEAP_HEIGHT); }

    public int getMinRange() { return leapMinRange; }

    public int getMaxRange() { return leapMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        leapMinRange = Math.min(min, max);
        leapMaxRange = Math.max(min, max);
    }

    public int getOffsetX() { return leapOffsetX; }

    public int getOffsetY() { return leapOffsetY; }

    public int getOffsetZ() { return leapOffsetZ; }

    public void setOffset(int x, int y, int z) {
        leapOffsetX = Mth.clamp(x, -64, 64);
        leapOffsetY = Mth.clamp(y, -64, 64);
        leapOffsetZ = Mth.clamp(z, -64, 64);
    }

    public int getFixedX() { return leapFixedX; }

    public int getFixedY() { return leapFixedY; }

    public int getFixedZ() { return leapFixedZ; }

    public void setFixed(int x, int y, int z) {
        leapFixedX = Mth.clamp(x, -BossPhaseData.MAX_LEAP_COORDINATE, BossPhaseData.MAX_LEAP_COORDINATE);
        leapFixedY = Mth.clamp(y, -BossPhaseData.MAX_LEAP_COORDINATE, BossPhaseData.MAX_LEAP_COORDINATE);
        leapFixedZ = Mth.clamp(z, -BossPhaseData.MAX_LEAP_COORDINATE, BossPhaseData.MAX_LEAP_COORDINATE);
    }

    public int getImpactDamage() { return leapImpactDamage; }

    public void setImpactDamage(int value) { leapImpactDamage = Mth.clamp(value, 0, 1000); }

    public int getImpactRadius() { return leapImpactRadius; }

    public void setImpactRadius(int value) { leapImpactRadius = Mth.clamp(value, 1, 32); }

    public int getImpactKnockback() { return leapImpactKnockback; }

    public void setImpactKnockback(int value) { leapImpactKnockback = Mth.clamp(value, 0, 10); }

    public int getMaxAirTicks() { return leapMaxAirTicks; }

    public void setMaxAirTicks(int value) { leapMaxAirTicks = Mth.clamp(value, 20, 400); }

    /** Draws the landing ring through the windup and the flight. */
    public boolean isTelegraph() { return leapTelegraph; }

    public void setTelegraph(boolean value) { leapTelegraph = value; }

    public String getVfx() { return leapVfx; }

    public void setVfx(String value) { leapVfx = AreaVfxStyles.normalize(value); }

    public boolean isBlockWave() { return leapBlockWave; }

    public void setBlockWave(boolean value) { leapBlockWave = value; }

    public BossEffectSet getEffects() { return leapEffects; }

    public int getReachPercent() { return leapReachPercent; }

    public void setReachPercent(int value) {
        leapReachPercent = Mth.clamp(value, MIN_REACH_PERCENT, MAX_REACH_PERCENT);
    }

    public double getReachCorrection() { return leapReachPercent / 100.0D; }

    public int getMaxSpeedTenths() { return leapMaxSpeed; }

    public void setMaxSpeedTenths(int value) {
        leapMaxSpeed = Mth.clamp(value, MIN_MAX_SPEED, MAX_MAX_SPEED);
    }

    public double getMaxSpeed() { return leapMaxSpeed / 10.0D; }

    public int getLaunchGraceTicks() { return leapLaunchGraceTicks; }

    public void setLaunchGraceTicks(int value) {
        leapLaunchGraceTicks = Mth.clamp(value, MIN_LAUNCH_GRACE, MAX_LAUNCH_GRACE);
    }

    /** How long the wave the slam sends out travels for. */
    public int getVfxTicks() { return leapVfxTicks; }

    public void setVfxTicks(int value) { leapVfxTicks = Mth.clamp(value, MIN_VFX_TICKS, MAX_VFX_TICKS); }

    public int getLeashMarginTenths() { return leapLeashMargin; }

    public void setLeashMarginTenths(int value) {
        leapLeashMargin = Mth.clamp(value, 0, MAX_LEASH_MARGIN);
    }

    /** The dash reads this too: the two stop inside the same edge. */
    public double getLeashMargin() { return leapLeashMargin / 10.0D; }

    public int getDebrisCount() { return leapDebrisCount; }

    public void setDebrisCount(int value) { leapDebrisCount = Mth.clamp(value, 0, MAX_DEBRIS); }

    public BossSoundCue getTakeoffSound() { return leapTakeoffSound; }

    public BossParticleCue getTakeoffParticles() { return leapTakeoffParticles; }

    public BossSoundCue getLandingSound() { return leapLandingSound; }

    public BossParticleCue getLandingParticles() { return leapLandingParticles; }

    public BossParticleCue getTrailParticles() { return leapTrailParticles; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return leapCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("LeapEnabled", leapEnabled);
        tag.putString("LeapAnimation", leapAnimation);
        tag.putString("LeapLandAnimation", leapLandAnimation);
        tag.putInt("LeapActionDelayTicks", leapActionDelayTicks);
        tag.putInt("LeapCooldownTicks", leapCooldownTicks);
        tag.putInt("LeapMode", leapMode);
        tag.putInt("LeapTargetMode", leapTargetMode);
        tag.putInt("LeapHeight", leapHeight);
        tag.putInt("LeapMinRange", leapMinRange);
        tag.putInt("LeapMaxRange", leapMaxRange);
        tag.putInt("LeapOffsetX", leapOffsetX);
        tag.putInt("LeapOffsetY", leapOffsetY);
        tag.putInt("LeapOffsetZ", leapOffsetZ);
        tag.putInt("LeapFixedX", leapFixedX);
        tag.putInt("LeapFixedY", leapFixedY);
        tag.putInt("LeapFixedZ", leapFixedZ);
        tag.putInt("LeapImpactDamage", leapImpactDamage);
        tag.putInt("LeapImpactRadius", leapImpactRadius);
        tag.putInt("LeapImpactKnockback", leapImpactKnockback);
        tag.putInt("LeapMaxAirTicks", leapMaxAirTicks);
        tag.putBoolean("LeapTelegraph", leapTelegraph);
        tag.putString("LeapVfx", leapVfx);
        tag.putBoolean("LeapBlockWave", leapBlockWave);
        tag.put("LeapEffects", leapEffects.writeToNBT());
        tag.putInt("LeapReachPercent", leapReachPercent);
        tag.putInt("LeapMaxSpeed", leapMaxSpeed);
        tag.putInt("LeapLaunchGrace", leapLaunchGraceTicks);
        tag.putInt("LeapVfxTicks", leapVfxTicks);
        tag.putInt("LeapLeashMargin", leapLeashMargin);
        tag.putInt("LeapDebris", leapDebrisCount);
        leapTakeoffSound.writeToNBT(tag, "LeapTakeoffSound");
        leapTakeoffParticles.writeToNBT(tag, "LeapTakeoffParticles");
        leapLandingSound.writeToNBT(tag, "LeapLandingSound");
        leapLandingParticles.writeToNBT(tag, "LeapLandingParticles");
        leapTrailParticles.writeToNBT(tag, "LeapTrailParticles");
        leapCastSpot.writeToNBT(tag, "Leap");
    }

    void readFromNBT(CompoundTag tag) {
        leapEnabled = tag.getBoolean("LeapEnabled");
        leapAnimation = clean(tag.getString("LeapAnimation"));
        leapLandAnimation = clean(tag.getString("LeapLandAnimation"));
        leapActionDelayTicks = value(tag, "LeapActionDelayTicks", 12, 0, 1200);
        leapCooldownTicks = value(tag, "LeapCooldownTicks", 200, 1, 12000);
        leapMode = value(tag, "LeapMode", BossPhaseData.LEAP_MODE_UP, BossPhaseData.LEAP_MODE_UP, BossPhaseData.LEAP_MODE_ARENA_OFFSET);
        leapTargetMode = value(tag, "LeapTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        leapHeight = value(tag, "LeapHeight", 8, 1, BossPhaseData.MAX_LEAP_HEIGHT);
        setRange(
                value(tag, "LeapMinRange", 4, 0, 64),
                value(tag, "LeapMaxRange", 24, 1, 128));
        leapOffsetX = value(tag, "LeapOffsetX", 0, -64, 64);
        leapOffsetY = value(tag, "LeapOffsetY", 0, -64, 64);
        leapOffsetZ = value(tag, "LeapOffsetZ", 0, -64, 64);
        leapFixedX = value(tag, "LeapFixedX", 0, -BossPhaseData.MAX_LEAP_COORDINATE, BossPhaseData.MAX_LEAP_COORDINATE);
        leapFixedY = value(tag, "LeapFixedY", 0, -BossPhaseData.MAX_LEAP_COORDINATE, BossPhaseData.MAX_LEAP_COORDINATE);
        leapFixedZ = value(tag, "LeapFixedZ", 0, -BossPhaseData.MAX_LEAP_COORDINATE, BossPhaseData.MAX_LEAP_COORDINATE);
        leapImpactDamage = value(tag, "LeapImpactDamage", 10, 0, 1000);
        leapImpactRadius = value(tag, "LeapImpactRadius", 4, 1, 32);
        leapImpactKnockback = value(tag, "LeapImpactKnockback", 2, 0, 10);
        leapMaxAirTicks = value(tag, "LeapMaxAirTicks", 100, 20, 400);
        // A boss saved before the marker existed gets it: an unannounced slam that big
        // reads as an unfair death rather than as a mechanic.
        leapTelegraph = !tag.contains("LeapTelegraph") || tag.getBoolean("LeapTelegraph");
        leapVfx = AreaVfxStyles.normalize(tag.getString("LeapVfx"));
        leapBlockWave = tag.getBoolean("LeapBlockWave");
        leapEffects.readFromNBT(tag, "LeapEffects");
        // A boss saved before these were settings carries none of them and jumps on the
        // numbers that used to be literals in the flight itself.
        leapReachPercent = value(tag, "LeapReachPercent", 103, MIN_REACH_PERCENT, MAX_REACH_PERCENT);
        leapMaxSpeed = value(tag, "LeapMaxSpeed", 40, MIN_MAX_SPEED, MAX_MAX_SPEED);
        leapLaunchGraceTicks = value(tag, "LeapLaunchGrace", 5, MIN_LAUNCH_GRACE, MAX_LAUNCH_GRACE);
        leapVfxTicks = value(tag, "LeapVfxTicks", 20, MIN_VFX_TICKS, MAX_VFX_TICKS);
        leapLeashMargin = value(tag, "LeapLeashMargin", 15, 0, MAX_LEASH_MARGIN);
        leapDebrisCount = value(tag, "LeapDebris", 30, 0, MAX_DEBRIS);
        leapTakeoffSound.readFromNBT(tag, "LeapTakeoffSound");
        leapTakeoffParticles.readFromNBT(tag, "LeapTakeoffParticles");
        leapLandingSound.readFromNBT(tag, "LeapLandingSound");
        leapLandingParticles.readFromNBT(tag, "LeapLandingParticles");
        leapTrailParticles.readFromNBT(tag, "LeapTrailParticles");
        leapCastSpot.readFromNBT(tag, "Leap");
    }
}
