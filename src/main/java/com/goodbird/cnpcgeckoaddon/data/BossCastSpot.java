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
    /** Tenths of a block: how close the walk has to get to count as standing on the spot. */
    public static final int MIN_ARRIVAL_DISTANCE = 2;
    public static final int MAX_ARRIVAL_DISTANCE = 50;
    public static final int MAX_GROUND_SEARCH = 16;
    public static final int MIN_REPATH_INTERVAL = 1;
    public static final int MAX_REPATH_INTERVAL = 40;
    public static final int MAX_RETRY_TICKS = 1200;
    /** A percentage on top of the walking speed the npc's own ai settings give. */
    public static final int MIN_WALK_SPEED_PERCENT = 10;
    public static final int MAX_WALK_SPEED_PERCENT = 400;

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
    /** Tenths of a block: how close the walk has to get before the boss counts as arrived. */
    private int arrivalDistance = 10;
    /** How far below the spot the floor may be before it counts as a spot over nothing. */
    private int groundSearch = 3;
    /** How often the walk re-asks for its path; the path is cached for the same target between. */
    private int repathInterval = 4;
    /** How long an ability that refused to start on its spot waits before it is taken there again. */
    private int retryTicks = 100;
    /** What the walk's own speed is multiplied by, as a percentage. */
    private int walkSpeedPercent = 100;

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

    public int getArrivalDistanceTenths() { return arrivalDistance; }

    public void setArrivalDistanceTenths(int value) {
        arrivalDistance = Mth.clamp(value, MIN_ARRIVAL_DISTANCE, MAX_ARRIVAL_DISTANCE);
    }

    public double getArrivalDistance() { return arrivalDistance / 10.0D; }

    public int getGroundSearch() { return groundSearch; }

    public void setGroundSearch(int value) { groundSearch = Mth.clamp(value, 0, MAX_GROUND_SEARCH); }

    public int getRepathInterval() { return repathInterval; }

    public void setRepathInterval(int value) {
        repathInterval = Mth.clamp(value, MIN_REPATH_INTERVAL, MAX_REPATH_INTERVAL);
    }

    public int getRetryTicks() { return retryTicks; }

    public void setRetryTicks(int value) { retryTicks = Mth.clamp(value, 0, MAX_RETRY_TICKS); }

    public int getWalkSpeedPercent() { return walkSpeedPercent; }

    public void setWalkSpeedPercent(int value) {
        walkSpeedPercent = Mth.clamp(value, MIN_WALK_SPEED_PERCENT, MAX_WALK_SPEED_PERCENT);
    }

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
        tag.putInt(prefix + "SpotArrival", arrivalDistance);
        tag.putInt(prefix + "SpotGroundSearch", groundSearch);
        tag.putInt(prefix + "SpotRepath", repathInterval);
        tag.putInt(prefix + "SpotRetry", retryTicks);
        tag.putInt(prefix + "SpotWalkSpeed", walkSpeedPercent);
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
        // A boss saved before these were settings walks on the numbers that used to be
        // literals in the journey itself.
        arrivalDistance = value(tag, prefix + "SpotArrival", 10,
                MIN_ARRIVAL_DISTANCE, MAX_ARRIVAL_DISTANCE);
        groundSearch = value(tag, prefix + "SpotGroundSearch", 3, 0, MAX_GROUND_SEARCH);
        repathInterval = value(tag, prefix + "SpotRepath", 4, MIN_REPATH_INTERVAL, MAX_REPATH_INTERVAL);
        retryTicks = value(tag, prefix + "SpotRetry", 100, 0, MAX_RETRY_TICKS);
        walkSpeedPercent = value(tag, prefix + "SpotWalkSpeed", 100,
                MIN_WALK_SPEED_PERCENT, MAX_WALK_SPEED_PERCENT);
    }
}
