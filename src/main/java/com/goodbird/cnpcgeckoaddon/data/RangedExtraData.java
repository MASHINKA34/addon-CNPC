package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

public class RangedExtraData {
    /**
     * The furthest an npc may be told to back away.
     *
     * <p>The editor offers the same bound, but it is not the only way in: a number out of a
     * hand-edited save or an older world reaches {@code KeepDistanceGoal} exactly as a typed
     * one does, and the goal squares it.</p>
     */
    public static final int MAX_KEEP_DISTANCE = 64;

    /** Tenths of a block from the eyes, either way: the muzzle used to sit at {@code eyeY - 0.2}. */
    public static final int MIN_MUZZLE_HEIGHT = -20;
    public static final int MAX_MUZZLE_HEIGHT = 20;
    public static final int DEFAULT_MUZZLE_HEIGHT = -2;
    /** Tenths, the way every cue keeps a volume: the shot used to be played at 2.0F / 1.0F. */
    public static final int MAX_SHOT_VOLUME = 100;
    public static final int DEFAULT_SHOT_VOLUME = 20;
    public static final int MIN_SHOT_PITCH = 5;
    public static final int MAX_SHOT_PITCH = 30;
    public static final int DEFAULT_SHOT_PITCH = 10;

    /**
     * What CustomNPCs itself allows for the two ranged numbers this addon reads back out of
     * {@code DataRanged}, so its own editor is the only thing that decides them.
     *
     * <p>Both were held to figures of the addon's own invention, and neither was the one on
     * the screen: the explosion was forced to at least 1, so a projectile the CustomNPCs
     * editor was showing as "none" still went off, and the shot count was let up to 32, which
     * is three times what that editor offers and what its own load clamps to.</p>
     */
    public static final int MIN_EXPLODE_SIZE = 0;
    public static final int MAX_EXPLODE_SIZE = 3;
    public static final int MIN_SHOT_COUNT = 1;
    public static final int MAX_SHOT_COUNT = 10;

    /**
     * What an npc shoots when it has neither a usable entity of its own nor a projectile item.
     *
     * <p>CustomNPCs fires its own projectile whether or not the slot holds anything, and one
     * thrown as an empty stack takes the server down when it lands. An arrow is the default
     * rather than "nothing" so that an npc saved before this key existed goes on shooting
     * instead of going quiet; an empty id is how an npc is told not to shoot at all.</p>
     */
    public static final String DEFAULT_FALLBACK_PROJECTILE = "minecraft:arrow";
    private static final String FALLBACK_KEY = "GeckoNpcRangedFallback";

    private String projectileEntity = "";
    private String fallbackProjectile = DEFAULT_FALLBACK_PROJECTILE;
    private int keepDistance = 0;
    private int muzzleHeightTenths = DEFAULT_MUZZLE_HEIGHT;
    private int shotSoundVolumeTenths = DEFAULT_SHOT_VOLUME;
    private int shotSoundPitchTenths = DEFAULT_SHOT_PITCH;

    public CompoundTag writeToNBT(CompoundTag nbttagcompound) {
        nbttagcompound.putString("GeckoProjectileEntity", projectileEntity);
        nbttagcompound.putString(FALLBACK_KEY, fallbackProjectile);
        nbttagcompound.putInt("GeckoKeepDistance", keepDistance);
        nbttagcompound.putInt("GeckoNpcRangedMuzzle", muzzleHeightTenths);
        nbttagcompound.putInt("GeckoNpcRangedShotVolume", shotSoundVolumeTenths);
        nbttagcompound.putInt("GeckoNpcRangedShotPitch", shotSoundPitchTenths);
        return nbttagcompound;
    }

    public void readFromNBT(CompoundTag nbttagcompound) {
        setProjectileEntity(nbttagcompound.getString("GeckoProjectileEntity"));
        // Asked for as a string and nothing else: a save that never wrote the key and one
        // that holds something other than text under it both read the default, while an
        // empty string is a setting in its own right and stays empty.
        setFallbackProjectile(nbttagcompound.contains(FALLBACK_KEY, Tag.TAG_STRING)
                ? nbttagcompound.getString(FALLBACK_KEY) : DEFAULT_FALLBACK_PROJECTILE);
        setKeepDistance(nbttagcompound.getInt("GeckoKeepDistance"));
        muzzleHeightTenths = readInt(nbttagcompound, "GeckoNpcRangedMuzzle", DEFAULT_MUZZLE_HEIGHT,
                MIN_MUZZLE_HEIGHT, MAX_MUZZLE_HEIGHT);
        shotSoundVolumeTenths = readInt(nbttagcompound, "GeckoNpcRangedShotVolume",
                DEFAULT_SHOT_VOLUME, 0, MAX_SHOT_VOLUME);
        shotSoundPitchTenths = readInt(nbttagcompound, "GeckoNpcRangedShotPitch",
                DEFAULT_SHOT_PITCH, MIN_SHOT_PITCH, MAX_SHOT_PITCH);
    }

    /** The default for a key an older world never wrote, and the clamp for one it did. */
    private static int readInt(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    public String getProjectileEntity() {
        return projectileEntity;
    }

    public void setProjectileEntity(String projectileEntity) {
        this.projectileEntity = projectileEntity == null ? "" : projectileEntity.trim();
    }

    /** The entity fired when there is nothing else to fire; empty means the npc holds its fire. */
    public String getFallbackProjectile() {
        return fallbackProjectile;
    }

    public void setFallbackProjectile(String fallbackProjectile) {
        this.fallbackProjectile = fallbackProjectile == null ? "" : fallbackProjectile.trim();
    }

    public int getKeepDistance() {
        return keepDistance;
    }

    public void setKeepDistance(int keepDistance) {
        this.keepDistance = Mth.clamp(keepDistance, 0, MAX_KEEP_DISTANCE);
    }

    /** Where the projectile leaves from, in tenths of a block above or below the npc's eyes. */
    public int getMuzzleHeightTenths() {
        return muzzleHeightTenths;
    }

    public void setMuzzleHeightTenths(int muzzleHeightTenths) {
        this.muzzleHeightTenths = Mth.clamp(muzzleHeightTenths, MIN_MUZZLE_HEIGHT, MAX_MUZZLE_HEIGHT);
    }

    /** How loud the shot is, in tenths: 20 is the 2.0F the call used to hold. */
    public int getShotSoundVolumeTenths() {
        return shotSoundVolumeTenths;
    }

    public void setShotSoundVolumeTenths(int shotSoundVolumeTenths) {
        this.shotSoundVolumeTenths = Mth.clamp(shotSoundVolumeTenths, 0, MAX_SHOT_VOLUME);
    }

    /** And how high, in tenths. The sound itself is the one CustomNPCs' own editor names. */
    public int getShotSoundPitchTenths() {
        return shotSoundPitchTenths;
    }

    public void setShotSoundPitchTenths(int shotSoundPitchTenths) {
        this.shotSoundPitchTenths = Mth.clamp(shotSoundPitchTenths, MIN_SHOT_PITCH, MAX_SHOT_PITCH);
    }
}
