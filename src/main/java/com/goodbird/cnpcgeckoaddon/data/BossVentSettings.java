package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The vents: the builder's boxes in the arena's floor, ceiling and walls, which of them go off
 * on each beat of a timer of their own, and what they do when they go - a blast, a flame held
 * for a while, or a wall that shoves or pins whoever stands in front of them.
 *
 * <p>The boss only starts the timer with its cast; the vents then keep their own beat while
 * the rotation goes on as it would. A cast while the timer runs restarts it, stops it or is
 * turned away, as {@link #getRecast()} says.</p>
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#vent()}.
 * It owns its own save format under the {@code Vent} prefix; a boss saved before the vents
 * existed carries none of these keys and reads back with the ability switched off.</p>
 */
public final class BossVentSettings {

    /** One hit on everyone in front of the vent, thrown away from its face. */
    public static final int MODE_BURST = 0;
    /** A flame held for a while, burning everyone in front of the vent on a clock. */
    public static final int MODE_FLAME = 1;
    /** A wall that comes out of the face and shoves, or pins, whoever is in front of it. */
    public static final int MODE_WALL = 2;

    public static final String[] MODE_LABELS = {
            "cnpcgeckoaddon.boss.vent_mode.burst",
            "cnpcgeckoaddon.boss.vent_mode.flame",
            "cnpcgeckoaddon.boss.vent_mode.wall"
    };

    /** Every vent goes on every beat. */
    public static final int PATTERN_ALL = 0;
    /** One vent a beat, in list order and round again. */
    public static final int PATTERN_SEQUENCE = 1;
    /** A few vents a beat, drawn by their weights. */
    public static final int PATTERN_RANDOM = 2;

    public static final String[] PATTERN_LABELS = {
            "cnpcgeckoaddon.boss.vent_pattern.all",
            "cnpcgeckoaddon.boss.vent_pattern.sequence",
            "cnpcgeckoaddon.boss.vent_pattern.random"
    };

    /** A cast while the timer runs starts it over. */
    public static final int RECAST_RESTART = 0;
    /** A cast while the timer runs stops it: the cast is spent on that. */
    public static final int RECAST_STOP = 1;
    /** A cast while the timer runs is turned away and tried again shortly. */
    public static final int RECAST_IGNORE = 2;

    public static final String[] RECAST_LABELS = {
            "cnpcgeckoaddon.boss.vent_recast.restart",
            "cnpcgeckoaddon.boss.vent_recast.stop",
            "cnpcgeckoaddon.boss.vent_recast.ignore"
    };

    /** The wall shoves everyone out past its far side. */
    public static final int WALL_PUSH = 0;
    /** The wall carries everyone to its far side and holds them there. */
    public static final int WALL_PIN = 1;

    public static final String[] WALL_MODE_LABELS = {
            "cnpcgeckoaddon.boss.vent_wall_mode.push",
            "cnpcgeckoaddon.boss.vent_wall_mode.pin"
    };

    public static final int MAX_ACTION_DELAY_TICKS = 1200;
    public static final int MIN_COOLDOWN_TICKS = 1;
    public static final int MAX_COOLDOWN_TICKS = 12000;
    public static final int MIN_RANDOM_COUNT = 1;
    public static final int MAX_RANDOM_COUNT = BossVentZoneList.MAX_ENTRIES;
    /** A quarter of a second at the least: a timer faster than that is a vent that never stops. */
    public static final int MIN_CYCLE_TICKS = 5;
    public static final int MAX_CYCLE_TICKS = 12000;
    public static final int MAX_WARN_TICKS = 200;
    public static final int MIN_ACTIVE_TICKS = 1;
    public static final int MAX_ACTIVE_TICKS = 1200;
    /** Nought is a timer that runs until the phase or the fight ends, or a cast stops it. */
    public static final int MAX_REPEATS = 1000;
    public static final int MAX_DAMAGE = 1000;
    public static final int MIN_HIT_INTERVAL_TICKS = 1;
    public static final int MAX_HIT_INTERVAL_TICKS = 200;
    public static final int MAX_KNOCKBACK = 10;
    public static final int MIN_BURST_VFX_TICKS = 1;
    public static final int MAX_BURST_VFX_TICKS = 200;
    /** Tenths of a block per tick, the geyser's scale, and as fast as its throw goes. */
    public static final int MIN_WALL_PUSH = 1;
    public static final int MAX_WALL_PUSH = 40;
    public static final int MAX_WALL_LIFT = 20;
    public static final int MIN_PARTICLE_BUDGET = 8;
    public static final int MAX_PARTICLE_BUDGET = 200;
    public static final int MIN_FLAME_SOUND_INTERVAL_TICKS = 1;
    public static final int MAX_FLAME_SOUND_INTERVAL_TICKS = 200;

    private boolean ventEnabled;
    private String ventAnimation = "";
    private int ventActionDelayTicks = 20;
    private int ventCooldownTicks = 600;
    private final BossVentZoneList ventZones = new BossVentZoneList();
    private int ventMode = MODE_FLAME;
    private int ventPattern = PATTERN_ALL;
    private int ventRandomCount = 1;
    private int ventCycleTicks = 100;
    private int ventWarnTicks = 20;
    private int ventActiveTicks = 40;
    private int ventRepeats;
    private int ventRecast = RECAST_RESTART;
    /** What a blast hits for, and what a flame hits for on each beat of its interval. */
    private int ventDamage = 6;
    private int ventHitIntervalTicks = 10;
    private int ventKnockback = 1;
    private final BossEffectSet ventEffects = new BossEffectSet();
    private String ventBurstVfx = AreaVfxStyles.FIRE;
    private int ventBurstVfxTicks = 10;
    private int ventWallPushTenths = 8;
    private int ventWallMode = WALL_PUSH;
    /** What a wall hits whoever it pins for, on the interval; nought only holds them. */
    private int ventWallDamage;
    /** Tenths of a block per tick a floor vent's wall lifts on top of its push. */
    private int ventWallLift = 2;
    private int ventParticleBudget = 48;
    private int ventFlameSoundIntervalTicks = 10;
    private final BossSoundCue ventHissSound = new BossSoundCue("minecraft:block.fire.extinguish", 1.0F, 1.4F);
    private final BossSoundCue ventBurstSound = new BossSoundCue("minecraft:entity.generic.explode", 1.2F, 0.8F);
    private final BossSoundCue ventFlameSound = new BossSoundCue("minecraft:block.fire.ambient", 1.2F, 0.7F);
    private final BossSoundCue ventWallSound = new BossSoundCue("minecraft:block.piston.extend", 1.0F, 0.6F);
    private final BossParticleCue ventFlameParticles = new BossParticleCue("minecraft:flame", 8);
    private final BossParticleCue ventSmokeParticles = new BossParticleCue("minecraft:smoke", 2);
    private final BossParticleCue ventBurstParticles = new BossParticleCue("minecraft:lava", 12);
    private final BossParticleCue ventWallParticles = new BossParticleCue("minecraft:cloud", 4);
    /** The ability's own colour by default: the face of a vent about to go is dotted in it. */
    private final BossParticleCue ventWarnParticles = new BossParticleCue(BossParticleCue.DUST_ID, 6);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot ventCastSpot = new BossCastSpot();

    public boolean isEnabled() { return ventEnabled; }

    public void setEnabled(boolean value) { ventEnabled = value; }

    /** Whether the rotation may cast this: switched on, with a vent to fire. */
    public boolean canCast() { return ventEnabled && isConfigured(); }

    /**
     * Whether there is a vent switched on to fire, whatever the switch says: all a chained start
     * still needs.
     */
    public boolean isConfigured() { return ventZones.hasEnabled(); }

    /** The wind-up: played while the boss starts, restarts or stops the timer. */
    public String getAnimation() { return ventAnimation; }

    public void setAnimation(String value) { ventAnimation = clean(value); }

    public int getActionDelayTicks() { return ventActionDelayTicks; }

    public void setActionDelayTicks(int value) {
        ventActionDelayTicks = Mth.clamp(value, 0, MAX_ACTION_DELAY_TICKS);
    }

    public int getCooldownTicks() { return ventCooldownTicks; }

    public void setCooldownTicks(int value) {
        ventCooldownTicks = Mth.clamp(value, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
    }

    /** The builder's vents, in list order: the order a volley taken one after another walks. */
    public BossVentZoneList getZones() { return ventZones; }

    /** What a vent does when it has no mode of its own: a blast, a flame or a wall. */
    public int getMode() { return ventMode; }

    public void setMode(int value) { ventMode = Mth.clamp(value, MODE_BURST, MODE_WALL); }

    /** Which vents go on a beat: all of them, the next one along, or a few at random. */
    public int getPattern() { return ventPattern; }

    public void setPattern(int value) { ventPattern = Mth.clamp(value, PATTERN_ALL, PATTERN_RANDOM); }

    /** How many vents a random beat fires. */
    public int getRandomCount() { return ventRandomCount; }

    public void setRandomCount(int value) {
        ventRandomCount = Mth.clamp(value, MIN_RANDOM_COUNT, MAX_RANDOM_COUNT);
    }

    /** Ticks between two beats of the timer. */
    public int getCycleTicks() { return ventCycleTicks; }

    public void setCycleTicks(int value) { ventCycleTicks = Mth.clamp(value, MIN_CYCLE_TICKS, MAX_CYCLE_TICKS); }

    /** How long a vent is outlined and hisses before it goes; nought is at once. */
    public int getWarnTicks() { return ventWarnTicks; }

    public void setWarnTicks(int value) { ventWarnTicks = Mth.clamp(value, 0, MAX_WARN_TICKS); }

    /** How long a flame burns or a wall stands; a blast is over the tick it goes. */
    public int getActiveTicks() { return ventActiveTicks; }

    public void setActiveTicks(int value) {
        ventActiveTicks = Mth.clamp(value, MIN_ACTIVE_TICKS, MAX_ACTIVE_TICKS);
    }

    /** Beats a cast runs for; nought runs until the phase or the fight ends, or a cast stops it. */
    public int getRepeats() { return ventRepeats; }

    public void setRepeats(int value) { ventRepeats = Mth.clamp(value, 0, MAX_REPEATS); }

    /** What a cast does while the timer from the last one is still running. */
    public int getRecast() { return ventRecast; }

    public void setRecast(int value) { ventRecast = Mth.clamp(value, RECAST_RESTART, RECAST_IGNORE); }

    public int getDamage() { return ventDamage; }

    public void setDamage(int value) { ventDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** Ticks between two hits of a flame, and between two of a wall on whoever it pins. */
    public int getHitIntervalTicks() { return ventHitIntervalTicks; }

    public void setHitIntervalTicks(int value) {
        ventHitIntervalTicks = Mth.clamp(value, MIN_HIT_INTERVAL_TICKS, MAX_HIT_INTERVAL_TICKS);
    }

    /** How hard a blast throws everyone away from the vent's face. */
    public int getKnockback() { return ventKnockback; }

    public void setKnockback(int value) { ventKnockback = Mth.clamp(value, 0, MAX_KNOCKBACK); }

    public BossEffectSet getEffects() { return ventEffects; }

    /** The wave a floor or ceiling vent's blast throws across the floor. */
    public String getBurstVfx() { return ventBurstVfx; }

    public void setBurstVfx(String value) { ventBurstVfx = AreaVfxStyles.normalize(value); }

    public int getBurstVfxTicks() { return ventBurstVfxTicks; }

    public void setBurstVfxTicks(int value) {
        ventBurstVfxTicks = Mth.clamp(value, MIN_BURST_VFX_TICKS, MAX_BURST_VFX_TICKS);
    }

    /** Tenths of a block per tick a wall carries whoever is in front of it along. */
    public int getWallPushTenths() { return ventWallPushTenths; }

    public void setWallPushTenths(int value) {
        ventWallPushTenths = Mth.clamp(value, MIN_WALL_PUSH, MAX_WALL_PUSH);
    }

    /** Whether a wall shoves everyone out past its far side or pins them against it. */
    public int getWallMode() { return ventWallMode; }

    public void setWallMode(int value) { ventWallMode = Mth.clamp(value, WALL_PUSH, WALL_PIN); }

    public int getWallDamage() { return ventWallDamage; }

    public void setWallDamage(int value) { ventWallDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** Tenths of a block per tick a floor vent's wall adds to its push, so nobody is left on the floor. */
    public int getWallLift() { return ventWallLift; }

    public void setWallLift(int value) { ventWallLift = Mth.clamp(value, 0, MAX_WALL_LIFT); }

    /** The most particles one vent spends in a tick, every cue of it together. */
    public int getParticleBudget() { return ventParticleBudget; }

    public void setParticleBudget(int value) {
        ventParticleBudget = Mth.clamp(value, MIN_PARTICLE_BUDGET, MAX_PARTICLE_BUDGET);
    }

    /** Ticks between two roars of a burning flame. */
    public int getFlameSoundIntervalTicks() { return ventFlameSoundIntervalTicks; }

    public void setFlameSoundIntervalTicks(int value) {
        ventFlameSoundIntervalTicks = Mth.clamp(value, MIN_FLAME_SOUND_INTERVAL_TICKS,
                MAX_FLAME_SOUND_INTERVAL_TICKS);
    }

    /** The hiss as a vent's warning starts. */
    public BossSoundCue getHissSound() { return ventHissSound; }

    public BossSoundCue getBurstSound() { return ventBurstSound; }

    /** The roar of a burning flame, once per its interval. */
    public BossSoundCue getFlameSound() { return ventFlameSound; }

    /** The thud as a wall comes out of its face. */
    public BossSoundCue getWallSound() { return ventWallSound; }

    /** The stream out of a burning vent's face. */
    public BossParticleCue getFlameParticles() { return ventFlameParticles; }

    /** The smoke where a flame's stream gives out, at its far side. */
    public BossParticleCue getSmokeParticles() { return ventSmokeParticles; }

    /** What a blast throws up off the vent's face. */
    public BossParticleCue getBurstParticles() { return ventBurstParticles; }

    /** The front of a wall, as it comes out and while it stands. */
    public BossParticleCue getWallParticles() { return ventWallParticles; }

    /** The dots over the face of a vent about to go, with its outline. */
    public BossParticleCue getWarnParticles() { return ventWarnParticles; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return ventCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("VentEnabled", ventEnabled);
        tag.putString("VentAnimation", ventAnimation);
        tag.putInt("VentActionDelayTicks", ventActionDelayTicks);
        tag.putInt("VentCooldownTicks", ventCooldownTicks);
        tag.put("VentZones", ventZones.writeToNBT());
        tag.putInt("VentMode", ventMode);
        tag.putInt("VentPattern", ventPattern);
        tag.putInt("VentRandomCount", ventRandomCount);
        tag.putInt("VentCycleTicks", ventCycleTicks);
        tag.putInt("VentWarnTicks", ventWarnTicks);
        tag.putInt("VentActiveTicks", ventActiveTicks);
        tag.putInt("VentRepeats", ventRepeats);
        tag.putInt("VentRecast", ventRecast);
        tag.putInt("VentDamage", ventDamage);
        tag.putInt("VentHitIntervalTicks", ventHitIntervalTicks);
        tag.putInt("VentKnockback", ventKnockback);
        tag.put("VentEffects", ventEffects.writeToNBT());
        tag.putString("VentBurstVfx", ventBurstVfx);
        tag.putInt("VentBurstVfxTicks", ventBurstVfxTicks);
        tag.putInt("VentWallPushTenths", ventWallPushTenths);
        tag.putInt("VentWallMode", ventWallMode);
        tag.putInt("VentWallDamage", ventWallDamage);
        tag.putInt("VentWallLift", ventWallLift);
        tag.putInt("VentParticleBudget", ventParticleBudget);
        tag.putInt("VentFlameSoundIntervalTicks", ventFlameSoundIntervalTicks);
        ventHissSound.writeToNBT(tag, "VentHissSound");
        ventBurstSound.writeToNBT(tag, "VentBurstSound");
        ventFlameSound.writeToNBT(tag, "VentFlameSound");
        ventWallSound.writeToNBT(tag, "VentWallSound");
        ventFlameParticles.writeToNBT(tag, "VentFlameParticles");
        ventSmokeParticles.writeToNBT(tag, "VentSmokeParticles");
        ventBurstParticles.writeToNBT(tag, "VentBurstParticles");
        ventWallParticles.writeToNBT(tag, "VentWallParticles");
        ventWarnParticles.writeToNBT(tag, "VentWarnParticles");
        ventCastSpot.writeToNBT(tag, "Vent");
    }

    void readFromNBT(CompoundTag tag) {
        ventEnabled = tag.getBoolean("VentEnabled");
        ventAnimation = clean(tag.getString("VentAnimation"));
        ventActionDelayTicks = value(tag, "VentActionDelayTicks", 20, 0, MAX_ACTION_DELAY_TICKS);
        ventCooldownTicks = value(tag, "VentCooldownTicks", 600, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
        ventZones.readFromNBT(tag, "VentZones");
        ventMode = value(tag, "VentMode", MODE_FLAME, MODE_BURST, MODE_WALL);
        ventPattern = value(tag, "VentPattern", PATTERN_ALL, PATTERN_ALL, PATTERN_RANDOM);
        ventRandomCount = value(tag, "VentRandomCount", 1, MIN_RANDOM_COUNT, MAX_RANDOM_COUNT);
        ventCycleTicks = value(tag, "VentCycleTicks", 100, MIN_CYCLE_TICKS, MAX_CYCLE_TICKS);
        ventWarnTicks = value(tag, "VentWarnTicks", 20, 0, MAX_WARN_TICKS);
        ventActiveTicks = value(tag, "VentActiveTicks", 40, MIN_ACTIVE_TICKS, MAX_ACTIVE_TICKS);
        ventRepeats = value(tag, "VentRepeats", 0, 0, MAX_REPEATS);
        ventRecast = value(tag, "VentRecast", RECAST_RESTART, RECAST_RESTART, RECAST_IGNORE);
        ventDamage = value(tag, "VentDamage", 6, 0, MAX_DAMAGE);
        ventHitIntervalTicks = value(tag, "VentHitIntervalTicks", 10, MIN_HIT_INTERVAL_TICKS,
                MAX_HIT_INTERVAL_TICKS);
        ventKnockback = value(tag, "VentKnockback", 1, 0, MAX_KNOCKBACK);
        ventEffects.readFromNBT(tag, "VentEffects");
        // Unlike the area attack's wave, a blast shows by default: an absent key is the fire the
        // ability shipped with, not silence.
        ventBurstVfx = tag.contains("VentBurstVfx") ? AreaVfxStyles.normalize(tag.getString("VentBurstVfx"))
                : AreaVfxStyles.FIRE;
        ventBurstVfxTicks = value(tag, "VentBurstVfxTicks", 10, MIN_BURST_VFX_TICKS, MAX_BURST_VFX_TICKS);
        ventWallPushTenths = value(tag, "VentWallPushTenths", 8, MIN_WALL_PUSH, MAX_WALL_PUSH);
        ventWallMode = value(tag, "VentWallMode", WALL_PUSH, WALL_PUSH, WALL_PIN);
        ventWallDamage = value(tag, "VentWallDamage", 0, 0, MAX_DAMAGE);
        ventWallLift = value(tag, "VentWallLift", 2, 0, MAX_WALL_LIFT);
        ventParticleBudget = value(tag, "VentParticleBudget", 48, MIN_PARTICLE_BUDGET, MAX_PARTICLE_BUDGET);
        ventFlameSoundIntervalTicks = value(tag, "VentFlameSoundIntervalTicks", 10,
                MIN_FLAME_SOUND_INTERVAL_TICKS, MAX_FLAME_SOUND_INTERVAL_TICKS);
        ventHissSound.readFromNBT(tag, "VentHissSound");
        ventBurstSound.readFromNBT(tag, "VentBurstSound");
        ventFlameSound.readFromNBT(tag, "VentFlameSound");
        ventWallSound.readFromNBT(tag, "VentWallSound");
        ventFlameParticles.readFromNBT(tag, "VentFlameParticles");
        ventSmokeParticles.readFromNBT(tag, "VentSmokeParticles");
        ventBurstParticles.readFromNBT(tag, "VentBurstParticles");
        ventWallParticles.readFromNBT(tag, "VentWallParticles");
        ventWarnParticles.readFromNBT(tag, "VentWarnParticles");
        ventCastSpot.readFromNBT(tag, "Vent");
    }
}
