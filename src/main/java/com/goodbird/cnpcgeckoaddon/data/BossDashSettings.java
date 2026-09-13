package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The charge down a committed line, what it does to whoever it meets, and what a wall does
 * to the boss instead.
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#dash()}.
 * It owns its own save format under the {@code Dash} prefix; a boss saved before the dash
 * existed carries none of these keys and reads back with the ability switched off.</p>
 */
public final class BossDashSettings {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 64;
    /** Tenths of a block per tick: 2 is a brisk walk, 30 is three blocks a tick. */
    public static final int MIN_SPEED = 2;
    public static final int MAX_SPEED = 30;
    public static final int MAX_WIDTH = 6;
    public static final int MAX_HEIGHT = 8;
    public static final int MAX_DAMAGE = 1000;
    public static final int MAX_KNOCKBACK = 10;
    public static final int MAX_STUN_TICKS = 1200;
    /** What a stunned boss takes, as a percentage: never less than a hit's own worth. */
    public static final int MIN_TAKEN_PERCENT = 100;
    public static final int MAX_TAKEN_PERCENT = 1000;
    public static final int MAX_SLAM_RADIUS = 16;

    private boolean dashEnabled;
    private String dashAnimation = "";
    private int dashActionDelayTicks = 12;
    private int dashCooldownTicks = 240;
    private int dashDirection = BossPhaseData.DASH_DIRECTION_TARGET;
    private int dashTargetMode = BossTargetMode.MAIN;
    private int dashLength = 12;
    private int dashSpeed = 8;
    private int dashWidth = 2;
    private int dashHeight = 3;
    private int dashDamage = 12;
    private int dashKnockback = 3;
    private final BossEffectSet dashEffects = new BossEffectSet();
    /** Off: the run carries on through everyone in the lane and hits each of them once. */
    private boolean dashStopOnHit = true;
    private int dashWallMode = BossPhaseData.DASH_WALL_STUN;
    private int dashStunTicks = 60;
    private int dashStunDamagePercent = 200;
    private String dashStunAnimation = "";
    private int dashSlamRadius = 4;
    private int dashSlamDamage = 10;
    private int dashSlamKnockback = 2;
    private String dashSlamVfx = AreaVfxStyles.NONE;
    /** Whether running through the chain between two leashed victims stuns the boss. */
    private boolean dashChainStun = true;
    private int dashChainStunTicks = 80;
    private int dashChainDamagePercent = 200;
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot dashCastSpot = new BossCastSpot();

    public boolean isEnabled() { return dashEnabled; }

    public void setEnabled(boolean value) { dashEnabled = value; }

    /** The wind-up: played while the corridor is marked, before the run. */
    public String getAnimation() { return dashAnimation; }

    public void setAnimation(String value) { dashAnimation = clean(value); }

    public int getActionDelayTicks() { return dashActionDelayTicks; }

    public void setActionDelayTicks(int value) { dashActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return dashCooldownTicks; }

    public void setCooldownTicks(int value) { dashCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Whether the run goes at whoever the dash picked or the way the boss is facing. */
    public int getDirection() { return dashDirection; }

    public void setDirection(int value) {
        dashDirection = Mth.clamp(value, BossPhaseData.DASH_DIRECTION_TARGET, BossPhaseData.DASH_DIRECTION_FACING);
    }

    /** Who the run is aimed at; only read when it runs at a target. */
    public int getTargetMode() { return dashTargetMode; }

    public void setTargetMode(int value) { dashTargetMode = BossTargetMode.clamp(value); }

    /** The longest run, in blocks, before the home leash has its say. */
    public int getLength() { return dashLength; }

    public void setLength(int value) { dashLength = Mth.clamp(value, MIN_LENGTH, MAX_LENGTH); }

    /** How fast the boss runs, in tenths of a block per tick. */
    public int getSpeed() { return dashSpeed; }

    public void setSpeed(int value) { dashSpeed = Mth.clamp(value, MIN_SPEED, MAX_SPEED); }

    /** The full width of the lane the run hits in, half of it to either side of the line. */
    public int getWidth() { return dashWidth; }

    public void setWidth(int value) { dashWidth = Mth.clamp(value, 1, MAX_WIDTH); }

    /** How far up from the boss' feet the lane reaches. */
    public int getHeight() { return dashHeight; }

    public void setHeight(int value) { dashHeight = Mth.clamp(value, 1, MAX_HEIGHT); }

    public int getDamage() { return dashDamage; }

    public void setDamage(int value) { dashDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** The shove down the line the run was going. */
    public int getKnockback() { return dashKnockback; }

    public void setKnockback(int value) { dashKnockback = Mth.clamp(value, 0, MAX_KNOCKBACK); }

    /** What whoever the run meets is given, the wall's slam included. */
    public BossEffectSet getEffects() { return dashEffects; }

    public boolean isStopOnHit() { return dashStopOnHit; }

    public void setStopOnHit(boolean value) { dashStopOnHit = value; }

    /** What running into a wall does to the boss. */
    public int getWallMode() { return dashWallMode; }

    public void setWallMode(int value) {
        dashWallMode = Mth.clamp(value, BossPhaseData.DASH_WALL_STUN, BossPhaseData.DASH_WALL_RAGE);
    }

    public int getStunTicks() { return dashStunTicks; }

    public void setStunTicks(int value) { dashStunTicks = Mth.clamp(value, 0, MAX_STUN_TICKS); }

    /** What the boss takes while a wall has it stunned, as a percentage of each hit. */
    public int getStunDamagePercent() { return dashStunDamagePercent; }

    public void setStunDamagePercent(int value) {
        dashStunDamagePercent = Mth.clamp(value, MIN_TAKEN_PERCENT, MAX_TAKEN_PERCENT);
    }

    /** Played as the boss is stunned, by a wall or by a chain. */
    public String getStunAnimation() { return dashStunAnimation; }

    public void setStunAnimation(String value) { dashStunAnimation = clean(value); }

    public int getSlamRadius() { return dashSlamRadius; }

    public void setSlamRadius(int value) { dashSlamRadius = Mth.clamp(value, 1, MAX_SLAM_RADIUS); }

    public int getSlamDamage() { return dashSlamDamage; }

    public void setSlamDamage(int value) { dashSlamDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** The shove away from the boss the wall's slam throws out. */
    public int getSlamKnockback() { return dashSlamKnockback; }

    public void setSlamKnockback(int value) { dashSlamKnockback = Mth.clamp(value, 0, MAX_KNOCKBACK); }

    public String getSlamVfx() { return dashSlamVfx; }

    public void setSlamVfx(String value) { dashSlamVfx = AreaVfxStyles.normalize(value); }

    public boolean isChainStun() { return dashChainStun; }

    public void setChainStun(boolean value) { dashChainStun = value; }

    public int getChainStunTicks() { return dashChainStunTicks; }

    public void setChainStunTicks(int value) { dashChainStunTicks = Mth.clamp(value, 0, MAX_STUN_TICKS); }

    /** What the boss takes while a chain has it stunned, as a percentage of each hit. */
    public int getChainDamagePercent() { return dashChainDamagePercent; }

    public void setChainDamagePercent(int value) {
        dashChainDamagePercent = Mth.clamp(value, MIN_TAKEN_PERCENT, MAX_TAKEN_PERCENT);
    }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return dashCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("DashEnabled", dashEnabled);
        tag.putString("DashAnimation", dashAnimation);
        tag.putInt("DashActionDelayTicks", dashActionDelayTicks);
        tag.putInt("DashCooldownTicks", dashCooldownTicks);
        tag.putInt("DashDirection", dashDirection);
        tag.putInt("DashTargetMode", dashTargetMode);
        tag.putInt("DashLength", dashLength);
        tag.putInt("DashSpeed", dashSpeed);
        tag.putInt("DashWidth", dashWidth);
        tag.putInt("DashHeight", dashHeight);
        tag.putInt("DashDamage", dashDamage);
        tag.putInt("DashKnockback", dashKnockback);
        tag.put("DashEffects", dashEffects.writeToNBT());
        tag.putBoolean("DashStopOnHit", dashStopOnHit);
        tag.putInt("DashWallMode", dashWallMode);
        tag.putInt("DashStunTicks", dashStunTicks);
        tag.putInt("DashStunDamagePercent", dashStunDamagePercent);
        tag.putString("DashStunAnimation", dashStunAnimation);
        tag.putInt("DashSlamRadius", dashSlamRadius);
        tag.putInt("DashSlamDamage", dashSlamDamage);
        tag.putInt("DashSlamKnockback", dashSlamKnockback);
        tag.putString("DashSlamVfx", dashSlamVfx);
        tag.putBoolean("DashChainStun", dashChainStun);
        tag.putInt("DashChainStunTicks", dashChainStunTicks);
        tag.putInt("DashChainDamagePercent", dashChainDamagePercent);
        dashCastSpot.writeToNBT(tag, "Dash");
    }

    void readFromNBT(CompoundTag tag) {
        dashEnabled = tag.getBoolean("DashEnabled");
        dashAnimation = clean(tag.getString("DashAnimation"));
        dashActionDelayTicks = value(tag, "DashActionDelayTicks", 12, 0, 1200);
        dashCooldownTicks = value(tag, "DashCooldownTicks", 240, 1, 12000);
        dashDirection = value(tag, "DashDirection", BossPhaseData.DASH_DIRECTION_TARGET,
                BossPhaseData.DASH_DIRECTION_TARGET, BossPhaseData.DASH_DIRECTION_FACING);
        dashTargetMode = value(tag, "DashTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        dashLength = value(tag, "DashLength", 12, MIN_LENGTH, MAX_LENGTH);
        dashSpeed = value(tag, "DashSpeed", 8, MIN_SPEED, MAX_SPEED);
        dashWidth = value(tag, "DashWidth", 2, 1, MAX_WIDTH);
        dashHeight = value(tag, "DashHeight", 3, 1, MAX_HEIGHT);
        dashDamage = value(tag, "DashDamage", 12, 0, MAX_DAMAGE);
        dashKnockback = value(tag, "DashKnockback", 3, 0, MAX_KNOCKBACK);
        dashEffects.readFromNBT(tag, "DashEffects");
        dashStopOnHit = !tag.contains("DashStopOnHit") || tag.getBoolean("DashStopOnHit");
        dashWallMode = value(tag, "DashWallMode", BossPhaseData.DASH_WALL_STUN,
                BossPhaseData.DASH_WALL_STUN, BossPhaseData.DASH_WALL_RAGE);
        dashStunTicks = value(tag, "DashStunTicks", 60, 0, MAX_STUN_TICKS);
        dashStunDamagePercent = value(tag, "DashStunDamagePercent", 200, MIN_TAKEN_PERCENT, MAX_TAKEN_PERCENT);
        dashStunAnimation = clean(tag.getString("DashStunAnimation"));
        dashSlamRadius = value(tag, "DashSlamRadius", 4, 1, MAX_SLAM_RADIUS);
        dashSlamDamage = value(tag, "DashSlamDamage", 10, 0, MAX_DAMAGE);
        dashSlamKnockback = value(tag, "DashSlamKnockback", 2, 0, MAX_KNOCKBACK);
        dashSlamVfx = AreaVfxStyles.normalize(tag.getString("DashSlamVfx"));
        dashChainStun = !tag.contains("DashChainStun") || tag.getBoolean("DashChainStun");
        dashChainStunTicks = value(tag, "DashChainStunTicks", 80, 0, MAX_STUN_TICKS);
        dashChainDamagePercent = value(tag, "DashChainDamagePercent", 200, MIN_TAKEN_PERCENT, MAX_TAKEN_PERCENT);
        dashCastSpot.readFromNBT(tag, "Dash");
    }
}
