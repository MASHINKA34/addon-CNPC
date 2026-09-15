package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The seismic waves: rings of the floor round the boss that hit one after another, from the
 * circle under its feet out to the edge, or at random radii, and what each ring does to
 * whoever stands in it.
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#seismic()}.
 * It owns its own save format under the {@code Seismic} prefix; a boss saved before the waves
 * existed carries none of these keys and reads back with the ability switched off.</p>
 */
public final class BossSeismicSettings {

    /** The rings go outward one after another, from the core circle to the last ring. */
    public static final int MODE_GROWING = 0;
    /** Each pulse picks a handful of rings at random radii. */
    public static final int MODE_RANDOM = 1;

    public static final String[] MODE_LABELS = {
            "cnpcgeckoaddon.boss.seismic_mode.growing",
            "cnpcgeckoaddon.boss.seismic_mode.random"
    };

    /** A ring only hits. */
    public static final int HIT_ONLY = 0;
    /** A ring hits and throws its victims up. */
    public static final int HIT_LAUNCH = 1;
    /** A ring hits, throws its victims up, and a moment later yanks them back down. */
    public static final int HIT_SLAM = 2;

    public static final String[] HIT_MODE_LABELS = {
            "cnpcgeckoaddon.boss.seismic_hit_mode.hit",
            "cnpcgeckoaddon.boss.seismic_hit_mode.launch",
            "cnpcgeckoaddon.boss.seismic_hit_mode.slam"
    };

    public static final int MIN_CORE_RADIUS = 1;
    public static final int MAX_CORE_RADIUS = 32;
    public static final int MIN_RING_WIDTH = 1;
    public static final int MAX_RING_WIDTH = 16;
    public static final int MAX_RING_GAP = 16;
    public static final int MIN_MAX_RADIUS = 2;
    public static final int MAX_MAX_RADIUS = 64;
    public static final int MIN_INTERVAL_TICKS = 1;
    public static final int MAX_INTERVAL_TICKS = 200;
    public static final int MAX_WARN_TICKS = 200;
    public static final int MIN_RANDOM_RINGS = 1;
    public static final int MAX_RANDOM_RINGS = 8;
    public static final int MAX_RANDOM_PULSES = 64;
    public static final int MIN_REPEATS = 1;
    public static final int MAX_REPEATS = 20;
    public static final int MAX_REPEAT_DELAY_TICKS = 1200;
    /** Tenths of a block: how far above the floor a wave still reaches. */
    public static final int MAX_HEIGHT = 100;
    public static final int MAX_DAMAGE = 1000;
    public static final int MAX_KNOCKBACK = 10;
    /** Tenths of a block per tick, the geyser's own scale. */
    public static final int MIN_LAUNCH = 1;
    public static final int MAX_LAUNCH = 40;
    public static final int MIN_SLAM_DELAY_TICKS = 1;
    public static final int MAX_SLAM_DELAY_TICKS = 60;
    public static final int MIN_SLAM_STRENGTH = 1;
    public static final int MAX_SLAM_STRENGTH = 60;
    public static final int MIN_SLAM_TIMEOUT_TICKS = 20;
    public static final int MAX_SLAM_TIMEOUT_TICKS = 1200;
    public static final int MIN_VFX_TICKS = 1;
    public static final int MAX_VFX_TICKS = 200;

    private boolean seismicEnabled;
    private String seismicAnimation = "";
    private int seismicActionDelayTicks = 20;
    private int seismicCooldownTicks = 300;
    private int seismicMode = MODE_GROWING;
    private int seismicCoreRadius = 3;
    private int seismicRingWidth = 2;
    private int seismicRingGap;
    private int seismicMaxRadius = 16;
    private int seismicIntervalTicks = 10;
    private int seismicWarnTicks = 10;
    private int seismicRandomMin = 1;
    private int seismicRandomMax = 3;
    /** Nought is one pulse per ring of the plan. */
    private int seismicRandomPulses;
    private boolean seismicRandomCore = true;
    private int seismicRepeats = 1;
    private int seismicRepeatDelayTicks = 20;
    private boolean seismicRepeatAnimation = true;
    private boolean seismicFollowBoss;
    private boolean seismicRootWhileRunning = true;
    /** Tenths of a block; nought is no height check at all. */
    private int seismicHeight = 10;
    private int seismicDamage = 8;
    private int seismicKnockback;
    private final BossEffectSet seismicEffects = new BossEffectSet();
    private int seismicHitMode = HIT_ONLY;
    private int seismicLaunch = 8;
    private int seismicSlamDelayTicks = 8;
    private int seismicSlamStrength = 15;
    private int seismicSlamDamage = 4;
    private boolean seismicSlamFallDamage = true;
    private int seismicSlamTimeoutTicks = 100;
    private String seismicVfx = AreaVfxStyles.STONE;
    private boolean seismicBlockWave;
    private int seismicVfxTicks = 8;
    private final BossSoundCue seismicPulseSound =
            new BossSoundCue("minecraft:entity.generic.explode", 1.0F, 0.5F);
    private final BossSoundCue seismicHitSound =
            new BossSoundCue("minecraft:entity.iron_golem.damage", 1.0F, 0.8F);
    private final BossSoundCue seismicLaunchSound =
            new BossSoundCue("minecraft:entity.breeze.jump", 1.0F, 0.7F);
    private final BossSoundCue seismicSlamSound =
            new BossSoundCue("minecraft:block.anvil.land", 1.0F, 0.6F);
    /** The ability's own colour by default: the ring is dotted in it as it hits. */
    private final BossParticleCue seismicPulseParticles = new BossParticleCue(BossParticleCue.DUST_ID, 16);
    private final BossParticleCue seismicHitParticles = new BossParticleCue("minecraft:crit", 8);
    private final BossParticleCue seismicSlamParticles = new BossParticleCue("minecraft:poof", 8);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot seismicCastSpot = new BossCastSpot();

    public boolean isEnabled() { return seismicEnabled; }

    public void setEnabled(boolean value) { seismicEnabled = value; }

    /** The wind-up, and the swing played again at the start of every repeat of the series. */
    public String getAnimation() { return seismicAnimation; }

    public void setAnimation(String value) { seismicAnimation = clean(value); }

    public int getActionDelayTicks() { return seismicActionDelayTicks; }

    public void setActionDelayTicks(int value) { seismicActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return seismicCooldownTicks; }

    public void setCooldownTicks(int value) { seismicCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Whether the rings go outward in order or come up at random radii. */
    public int getMode() { return seismicMode; }

    public void setMode(int value) { seismicMode = Mth.clamp(value, MODE_GROWING, MODE_RANDOM); }

    /** The circle under the boss: the first ring of the plan. */
    public int getCoreRadius() { return seismicCoreRadius; }

    public void setCoreRadius(int value) { seismicCoreRadius = Mth.clamp(value, MIN_CORE_RADIUS, MAX_CORE_RADIUS); }

    public int getRingWidth() { return seismicRingWidth; }

    public void setRingWidth(int value) { seismicRingWidth = Mth.clamp(value, MIN_RING_WIDTH, MAX_RING_WIDTH); }

    /** Floor left untouched between one ring and the next. */
    public int getRingGap() { return seismicRingGap; }

    public void setRingGap(int value) { seismicRingGap = Mth.clamp(value, 0, MAX_RING_GAP); }

    /** No ring reaches past this; the last one is cut to it. */
    public int getMaxRadius() { return seismicMaxRadius; }

    public void setMaxRadius(int value) { seismicMaxRadius = Mth.clamp(value, MIN_MAX_RADIUS, MAX_MAX_RADIUS); }

    public int getIntervalTicks() { return seismicIntervalTicks; }

    public void setIntervalTicks(int value) {
        seismicIntervalTicks = Mth.clamp(value, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
    }

    /** How long a ring is outlined before it hits; nought hits the moment the pulse comes. */
    public int getWarnTicks() { return seismicWarnTicks; }

    public void setWarnTicks(int value) { seismicWarnTicks = Mth.clamp(value, 0, MAX_WARN_TICKS); }

    public int getRandomMin() { return seismicRandomMin; }

    public void setRandomMin(int value) { seismicRandomMin = Mth.clamp(value, MIN_RANDOM_RINGS, MAX_RANDOM_RINGS); }

    public int getRandomMax() { return seismicRandomMax; }

    public void setRandomMax(int value) { seismicRandomMax = Mth.clamp(value, MIN_RANDOM_RINGS, MAX_RANDOM_RINGS); }

    /** Pulses a random series runs; nought is as many as the plan has rings. */
    public int getRandomPulses() { return seismicRandomPulses; }

    public void setRandomPulses(int value) { seismicRandomPulses = Mth.clamp(value, 0, MAX_RANDOM_PULSES); }

    /** Whether a random pulse may pick the circle under the boss as well as the rings. */
    public boolean isRandomCore() { return seismicRandomCore; }

    public void setRandomCore(boolean value) { seismicRandomCore = value; }

    /** How many series one cast runs. */
    public int getRepeats() { return seismicRepeats; }

    public void setRepeats(int value) { seismicRepeats = Mth.clamp(value, MIN_REPEATS, MAX_REPEATS); }

    public int getRepeatDelayTicks() { return seismicRepeatDelayTicks; }

    public void setRepeatDelayTicks(int value) {
        seismicRepeatDelayTicks = Mth.clamp(value, 0, MAX_REPEAT_DELAY_TICKS);
    }

    /** Whether the wind-up's animation plays again at the start of every series after the first. */
    public boolean isRepeatAnimation() { return seismicRepeatAnimation; }

    public void setRepeatAnimation(boolean value) { seismicRepeatAnimation = value; }

    /** Whether every pulse is centred under the boss as it stands, rather than where it cast. */
    public boolean isFollowBoss() { return seismicFollowBoss; }

    public void setFollowBoss(boolean value) { seismicFollowBoss = value; }

    /** Whether the boss stands still for as long as a series runs. */
    public boolean isRootWhileRunning() { return seismicRootWhileRunning; }

    public void setRootWhileRunning(boolean value) { seismicRootWhileRunning = value; }

    /** Tenths of a block above the floor a wave still reaches; nought reaches any height. */
    public int getHeightTenths() { return seismicHeight; }

    public void setHeightTenths(int value) { seismicHeight = Mth.clamp(value, 0, MAX_HEIGHT); }

    public double getHeight() { return seismicHeight / 10.0D; }

    public int getDamage() { return seismicDamage; }

    public void setDamage(int value) { seismicDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** The shove away from the centre a ring gives whoever it hits. */
    public int getKnockback() { return seismicKnockback; }

    public void setKnockback(int value) { seismicKnockback = Mth.clamp(value, 0, MAX_KNOCKBACK); }

    public BossEffectSet getEffects() { return seismicEffects; }

    /** What a ring does beyond the hit: nothing, a throw up, or a throw up and a slam down. */
    public int getHitMode() { return seismicHitMode; }

    public void setHitMode(int value) { seismicHitMode = Mth.clamp(value, HIT_ONLY, HIT_SLAM); }

    /** Tenths of a block per tick straight up, the geyser's scale. */
    public int getLaunch() { return seismicLaunch; }

    public void setLaunch(int value) { seismicLaunch = Mth.clamp(value, MIN_LAUNCH, MAX_LAUNCH); }

    /** How long after the throw the slam pulls a victim back down. */
    public int getSlamDelayTicks() { return seismicSlamDelayTicks; }

    public void setSlamDelayTicks(int value) {
        seismicSlamDelayTicks = Mth.clamp(value, MIN_SLAM_DELAY_TICKS, MAX_SLAM_DELAY_TICKS);
    }

    /** Tenths of a block per tick straight down. */
    public int getSlamStrengthTenths() { return seismicSlamStrength; }

    public void setSlamStrengthTenths(int value) {
        seismicSlamStrength = Mth.clamp(value, MIN_SLAM_STRENGTH, MAX_SLAM_STRENGTH);
    }

    public double getSlamStrength() { return seismicSlamStrength / 10.0D; }

    /** What the landing after a slam hits for, on top of any fall. */
    public int getSlamDamage() { return seismicSlamDamage; }

    public void setSlamDamage(int value) { seismicSlamDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** Whether vanilla's own fall damage lands after a slam as well; off, the fall is forgiven. */
    public boolean isSlamFallDamage() { return seismicSlamFallDamage; }

    public void setSlamFallDamage(boolean value) { seismicSlamFallDamage = value; }

    /** How long a slammed victim's landing is waited for before it is forgotten. */
    public int getSlamTimeoutTicks() { return seismicSlamTimeoutTicks; }

    public void setSlamTimeoutTicks(int value) {
        seismicSlamTimeoutTicks = Mth.clamp(value, MIN_SLAM_TIMEOUT_TICKS, MAX_SLAM_TIMEOUT_TICKS);
    }

    /** The wave each ring throws out from its inner edge to its outer one. */
    public String getVfx() { return seismicVfx; }

    public void setVfx(String value) { seismicVfx = AreaVfxStyles.normalize(value); }

    public boolean isBlockWave() { return seismicBlockWave; }

    public void setBlockWave(boolean value) { seismicBlockWave = value; }

    /** How long a ring's wave takes to cross the ring. */
    public int getVfxTicks() { return seismicVfxTicks; }

    public void setVfxTicks(int value) { seismicVfxTicks = Mth.clamp(value, MIN_VFX_TICKS, MAX_VFX_TICKS); }

    public BossSoundCue getPulseSound() { return seismicPulseSound; }

    public BossSoundCue getHitSound() { return seismicHitSound; }

    public BossSoundCue getLaunchSound() { return seismicLaunchSound; }

    public BossSoundCue getSlamSound() { return seismicSlamSound; }

    public BossParticleCue getPulseParticles() { return seismicPulseParticles; }

    public BossParticleCue getHitParticles() { return seismicHitParticles; }

    public BossParticleCue getSlamParticles() { return seismicSlamParticles; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return seismicCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("SeismicEnabled", seismicEnabled);
        tag.putString("SeismicAnimation", seismicAnimation);
        tag.putInt("SeismicActionDelayTicks", seismicActionDelayTicks);
        tag.putInt("SeismicCooldownTicks", seismicCooldownTicks);
        tag.putInt("SeismicMode", seismicMode);
        tag.putInt("SeismicCoreRadius", seismicCoreRadius);
        tag.putInt("SeismicRingWidth", seismicRingWidth);
        tag.putInt("SeismicRingGap", seismicRingGap);
        tag.putInt("SeismicMaxRadius", seismicMaxRadius);
        tag.putInt("SeismicIntervalTicks", seismicIntervalTicks);
        tag.putInt("SeismicWarnTicks", seismicWarnTicks);
        tag.putInt("SeismicRandomMin", seismicRandomMin);
        tag.putInt("SeismicRandomMax", seismicRandomMax);
        tag.putInt("SeismicRandomPulses", seismicRandomPulses);
        tag.putBoolean("SeismicRandomCore", seismicRandomCore);
        tag.putInt("SeismicRepeats", seismicRepeats);
        tag.putInt("SeismicRepeatDelayTicks", seismicRepeatDelayTicks);
        tag.putBoolean("SeismicRepeatAnimation", seismicRepeatAnimation);
        tag.putBoolean("SeismicFollowBoss", seismicFollowBoss);
        tag.putBoolean("SeismicRootWhileRunning", seismicRootWhileRunning);
        tag.putInt("SeismicHeight", seismicHeight);
        tag.putInt("SeismicDamage", seismicDamage);
        tag.putInt("SeismicKnockback", seismicKnockback);
        tag.put("SeismicEffects", seismicEffects.writeToNBT());
        tag.putInt("SeismicHitMode", seismicHitMode);
        tag.putInt("SeismicLaunch", seismicLaunch);
        tag.putInt("SeismicSlamDelayTicks", seismicSlamDelayTicks);
        tag.putInt("SeismicSlamStrength", seismicSlamStrength);
        tag.putInt("SeismicSlamDamage", seismicSlamDamage);
        tag.putBoolean("SeismicSlamFallDamage", seismicSlamFallDamage);
        tag.putInt("SeismicSlamTimeoutTicks", seismicSlamTimeoutTicks);
        tag.putString("SeismicVfx", seismicVfx);
        tag.putBoolean("SeismicBlockWave", seismicBlockWave);
        tag.putInt("SeismicVfxTicks", seismicVfxTicks);
        seismicPulseSound.writeToNBT(tag, "SeismicPulseSound");
        seismicHitSound.writeToNBT(tag, "SeismicHitSound");
        seismicLaunchSound.writeToNBT(tag, "SeismicLaunchSound");
        seismicSlamSound.writeToNBT(tag, "SeismicSlamSound");
        seismicPulseParticles.writeToNBT(tag, "SeismicPulseParticles");
        seismicHitParticles.writeToNBT(tag, "SeismicHitParticles");
        seismicSlamParticles.writeToNBT(tag, "SeismicSlamParticles");
        seismicCastSpot.writeToNBT(tag, "Seismic");
    }

    void readFromNBT(CompoundTag tag) {
        seismicEnabled = tag.getBoolean("SeismicEnabled");
        seismicAnimation = clean(tag.getString("SeismicAnimation"));
        seismicActionDelayTicks = value(tag, "SeismicActionDelayTicks", 20, 0, 1200);
        seismicCooldownTicks = value(tag, "SeismicCooldownTicks", 300, 1, 12000);
        seismicMode = value(tag, "SeismicMode", MODE_GROWING, MODE_GROWING, MODE_RANDOM);
        seismicCoreRadius = value(tag, "SeismicCoreRadius", 3, MIN_CORE_RADIUS, MAX_CORE_RADIUS);
        seismicRingWidth = value(tag, "SeismicRingWidth", 2, MIN_RING_WIDTH, MAX_RING_WIDTH);
        seismicRingGap = value(tag, "SeismicRingGap", 0, 0, MAX_RING_GAP);
        seismicMaxRadius = value(tag, "SeismicMaxRadius", 16, MIN_MAX_RADIUS, MAX_MAX_RADIUS);
        seismicIntervalTicks = value(tag, "SeismicIntervalTicks", 10, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
        seismicWarnTicks = value(tag, "SeismicWarnTicks", 10, 0, MAX_WARN_TICKS);
        seismicRandomMin = value(tag, "SeismicRandomMin", 1, MIN_RANDOM_RINGS, MAX_RANDOM_RINGS);
        // The two are read as a range: a save with the pair the wrong way round reads as
        // "exactly the smaller number" rather than as a range nothing can fall in.
        seismicRandomMax = Math.max(seismicRandomMin,
                value(tag, "SeismicRandomMax", 3, MIN_RANDOM_RINGS, MAX_RANDOM_RINGS));
        seismicRandomPulses = value(tag, "SeismicRandomPulses", 0, 0, MAX_RANDOM_PULSES);
        seismicRandomCore = !tag.contains("SeismicRandomCore") || tag.getBoolean("SeismicRandomCore");
        seismicRepeats = value(tag, "SeismicRepeats", 1, MIN_REPEATS, MAX_REPEATS);
        seismicRepeatDelayTicks = value(tag, "SeismicRepeatDelayTicks", 20, 0, MAX_REPEAT_DELAY_TICKS);
        seismicRepeatAnimation = !tag.contains("SeismicRepeatAnimation") || tag.getBoolean("SeismicRepeatAnimation");
        seismicFollowBoss = tag.getBoolean("SeismicFollowBoss");
        seismicRootWhileRunning = !tag.contains("SeismicRootWhileRunning") || tag.getBoolean("SeismicRootWhileRunning");
        seismicHeight = value(tag, "SeismicHeight", 10, 0, MAX_HEIGHT);
        seismicDamage = value(tag, "SeismicDamage", 8, 0, MAX_DAMAGE);
        seismicKnockback = value(tag, "SeismicKnockback", 0, 0, MAX_KNOCKBACK);
        seismicEffects.readFromNBT(tag, "SeismicEffects");
        seismicHitMode = value(tag, "SeismicHitMode", HIT_ONLY, HIT_ONLY, HIT_SLAM);
        seismicLaunch = value(tag, "SeismicLaunch", 8, MIN_LAUNCH, MAX_LAUNCH);
        seismicSlamDelayTicks = value(tag, "SeismicSlamDelayTicks", 8, MIN_SLAM_DELAY_TICKS, MAX_SLAM_DELAY_TICKS);
        seismicSlamStrength = value(tag, "SeismicSlamStrength", 15, MIN_SLAM_STRENGTH, MAX_SLAM_STRENGTH);
        seismicSlamDamage = value(tag, "SeismicSlamDamage", 4, 0, MAX_DAMAGE);
        seismicSlamFallDamage = !tag.contains("SeismicSlamFallDamage") || tag.getBoolean("SeismicSlamFallDamage");
        seismicSlamTimeoutTicks = value(tag, "SeismicSlamTimeoutTicks", 100,
                MIN_SLAM_TIMEOUT_TICKS, MAX_SLAM_TIMEOUT_TICKS);
        // Unlike the area attack's wave, the rings show by default: an absent key is the
        // stone wave the ability shipped with, not silence.
        seismicVfx = tag.contains("SeismicVfx") ? AreaVfxStyles.normalize(tag.getString("SeismicVfx"))
                : AreaVfxStyles.STONE;
        seismicBlockWave = tag.getBoolean("SeismicBlockWave");
        seismicVfxTicks = value(tag, "SeismicVfxTicks", 8, MIN_VFX_TICKS, MAX_VFX_TICKS);
        seismicPulseSound.readFromNBT(tag, "SeismicPulseSound");
        seismicHitSound.readFromNBT(tag, "SeismicHitSound");
        seismicLaunchSound.readFromNBT(tag, "SeismicLaunchSound");
        seismicSlamSound.readFromNBT(tag, "SeismicSlamSound");
        seismicPulseParticles.readFromNBT(tag, "SeismicPulseParticles");
        seismicHitParticles.readFromNBT(tag, "SeismicHitParticles");
        seismicSlamParticles.readFromNBT(tag, "SeismicSlamParticles");
        seismicCastSpot.readFromNBT(tag, "Seismic");
    }
}
