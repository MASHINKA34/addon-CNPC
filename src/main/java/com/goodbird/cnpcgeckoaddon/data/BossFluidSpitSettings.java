package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The lobbed ball of fluid and the puddle it leaves.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#fluidSpit()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossFluidSpitSettings {

    /** Degrees a tick the head turns while the strike aims; one is a boss that barely tracks. */
    public static final int MIN_AIM_TURN = 1;
    public static final int MAX_AIM_TURN = 180;

    // The throw itself, each in the unit its label names.
    public static final int MAX_ARC_LIFT = 100;
    public static final int MIN_VELOCITY = 2;
    public static final int MAX_VELOCITY = 60;
    public static final int MAX_INACCURACY = 200;
    public static final int MAX_GRAVITY = 300;
    public static final int MIN_PROJECTILE_LIFE = 20;
    public static final int MAX_PROJECTILE_LIFE = 1200;
    public static final int MAX_SPLASH_BASE = 100;
    public static final int MAX_SPLASH_PER_RADIUS = 50;

    private boolean fluidSpitEnabled;
    private String fluidSpitAnimation = "";
    private int fluidSpitActionDelayTicks = 12;
    private int fluidSpitCooldownTicks = 120;
    private String fluidSpitBlock = "minecraft:lava";
    private int fluidSpitLifetimeTicks = 60;
    private int fluidSpitRadius = 1;
    private int fluidSpitDamage = 0;
    private int fluidSpitMinRange = 2;
    private int fluidSpitMaxRange = 24;
    private int fluidSpitTargetMode = BossTargetMode.MAIN;
    private final BossEffectSet fluidSpitEffects = new BossEffectSet();
    /** How fast the head swings onto the victim as the glob leaves, in degrees a tick. */
    private int fluidAimTurnDegrees = 30;
    /** Hundredths of the flat distance added as lift, so the ball arcs onto the feet. */
    private int fluidArcLift = 20;
    /** Tenths of a block a tick the glob leaves at. */
    private int fluidVelocity = 12;
    /** Tenths of vanilla's spread unit: how far off the aim one throw may land. */
    private int fluidInaccuracy = 40;
    /** Thousandths of a block a tick the glob loses in flight. */
    private int fluidGravity = 50;
    /** Ticks a glob that never hits anything keeps flying before it gives up. */
    private int fluidProjectileLifeTicks = 200;
    /** Splash particles at radius zero, and how many each block of radius adds. */
    private int fluidSplashBase = 12;
    private int fluidSplashPerRadius = 8;
    private final BossSoundCue fluidSpitSound =
            new BossSoundCue("minecraft:entity.llama.spit", 1.0F, 0.8F);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot fluidSpitCastSpot = new BossCastSpot();

    public boolean isEnabled() { return fluidSpitEnabled; }

    public void setEnabled(boolean value) { fluidSpitEnabled = value; }

    public String getAnimation() { return fluidSpitAnimation; }

    public void setAnimation(String value) { fluidSpitAnimation = clean(value); }

    public int getActionDelayTicks() { return fluidSpitActionDelayTicks; }

    public void setActionDelayTicks(int value) {
        fluidSpitActionDelayTicks = Mth.clamp(value, 0, 1200);
    }

    public int getCooldownTicks() { return fluidSpitCooldownTicks; }

    public void setCooldownTicks(int value) {
        fluidSpitCooldownTicks = Mth.clamp(value, 1, 12000);
    }

    /** Block id of the fluid to spit, for example {@code minecraft:lava}. */
    public String getBlock() { return fluidSpitBlock; }

    public void setBlock(String value) { fluidSpitBlock = clean(value); }

    public int getLifetimeTicks() { return fluidSpitLifetimeTicks; }

    public void setLifetimeTicks(int value) {
        fluidSpitLifetimeTicks = Mth.clamp(value, 5, 1200);
    }

    public int getRadius() { return fluidSpitRadius; }

    public void setRadius(int value) { fluidSpitRadius = Mth.clamp(value, 0, 4); }

    public int getDamage() { return fluidSpitDamage; }

    public void setDamage(int value) { fluidSpitDamage = Mth.clamp(value, 0, 1000); }

    public int getMinRange() { return fluidSpitMinRange; }

    public int getMaxRange() { return fluidSpitMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        fluidSpitMinRange = Math.min(min, max);
        fluidSpitMaxRange = Math.max(min, max);
    }

    public int getTargetMode() { return fluidSpitTargetMode; }

    public void setTargetMode(int value) {
        fluidSpitTargetMode = BossTargetMode.clamp(value);
    }

    public boolean canSpit() { return fluidSpitEnabled && isConfigured(); }

    /** Whether there is a block to spit, whatever the switch says: all a chained start still needs. */
    public boolean isConfigured() { return !fluidSpitBlock.isEmpty(); }

    public BossEffectSet getEffects() { return fluidSpitEffects; }

    public int getAimTurnDegrees() { return fluidAimTurnDegrees; }

    public void setAimTurnDegrees(int value) {
        fluidAimTurnDegrees = Mth.clamp(value, MIN_AIM_TURN, MAX_AIM_TURN);
    }

    /** Hundredths: 20 is the fifth of the flat distance the arc was always lifted by. */
    public int getArcLiftHundredths() { return fluidArcLift; }

    public void setArcLiftHundredths(int value) { fluidArcLift = Mth.clamp(value, 0, MAX_ARC_LIFT); }

    public double getArcLift() { return fluidArcLift / 100.0D; }

    /** Tenths of a block a tick. */
    public int getVelocityTenths() { return fluidVelocity; }

    public void setVelocityTenths(int value) {
        fluidVelocity = Mth.clamp(value, MIN_VELOCITY, MAX_VELOCITY);
    }

    public float getVelocity() { return fluidVelocity / 10.0F; }

    /** Tenths of vanilla's spread unit; zero throws dead on the aim. */
    public int getInaccuracyTenths() { return fluidInaccuracy; }

    public void setInaccuracyTenths(int value) {
        fluidInaccuracy = Mth.clamp(value, 0, MAX_INACCURACY);
    }

    public float getInaccuracy() { return fluidInaccuracy / 10.0F; }

    /** Thousandths of a block a tick. */
    public int getGravityThousandths() { return fluidGravity; }

    public void setGravityThousandths(int value) { fluidGravity = Mth.clamp(value, 0, MAX_GRAVITY); }

    public double getGravity() { return fluidGravity / 1000.0D; }

    /** How long a glob that hits nothing lives before it gives up on its own. */
    public int getProjectileLifeTicks() { return fluidProjectileLifeTicks; }

    public void setProjectileLifeTicks(int value) {
        fluidProjectileLifeTicks = Mth.clamp(value, MIN_PROJECTILE_LIFE, MAX_PROJECTILE_LIFE);
    }

    public int getSplashBase() { return fluidSplashBase; }

    public void setSplashBase(int value) { fluidSplashBase = Mth.clamp(value, 0, MAX_SPLASH_BASE); }

    public int getSplashPerRadius() { return fluidSplashPerRadius; }

    public void setSplashPerRadius(int value) {
        fluidSplashPerRadius = Mth.clamp(value, 0, MAX_SPLASH_PER_RADIUS);
    }

    public BossSoundCue getSpitSound() { return fluidSpitSound; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return fluidSpitCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("FluidSpitEnabled", fluidSpitEnabled);
        tag.putString("FluidSpitAnimation", fluidSpitAnimation);
        tag.putInt("FluidSpitActionDelayTicks", fluidSpitActionDelayTicks);
        tag.putInt("FluidSpitCooldownTicks", fluidSpitCooldownTicks);
        tag.putString("FluidSpitBlock", fluidSpitBlock);
        tag.putInt("FluidSpitLifetimeTicks", fluidSpitLifetimeTicks);
        tag.putInt("FluidSpitRadius", fluidSpitRadius);
        tag.putInt("FluidSpitDamage", fluidSpitDamage);
        tag.putInt("FluidSpitMinRange", fluidSpitMinRange);
        tag.putInt("FluidSpitMaxRange", fluidSpitMaxRange);
        tag.putInt("FluidSpitTargetMode", fluidSpitTargetMode);
        tag.put("FluidSpitEffects", fluidSpitEffects.writeToNBT());
        tag.putInt("FluidAimTurn", fluidAimTurnDegrees);
        tag.putInt("FluidArcLift", fluidArcLift);
        tag.putInt("FluidVelocity", fluidVelocity);
        tag.putInt("FluidInaccuracy", fluidInaccuracy);
        tag.putInt("FluidGravity", fluidGravity);
        tag.putInt("FluidProjectileLife", fluidProjectileLifeTicks);
        tag.putInt("FluidSplashBase", fluidSplashBase);
        tag.putInt("FluidSplashPerRadius", fluidSplashPerRadius);
        fluidSpitSound.writeToNBT(tag, "FluidSpitSound");
        fluidSpitCastSpot.writeToNBT(tag, "FluidSpit");
    }

    void readFromNBT(CompoundTag tag) {
        fluidSpitEnabled = tag.getBoolean("FluidSpitEnabled");
        fluidSpitAnimation = clean(tag.getString("FluidSpitAnimation"));
        fluidSpitActionDelayTicks = value(tag, "FluidSpitActionDelayTicks", 12, 0, 1200);
        fluidSpitCooldownTicks = value(tag, "FluidSpitCooldownTicks", 120, 1, 12000);
        fluidSpitBlock = tag.contains("FluidSpitBlock")
                ? clean(tag.getString("FluidSpitBlock")) : "minecraft:lava";
        fluidSpitLifetimeTicks = value(tag, "FluidSpitLifetimeTicks", 60, 5, 1200);
        fluidSpitRadius = value(tag, "FluidSpitRadius", 1, 0, 4);
        fluidSpitDamage = value(tag, "FluidSpitDamage", 0, 0, 1000);
        setRange(
                value(tag, "FluidSpitMinRange", 2, 0, 64),
                value(tag, "FluidSpitMaxRange", 24, 1, 128));
        fluidSpitTargetMode = value(tag, "FluidSpitTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        fluidSpitEffects.readFromNBT(tag, "FluidSpitEffects");
        fluidAimTurnDegrees = value(tag, "FluidAimTurn", 30, MIN_AIM_TURN, MAX_AIM_TURN);
        // A boss saved before these were settings carries none of them and spits on the
        // numbers that used to be literals in the throw and in the glob itself.
        fluidArcLift = value(tag, "FluidArcLift", 20, 0, MAX_ARC_LIFT);
        fluidVelocity = value(tag, "FluidVelocity", 12, MIN_VELOCITY, MAX_VELOCITY);
        fluidInaccuracy = value(tag, "FluidInaccuracy", 40, 0, MAX_INACCURACY);
        fluidGravity = value(tag, "FluidGravity", 50, 0, MAX_GRAVITY);
        fluidProjectileLifeTicks = value(tag, "FluidProjectileLife", 200,
                MIN_PROJECTILE_LIFE, MAX_PROJECTILE_LIFE);
        fluidSplashBase = value(tag, "FluidSplashBase", 12, 0, MAX_SPLASH_BASE);
        fluidSplashPerRadius = value(tag, "FluidSplashPerRadius", 8, 0, MAX_SPLASH_PER_RADIUS);
        fluidSpitSound.readFromNBT(tag, "FluidSpitSound");
        fluidSpitCastSpot.readFromNBT(tag, "FluidSpit");
    }
}
