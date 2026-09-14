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
    /** The look of the fill, the pillars and the smoulder, in the units their labels name. */
    public static final int MIN_EDGE_SPACING = 25;
    public static final int MAX_EDGE_SPACING = 200;
    public static final int MAX_FILL_DENSITY = 60;
    public static final int MAX_FUSE_RAMP = 500;
    public static final int MAX_PILLAR_HEIGHT = 80;

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
    /**
     * Hundredths of a block between two points of the outline. Half a block reads as a line
     * from across the arena where the whole block the hazard's edge walks at read as dots.
     *
     * <p>These, the fill, the pillars and the smoulder below are the one deliberate break with
     * "an old save burns as it used to": the old look was found to be all but invisible, so
     * an old boss gets the new look too. Only the picture moves - its damage, fuse, shove and
     * noises are exactly what they were - and the sparse look is back at densities of nought.</p>
     */
    private int platformEdgeSpacing = 50;
    /** Fill particles per ten square blocks on each repaint of the fuse, and how much more by its end. */
    private int platformFuseFillDensity = 6;
    private int platformFuseRampPercent = 200;
    private final BossParticleCue platformFuseParticles = new BossParticleCue("minecraft:flame", 1);
    /** Tenths of a block the pillar in each corner rises to; nought is no pillars. */
    private int platformPillarHeight = 20;
    private final BossParticleCue platformPillarParticles = new BossParticleCue("minecraft:soul_fire_flame", 1);
    /** Smoulder particles per ten square blocks on each repaint of a platform that went off. */
    private int platformSmoulderDensity = 4;
    private final BossParticleCue platformSmokeParticles = new BossParticleCue("minecraft:smoke", 1);
    private final BossParticleCue platformBlastFlash = new BossParticleCue("minecraft:explosion_emitter", 1);
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

    /** The smoulder: the flame inside a platform that has already gone off. */
    public BossParticleCue getOutlineParticles() { return platformOutlineParticles; }

    /** The pops thrown up as the platform goes off, one puff per pop. */
    public BossParticleCue getBlastParticles() { return platformBlastParticles; }

    public BossSoundCue getBlastSound() { return platformBlastSound; }

    /** Hundredths of a block between two points of the outline. */
    public int getEdgeSpacing() { return platformEdgeSpacing; }

    public void setEdgeSpacing(int value) {
        platformEdgeSpacing = Mth.clamp(value, MIN_EDGE_SPACING, MAX_EDGE_SPACING);
    }

    /** The same in blocks, the number the outline is walked with. */
    public double edgeSpacing() { return platformEdgeSpacing / 100.0D; }

    /** Fill particles per ten square blocks on each repaint of the fuse; nought is no fill. */
    public int getFuseFillDensity() { return platformFuseFillDensity; }

    public void setFuseFillDensity(int value) {
        platformFuseFillDensity = Mth.clamp(value, 0, MAX_FILL_DENSITY);
    }

    /** How much thicker the fill is by the end of the fuse than at its start, in per cent. */
    public int getFuseRampPercent() { return platformFuseRampPercent; }

    public void setFuseRampPercent(int value) {
        platformFuseRampPercent = Mth.clamp(value, 0, MAX_FUSE_RAMP);
    }

    /** The fire over the floor while the fuse burns. */
    public BossParticleCue getFuseParticles() { return platformFuseParticles; }

    /** Tenths of a block the pillar in each corner rises to; nought is no pillars. */
    public int getPillarHeight() { return platformPillarHeight; }

    public void setPillarHeight(int value) {
        platformPillarHeight = Mth.clamp(value, 0, MAX_PILLAR_HEIGHT);
    }

    /** The same in blocks. */
    public double pillarHeight() { return platformPillarHeight / 10.0D; }

    /** One of these every half block up each pillar. */
    public BossParticleCue getPillarParticles() { return platformPillarParticles; }

    /** Smoulder particles per ten square blocks on each repaint after the platform went off. */
    public int getSmoulderDensity() { return platformSmoulderDensity; }

    public void setSmoulderDensity(int value) {
        platformSmoulderDensity = Mth.clamp(value, 0, MAX_FILL_DENSITY);
    }

    /** The smoke that drifts up off the smoulder, as many as the smoulder itself. */
    public BossParticleCue getSmokeParticles() { return platformSmokeParticles; }

    /** The one flash in the middle of the platform as it goes off. */
    public BossParticleCue getBlastFlash() { return platformBlastFlash; }

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
        tag.putInt("PlatformEdgeSpacing", platformEdgeSpacing);
        tag.putInt("PlatformFuseFill", platformFuseFillDensity);
        tag.putInt("PlatformFuseRamp", platformFuseRampPercent);
        platformFuseParticles.writeToNBT(tag, "PlatformFuse");
        tag.putInt("PlatformPillarHeight", platformPillarHeight);
        platformPillarParticles.writeToNBT(tag, "PlatformPillarCue");
        tag.putInt("PlatformSmoulder", platformSmoulderDensity);
        platformSmokeParticles.writeToNBT(tag, "PlatformSmoke");
        platformBlastFlash.writeToNBT(tag, "PlatformFlash");
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
        platformEdgeSpacing = value(tag, "PlatformEdgeSpacing", 50, MIN_EDGE_SPACING, MAX_EDGE_SPACING);
        platformFuseFillDensity = value(tag, "PlatformFuseFill", 6, 0, MAX_FILL_DENSITY);
        platformFuseRampPercent = value(tag, "PlatformFuseRamp", 200, 0, MAX_FUSE_RAMP);
        platformFuseParticles.readFromNBT(tag, "PlatformFuse");
        platformPillarHeight = value(tag, "PlatformPillarHeight", 20, 0, MAX_PILLAR_HEIGHT);
        platformPillarParticles.readFromNBT(tag, "PlatformPillarCue");
        platformSmoulderDensity = value(tag, "PlatformSmoulder", 4, 0, MAX_FILL_DENSITY);
        platformSmokeParticles.readFromNBT(tag, "PlatformSmoke");
        platformBlastFlash.readFromNBT(tag, "PlatformFlash");
        platformCastSpot.readFromNBT(tag, "Platform");
    }
}
