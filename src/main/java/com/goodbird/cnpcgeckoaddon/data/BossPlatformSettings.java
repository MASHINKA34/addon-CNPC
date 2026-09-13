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
        platformCastSpot.readFromNBT(tag, "Platform");
    }
}
