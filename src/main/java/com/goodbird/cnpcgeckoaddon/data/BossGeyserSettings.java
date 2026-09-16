package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The fuse under a victim and the column that follows it.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#geyser()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossGeyserSettings {

    /** The eruption's wave, its column and its boil, each in the unit its label names. */
    public static final int MIN_VFX_TICKS = 1;
    public static final int MAX_VFX_TICKS = 200;
    public static final int MAX_COLUMN_PER_RADIUS = 50;
    public static final int MAX_COLUMN_HEIGHT = 400;
    public static final int MAX_BOIL_SPEED = 100;

    /** The column is a line up through the middle of the circle, as it always was. */
    public static final int COLUMN_STRAIGHT = 0;
    /** The column comes up off the whole circle and narrows towards its top. */
    public static final int COLUMN_CONE = 1;
    public static final String[] COLUMN_SHAPE_LABELS = {
            "cnpcgeckoaddon.boss.geyser_column_shape.straight",
            "cnpcgeckoaddon.boss.geyser_column_shape.cone"
    };

    /** The cone's top, its rise and its dots, each in the unit its label names. */
    public static final int MAX_COLUMN_TOP_RADIUS = 160;
    public static final int MAX_COLUMN_RISE_TICKS = 100;
    public static final int MIN_COLUMN_POINTS = 1;
    public static final int MAX_COLUMN_POINTS = 24;

    /** The strike from above: when, from where, how long, how wide, how hard. */
    public static final int MIN_SKY_DELAY_TICKS = 1;
    public static final int MAX_SKY_DELAY_TICKS = 600;
    public static final int MIN_SKY_HEIGHT = 2;
    public static final int MAX_SKY_HEIGHT = 64;
    public static final int MIN_SKY_FALL_TICKS = 1;
    public static final int MAX_SKY_FALL_TICKS = 100;
    public static final int MAX_SKY_RADIUS = 16;
    public static final int MAX_SKY_PRESS = 60;

    /** The residue: how long it lies, how wide, its doses, its stacks and their fading. */
    public static final int MIN_RESIDUE_LIFETIME_TICKS = 20;
    public static final int MAX_RESIDUE_LIFETIME_TICKS = 12000;
    public static final int MAX_RESIDUE_RADIUS = 16;
    public static final int MIN_RESIDUE_INTERVAL_TICKS = 1;
    public static final int MAX_RESIDUE_INTERVAL_TICKS = 200;
    public static final int MIN_RESIDUE_STACK_TICKS = 1;
    public static final int MAX_RESIDUE_STACK_TICKS = 1200;
    public static final int MIN_RESIDUE_MAX_STACKS = 1;
    public static final int MAX_RESIDUE_MAX_STACKS = 20;
    public static final int MIN_RESIDUE_DECAY_TICKS = 1;
    public static final int MAX_RESIDUE_DECAY_TICKS = 1200;
    public static final int MIN_RESIDUE_HEIGHT = 1;
    public static final int MAX_RESIDUE_HEIGHT = 100;
    public static final int MIN_RESIDUE_SOUND_INTERVAL_TICKS = 5;
    public static final int MAX_RESIDUE_SOUND_INTERVAL_TICKS = 400;
    public static final int MAX_DAMAGE = 1000;

    private boolean geyserEnabled;
    private String geyserAnimation = "";
    private int geyserActionDelayTicks = 12;
    private int geyserCooldownTicks = 160;
    private int geyserTargetMode = BossTargetMode.RANDOM;
    private int geyserTargetCount = 1;
    private int geyserMinRange = 3;
    private int geyserMaxRange = 24;
    /** How long the mark sits on the floor before the column comes up through it. */
    private int geyserFuseTicks = 25;
    private int geyserRadius = 3;
    private int geyserDamage = 8;
    /** Tenths of a block per tick, so 8 throws a victim up at 0.8 blocks a tick. */
    private int geyserLaunch = 8;
    private boolean geyserFollowTarget;
    /** Empty leaves nothing behind; anything else is a block id the eruption pools. */
    private String geyserFluid = "";
    private int geyserFluidLifetimeTicks = 60;
    private String geyserVfx = AreaVfxStyles.NONE;
    private boolean geyserBlockWave;
    /** How long the wave that runs out of the eruption is drawn for. */
    private int geyserVfxTicks = 20;
    /** Tenths of a block the column climbs per block of radius, and the ends it is held between. */
    private int geyserColumnPerRadius = 15;
    private int geyserColumnMin = 30;
    private int geyserColumnMax = 120;
    /** Hundredths of a block a tick the boil at the middle spits, from lit to the last tick. */
    private int geyserBoilMin = 2;
    private int geyserBoilMax = 12;
    private final BossSoundCue geyserLitSound = new BossSoundCue("minecraft:block.lava.pop", 1.6F, 0.5F);
    private final BossSoundCue geyserEruptSound =
            new BossSoundCue("minecraft:block.lava.extinguish", 3.0F, 0.5F);
    private final BossEffectSet geyserEffects = new BossEffectSet();
    /** A line at the middle by default, which is the column every boss saved before the cone had. */
    private int geyserColumnShape = COLUMN_STRAIGHT;
    /** Tenths of a block; the cone's bottom is the geyser's own radius. */
    private int geyserColumnTopRadius = 5;
    /** Ticks the column takes to come up; nought is the whole of it at once, as it always was. */
    private int geyserColumnRiseTicks;
    private int geyserColumnPointsPerSlice = 6;
    /** The counts the column used to be drawn with, now the builder's to change. */
    private final BossParticleCue geyserColumnParticles = new BossParticleCue("minecraft:cloud", 2);
    private final BossParticleCue geyserColumnSmoke = new BossParticleCue("minecraft:large_smoke", 1);
    /** The second strike, from above, off by default: a boss saved before it existed erupts once. */
    private boolean geyserSkyEnabled;
    private int geyserSkyDelayTicks = 30;
    private int geyserSkyHeight = 12;
    private int geyserSkyFallTicks = 10;
    /** Nought is the geyser's own radius. */
    private int geyserSkyRadius;
    private int geyserSkyDamage = 6;
    /** Tenths of a block a tick the airborne are pressed down with; nought presses nobody. */
    private int geyserSkyPress = 12;
    private final BossEffectSet geyserSkyEffects = new BossEffectSet();
    private String geyserSkyVfx = AreaVfxStyles.NONE;
    private final BossParticleCue geyserSkyParticles = new BossParticleCue("minecraft:cloud", 3);
    private final BossSoundCue geyserSkySound = new BossSoundCue("minecraft:entity.generic.splash", 1.5F, 0.7F);
    private final BossSoundCue geyserSkyHitSound =
            new BossSoundCue("minecraft:entity.generic.explode", 1.0F, 0.8F);
    private final BossParticleCue geyserSkyHitParticles = new BossParticleCue("minecraft:splash", 12);
    /** The residue left on the floor, off by default for the same reason. */
    private boolean geyserResidueEnabled;
    private int geyserResidueLifetimeTicks = 200;
    /** Nought is the geyser's own radius. */
    private int geyserResidueRadius;
    /** Nought hurts nobody: the residue is then its potions and nothing else. */
    private int geyserResidueDamage = 2;
    private int geyserResidueIntervalTicks = 20;
    /** Ticks inside per stack, the ceiling on the stacks, and ticks outside per stack lost. */
    private int geyserResidueStackTicks = 40;
    private int geyserResidueMaxStacks = 4;
    private int geyserResidueDecayTicks = 60;
    /** Tenths of a block the residue reaches up off the floor. */
    private int geyserResidueHeight = 10;
    private final BossEffectSet geyserResidueEffects = new BossEffectSet();
    private int geyserResidueSoundIntervalTicks = 40;
    private final BossParticleCue geyserResidueParticles = new BossParticleCue("minecraft:bubble_pop", 4);
    private final BossSoundCue geyserResidueSound =
            new BossSoundCue("minecraft:block.bubble_column.bubble_pop", 0.6F, 0.9F);
    private final BossParticleCue geyserResidueHitParticles = new BossParticleCue("minecraft:smoke", 4);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot geyserCastSpot = new BossCastSpot();

    public boolean isEnabled() { return geyserEnabled; }

    public void setEnabled(boolean value) { geyserEnabled = value; }

    public String getAnimation() { return geyserAnimation; }

    public void setAnimation(String value) { geyserAnimation = clean(value); }

    public int getActionDelayTicks() { return geyserActionDelayTicks; }

    public void setActionDelayTicks(int value) { geyserActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return geyserCooldownTicks; }

    public void setCooldownTicks(int value) { geyserCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return geyserTargetMode; }

    public void setTargetMode(int value) { geyserTargetMode = BossTargetMode.clamp(value); }

    /** How many marks a single cast puts on the floor, one under each victim it picked. */
    public int getTargetCount() { return geyserTargetCount; }

    public void setTargetCount(int value) { geyserTargetCount = Mth.clamp(value, 1, 8); }

    public int getMinRange() { return geyserMinRange; }

    public int getMaxRange() { return geyserMaxRange; }

    public void setRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        geyserMinRange = Math.min(min, max);
        geyserMaxRange = Math.max(min, max);
    }

    /** The window a victim has to walk out of the circle, which is the whole mechanic. */
    public int getFuseTicks() { return geyserFuseTicks; }

    public void setFuseTicks(int value) { geyserFuseTicks = Mth.clamp(value, 5, 200); }

    public int getRadius() { return geyserRadius; }

    public void setRadius(int value) { geyserRadius = Mth.clamp(value, 1, 16); }

    public int getDamage() { return geyserDamage; }

    public void setDamage(int value) { geyserDamage = Mth.clamp(value, 0, 1000); }

    /** Upward throw in tenths of a block per tick; zero leaves the victim on the floor. */
    public int getLaunch() { return geyserLaunch; }

    public void setLaunch(int value) { geyserLaunch = Mth.clamp(value, 0, 20); }

    /** With this on the mark rides the victim, so there is nowhere to step out to. */
    public boolean isFollowTarget() { return geyserFollowTarget; }

    public void setFollowTarget(boolean value) { geyserFollowTarget = value; }

    public String getFluid() { return geyserFluid; }

    public void setFluid(String value) { geyserFluid = clean(value); }

    public int getFluidLifetimeTicks() { return geyserFluidLifetimeTicks; }

    public void setFluidLifetimeTicks(int value) {
        geyserFluidLifetimeTicks = Mth.clamp(value, 5, 1200);
    }

    public String getVfx() { return geyserVfx; }

    public void setVfx(String value) { geyserVfx = AreaVfxStyles.normalize(value); }

    public boolean isBlockWave() { return geyserBlockWave; }

    public void setBlockWave(boolean value) { geyserBlockWave = value; }

    /** Ticks the eruption's wave runs for. */
    public int getVfxTicks() { return geyserVfxTicks; }

    public void setVfxTicks(int value) {
        geyserVfxTicks = Mth.clamp(value, MIN_VFX_TICKS, MAX_VFX_TICKS);
    }

    /** Tenths of a block of column per block of radius. */
    public int getColumnPerRadiusTenths() { return geyserColumnPerRadius; }

    public void setColumnPerRadiusTenths(int value) {
        geyserColumnPerRadius = Mth.clamp(value, 0, MAX_COLUMN_PER_RADIUS);
    }

    /** The ends the column is held between, in tenths of a block. */
    public int getColumnMinTenths() { return geyserColumnMin; }

    public void setColumnMinTenths(int value) {
        geyserColumnMin = Mth.clamp(value, 0, MAX_COLUMN_HEIGHT);
    }

    public int getColumnMaxTenths() { return geyserColumnMax; }

    public void setColumnMaxTenths(int value) {
        geyserColumnMax = Mth.clamp(value, 0, MAX_COLUMN_HEIGHT);
    }

    /** Hundredths of a block a tick the boil spits on the tick the fuse was lit. */
    public int getBoilMinHundredths() { return geyserBoilMin; }

    public void setBoilMinHundredths(int value) {
        geyserBoilMin = Mth.clamp(value, 0, MAX_BOIL_SPEED);
    }

    /** And on its last tick, which is how the mark says how long is left. */
    public int getBoilMaxHundredths() { return geyserBoilMax; }

    public void setBoilMaxHundredths(int value) {
        geyserBoilMax = Mth.clamp(value, 0, MAX_BOIL_SPEED);
    }

    /** The hiss as the ground opens. */
    public BossSoundCue getLitSound() { return geyserLitSound; }

    public BossSoundCue getEruptSound() { return geyserEruptSound; }

    /** Whether the eruption pools anything, i.e. whether the fluid id is worth resolving. */
    public boolean leavesGeyserFluid() { return !geyserFluid.isEmpty(); }

    public BossEffectSet getEffects() { return geyserEffects; }

    /** {@link #COLUMN_STRAIGHT} or {@link #COLUMN_CONE}. */
    public int getColumnShape() { return geyserColumnShape; }

    public void setColumnShape(int value) { geyserColumnShape = Mth.clamp(value, COLUMN_STRAIGHT, COLUMN_CONE); }

    /** The cone's top in tenths of a block; its bottom is the geyser's radius. */
    public int getColumnTopRadiusTenths() { return geyserColumnTopRadius; }

    public void setColumnTopRadiusTenths(int value) {
        geyserColumnTopRadius = Mth.clamp(value, 0, MAX_COLUMN_TOP_RADIUS);
    }

    public double getColumnTopRadius() { return geyserColumnTopRadius / 10.0D; }

    /** Ticks the column takes to come up from the floor; nought draws the whole of it at once. */
    public int getColumnRiseTicks() { return geyserColumnRiseTicks; }

    public void setColumnRiseTicks(int value) {
        geyserColumnRiseTicks = Mth.clamp(value, 0, MAX_COLUMN_RISE_TICKS);
    }

    /** Dots round each slice of the cone; a straight column is one dot at the middle whatever this says. */
    public int getColumnPointsPerSlice() { return geyserColumnPointsPerSlice; }

    public void setColumnPointsPerSlice(int value) {
        geyserColumnPointsPerSlice = Mth.clamp(value, MIN_COLUMN_POINTS, MAX_COLUMN_POINTS);
    }

    public BossParticleCue getColumnParticles() { return geyserColumnParticles; }

    public BossParticleCue getColumnSmoke() { return geyserColumnSmoke; }

    /** Whether a second column falls onto the circle from above after the eruption. */
    public boolean isSkyEnabled() { return geyserSkyEnabled; }

    public void setSkyEnabled(boolean value) { geyserSkyEnabled = value; }

    /** Ticks after the eruption the fall begins. */
    public int getSkyDelayTicks() { return geyserSkyDelayTicks; }

    public void setSkyDelayTicks(int value) {
        geyserSkyDelayTicks = Mth.clamp(value, MIN_SKY_DELAY_TICKS, MAX_SKY_DELAY_TICKS);
    }

    /** Blocks above the floor the fall starts from. */
    public int getSkyHeight() { return geyserSkyHeight; }

    public void setSkyHeight(int value) { geyserSkyHeight = Mth.clamp(value, MIN_SKY_HEIGHT, MAX_SKY_HEIGHT); }

    /** Ticks the column takes to reach the floor. */
    public int getSkyFallTicks() { return geyserSkyFallTicks; }

    public void setSkyFallTicks(int value) {
        geyserSkyFallTicks = Mth.clamp(value, MIN_SKY_FALL_TICKS, MAX_SKY_FALL_TICKS);
    }

    /** The strike's own radius, or nought for the geyser's. */
    public int getSkyRadius() { return geyserSkyRadius; }

    public void setSkyRadius(int value) { geyserSkyRadius = Mth.clamp(value, 0, MAX_SKY_RADIUS); }

    public int getSkyDamage() { return geyserSkyDamage; }

    public void setSkyDamage(int value) { geyserSkyDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** Tenths of a block a tick whoever is off the floor is pressed down with; nought presses nobody. */
    public int getSkyPressTenths() { return geyserSkyPress; }

    public void setSkyPressTenths(int value) { geyserSkyPress = Mth.clamp(value, 0, MAX_SKY_PRESS); }

    public double getSkyPress() { return geyserSkyPress / 10.0D; }

    public BossEffectSet getSkyEffects() { return geyserSkyEffects; }

    /** The wave the strike from above throws out as it lands. */
    public String getSkyVfx() { return geyserSkyVfx; }

    public void setSkyVfx(String value) { geyserSkyVfx = AreaVfxStyles.normalize(value); }

    public BossParticleCue getSkyParticles() { return geyserSkyParticles; }

    public BossSoundCue getSkySound() { return geyserSkySound; }

    public BossSoundCue getSkyHitSound() { return geyserSkyHitSound; }

    public BossParticleCue getSkyHitParticles() { return geyserSkyHitParticles; }

    /** Whether the eruption leaves a residue that keeps hurting and stacking its potions. */
    public boolean isResidueEnabled() { return geyserResidueEnabled; }

    public void setResidueEnabled(boolean value) { geyserResidueEnabled = value; }

    public int getResidueLifetimeTicks() { return geyserResidueLifetimeTicks; }

    public void setResidueLifetimeTicks(int value) {
        geyserResidueLifetimeTicks = Mth.clamp(value, MIN_RESIDUE_LIFETIME_TICKS, MAX_RESIDUE_LIFETIME_TICKS);
    }

    /** The residue's own radius, or nought for the geyser's. */
    public int getResidueRadius() { return geyserResidueRadius; }

    public void setResidueRadius(int value) { geyserResidueRadius = Mth.clamp(value, 0, MAX_RESIDUE_RADIUS); }

    /** What a dose hurts for; nought is a residue of potions alone. */
    public int getResidueDamage() { return geyserResidueDamage; }

    public void setResidueDamage(int value) { geyserResidueDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    public int getResidueIntervalTicks() { return geyserResidueIntervalTicks; }

    public void setResidueIntervalTicks(int value) {
        geyserResidueIntervalTicks = Mth.clamp(value, MIN_RESIDUE_INTERVAL_TICKS, MAX_RESIDUE_INTERVAL_TICKS);
    }

    /** Ticks inside per stack. */
    public int getResidueStackTicks() { return geyserResidueStackTicks; }

    public void setResidueStackTicks(int value) {
        geyserResidueStackTicks = Mth.clamp(value, MIN_RESIDUE_STACK_TICKS, MAX_RESIDUE_STACK_TICKS);
    }

    public int getResidueMaxStacks() { return geyserResidueMaxStacks; }

    public void setResidueMaxStacks(int value) {
        geyserResidueMaxStacks = Mth.clamp(value, MIN_RESIDUE_MAX_STACKS, MAX_RESIDUE_MAX_STACKS);
    }

    /** Ticks outside per stack lost. */
    public int getResidueDecayTicks() { return geyserResidueDecayTicks; }

    public void setResidueDecayTicks(int value) {
        geyserResidueDecayTicks = Mth.clamp(value, MIN_RESIDUE_DECAY_TICKS, MAX_RESIDUE_DECAY_TICKS);
    }

    /** How far up off the floor the residue reaches, in tenths of a block. */
    public int getResidueHeightTenths() { return geyserResidueHeight; }

    public void setResidueHeightTenths(int value) {
        geyserResidueHeight = Mth.clamp(value, MIN_RESIDUE_HEIGHT, MAX_RESIDUE_HEIGHT);
    }

    public double getResidueHeight() { return geyserResidueHeight / 10.0D; }

    public BossEffectSet getResidueEffects() { return geyserResidueEffects; }

    public int getResidueSoundIntervalTicks() { return geyserResidueSoundIntervalTicks; }

    public void setResidueSoundIntervalTicks(int value) {
        geyserResidueSoundIntervalTicks = Mth.clamp(value, MIN_RESIDUE_SOUND_INTERVAL_TICKS,
                MAX_RESIDUE_SOUND_INTERVAL_TICKS);
    }

    public BossParticleCue getResidueParticles() { return geyserResidueParticles; }

    public BossSoundCue getResidueSound() { return geyserResidueSound; }

    public BossParticleCue getResidueHitParticles() { return geyserResidueHitParticles; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return geyserCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("GeyserEnabled", geyserEnabled);
        tag.putString("GeyserAnimation", geyserAnimation);
        tag.putInt("GeyserActionDelayTicks", geyserActionDelayTicks);
        tag.putInt("GeyserCooldownTicks", geyserCooldownTicks);
        tag.putInt("GeyserTargetMode", geyserTargetMode);
        tag.putInt("GeyserTargetCount", geyserTargetCount);
        tag.putInt("GeyserMinRange", geyserMinRange);
        tag.putInt("GeyserMaxRange", geyserMaxRange);
        tag.putInt("GeyserFuseTicks", geyserFuseTicks);
        tag.putInt("GeyserRadius", geyserRadius);
        tag.putInt("GeyserDamage", geyserDamage);
        tag.putInt("GeyserLaunch", geyserLaunch);
        tag.putBoolean("GeyserFollowTarget", geyserFollowTarget);
        tag.putString("GeyserFluid", geyserFluid);
        tag.putInt("GeyserFluidLifetime", geyserFluidLifetimeTicks);
        tag.putString("GeyserVfx", geyserVfx);
        tag.putBoolean("GeyserBlockWave", geyserBlockWave);
        tag.put("GeyserEffects", geyserEffects.writeToNBT());
        tag.putInt("GeyserVfxTicks", geyserVfxTicks);
        tag.putInt("GeyserColumnPerRadius", geyserColumnPerRadius);
        tag.putInt("GeyserColumnMin", geyserColumnMin);
        tag.putInt("GeyserColumnMax", geyserColumnMax);
        tag.putInt("GeyserBoilMin", geyserBoilMin);
        tag.putInt("GeyserBoilMax", geyserBoilMax);
        geyserLitSound.writeToNBT(tag, "GeyserLitSound");
        geyserEruptSound.writeToNBT(tag, "GeyserEruptSound");
        tag.putInt("GeyserColumnShape", geyserColumnShape);
        tag.putInt("GeyserColumnTopRadius", geyserColumnTopRadius);
        tag.putInt("GeyserColumnRiseTicks", geyserColumnRiseTicks);
        tag.putInt("GeyserColumnPoints", geyserColumnPointsPerSlice);
        geyserColumnParticles.writeToNBT(tag, "GeyserColumnParticles");
        geyserColumnSmoke.writeToNBT(tag, "GeyserColumnSmoke");
        tag.putBoolean("GeyserSkyEnabled", geyserSkyEnabled);
        tag.putInt("GeyserSkyDelayTicks", geyserSkyDelayTicks);
        tag.putInt("GeyserSkyHeight", geyserSkyHeight);
        tag.putInt("GeyserSkyFallTicks", geyserSkyFallTicks);
        tag.putInt("GeyserSkyRadius", geyserSkyRadius);
        tag.putInt("GeyserSkyDamage", geyserSkyDamage);
        tag.putInt("GeyserSkyPress", geyserSkyPress);
        tag.put("GeyserSkyEffects", geyserSkyEffects.writeToNBT());
        tag.putString("GeyserSkyVfx", geyserSkyVfx);
        geyserSkyParticles.writeToNBT(tag, "GeyserSkyParticles");
        geyserSkySound.writeToNBT(tag, "GeyserSkySound");
        geyserSkyHitSound.writeToNBT(tag, "GeyserSkyHitSound");
        geyserSkyHitParticles.writeToNBT(tag, "GeyserSkyHitParticles");
        tag.putBoolean("GeyserResidueEnabled", geyserResidueEnabled);
        tag.putInt("GeyserResidueLifetimeTicks", geyserResidueLifetimeTicks);
        tag.putInt("GeyserResidueRadius", geyserResidueRadius);
        tag.putInt("GeyserResidueDamage", geyserResidueDamage);
        tag.putInt("GeyserResidueIntervalTicks", geyserResidueIntervalTicks);
        tag.putInt("GeyserResidueStackTicks", geyserResidueStackTicks);
        tag.putInt("GeyserResidueMaxStacks", geyserResidueMaxStacks);
        tag.putInt("GeyserResidueDecayTicks", geyserResidueDecayTicks);
        tag.putInt("GeyserResidueHeight", geyserResidueHeight);
        tag.put("GeyserResidueEffects", geyserResidueEffects.writeToNBT());
        tag.putInt("GeyserResidueSoundIntervalTicks", geyserResidueSoundIntervalTicks);
        geyserResidueParticles.writeToNBT(tag, "GeyserResidueParticles");
        geyserResidueSound.writeToNBT(tag, "GeyserResidueSound");
        geyserResidueHitParticles.writeToNBT(tag, "GeyserResidueHitParticles");
        geyserCastSpot.writeToNBT(tag, "Geyser");
    }

    void readFromNBT(CompoundTag tag) {
        geyserEnabled = tag.getBoolean("GeyserEnabled");
        geyserAnimation = clean(tag.getString("GeyserAnimation"));
        geyserActionDelayTicks = value(tag, "GeyserActionDelayTicks", 12, 0, 1200);
        geyserCooldownTicks = value(tag, "GeyserCooldownTicks", 160, 1, 12000);
        geyserTargetMode = value(tag, "GeyserTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        geyserTargetCount = value(tag, "GeyserTargetCount", 1, 1, 8);
        setRange(
                value(tag, "GeyserMinRange", 3, 0, 64),
                value(tag, "GeyserMaxRange", 24, 1, 128));
        geyserFuseTicks = value(tag, "GeyserFuseTicks", 25, 5, 200);
        geyserRadius = value(tag, "GeyserRadius", 3, 1, 16);
        geyserDamage = value(tag, "GeyserDamage", 8, 0, 1000);
        geyserLaunch = value(tag, "GeyserLaunch", 8, 0, 20);
        geyserFollowTarget = tag.getBoolean("GeyserFollowTarget");
        geyserFluid = clean(tag.getString("GeyserFluid"));
        geyserFluidLifetimeTicks = value(tag, "GeyserFluidLifetime", 60, 5, 1200);
        geyserVfx = AreaVfxStyles.normalize(tag.getString("GeyserVfx"));
        geyserBlockWave = tag.getBoolean("GeyserBlockWave");
        geyserEffects.readFromNBT(tag, "GeyserEffects");
        geyserVfxTicks = value(tag, "GeyserVfxTicks", 20, MIN_VFX_TICKS, MAX_VFX_TICKS);
        geyserColumnPerRadius = value(tag, "GeyserColumnPerRadius", 15, 0, MAX_COLUMN_PER_RADIUS);
        geyserColumnMin = value(tag, "GeyserColumnMin", 30, 0, MAX_COLUMN_HEIGHT);
        geyserColumnMax = value(tag, "GeyserColumnMax", 120, 0, MAX_COLUMN_HEIGHT);
        geyserBoilMin = value(tag, "GeyserBoilMin", 2, 0, MAX_BOIL_SPEED);
        geyserBoilMax = value(tag, "GeyserBoilMax", 12, 0, MAX_BOIL_SPEED);
        geyserLitSound.readFromNBT(tag, "GeyserLitSound");
        geyserEruptSound.readFromNBT(tag, "GeyserEruptSound");
        // Every key below is absent from a boss saved before the cone, the strike from above and
        // the residue existed, and each default is that boss' old behaviour: a straight column
        // drawn at once, one eruption, nothing left on the floor.
        geyserColumnShape = value(tag, "GeyserColumnShape", COLUMN_STRAIGHT, COLUMN_STRAIGHT, COLUMN_CONE);
        geyserColumnTopRadius = value(tag, "GeyserColumnTopRadius", 5, 0, MAX_COLUMN_TOP_RADIUS);
        geyserColumnRiseTicks = value(tag, "GeyserColumnRiseTicks", 0, 0, MAX_COLUMN_RISE_TICKS);
        geyserColumnPointsPerSlice = value(tag, "GeyserColumnPoints", 6, MIN_COLUMN_POINTS, MAX_COLUMN_POINTS);
        geyserColumnParticles.readFromNBT(tag, "GeyserColumnParticles");
        geyserColumnSmoke.readFromNBT(tag, "GeyserColumnSmoke");
        geyserSkyEnabled = tag.getBoolean("GeyserSkyEnabled");
        geyserSkyDelayTicks = value(tag, "GeyserSkyDelayTicks", 30, MIN_SKY_DELAY_TICKS, MAX_SKY_DELAY_TICKS);
        geyserSkyHeight = value(tag, "GeyserSkyHeight", 12, MIN_SKY_HEIGHT, MAX_SKY_HEIGHT);
        geyserSkyFallTicks = value(tag, "GeyserSkyFallTicks", 10, MIN_SKY_FALL_TICKS, MAX_SKY_FALL_TICKS);
        geyserSkyRadius = value(tag, "GeyserSkyRadius", 0, 0, MAX_SKY_RADIUS);
        geyserSkyDamage = value(tag, "GeyserSkyDamage", 6, 0, MAX_DAMAGE);
        geyserSkyPress = value(tag, "GeyserSkyPress", 12, 0, MAX_SKY_PRESS);
        geyserSkyEffects.readFromNBT(tag, "GeyserSkyEffects");
        geyserSkyVfx = AreaVfxStyles.normalize(tag.getString("GeyserSkyVfx"));
        geyserSkyParticles.readFromNBT(tag, "GeyserSkyParticles");
        geyserSkySound.readFromNBT(tag, "GeyserSkySound");
        geyserSkyHitSound.readFromNBT(tag, "GeyserSkyHitSound");
        geyserSkyHitParticles.readFromNBT(tag, "GeyserSkyHitParticles");
        geyserResidueEnabled = tag.getBoolean("GeyserResidueEnabled");
        geyserResidueLifetimeTicks = value(tag, "GeyserResidueLifetimeTicks", 200,
                MIN_RESIDUE_LIFETIME_TICKS, MAX_RESIDUE_LIFETIME_TICKS);
        geyserResidueRadius = value(tag, "GeyserResidueRadius", 0, 0, MAX_RESIDUE_RADIUS);
        geyserResidueDamage = value(tag, "GeyserResidueDamage", 2, 0, MAX_DAMAGE);
        geyserResidueIntervalTicks = value(tag, "GeyserResidueIntervalTicks", 20,
                MIN_RESIDUE_INTERVAL_TICKS, MAX_RESIDUE_INTERVAL_TICKS);
        geyserResidueStackTicks = value(tag, "GeyserResidueStackTicks", 40,
                MIN_RESIDUE_STACK_TICKS, MAX_RESIDUE_STACK_TICKS);
        geyserResidueMaxStacks = value(tag, "GeyserResidueMaxStacks", 4,
                MIN_RESIDUE_MAX_STACKS, MAX_RESIDUE_MAX_STACKS);
        geyserResidueDecayTicks = value(tag, "GeyserResidueDecayTicks", 60,
                MIN_RESIDUE_DECAY_TICKS, MAX_RESIDUE_DECAY_TICKS);
        geyserResidueHeight = value(tag, "GeyserResidueHeight", 10, MIN_RESIDUE_HEIGHT, MAX_RESIDUE_HEIGHT);
        geyserResidueEffects.readFromNBT(tag, "GeyserResidueEffects");
        geyserResidueSoundIntervalTicks = value(tag, "GeyserResidueSoundIntervalTicks", 40,
                MIN_RESIDUE_SOUND_INTERVAL_TICKS, MAX_RESIDUE_SOUND_INTERVAL_TICKS);
        geyserResidueParticles.readFromNBT(tag, "GeyserResidueParticles");
        geyserResidueSound.readFromNBT(tag, "GeyserResidueSound");
        geyserResidueHitParticles.readFromNBT(tag, "GeyserResidueHitParticles");
        geyserCastSpot.readFromNBT(tag, "Geyser");
    }
}
