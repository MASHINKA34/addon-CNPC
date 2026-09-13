package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The field that pulls, pushes or throws everything around the boss.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#gravity()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossGravitySettings {

    /** The ends the field's own clocks, slack, wait and wind are held between. */
    public static final int MIN_INTERVAL_TICKS = 1;
    public static final int MAX_INTERVAL_TICKS = 200;
    public static final int MAX_PULL_SLACK = 50;
    public static final int MIN_LANDING_TIMEOUT_TICKS = 20;
    public static final int MAX_LANDING_TIMEOUT_TICKS = 2400;
    public static final int MAX_STREAM_PARTICLES = 20;
    public static final int MAX_STREAM_INNER_PERCENT = 100;
    public static final int MIN_STREAM_HEIGHT = 1;
    public static final int MAX_STREAM_HEIGHT = 100;

    private boolean gravityEnabled;
    private String gravityAnimation = "";
    private int gravityActionDelayTicks = 20;
    private int gravityCooldownTicks = 300;
    private int gravityMode = BossPhaseData.GRAVITY_MODE_PULL;
    private int gravityRadius = 16;
    /** How long the pull or the push keeps working; a throw is over the tick it happens. */
    private int gravityDurationTicks = 60;
    /**
     * Pull and push: hundredths of a block per tick added every tick, so 10 is a steady
     * 0.10. Throw: tenths of a block per tick straight up, the way the geyser's launch is.
     *
     * <p>The default is pitched against what a player on plain ground puts in per tick -
     * 0.098 walking, 0.127 sprinting - and sits between the two: a walker is held where
     * they stand, a sprinter gains about a block every sixteen ticks. Anyone standing still
     * is reeled in at walking pace.</p>
     */
    private int gravityStrength = 10;
    /** How close to the boss counts as touching it, for the pull's bite. */
    private int gravityTouchRadius = 2;
    private int gravityDamage = 8;
    private String gravityVfx = AreaVfxStyles.NONE;
    /** Ticks between one dose of the held effects and the next. */
    private int gravityEffectIntervalTicks = 20;
    /** The least a victim held against the boss gets between one bite and the next. */
    private int gravityBiteIntervalTicks = 20;
    /** How long the opening wave runs for; the field's own length is a setting of its own. */
    private int gravityVfxTicks = 20;
    /** Tenths of a block: inside this the pull lets go, or a victim at the boss would twitch. */
    private int gravityPullSlack = 10;
    /**
     * How long a thrown victim is waited for. A throw at full strength is down again inside a
     * hundred ticks; one that never comes down - flown off, teleported away - is forgotten
     * rather than bitten a minute later on some unrelated landing.
     */
    private int gravityLandingTimeoutTicks = 400;
    /** Motes drifting with the field each tick, so it reads as a wind and not only as a ring. */
    private int gravityStreamParticles = 3;
    /** The motes start no nearer the boss than this share of the radius, or they say nothing. */
    private int gravityStreamInnerPercent = 35;
    /** Tenths of a block the motes are scattered up through. */
    private int gravityStreamHeight = 20;
    private final BossSoundCue gravityOpenSound =
            new BossSoundCue("minecraft:block.beacon.activate", 1.5F, 0.5F);
    private final BossSoundCue gravityPushSound =
            new BossSoundCue("minecraft:entity.wind_charge.wind_burst", 1.5F, 0.7F);
    private final BossSoundCue gravityLaunchSound =
            new BossSoundCue("minecraft:entity.wind_charge.wind_burst", 2.0F, 0.5F);
    private final BossSoundCue gravityLandingSound =
            new BossSoundCue("minecraft:entity.generic.big_fall", 1.0F, 0.8F);
    /** Dosed every second to everyone inside the field, whichever way it is pushing them. */
    private final BossEffectSet gravityEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot gravityCastSpot = new BossCastSpot();

    public boolean isEnabled() { return gravityEnabled; }

    public void setEnabled(boolean value) { gravityEnabled = value; }

    public String getAnimation() { return gravityAnimation; }

    public void setAnimation(String value) { gravityAnimation = clean(value); }

    public int getActionDelayTicks() { return gravityActionDelayTicks; }

    public void setActionDelayTicks(int value) { gravityActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return gravityCooldownTicks; }

    public void setCooldownTicks(int value) { gravityCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Which way the field works: in, out, or up. */
    public int getMode() { return gravityMode; }

    public void setMode(int value) {
        gravityMode = Mth.clamp(value, BossPhaseData.GRAVITY_MODE_PULL, BossPhaseData.GRAVITY_MODE_LIFT);
    }

    public int getRadius() { return gravityRadius; }

    public void setRadius(int value) { gravityRadius = Mth.clamp(value, 3, 48); }

    /** How long a pull or a push runs for; a throw ignores it. */
    public int getDurationTicks() { return gravityDurationTicks; }

    public void setDurationTicks(int value) { gravityDurationTicks = Mth.clamp(value, 5, 400); }

    /** Hundredths of a block per tick for the pull and the push, tenths for the throw. */
    public int getStrength() { return gravityStrength; }

    public void setStrength(int value) { gravityStrength = Mth.clamp(value, 1, 20); }

    /** How close to the boss the pull has to get somebody before it starts to hurt them. */
    public int getTouchRadius() { return gravityTouchRadius; }

    public void setTouchRadius(int value) { gravityTouchRadius = Mth.clamp(value, 1, 6); }

    /** What the pull's bite and the throw's landing hit for; the push never hurts. */
    public int getDamage() { return gravityDamage; }

    public void setDamage(int value) { gravityDamage = Mth.clamp(value, 0, 1000); }

    public String getVfx() { return gravityVfx; }

    public void setVfx(String value) { gravityVfx = AreaVfxStyles.normalize(value); }

    /** Ticks between one dose of the held effects and the next. */
    public int getEffectIntervalTicks() { return gravityEffectIntervalTicks; }

    public void setEffectIntervalTicks(int value) {
        gravityEffectIntervalTicks = Mth.clamp(value, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
    }

    /** The least somebody held against the boss gets between one bite and the next. */
    public int getBiteIntervalTicks() { return gravityBiteIntervalTicks; }

    public void setBiteIntervalTicks(int value) {
        gravityBiteIntervalTicks = Mth.clamp(value, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
    }

    /** Ticks the wave that opens the field is drawn for. */
    public int getVfxTicks() { return gravityVfxTicks; }

    public void setVfxTicks(int value) {
        gravityVfxTicks = Mth.clamp(value, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
    }

    /** Tenths of a block inside which the pull lets go; nought drags all the way in. */
    public int getPullSlackTenths() { return gravityPullSlack; }

    public void setPullSlackTenths(int value) {
        gravityPullSlack = Mth.clamp(value, 0, MAX_PULL_SLACK);
    }

    /** How long a thrown victim is waited for before the landing is forgiven. */
    public int getLandingTimeoutTicks() { return gravityLandingTimeoutTicks; }

    public void setLandingTimeoutTicks(int value) {
        gravityLandingTimeoutTicks = Mth.clamp(value, MIN_LANDING_TIMEOUT_TICKS, MAX_LANDING_TIMEOUT_TICKS);
    }

    /** Motes of wind thrown each tick; nought leaves the field with only its ring. */
    public int getStreamParticles() { return gravityStreamParticles; }

    public void setStreamParticles(int value) {
        gravityStreamParticles = Mth.clamp(value, 0, MAX_STREAM_PARTICLES);
    }

    /** How much of the radius nearest the boss the wind leaves empty. */
    public int getStreamInnerPercent() { return gravityStreamInnerPercent; }

    public void setStreamInnerPercent(int value) {
        gravityStreamInnerPercent = Mth.clamp(value, 0, MAX_STREAM_INNER_PERCENT);
    }

    /** Tenths of a block the wind is scattered up through. */
    public int getStreamHeightTenths() { return gravityStreamHeight; }

    public void setStreamHeightTenths(int value) {
        gravityStreamHeight = Mth.clamp(value, MIN_STREAM_HEIGHT, MAX_STREAM_HEIGHT);
    }

    /** The hum as a pull opens. */
    public BossSoundCue getOpenSound() { return gravityOpenSound; }

    /** And the gust as a push does; the two modes never sounded alike. */
    public BossSoundCue getPushSound() { return gravityPushSound; }

    /** The burst of a throw. */
    public BossSoundCue getLaunchSound() { return gravityLaunchSound; }

    /** And the thud whoever was thrown comes down with. */
    public BossSoundCue getLandingSound() { return gravityLandingSound; }

    public BossEffectSet getEffects() { return gravityEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return gravityCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("GravityEnabled", gravityEnabled);
        tag.putString("GravityAnimation", gravityAnimation);
        tag.putInt("GravityActionDelayTicks", gravityActionDelayTicks);
        tag.putInt("GravityCooldownTicks", gravityCooldownTicks);
        tag.putInt("GravityMode", gravityMode);
        tag.putInt("GravityRadius", gravityRadius);
        tag.putInt("GravityDurationTicks", gravityDurationTicks);
        tag.putInt("GravityStrength", gravityStrength);
        tag.putInt("GravityTouchRadius", gravityTouchRadius);
        tag.putInt("GravityDamage", gravityDamage);
        tag.putString("GravityVfx", gravityVfx);
        tag.putInt("GravityEffectInterval", gravityEffectIntervalTicks);
        tag.putInt("GravityBiteInterval", gravityBiteIntervalTicks);
        // Not "GravityVfx": that key is the style the wave is drawn in, and has been since
        // before the wave had a length of its own.
        tag.putInt("GravityVfxTicks", gravityVfxTicks);
        tag.putInt("GravityPullSlack", gravityPullSlack);
        tag.putInt("GravityLandingTimeout", gravityLandingTimeoutTicks);
        tag.putInt("GravityStreamParticles", gravityStreamParticles);
        tag.putInt("GravityStreamInner", gravityStreamInnerPercent);
        tag.putInt("GravityStreamHeight", gravityStreamHeight);
        gravityOpenSound.writeToNBT(tag, "GravityOpenSound");
        gravityPushSound.writeToNBT(tag, "GravityPushSound");
        gravityLaunchSound.writeToNBT(tag, "GravityLaunchSound");
        gravityLandingSound.writeToNBT(tag, "GravityLandingSound");
        tag.put("GravityEffects", gravityEffects.writeToNBT());
        gravityCastSpot.writeToNBT(tag, "Gravity");
    }

    void readFromNBT(CompoundTag tag) {
        gravityEnabled = tag.getBoolean("GravityEnabled");
        gravityAnimation = clean(tag.getString("GravityAnimation"));
        gravityActionDelayTicks = value(tag, "GravityActionDelayTicks", 20, 0, 1200);
        gravityCooldownTicks = value(tag, "GravityCooldownTicks", 300, 1, 12000);
        gravityMode = value(tag, "GravityMode", BossPhaseData.GRAVITY_MODE_PULL, BossPhaseData.GRAVITY_MODE_PULL, BossPhaseData.GRAVITY_MODE_LIFT);
        gravityRadius = value(tag, "GravityRadius", 16, 3, 48);
        gravityDurationTicks = value(tag, "GravityDurationTicks", 60, 5, 400);
        gravityStrength = value(tag, "GravityStrength", 10, 1, 20);
        gravityTouchRadius = value(tag, "GravityTouchRadius", 2, 1, 6);
        gravityDamage = value(tag, "GravityDamage", 8, 0, 1000);
        gravityVfx = AreaVfxStyles.normalize(tag.getString("GravityVfx"));
        gravityEffectIntervalTicks = value(tag, "GravityEffectInterval", 20,
                MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
        gravityBiteIntervalTicks = value(tag, "GravityBiteInterval", 20,
                MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
        gravityVfxTicks = value(tag, "GravityVfxTicks", 20, MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
        gravityPullSlack = value(tag, "GravityPullSlack", 10, 0, MAX_PULL_SLACK);
        gravityLandingTimeoutTicks = value(tag, "GravityLandingTimeout", 400,
                MIN_LANDING_TIMEOUT_TICKS, MAX_LANDING_TIMEOUT_TICKS);
        gravityStreamParticles = value(tag, "GravityStreamParticles", 3, 0, MAX_STREAM_PARTICLES);
        gravityStreamInnerPercent = value(tag, "GravityStreamInner", 35, 0, MAX_STREAM_INNER_PERCENT);
        gravityStreamHeight = value(tag, "GravityStreamHeight", 20, MIN_STREAM_HEIGHT, MAX_STREAM_HEIGHT);
        gravityOpenSound.readFromNBT(tag, "GravityOpenSound");
        gravityPushSound.readFromNBT(tag, "GravityPushSound");
        gravityLaunchSound.readFromNBT(tag, "GravityLaunchSound");
        gravityLandingSound.readFromNBT(tag, "GravityLandingSound");
        gravityEffects.readFromNBT(tag, "GravityEffects");
        gravityCastSpot.readFromNBT(tag, "Gravity");
    }
}
