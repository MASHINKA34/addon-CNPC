package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Per-npc settings that make this npc a launch pad: a player who touches it is thrown along an
 * arc and comes down on the block set here.
 *
 * <p>These live on the npc for the reason carrying does: what a boss drops into its arena for
 * the players to jump with is a plain clone, and a pad placed by hand has to work with no boss
 * anywhere near it.</p>
 */
public class NpcLaunchPadData {
    /** The block is counted from the block the npc stands in, so the spot travels with the pad. */
    public static final int COORDINATE_NPC_OFFSET = 0;
    public static final int COORDINATE_ABSOLUTE = 1;
    public static final int MAX_COORDINATE = 30000000;
    public static final int MIN_ARC_HEIGHT = 1;
    public static final int MAX_ARC_HEIGHT = 64;
    public static final int DEFAULT_ARC_HEIGHT = 6;
    public static final int MAX_COOLDOWN_TICKS = 1200;
    public static final int DEFAULT_COOLDOWN_TICKS = 20;
    public static final int MAX_LIFETIME_TICKS = 72000;
    /** Tenths of a block: how far outside the pad's own box standing against it still counts. */
    public static final int DEFAULT_TOUCH_MARGIN_TENTHS = 3;
    public static final int MAX_TOUCH_MARGIN_TENTHS = 20;
    public static final int DEFAULT_LANDING_GRACE_TICKS = 40;
    public static final int MAX_LANDING_GRACE_TICKS = 400;

    private static final String ENABLED_KEY = "GeckoNpcLaunchEnabled";
    private static final String COORDINATE_MODE_KEY = "GeckoNpcLaunchCoordMode";
    private static final String X_KEY = "GeckoNpcLaunchX";
    private static final String Y_KEY = "GeckoNpcLaunchY";
    private static final String Z_KEY = "GeckoNpcLaunchZ";
    private static final String HEIGHT_KEY = "GeckoNpcLaunchHeight";
    private static final String COOLDOWN_KEY = "GeckoNpcLaunchCooldown";
    private static final String NO_FALL_KEY = "GeckoNpcLaunchNoFall";
    private static final String SOUND_KEY = "GeckoNpcLaunchSound";
    private static final String LIFETIME_KEY = "GeckoNpcLaunchLifetime";
    private static final String TOUCH_MARGIN_KEY = "GeckoNpcLaunchTouchMargin";
    private static final String GRACE_KEY = "GeckoNpcLaunchGrace";
    /**
     * The cue prefixes. The launch sound's is the switch's own key with the cue's suffixes
     * after it - {@code ...SoundOn}, {@code ...SoundSound} - which is a different string from
     * the bare {@code GeckoNpcLaunchSound} the switch is saved under, so the two never meet.
     */
    private static final String LAUNCH_SOUND_PREFIX = "GeckoNpcLaunchSound";
    private static final String LAUNCH_PARTICLES_PREFIX = "GeckoNpcLaunchParticles";
    private static final String EXPIRE_PARTICLES_PREFIX = "GeckoNpcLaunchExpire";

    private boolean enabled;
    private int coordinateMode = COORDINATE_NPC_OFFSET;
    private int x;
    private int y;
    private int z;
    private int arcHeight = DEFAULT_ARC_HEIGHT;
    private int cooldownTicks = DEFAULT_COOLDOWN_TICKS;
    private boolean noFallDamage = true;
    private boolean sound = true;
    private int lifetimeTicks;
    private int touchMarginTenths = DEFAULT_TOUCH_MARGIN_TENTHS;
    private int landingGraceTicks = DEFAULT_LANDING_GRACE_TICKS;
    private final BossSoundCue launchSound =
            new BossSoundCue("minecraft:block.slime_block.fall", 1.0F, 1.2F);
    private final BossParticleCue launchParticles = new BossParticleCue("minecraft:cloud", 12);
    private final BossParticleCue expireParticles = new BossParticleCue("minecraft:poof", 8);

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putInt(COORDINATE_MODE_KEY, coordinateMode);
        tag.putInt(X_KEY, x);
        tag.putInt(Y_KEY, y);
        tag.putInt(Z_KEY, z);
        tag.putInt(HEIGHT_KEY, arcHeight);
        tag.putInt(COOLDOWN_KEY, cooldownTicks);
        tag.putBoolean(NO_FALL_KEY, noFallDamage);
        tag.putBoolean(SOUND_KEY, sound);
        tag.putInt(LIFETIME_KEY, lifetimeTicks);
        tag.putInt(TOUCH_MARGIN_KEY, touchMarginTenths);
        tag.putInt(GRACE_KEY, landingGraceTicks);
        launchSound.writeToNBT(tag, LAUNCH_SOUND_PREFIX);
        launchParticles.writeToNBT(tag, LAUNCH_PARTICLES_PREFIX);
        expireParticles.writeToNBT(tag, EXPIRE_PARTICLES_PREFIX);
        return tag;
    }

    /**
     * Reads the settings back, keeping the defaults for keys an older world never wrote.
     *
     * <p>A missing boolean reads as false, which is right for the switch itself - an npc saved
     * before pads existed is not one - but would quietly turn the landing protection and the
     * sound off on a pad, so those two are asked for by name.</p>
     */
    public void readFromNBT(CompoundTag tag) {
        enabled = tag.getBoolean(ENABLED_KEY);
        coordinateMode = readInt(tag, COORDINATE_MODE_KEY, COORDINATE_NPC_OFFSET,
                COORDINATE_NPC_OFFSET, COORDINATE_ABSOLUTE);
        x = readInt(tag, X_KEY, 0, -MAX_COORDINATE, MAX_COORDINATE);
        y = readInt(tag, Y_KEY, 0, -MAX_COORDINATE, MAX_COORDINATE);
        z = readInt(tag, Z_KEY, 0, -MAX_COORDINATE, MAX_COORDINATE);
        arcHeight = readInt(tag, HEIGHT_KEY, DEFAULT_ARC_HEIGHT, MIN_ARC_HEIGHT, MAX_ARC_HEIGHT);
        cooldownTicks = readInt(tag, COOLDOWN_KEY, DEFAULT_COOLDOWN_TICKS, 0, MAX_COOLDOWN_TICKS);
        noFallDamage = !tag.contains(NO_FALL_KEY) || tag.getBoolean(NO_FALL_KEY);
        sound = !tag.contains(SOUND_KEY) || tag.getBoolean(SOUND_KEY);
        lifetimeTicks = readInt(tag, LIFETIME_KEY, 0, 0, MAX_LIFETIME_TICKS);
        touchMarginTenths = readInt(tag, TOUCH_MARGIN_KEY, DEFAULT_TOUCH_MARGIN_TENTHS,
                0, MAX_TOUCH_MARGIN_TENTHS);
        landingGraceTicks = readInt(tag, GRACE_KEY, DEFAULT_LANDING_GRACE_TICKS,
                0, MAX_LANDING_GRACE_TICKS);
        launchSound.readFromNBT(tag, LAUNCH_SOUND_PREFIX);
        launchParticles.readFromNBT(tag, LAUNCH_PARTICLES_PREFIX);
        expireParticles.readFromNBT(tag, EXPIRE_PARTICLES_PREFIX);
    }

    /** The default for a key an older world never wrote, and the clamp for one it did. */
    private static int readInt(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@link #COORDINATE_NPC_OFFSET} or {@link #COORDINATE_ABSOLUTE}. */
    public int getCoordinateMode() {
        return coordinateMode;
    }

    public void setCoordinateMode(int coordinateMode) {
        this.coordinateMode = Mth.clamp(coordinateMode, COORDINATE_NPC_OFFSET, COORDINATE_ABSOLUTE);
    }

    /** The block players land on top of, as a world position or as an offset from the npc. */
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public void setPosition(int x, int y, int z) {
        this.x = Mth.clamp(x, -MAX_COORDINATE, MAX_COORDINATE);
        this.y = Mth.clamp(y, -MAX_COORDINATE, MAX_COORDINATE);
        this.z = Mth.clamp(z, -MAX_COORDINATE, MAX_COORDINATE);
    }

    /** How far the arc climbs above the higher of the take-off and the landing. */
    public int getArcHeight() {
        return arcHeight;
    }

    public void setArcHeight(int arcHeight) {
        this.arcHeight = Mth.clamp(arcHeight, MIN_ARC_HEIGHT, MAX_ARC_HEIGHT);
    }

    /** How long this pad leaves a player it has thrown before it throws them again. */
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Mth.clamp(cooldownTicks, 0, MAX_COOLDOWN_TICKS);
    }

    public boolean isNoFallDamage() {
        return noFallDamage;
    }

    public void setNoFallDamage(boolean noFallDamage) {
        this.noFallDamage = noFallDamage;
    }

    public boolean isSound() {
        return sound;
    }

    public void setSound(boolean sound) {
        this.sound = sound;
    }

    /** How long a pad a boss summoned stays in the arena, or 0 for as long as the fight keeps it. */
    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public void setLifetimeTicks(int lifetimeTicks) {
        this.lifetimeTicks = Mth.clamp(lifetimeTicks, 0, MAX_LIFETIME_TICKS);
    }

    /**
     * How far past the pad's own box a player still counts as touching it, in tenths of a block.
     *
     * <p>A solid hitbox cannot be walked into, so standing against it from outside has to be
     * the touch, and how much "against" is worth depends on how wide the pad is drawn.</p>
     */
    public int getTouchMarginTenths() {
        return touchMarginTenths;
    }

    public void setTouchMarginTenths(int touchMarginTenths) {
        this.touchMarginTenths = Mth.clamp(touchMarginTenths, 0, MAX_TOUCH_MARGIN_TENTHS);
    }

    /** How long past the solved flight a launched player is still treated as in the air. */
    public int getLandingGraceTicks() {
        return landingGraceTicks;
    }

    public void setLandingGraceTicks(int landingGraceTicks) {
        this.landingGraceTicks = Mth.clamp(landingGraceTicks, 0, MAX_LANDING_GRACE_TICKS);
    }

    /** The noise the throw makes; {@link #isSound()} is still the switch over both cues. */
    public BossSoundCue getLaunchSound() {
        return launchSound;
    }

    /** The motes kicked up at the player's feet when they are thrown. */
    public BossParticleCue getLaunchParticles() {
        return launchParticles;
    }

    /** The puff a summoned pad leaves when its lifetime runs out. */
    public BossParticleCue getExpireParticles() {
        return expireParticles;
    }
}
