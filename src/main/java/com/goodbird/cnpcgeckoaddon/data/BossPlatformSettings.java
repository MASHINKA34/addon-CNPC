package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The platforms: the builder's boxes, which of them the boss sets alight, how long the party
 * gets to jump off, and what happens to whoever is still standing on one when it goes.
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#platform()}.
 * It owns its own save format under the {@code Platform} prefix; a boss saved before the
 * platforms existed carries none of these keys and reads back with the ability switched off.</p>
 */
public final class BossPlatformSettings {

    public static final int MAX_ACTION_DELAY_TICKS = 1200;
    public static final int MIN_COOLDOWN_TICKS = 1;
    public static final int MAX_COOLDOWN_TICKS = 12000;
    /** Half a second at the least: shorter than that nobody can see the outline, let alone jump. */
    public static final int MIN_FUSE_TICKS = 10;
    public static final int MAX_FUSE_TICKS = 1200;
    public static final int MAX_DAMAGE = 1000;
    public static final int MAX_KNOCKBACK = 10;
    /** Tenths of a block per tick, the geyser's units, and as high as the cone strike's throw goes. */
    public static final int MAX_LAUNCH = 40;
    public static final int MAX_LINGER_TICKS = 12000;
    public static final int MIN_LINGER_INTERVAL_TICKS = 1;
    public static final int MAX_LINGER_INTERVAL_TICKS = 200;
    /** The look of the fuse and of the bang, in the units their labels name. */
    public static final int MIN_BLINK_TICKS = 1;
    public static final int MAX_BLINK_TICKS = 40;
    public static final int MIN_COUNTDOWN_INTERVAL_TICKS = 5;
    public static final int MAX_COUNTDOWN_INTERVAL_TICKS = 200;
    public static final int MAX_FLARE = 200;
    public static final int MIN_FLARE_AREA = 5;
    public static final int MAX_FLARE_AREA = 400;

    private boolean platformEnabled;
    private String platformAnimation = "";
    private int platformActionDelayTicks = 10;
    private int platformCooldownTicks = 300;
    private final BossPlatformZoneList platformZones = new BossPlatformZoneList();
    private int platformPickMode = BossPhaseData.PLATFORM_PICK_RANDOM;
    /** The window between the outline lighting up and the platform going off: the whole mechanic. */
    private int platformFuseTicks = 60;
    private int platformDamage = 12;
    private int platformKnockback = 2;
    private int platformLaunch;
    private final BossEffectSet platformEffects = new BossEffectSet();
    /** How long a platform that went off keeps burning whoever stands on it; zero is the one hit. */
    private int platformLingerTicks;
    private int platformLingerIntervalTicks = 20;
    private String platformVfx = AreaVfxStyles.NONE;
    /** Half a flash of the outline: painted for this many ticks, then not for as many. */
    private int platformBlinkTicks = 4;
    /** How often the countdown names a new number; once a second is what it always did. */
    private int platformCountdownIntervalTicks = 20;
    /** The most pops the bang throws up, and how much floor each one stands for. */
    private int platformFlareMax = 24;
    /** Tenths of a square block per pop, so 40 is the 4.0 the code used to divide by. */
    private int platformFlareArea = 40;
    private final BossSoundCue platformLitSound = new BossSoundCue("minecraft:entity.tnt.primed", 1.5F, 0.8F);
    private final BossParticleCue platformOutlineParticles = new BossParticleCue("minecraft:flame", 1);
    private final BossParticleCue platformBlastParticles = new BossParticleCue("minecraft:lava", 1);
    private final BossSoundCue platformBlastSound = new BossSoundCue("minecraft:entity.generic.explode", 2.0F, 0.9F);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot platformCastSpot = new BossCastSpot();

    public boolean isEnabled() { return platformEnabled; }

    public void setEnabled(boolean value) { platformEnabled = value; }

    /** Whether the rotation may cast this: switched on, with a platform to set alight. */
    public boolean canCast() { return platformEnabled && isConfigured(); }

    /**
     * Whether there is a platform switched on to set alight, whatever the switch says: all a
     * chained start still needs.
     */
    public boolean isConfigured() { return platformZones.hasEnabled(); }

    /** The wind-up: played while the boss picks its platform, before the fuse is lit. */
    public String getAnimation() { return platformAnimation; }

    public void setAnimation(String value) { platformAnimation = clean(value); }

    public int getActionDelayTicks() { return platformActionDelayTicks; }

    public void setActionDelayTicks(int value) {
        platformActionDelayTicks = Mth.clamp(value, 0, MAX_ACTION_DELAY_TICKS);
    }

    public int getCooldownTicks() { return platformCooldownTicks; }

    public void setCooldownTicks(int value) {
        platformCooldownTicks = Mth.clamp(value, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
    }

    /** The builder's platforms, in list order: the order a cast taken in turn walks. */
    public BossPlatformZoneList getZones() { return platformZones; }

    /** Which platforms a cast sets alight: a random one, the target's, the next in turn, or all but one. */
    public int getPickMode() { return platformPickMode; }

    public void setPickMode(int value) {
        platformPickMode = Mth.clamp(value, BossPhaseData.PLATFORM_PICK_RANDOM, BossPhaseData.PLATFORM_PICK_ALL_BUT_ONE);
    }

    public int getFuseTicks() { return platformFuseTicks; }

    public void setFuseTicks(int value) { platformFuseTicks = Mth.clamp(value, MIN_FUSE_TICKS, MAX_FUSE_TICKS); }

    /** What the platform hits for when it goes off, and what each dose of its smoulder hits for. */
    public int getDamage() { return platformDamage; }

    public void setDamage(int value) { platformDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** How hard whoever stays on is shoved away from the platform's middle. */
    public int getKnockback() { return platformKnockback; }

    public void setKnockback(int value) { platformKnockback = Mth.clamp(value, 0, MAX_KNOCKBACK); }

    /** Upward throw in tenths of a block per tick, the way a geyser's is given; zero throws nobody. */
    public int getLaunch() { return platformLaunch; }

    public void setLaunch(int value) { platformLaunch = Mth.clamp(value, 0, MAX_LAUNCH); }

    public BossEffectSet getEffects() { return platformEffects; }

    public int getLingerTicks() { return platformLingerTicks; }

    public void setLingerTicks(int value) { platformLingerTicks = Mth.clamp(value, 0, MAX_LINGER_TICKS); }

    /** The pause between two doses of a smouldering platform. */
    public int getLingerIntervalTicks() { return platformLingerIntervalTicks; }

    public void setLingerIntervalTicks(int value) {
        platformLingerIntervalTicks = Mth.clamp(value, MIN_LINGER_INTERVAL_TICKS, MAX_LINGER_INTERVAL_TICKS);
    }

    /** The wave that runs out of a platform's middle as it goes off. */
    public String getVfx() { return platformVfx; }

    public void setVfx(String value) { platformVfx = AreaVfxStyles.normalize(value); }

    /** Half a flash of the outline, in ticks: bigger is a slower, calmer blink. */
    public int getBlinkTicks() { return platformBlinkTicks; }

    public void setBlinkTicks(int value) {
        platformBlinkTicks = Mth.clamp(value, MIN_BLINK_TICKS, MAX_BLINK_TICKS);
    }

    /** Ticks between two numbers of the countdown in the party's action bar. */
    public int getCountdownIntervalTicks() { return platformCountdownIntervalTicks; }

    public void setCountdownIntervalTicks(int value) {
        platformCountdownIntervalTicks = Mth.clamp(value, MIN_COUNTDOWN_INTERVAL_TICKS,
                MAX_COUNTDOWN_INTERVAL_TICKS);
    }

    /** The ceiling on the pops one bang throws up; zero throws none at all. */
    public int getFlareMax() { return platformFlareMax; }

    public void setFlareMax(int value) { platformFlareMax = Mth.clamp(value, 0, MAX_FLARE); }

    /** Tenths of a square block of platform per pop, so a small one still reads as going off. */
    public int getFlareArea() { return platformFlareArea; }

    public void setFlareArea(int value) {
        platformFlareArea = Mth.clamp(value, MIN_FLARE_AREA, MAX_FLARE_AREA);
    }

    /** Square blocks per pop, the number the count is worked out with. */
    public double flareAreaPerPop() { return platformFlareArea / 10.0D; }

    /** The hiss as the fuse catches. */
    public BossSoundCue getLitSound() { return platformLitSound; }

    /** The flame inside a platform that is already burning. */
    public BossParticleCue getOutlineParticles() { return platformOutlineParticles; }

    /** The pops thrown up as the platform goes off, one puff per pop. */
    public BossParticleCue getBlastParticles() { return platformBlastParticles; }

    public BossSoundCue getBlastSound() { return platformBlastSound; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return platformCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("PlatformEnabled", platformEnabled);
        tag.putString("PlatformAnimation", platformAnimation);
        tag.putInt("PlatformActionDelayTicks", platformActionDelayTicks);
        tag.putInt("PlatformCooldownTicks", platformCooldownTicks);
        tag.put("PlatformZones", platformZones.writeToNBT());
        tag.putInt("PlatformPickMode", platformPickMode);
        tag.putInt("PlatformFuseTicks", platformFuseTicks);
        tag.putInt("PlatformDamage", platformDamage);
        tag.putInt("PlatformKnockback", platformKnockback);
        tag.putInt("PlatformLaunch", platformLaunch);
        tag.put("PlatformEffects", platformEffects.writeToNBT());
        tag.putInt("PlatformLingerTicks", platformLingerTicks);
        tag.putInt("PlatformLingerIntervalTicks", platformLingerIntervalTicks);
        tag.putString("PlatformVfx", platformVfx);
        tag.putInt("PlatformBlink", platformBlinkTicks);
        tag.putInt("PlatformCountdownInterval", platformCountdownIntervalTicks);
        tag.putInt("PlatformFlareMax", platformFlareMax);
        tag.putInt("PlatformFlareArea", platformFlareArea);
        platformLitSound.writeToNBT(tag, "PlatformLit");
        platformOutlineParticles.writeToNBT(tag, "PlatformOutline");
        platformBlastParticles.writeToNBT(tag, "PlatformBlastParticles");
        platformBlastSound.writeToNBT(tag, "PlatformBlast");
        platformCastSpot.writeToNBT(tag, "Platform");
    }

    void readFromNBT(CompoundTag tag) {
        platformEnabled = tag.getBoolean("PlatformEnabled");
        platformAnimation = clean(tag.getString("PlatformAnimation"));
        platformActionDelayTicks = value(tag, "PlatformActionDelayTicks", 10, 0, MAX_ACTION_DELAY_TICKS);
        platformCooldownTicks = value(tag, "PlatformCooldownTicks", 300, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
        platformZones.readFromNBT(tag, "PlatformZones");
        platformPickMode = value(tag, "PlatformPickMode", BossPhaseData.PLATFORM_PICK_RANDOM,
                BossPhaseData.PLATFORM_PICK_RANDOM, BossPhaseData.PLATFORM_PICK_ALL_BUT_ONE);
        platformFuseTicks = value(tag, "PlatformFuseTicks", 60, MIN_FUSE_TICKS, MAX_FUSE_TICKS);
        platformDamage = value(tag, "PlatformDamage", 12, 0, MAX_DAMAGE);
        platformKnockback = value(tag, "PlatformKnockback", 2, 0, MAX_KNOCKBACK);
        platformLaunch = value(tag, "PlatformLaunch", 0, 0, MAX_LAUNCH);
        platformEffects.readFromNBT(tag, "PlatformEffects");
        platformLingerTicks = value(tag, "PlatformLingerTicks", 0, 0, MAX_LINGER_TICKS);
        platformLingerIntervalTicks = value(tag, "PlatformLingerIntervalTicks", 20,
                MIN_LINGER_INTERVAL_TICKS, MAX_LINGER_INTERVAL_TICKS);
        platformVfx = AreaVfxStyles.normalize(tag.getString("PlatformVfx"));
        platformBlinkTicks = value(tag, "PlatformBlink", 4, MIN_BLINK_TICKS, MAX_BLINK_TICKS);
        platformCountdownIntervalTicks = value(tag, "PlatformCountdownInterval", 20,
                MIN_COUNTDOWN_INTERVAL_TICKS, MAX_COUNTDOWN_INTERVAL_TICKS);
        platformFlareMax = value(tag, "PlatformFlareMax", 24, 0, MAX_FLARE);
        platformFlareArea = value(tag, "PlatformFlareArea", 40, MIN_FLARE_AREA, MAX_FLARE_AREA);
        platformLitSound.readFromNBT(tag, "PlatformLit");
        platformOutlineParticles.readFromNBT(tag, "PlatformOutline");
        platformBlastParticles.readFromNBT(tag, "PlatformBlastParticles");
        platformBlastSound.readFromNBT(tag, "PlatformBlast");
        platformCastSpot.readFromNBT(tag, "Platform");
    }
}
