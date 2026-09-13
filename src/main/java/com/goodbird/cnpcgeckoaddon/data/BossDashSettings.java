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

    // The run's own slacks, every one of them in tenths of a block unless it says otherwise.
    public static final int MAX_MIN_REACH = 100;
    public static final int MIN_WALL_SHARE = 1;
    public static final int MAX_WALL_SHARE = 100;
    public static final int MIN_CONTACT_SLICE = 1;
    public static final int MAX_CONTACT_SLICE = 20;
    public static final int MAX_STEER = 50;
    public static final int MAX_SWEEP_SLACK = 50;
    public static final int MIN_TELEPORT_SLACK = 5;
    public static final int MAX_TELEPORT_SLACK = 100;
    public static final int MAX_CHAIN_HEIGHT_SLACK = 50;
    public static final int MIN_SLAM_VFX_TICKS = 1;
    public static final int MAX_SLAM_VFX_TICKS = 200;

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
    /** A lane the home leash cuts shorter than this is not worth running. */
    private int dashMinReach = 10;
    /**
     * A tick's progress below this share of the step it was given, with the boss up against
     * something, is a wall stopping it rather than a corner it is scraping past.
     */
    private int dashWallSharePercent = 25;
    /** How far down the lane past the first victim somebody still counts as met on the same step. */
    private int dashContactSlice = 4;
    /** The most the run steers back toward its line in one tick. */
    private int dashMaxSteer = 5;
    /** Extra room round the swept box, so a boss that drifted off its line still finds the lane's edge. */
    private int dashSweepSlack = 10;
    /** A move this much longer than a step in one tick was a carry or a teleport, not the run. */
    private int dashTeleportSlack = 20;
    /** How far above the boss' head or below its feet a chain still counts as across its path. */
    private int dashChainHeightSlack = 5;
    private int dashSlamVfxTicks = 20;
    private final BossSoundCue dashStartSound =
            new BossSoundCue("minecraft:entity.ravager.roar", 1.2F, 1.4F);
    private final BossParticleCue dashStartParticles = new BossParticleCue("minecraft:cloud", 12);
    private final BossSoundCue dashHitSound =
            new BossSoundCue("minecraft:entity.player.attack.knockback", 1.2F, 0.7F);
    private final BossSoundCue dashSlamSound = new BossSoundCue("minecraft:block.anvil.land", 2.0F, 0.5F);
    private final BossParticleCue dashSlamParticles = new BossParticleCue("minecraft:explosion", 1);
    private final BossSoundCue dashChainSound = new BossSoundCue("minecraft:block.chain.hit", 2.0F, 0.6F);
    /** The tether's own colour, which is what the chain the boss broke was drawn in. */
    private final BossParticleCue dashChainParticles = new BossParticleCue(BossParticleCue.DUST_ID, 20);
    private final BossSoundCue dashWallSound =
            new BossSoundCue("minecraft:entity.zombie.break_wooden_door", 1.0F, 0.7F);
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

    /** Tenths of a block: the shortest lane still worth running. */
    public int getMinReachTenths() { return dashMinReach; }

    public void setMinReachTenths(int value) { dashMinReach = Mth.clamp(value, 0, MAX_MIN_REACH); }

    public double getMinReach() { return dashMinReach / 10.0D; }

    public int getWallSharePercent() { return dashWallSharePercent; }

    public void setWallSharePercent(int value) {
        dashWallSharePercent = Mth.clamp(value, MIN_WALL_SHARE, MAX_WALL_SHARE);
    }

    public double getWallShare() { return dashWallSharePercent / 100.0D; }

    public int getContactSliceTenths() { return dashContactSlice; }

    public void setContactSliceTenths(int value) {
        dashContactSlice = Mth.clamp(value, MIN_CONTACT_SLICE, MAX_CONTACT_SLICE);
    }

    public double getContactSlice() { return dashContactSlice / 10.0D; }

    public int getMaxSteerTenths() { return dashMaxSteer; }

    public void setMaxSteerTenths(int value) { dashMaxSteer = Mth.clamp(value, 0, MAX_STEER); }

    public double getMaxSteer() { return dashMaxSteer / 10.0D; }

    public int getSweepSlackTenths() { return dashSweepSlack; }

    public void setSweepSlackTenths(int value) { dashSweepSlack = Mth.clamp(value, 0, MAX_SWEEP_SLACK); }

    public double getSweepSlack() { return dashSweepSlack / 10.0D; }

    public int getTeleportSlackTenths() { return dashTeleportSlack; }

    public void setTeleportSlackTenths(int value) {
        dashTeleportSlack = Mth.clamp(value, MIN_TELEPORT_SLACK, MAX_TELEPORT_SLACK);
    }

    public double getTeleportSlack() { return dashTeleportSlack / 10.0D; }

    public int getChainHeightSlackTenths() { return dashChainHeightSlack; }

    public void setChainHeightSlackTenths(int value) {
        dashChainHeightSlack = Mth.clamp(value, 0, MAX_CHAIN_HEIGHT_SLACK);
    }

    public double getChainHeightSlack() { return dashChainHeightSlack / 10.0D; }

    /** How long the wave the wall's slam sends out travels for. */
    public int getSlamVfxTicks() { return dashSlamVfxTicks; }

    public void setSlamVfxTicks(int value) {
        dashSlamVfxTicks = Mth.clamp(value, MIN_SLAM_VFX_TICKS, MAX_SLAM_VFX_TICKS);
    }

    public BossSoundCue getStartSound() { return dashStartSound; }

    public BossParticleCue getStartParticles() { return dashStartParticles; }

    public BossSoundCue getHitSound() { return dashHitSound; }

    public BossSoundCue getSlamSound() { return dashSlamSound; }

    public BossParticleCue getSlamParticles() { return dashSlamParticles; }

    public BossSoundCue getChainSound() { return dashChainSound; }

    public BossParticleCue getChainParticles() { return dashChainParticles; }

    public BossSoundCue getWallSound() { return dashWallSound; }

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
        tag.putInt("DashMinReach", dashMinReach);
        tag.putInt("DashWallShare", dashWallSharePercent);
        tag.putInt("DashContactSlice", dashContactSlice);
        tag.putInt("DashMaxSteer", dashMaxSteer);
        tag.putInt("DashSweepSlack", dashSweepSlack);
        tag.putInt("DashTeleportSlack", dashTeleportSlack);
        tag.putInt("DashChainHeightSlack", dashChainHeightSlack);
        tag.putInt("DashSlamVfxTicks", dashSlamVfxTicks);
        dashStartSound.writeToNBT(tag, "DashStartSound");
        dashStartParticles.writeToNBT(tag, "DashStartParticles");
        dashHitSound.writeToNBT(tag, "DashHitSound");
        dashSlamSound.writeToNBT(tag, "DashSlamSound");
        dashSlamParticles.writeToNBT(tag, "DashSlamParticles");
        dashChainSound.writeToNBT(tag, "DashChainSound");
        dashChainParticles.writeToNBT(tag, "DashChainParticles");
        dashWallSound.writeToNBT(tag, "DashWallSound");
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
        // A boss saved before these were settings carries none of them and runs on the
        // numbers that used to be literals in the run itself.
        dashMinReach = value(tag, "DashMinReach", 10, 0, MAX_MIN_REACH);
        dashWallSharePercent = value(tag, "DashWallShare", 25, MIN_WALL_SHARE, MAX_WALL_SHARE);
        dashContactSlice = value(tag, "DashContactSlice", 4, MIN_CONTACT_SLICE, MAX_CONTACT_SLICE);
        dashMaxSteer = value(tag, "DashMaxSteer", 5, 0, MAX_STEER);
        dashSweepSlack = value(tag, "DashSweepSlack", 10, 0, MAX_SWEEP_SLACK);
        dashTeleportSlack = value(tag, "DashTeleportSlack", 20, MIN_TELEPORT_SLACK, MAX_TELEPORT_SLACK);
        dashChainHeightSlack = value(tag, "DashChainHeightSlack", 5, 0, MAX_CHAIN_HEIGHT_SLACK);
        dashSlamVfxTicks = value(tag, "DashSlamVfxTicks", 20, MIN_SLAM_VFX_TICKS, MAX_SLAM_VFX_TICKS);
        dashStartSound.readFromNBT(tag, "DashStartSound");
        dashStartParticles.readFromNBT(tag, "DashStartParticles");
        dashHitSound.readFromNBT(tag, "DashHitSound");
        dashSlamSound.readFromNBT(tag, "DashSlamSound");
        dashSlamParticles.readFromNBT(tag, "DashSlamParticles");
        dashChainSound.readFromNBT(tag, "DashChainSound");
        dashChainParticles.readFromNBT(tag, "DashChainParticles");
        dashWallSound.readFromNBT(tag, "DashWallSound");
        dashCastSpot.readFromNBT(tag, "Dash");
    }
}
