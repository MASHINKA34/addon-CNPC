package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The hurricane: storms let loose on the arena floor, how they travel, and what they do to
 * whoever they run into.
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#hurricane()}.
 * It owns its own save format under the {@code Hurricane} prefix; a boss saved before the
 * hurricane existed carries none of these keys and reads back with the ability switched off.</p>
 */
public final class BossHurricaneSettings {

    public static final int MAX_COUNT = 12;
    /** Tenths of a block per tick: 1 is a crawl, 30 is three blocks a tick. */
    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 30;
    public static final int MIN_RANGE = 4;
    public static final int MAX_RANGE = 64;
    public static final int MIN_LIFETIME_TICKS = 20;
    public static final int MAX_LIFETIME_TICKS = 1200;
    /** Tenths of a block: the eye's catch radius. */
    public static final int MIN_RADIUS = 5;
    public static final int MAX_RADIUS = 60;
    public static final int MAX_BOUNCES = 20;
    public static final int MIN_SPIRAL_DEGREES = 1;
    public static final int MAX_SPIRAL_DEGREES = 90;
    public static final int MAX_SPIRAL_GROWTH = 20;
    public static final int MIN_LIFT_HEIGHT = 1;
    public static final int MAX_LIFT_HEIGHT = 12;
    public static final int MIN_LIFT_TICKS = 1;
    public static final int MAX_LIFT_TICKS = 60;
    public static final int MIN_HOLD_TICKS = 5;
    public static final int MAX_HOLD_TICKS = 600;
    public static final int MAX_SPIN_DEGREES = 90;
    public static final int MAX_ORBIT_RADIUS = 40;
    public static final int MAX_VICTIMS = 16;
    public static final int MAX_DAMAGE = 1000;
    public static final int MIN_DAMAGE_INTERVAL = 1;
    public static final int MAX_DAMAGE_INTERVAL = 200;
    public static final int MAX_THROW = 40;
    public static final int MAX_GRACE_TICKS = 400;
    public static final int MIN_FLOOR_SEARCH = 1;
    public static final int MAX_FLOOR_SEARCH = 16;
    /** Tenths of a block: how tall the column of particles stands, and how tall the storm's box is. */
    public static final int MIN_COLUMN_HEIGHT = 10;
    public static final int MAX_COLUMN_HEIGHT = 160;
    public static final int MAX_COLUMN_DENSITY = 40;
    public static final int MIN_LOOP_INTERVAL = 5;
    public static final int MAX_LOOP_INTERVAL = 200;

    private boolean hurricaneEnabled;
    private String hurricaneAnimation = "";
    private int hurricaneActionDelayTicks = 20;
    private int hurricaneCooldownTicks = 400;
    private int hurricaneLaunchMode = BossPhaseData.HURRICANE_MODE_STRAIGHT;
    private int hurricaneAim = BossPhaseData.HURRICANE_AIM_TARGET;
    private int hurricaneTargetMode = BossTargetMode.MAIN;
    /** Storms in a spiral and in a typhoon; the straight launch has one and a cross four. */
    private int hurricaneCount = 3;
    private int hurricaneSpeed = 4;
    /** The path of a straight storm or a cross, the widest a spiral gets, the spread of a typhoon. */
    private int hurricaneRange = 16;
    private int hurricaneLifetimeTicks = 200;
    private int hurricaneRadius = 15;
    private int hurricaneBounces;
    private int hurricaneSpiralDegrees = 12;
    private int hurricaneSpiralGrowth = 2;
    private int hurricaneLiftHeight = 4;
    private int hurricaneLiftTicks = 10;
    private int hurricaneHoldTicks = 60;
    /** Degrees a tick: the victim's ride round the eye, and the turn of their view with it. */
    private int hurricaneSpinDegrees = 15;
    private int hurricaneOrbitRadius = 8;
    private boolean hurricaneSpinView = true;
    private int hurricaneMaxVictims = 4;
    private int hurricaneDamage = 4;
    private int hurricaneDamageIntervalTicks = 20;
    private final BossEffectSet hurricaneEffects = new BossEffectSet();
    private int hurricaneThrow = 6;
    private int hurricaneThrowUp = 6;
    private int hurricaneGraceTicks = 40;
    private int hurricaneFloorSearch = 4;
    private int hurricaneColumnHeight = 60;
    private int hurricaneColumnDensity = 6;
    private int hurricaneLoopIntervalTicks = 20;
    private final BossSoundCue hurricaneLaunchSound =
            new BossSoundCue("minecraft:entity.breeze.wind_burst", 1.5F, 0.8F);
    private final BossSoundCue hurricaneLoopSound =
            new BossSoundCue("minecraft:entity.breeze.idle_air", 1.0F, 1.0F);
    private final BossSoundCue hurricaneCatchSound =
            new BossSoundCue("minecraft:entity.breeze.jump", 1.0F, 1.2F);
    private final BossSoundCue hurricaneReleaseSound =
            new BossSoundCue("minecraft:entity.wind_charge.wind_burst", 1.0F, 0.9F);
    private final BossParticleCue hurricaneColumnParticles = new BossParticleCue("minecraft:cloud", 1);
    /** The ability's own colour by default: the ring at the storm's foot is drawn in it. */
    private final BossParticleCue hurricaneBaseParticles = new BossParticleCue(BossParticleCue.DUST_ID, 8);
    private final BossParticleCue hurricaneTrailParticles = new BossParticleCue("minecraft:gust", 1);
    private final BossParticleCue hurricaneCatchParticles =
            new BossParticleCue("minecraft:gust_emitter_small", 1);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot hurricaneCastSpot = new BossCastSpot();

    public boolean isEnabled() { return hurricaneEnabled; }

    public void setEnabled(boolean value) { hurricaneEnabled = value; }

    /** The wind-up: played while the paths are marked, before the storms set off. */
    public String getAnimation() { return hurricaneAnimation; }

    public void setAnimation(String value) { hurricaneAnimation = clean(value); }

    public int getActionDelayTicks() { return hurricaneActionDelayTicks; }

    public void setActionDelayTicks(int value) { hurricaneActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return hurricaneCooldownTicks; }

    public void setCooldownTicks(int value) { hurricaneCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** How the storms are let go: one ahead, a cross, a spiral or a typhoon. */
    public int getLaunchMode() { return hurricaneLaunchMode; }

    public void setLaunchMode(int value) {
        hurricaneLaunchMode = Mth.clamp(value, BossPhaseData.HURRICANE_MODE_STRAIGHT,
                BossPhaseData.HURRICANE_MODE_TYPHOON);
    }

    /** Whether the one straight storm goes at whoever the cast picked or the way the boss faces. */
    public int getAim() { return hurricaneAim; }

    public void setAim(int value) {
        hurricaneAim = Mth.clamp(value, BossPhaseData.HURRICANE_AIM_TARGET, BossPhaseData.HURRICANE_AIM_FACING);
    }

    /** Who the straight storm is aimed at; only read when it goes at a target. */
    public int getTargetMode() { return hurricaneTargetMode; }

    public void setTargetMode(int value) { hurricaneTargetMode = BossTargetMode.clamp(value); }

    /** Whether this cast needs somebody to aim at before it can start. */
    public boolean isAimedAtTarget() {
        return hurricaneLaunchMode == BossPhaseData.HURRICANE_MODE_STRAIGHT
                && hurricaneAim == BossPhaseData.HURRICANE_AIM_TARGET;
    }

    /** Whether the storms leave along committed axes, which the boss turns onto and the mark follows. */
    public boolean facesAxis() {
        return hurricaneLaunchMode == BossPhaseData.HURRICANE_MODE_STRAIGHT
                || hurricaneLaunchMode == BossPhaseData.HURRICANE_MODE_CROSS
                || hurricaneLaunchMode == BossPhaseData.HURRICANE_MODE_DIAGONAL;
    }

    /** The other two launches: the storms spread out from the boss rather than along a line. */
    public boolean spreadsFromBoss() {
        return !facesAxis();
    }

    public int getCount() { return hurricaneCount; }

    public void setCount(int value) { hurricaneCount = Mth.clamp(value, 1, MAX_COUNT); }

    /** How fast a storm travels, in tenths of a block per tick. */
    public int getSpeedTenths() { return hurricaneSpeed; }

    public void setSpeedTenths(int value) { hurricaneSpeed = Mth.clamp(value, MIN_SPEED, MAX_SPEED); }

    public double getSpeed() { return hurricaneSpeed / 10.0D; }

    public int getRange() { return hurricaneRange; }

    public void setRange(int value) { hurricaneRange = Mth.clamp(value, MIN_RANGE, MAX_RANGE); }

    public int getLifetimeTicks() { return hurricaneLifetimeTicks; }

    public void setLifetimeTicks(int value) {
        hurricaneLifetimeTicks = Mth.clamp(value, MIN_LIFETIME_TICKS, MAX_LIFETIME_TICKS);
    }

    /** Tenths of a block: how far from the eye somebody is caught. */
    public int getRadiusTenths() { return hurricaneRadius; }

    public void setRadiusTenths(int value) { hurricaneRadius = Mth.clamp(value, MIN_RADIUS, MAX_RADIUS); }

    public double getRadius() { return hurricaneRadius / 10.0D; }

    /** How many walls a storm comes off before it dies against one. */
    public int getBounces() { return hurricaneBounces; }

    public void setBounces(int value) { hurricaneBounces = Mth.clamp(value, 0, MAX_BOUNCES); }

    /** A spiral storm's turn round the boss, in degrees a tick. */
    public int getSpiralDegrees() { return hurricaneSpiralDegrees; }

    public void setSpiralDegrees(int value) {
        hurricaneSpiralDegrees = Mth.clamp(value, MIN_SPIRAL_DEGREES, MAX_SPIRAL_DEGREES);
    }

    /** Tenths of a block: how much wider a spiral storm's circle gets each tick. */
    public int getSpiralGrowthTenths() { return hurricaneSpiralGrowth; }

    public void setSpiralGrowthTenths(int value) { hurricaneSpiralGrowth = Mth.clamp(value, 0, MAX_SPIRAL_GROWTH); }

    public double getSpiralGrowth() { return hurricaneSpiralGrowth / 10.0D; }

    /** How high above the storm's floor a victim is carried, in blocks. */
    public int getLiftHeight() { return hurricaneLiftHeight; }

    public void setLiftHeight(int value) { hurricaneLiftHeight = Mth.clamp(value, MIN_LIFT_HEIGHT, MAX_LIFT_HEIGHT); }

    public int getLiftTicks() { return hurricaneLiftTicks; }

    public void setLiftTicks(int value) { hurricaneLiftTicks = Mth.clamp(value, MIN_LIFT_TICKS, MAX_LIFT_TICKS); }

    public int getHoldTicks() { return hurricaneHoldTicks; }

    public void setHoldTicks(int value) { hurricaneHoldTicks = Mth.clamp(value, MIN_HOLD_TICKS, MAX_HOLD_TICKS); }

    public int getSpinDegrees() { return hurricaneSpinDegrees; }

    public void setSpinDegrees(int value) { hurricaneSpinDegrees = Mth.clamp(value, 0, MAX_SPIN_DEGREES); }

    /** Tenths of a block: how far from the eye a held victim rides. */
    public int getOrbitRadiusTenths() { return hurricaneOrbitRadius; }

    public void setOrbitRadiusTenths(int value) { hurricaneOrbitRadius = Mth.clamp(value, 0, MAX_ORBIT_RADIUS); }

    public double getOrbitRadius() { return hurricaneOrbitRadius / 10.0D; }

    /** Whether a held player's view is turned with them, which is what makes the screen spin. */
    public boolean isSpinView() { return hurricaneSpinView; }

    public void setSpinView(boolean value) { hurricaneSpinView = value; }

    public int getMaxVictims() { return hurricaneMaxVictims; }

    public void setMaxVictims(int value) { hurricaneMaxVictims = Mth.clamp(value, 1, MAX_VICTIMS); }

    public int getDamage() { return hurricaneDamage; }

    public void setDamage(int value) { hurricaneDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    public int getDamageIntervalTicks() { return hurricaneDamageIntervalTicks; }

    public void setDamageIntervalTicks(int value) {
        hurricaneDamageIntervalTicks = Mth.clamp(value, MIN_DAMAGE_INTERVAL, MAX_DAMAGE_INTERVAL);
    }

    /** What whoever the storm catches is given, on the catch and with every hit. */
    public BossEffectSet getEffects() { return hurricaneEffects; }

    /** Tenths of a block per tick: the sideways throw out of the eye on the way out. */
    public int getThrowTenths() { return hurricaneThrow; }

    public void setThrowTenths(int value) { hurricaneThrow = Mth.clamp(value, 0, MAX_THROW); }

    public double getThrow() { return hurricaneThrow / 10.0D; }

    /** Tenths of a block per tick: the upward half of the same throw. */
    public int getThrowUpTenths() { return hurricaneThrowUp; }

    public void setThrowUpTenths(int value) { hurricaneThrowUp = Mth.clamp(value, 0, MAX_THROW); }

    public double getThrowUp() { return hurricaneThrowUp / 10.0D; }

    /** How long after the throw nobody's storm takes the same victim again. */
    public int getGraceTicks() { return hurricaneGraceTicks; }

    public void setGraceTicks(int value) { hurricaneGraceTicks = Mth.clamp(value, 0, MAX_GRACE_TICKS); }

    /** How far below a storm the floor may be before it counts as a hole the storm dies over. */
    public int getFloorSearch() { return hurricaneFloorSearch; }

    public void setFloorSearch(int value) {
        hurricaneFloorSearch = Mth.clamp(value, MIN_FLOOR_SEARCH, MAX_FLOOR_SEARCH);
    }

    /** Tenths of a block: how tall the column stands, and how tall the box a wall stops is. */
    public int getColumnHeightTenths() { return hurricaneColumnHeight; }

    public void setColumnHeightTenths(int value) {
        hurricaneColumnHeight = Mth.clamp(value, MIN_COLUMN_HEIGHT, MAX_COLUMN_HEIGHT);
    }

    public double getColumnHeight() { return hurricaneColumnHeight / 10.0D; }

    /** Particles up the column each tick; nought draws no column at all. */
    public int getColumnDensity() { return hurricaneColumnDensity; }

    public void setColumnDensity(int value) { hurricaneColumnDensity = Mth.clamp(value, 0, MAX_COLUMN_DENSITY); }

    public int getLoopIntervalTicks() { return hurricaneLoopIntervalTicks; }

    public void setLoopIntervalTicks(int value) {
        hurricaneLoopIntervalTicks = Mth.clamp(value, MIN_LOOP_INTERVAL, MAX_LOOP_INTERVAL);
    }

    public BossSoundCue getLaunchSound() { return hurricaneLaunchSound; }

    public BossSoundCue getLoopSound() { return hurricaneLoopSound; }

    public BossSoundCue getCatchSound() { return hurricaneCatchSound; }

    public BossSoundCue getReleaseSound() { return hurricaneReleaseSound; }

    public BossParticleCue getColumnParticles() { return hurricaneColumnParticles; }

    public BossParticleCue getBaseParticles() { return hurricaneBaseParticles; }

    public BossParticleCue getTrailParticles() { return hurricaneTrailParticles; }

    public BossParticleCue getCatchParticles() { return hurricaneCatchParticles; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return hurricaneCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("HurricaneEnabled", hurricaneEnabled);
        tag.putString("HurricaneAnimation", hurricaneAnimation);
        tag.putInt("HurricaneActionDelayTicks", hurricaneActionDelayTicks);
        tag.putInt("HurricaneCooldownTicks", hurricaneCooldownTicks);
        tag.putInt("HurricaneLaunchMode", hurricaneLaunchMode);
        tag.putInt("HurricaneAim", hurricaneAim);
        tag.putInt("HurricaneTargetMode", hurricaneTargetMode);
        tag.putInt("HurricaneCount", hurricaneCount);
        tag.putInt("HurricaneSpeed", hurricaneSpeed);
        tag.putInt("HurricaneRange", hurricaneRange);
        tag.putInt("HurricaneLifetimeTicks", hurricaneLifetimeTicks);
        tag.putInt("HurricaneRadius", hurricaneRadius);
        tag.putInt("HurricaneBounces", hurricaneBounces);
        tag.putInt("HurricaneSpiralDegrees", hurricaneSpiralDegrees);
        tag.putInt("HurricaneSpiralGrowth", hurricaneSpiralGrowth);
        tag.putInt("HurricaneLiftHeight", hurricaneLiftHeight);
        tag.putInt("HurricaneLiftTicks", hurricaneLiftTicks);
        tag.putInt("HurricaneHoldTicks", hurricaneHoldTicks);
        tag.putInt("HurricaneSpinDegrees", hurricaneSpinDegrees);
        tag.putInt("HurricaneOrbitRadius", hurricaneOrbitRadius);
        tag.putBoolean("HurricaneSpinView", hurricaneSpinView);
        tag.putInt("HurricaneMaxVictims", hurricaneMaxVictims);
        tag.putInt("HurricaneDamage", hurricaneDamage);
        tag.putInt("HurricaneDamageIntervalTicks", hurricaneDamageIntervalTicks);
        tag.put("HurricaneEffects", hurricaneEffects.writeToNBT());
        tag.putInt("HurricaneThrow", hurricaneThrow);
        tag.putInt("HurricaneThrowUp", hurricaneThrowUp);
        tag.putInt("HurricaneGraceTicks", hurricaneGraceTicks);
        tag.putInt("HurricaneFloorSearch", hurricaneFloorSearch);
        tag.putInt("HurricaneColumnHeight", hurricaneColumnHeight);
        tag.putInt("HurricaneColumnDensity", hurricaneColumnDensity);
        tag.putInt("HurricaneLoopIntervalTicks", hurricaneLoopIntervalTicks);
        hurricaneLaunchSound.writeToNBT(tag, "HurricaneLaunchSound");
        hurricaneLoopSound.writeToNBT(tag, "HurricaneLoopSound");
        hurricaneCatchSound.writeToNBT(tag, "HurricaneCatchSound");
        hurricaneReleaseSound.writeToNBT(tag, "HurricaneReleaseSound");
        hurricaneColumnParticles.writeToNBT(tag, "HurricaneColumnParticles");
        hurricaneBaseParticles.writeToNBT(tag, "HurricaneBaseParticles");
        hurricaneTrailParticles.writeToNBT(tag, "HurricaneTrailParticles");
        hurricaneCatchParticles.writeToNBT(tag, "HurricaneCatchParticles");
        hurricaneCastSpot.writeToNBT(tag, "Hurricane");
    }

    void readFromNBT(CompoundTag tag) {
        hurricaneEnabled = tag.getBoolean("HurricaneEnabled");
        hurricaneAnimation = clean(tag.getString("HurricaneAnimation"));
        hurricaneActionDelayTicks = value(tag, "HurricaneActionDelayTicks", 20, 0, 1200);
        hurricaneCooldownTicks = value(tag, "HurricaneCooldownTicks", 400, 1, 12000);
        hurricaneLaunchMode = value(tag, "HurricaneLaunchMode", BossPhaseData.HURRICANE_MODE_STRAIGHT,
                BossPhaseData.HURRICANE_MODE_STRAIGHT, BossPhaseData.HURRICANE_MODE_TYPHOON);
        hurricaneAim = value(tag, "HurricaneAim", BossPhaseData.HURRICANE_AIM_TARGET,
                BossPhaseData.HURRICANE_AIM_TARGET, BossPhaseData.HURRICANE_AIM_FACING);
        hurricaneTargetMode = value(tag, "HurricaneTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        hurricaneCount = value(tag, "HurricaneCount", 3, 1, MAX_COUNT);
        hurricaneSpeed = value(tag, "HurricaneSpeed", 4, MIN_SPEED, MAX_SPEED);
        hurricaneRange = value(tag, "HurricaneRange", 16, MIN_RANGE, MAX_RANGE);
        hurricaneLifetimeTicks = value(tag, "HurricaneLifetimeTicks", 200, MIN_LIFETIME_TICKS, MAX_LIFETIME_TICKS);
        hurricaneRadius = value(tag, "HurricaneRadius", 15, MIN_RADIUS, MAX_RADIUS);
        hurricaneBounces = value(tag, "HurricaneBounces", 0, 0, MAX_BOUNCES);
        hurricaneSpiralDegrees = value(tag, "HurricaneSpiralDegrees", 12, MIN_SPIRAL_DEGREES, MAX_SPIRAL_DEGREES);
        hurricaneSpiralGrowth = value(tag, "HurricaneSpiralGrowth", 2, 0, MAX_SPIRAL_GROWTH);
        hurricaneLiftHeight = value(tag, "HurricaneLiftHeight", 4, MIN_LIFT_HEIGHT, MAX_LIFT_HEIGHT);
        hurricaneLiftTicks = value(tag, "HurricaneLiftTicks", 10, MIN_LIFT_TICKS, MAX_LIFT_TICKS);
        hurricaneHoldTicks = value(tag, "HurricaneHoldTicks", 60, MIN_HOLD_TICKS, MAX_HOLD_TICKS);
        hurricaneSpinDegrees = value(tag, "HurricaneSpinDegrees", 15, 0, MAX_SPIN_DEGREES);
        hurricaneOrbitRadius = value(tag, "HurricaneOrbitRadius", 8, 0, MAX_ORBIT_RADIUS);
        hurricaneSpinView = !tag.contains("HurricaneSpinView") || tag.getBoolean("HurricaneSpinView");
        hurricaneMaxVictims = value(tag, "HurricaneMaxVictims", 4, 1, MAX_VICTIMS);
        hurricaneDamage = value(tag, "HurricaneDamage", 4, 0, MAX_DAMAGE);
        hurricaneDamageIntervalTicks = value(tag, "HurricaneDamageIntervalTicks", 20,
                MIN_DAMAGE_INTERVAL, MAX_DAMAGE_INTERVAL);
        hurricaneEffects.readFromNBT(tag, "HurricaneEffects");
        hurricaneThrow = value(tag, "HurricaneThrow", 6, 0, MAX_THROW);
        hurricaneThrowUp = value(tag, "HurricaneThrowUp", 6, 0, MAX_THROW);
        hurricaneGraceTicks = value(tag, "HurricaneGraceTicks", 40, 0, MAX_GRACE_TICKS);
        hurricaneFloorSearch = value(tag, "HurricaneFloorSearch", 4, MIN_FLOOR_SEARCH, MAX_FLOOR_SEARCH);
        hurricaneColumnHeight = value(tag, "HurricaneColumnHeight", 60, MIN_COLUMN_HEIGHT, MAX_COLUMN_HEIGHT);
        hurricaneColumnDensity = value(tag, "HurricaneColumnDensity", 6, 0, MAX_COLUMN_DENSITY);
        hurricaneLoopIntervalTicks = value(tag, "HurricaneLoopIntervalTicks", 20, MIN_LOOP_INTERVAL, MAX_LOOP_INTERVAL);
        hurricaneLaunchSound.readFromNBT(tag, "HurricaneLaunchSound");
        hurricaneLoopSound.readFromNBT(tag, "HurricaneLoopSound");
        hurricaneCatchSound.readFromNBT(tag, "HurricaneCatchSound");
        hurricaneReleaseSound.readFromNBT(tag, "HurricaneReleaseSound");
        hurricaneColumnParticles.readFromNBT(tag, "HurricaneColumnParticles");
        hurricaneBaseParticles.readFromNBT(tag, "HurricaneBaseParticles");
        hurricaneTrailParticles.readFromNBT(tag, "HurricaneTrailParticles");
        hurricaneCatchParticles.readFromNBT(tag, "HurricaneCatchParticles");
        hurricaneCastSpot.readFromNBT(tag, "Hurricane");
    }
}
