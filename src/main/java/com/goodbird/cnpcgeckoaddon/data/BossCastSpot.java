package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * Where a boss goes before it casts one ability, and how long it stays there afterwards.
 *
 * <p>Every ability on the rotation carries one of these, reached through its settings'
 * {@code castSpot()}, and most of them are never set: a spot in {@link #MODE_NONE} is the
 * boss casting from wherever it happens to stand, which is what every boss did before the
 * spot existed. The keys are written under the owning ability's own prefix -
 * {@code BeamSpotMode}, {@code GeyserSpotX} - so a phase tag stays one flat block and a boss
 * saved before the spot existed reads every one of them as unset.</p>
 */
public final class BossCastSpot {

    /** The boss casts from wherever it stands. */
    public static final int MODE_NONE = 0;
    /** The boss blinks onto the spot before the wind-up. */
    public static final int MODE_TELEPORT = 1;
    /** The boss walks to the spot, and blinks there if the walk takes too long. */
    public static final int MODE_WALK = 2;
    public static final String[] MODE_LABELS = {
            "cnpcgeckoaddon.boss.cast_spot_mode.none",
            "cnpcgeckoaddon.boss.cast_spot_mode.tp",
            "cnpcgeckoaddon.boss.cast_spot_mode.walk"
    };

    /** Coordinates measured from where the boss stood when it activated, like a spawn point's. */
    public static final int COORDINATE_HOME_OFFSET = 0;
    public static final int COORDINATE_ABSOLUTE = 1;
    public static final String[] COORDINATE_LABELS = {
            "cnpcgeckoaddon.boss.cast_spot_coords.home",
            "cnpcgeckoaddon.boss.cast_spot_coords.abs"
    };

    /** On the spot the boss keeps facing its target, the way it does everywhere else. */
    public static final int YAW_TARGET = 0;
    /** On the spot the boss is turned to {@link #getYaw()} and held there. */
    public static final int YAW_FIXED = 1;
    public static final String[] YAW_LABELS = {
            "cnpcgeckoaddon.boss.cast_spot_yaw.target",
            "cnpcgeckoaddon.boss.cast_spot_yaw.fixed"
    };

    /** The spot is held through the wind-up only; the boss is free again once the cast lands. */
    public static final int STAY_WINDUP = 0;
    /** The spot is held for as long as the ability's own effect keeps running. */
    public static final int STAY_ACTIVE = 1;
    /** The spot is held for {@link #getStayTicks()} after the cast lands. */
    public static final int STAY_TICKS = 2;
    public static final String[] STAY_LABELS = {
            "cnpcgeckoaddon.boss.cast_spot_stay.windup",
            "cnpcgeckoaddon.boss.cast_spot_stay.active",
            "cnpcgeckoaddon.boss.cast_spot_stay.ticks"
    };

    public static final int MAX_COORDINATE = 30000000;
    public static final int MIN_TRAVEL_TIMEOUT_TICKS = 10;
    public static final int MAX_TRAVEL_TIMEOUT_TICKS = 1200;
    public static final int MAX_STAY_TICKS = 12000;

    private static final int DEFAULT_TRAVEL_TIMEOUT_TICKS = 100;
    private static final int DEFAULT_STAY_TICKS = 100;

    private int mode = MODE_NONE;
    private int coordinateMode = COORDINATE_HOME_OFFSET;
    private int x;
    private int y;
    private int z;
    /** How long a walk may take before the boss gives up on it and blinks the rest of the way. */
    private int travelTimeoutTicks = DEFAULT_TRAVEL_TIMEOUT_TICKS;
    private int yawMode = YAW_TARGET;
    /** Minecraft yaw in degrees, for {@link #YAW_FIXED}. */
    private float yaw;
    private int stayMode = STAY_ACTIVE;
    /** How long the spot is held after the cast, for {@link #STAY_TICKS}. */
    private int stayTicks = DEFAULT_STAY_TICKS;

    /** Whether this ability sends the boss anywhere at all before it casts. */
    public boolean isSet() { return mode != MODE_NONE; }

    public int getMode() { return mode; }

    public void setMode(int value) { mode = Mth.clamp(value, MODE_NONE, MODE_WALK); }

    public int getCoordinateMode() { return coordinateMode; }

    public void setCoordinateMode(int value) {
        coordinateMode = Mth.clamp(value, COORDINATE_HOME_OFFSET, COORDINATE_ABSOLUTE);
    }

    public int getX() { return x; }

    public int getY() { return y; }

    public int getZ() { return z; }

    public void setPosition(int x, int y, int z) {
        this.x = Mth.clamp(x, -MAX_COORDINATE, MAX_COORDINATE);
        this.y = Mth.clamp(y, -MAX_COORDINATE, MAX_COORDINATE);
        this.z = Mth.clamp(z, -MAX_COORDINATE, MAX_COORDINATE);
    }

    public int getTravelTimeoutTicks() { return travelTimeoutTicks; }

    public void setTravelTimeoutTicks(int value) {
        travelTimeoutTicks = Mth.clamp(value, MIN_TRAVEL_TIMEOUT_TICKS, MAX_TRAVEL_TIMEOUT_TICKS);
    }

    public int getYawMode() { return yawMode; }

    public void setYawMode(int value) { yawMode = Mth.clamp(value, YAW_TARGET, YAW_FIXED); }

    public float getYaw() { return yaw; }

    /**
     * A clamp alone lets a NaN through - it compares false against both ends - and a boss
     * turned to a yaw of NaN is one nobody can see straight, the way a spawn point's would be.
     */
    public void setYaw(float value) {
        yaw = Float.isFinite(value) ? Mth.clamp(value, -180.0F, 180.0F) : 0.0F;
    }

    public int getStayMode() { return stayMode; }

    public void setStayMode(int value) { stayMode = Mth.clamp(value, STAY_WINDUP, STAY_TICKS); }

    public int getStayTicks() { return stayTicks; }

    public void setStayTicks(int value) { stayTicks = Mth.clamp(value, 0, MAX_STAY_TICKS); }

    /** Writes the spot under {@code prefix}, which is the owning ability's key prefix. */
    void writeToNBT(CompoundTag tag, String prefix) {
        tag.putInt(prefix + "SpotMode", mode);
        tag.putInt(prefix + "SpotCoordinateMode", coordinateMode);
        tag.putInt(prefix + "SpotX", x);
        tag.putInt(prefix + "SpotY", y);
        tag.putInt(prefix + "SpotZ", z);
        tag.putInt(prefix + "SpotTravelTimeoutTicks", travelTimeoutTicks);
        tag.putInt(prefix + "SpotYawMode", yawMode);
        tag.putFloat(prefix + "SpotYaw", yaw);
        tag.putInt(prefix + "SpotStayMode", stayMode);
        tag.putInt(prefix + "SpotStayTicks", stayTicks);
    }

    void readFromNBT(CompoundTag tag, String prefix) {
        // A boss saved before the spot existed carries none of these and casts where it stands.
        mode = value(tag, prefix + "SpotMode", MODE_NONE, MODE_NONE, MODE_WALK);
        coordinateMode = value(tag, prefix + "SpotCoordinateMode", COORDINATE_HOME_OFFSET,
                COORDINATE_HOME_OFFSET, COORDINATE_ABSOLUTE);
        setPosition(tag.getInt(prefix + "SpotX"), tag.getInt(prefix + "SpotY"), tag.getInt(prefix + "SpotZ"));
        travelTimeoutTicks = value(tag, prefix + "SpotTravelTimeoutTicks", DEFAULT_TRAVEL_TIMEOUT_TICKS,
                MIN_TRAVEL_TIMEOUT_TICKS, MAX_TRAVEL_TIMEOUT_TICKS);
        yawMode = value(tag, prefix + "SpotYawMode", YAW_TARGET, YAW_TARGET, YAW_FIXED);
        // An absent key reads as 0.0, which is the default; the setter drops a NaN.
        setYaw(tag.getFloat(prefix + "SpotYaw"));
        stayMode = value(tag, prefix + "SpotStayMode", STAY_ACTIVE, STAY_WINDUP, STAY_TICKS);
        stayTicks = value(tag, prefix + "SpotStayTicks", DEFAULT_STAY_TICKS, 0, MAX_STAY_TICKS);
    }
}
