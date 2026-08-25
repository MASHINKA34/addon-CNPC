package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** One stable configured spawn point in a phase's minion wave. */
public final class BossMinionSpawnPoint {
    public static final int COORDINATE_ARENA_OFFSET = 0;
    public static final int COORDINATE_FIXED = 1;
    public static final int MAX_COORDINATE = 30000000;

    private static final String ENABLED_KEY = "Enabled";
    private static final String COORDINATE_MODE_KEY = "CoordinateMode";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final String CLONE_NAME_KEY = "CloneNameOverride";
    private static final String CLONE_TAB_KEY = "CloneTabOverride";
    private static final String YAW_KEY = "Yaw";
    private static final String WEIGHT_KEY = "Weight";
    private static final String POINT_ID_KEY = "PointId";

    private boolean enabled = true;
    private int coordinateMode = COORDINATE_ARENA_OFFSET;
    private int x;
    private int y;
    private int z;
    private String cloneNameOverride = "";
    private int cloneTabOverride;
    private float yaw;
    private int weight = 1;
    private int pointId;

    BossMinionSpawnPoint(int pointId) {
        this.pointId = Math.max(1, pointId);
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putInt(COORDINATE_MODE_KEY, coordinateMode);
        tag.putInt(X_KEY, x);
        tag.putInt(Y_KEY, y);
        tag.putInt(Z_KEY, z);
        tag.putString(CLONE_NAME_KEY, cloneNameOverride);
        tag.putInt(CLONE_TAB_KEY, cloneTabOverride);
        tag.putFloat(YAW_KEY, yaw);
        tag.putInt(WEIGHT_KEY, weight);
        tag.putInt(POINT_ID_KEY, pointId);
        return tag;
    }

    static BossMinionSpawnPoint readFromNBT(CompoundTag tag, int fallbackPointId) {
        int savedId = tag.contains(POINT_ID_KEY) ? tag.getInt(POINT_ID_KEY) : fallbackPointId;
        BossMinionSpawnPoint point = new BossMinionSpawnPoint(savedId > 0 ? savedId : fallbackPointId);
        point.enabled = !tag.contains(ENABLED_KEY) || tag.getBoolean(ENABLED_KEY);
        point.setCoordinateMode(tag.getInt(COORDINATE_MODE_KEY));
        point.setPosition(tag.getInt(X_KEY), tag.getInt(Y_KEY), tag.getInt(Z_KEY));
        point.setCloneNameOverride(tag.getString(CLONE_NAME_KEY));
        point.setCloneTabOverride(tag.getInt(CLONE_TAB_KEY));
        point.setYaw(tag.getFloat(YAW_KEY));
        point.setWeight(tag.contains(WEIGHT_KEY) ? tag.getInt(WEIGHT_KEY) : 1);
        return point;
    }

    void assignPointId(int value) { pointId = Math.max(1, value); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getCoordinateMode() { return coordinateMode; }
    public void setCoordinateMode(int value) {
        coordinateMode = Mth.clamp(value, COORDINATE_ARENA_OFFSET, COORDINATE_FIXED);
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public void setPosition(int x, int y, int z) {
        this.x = Mth.clamp(x, -MAX_COORDINATE, MAX_COORDINATE);
        this.y = Mth.clamp(y, -MAX_COORDINATE, MAX_COORDINATE);
        this.z = Mth.clamp(z, -MAX_COORDINATE, MAX_COORDINATE);
    }
    public String getCloneNameOverride() { return cloneNameOverride; }
    public void setCloneNameOverride(String value) {
        cloneNameOverride = value == null ? "" : value.trim();
    }
    public int getCloneTabOverride() { return cloneTabOverride; }
    public void setCloneTabOverride(int value) { cloneTabOverride = Mth.clamp(value, 0, 9); }
    public float getYaw() { return yaw; }
    public void setYaw(float value) { yaw = Mth.clamp(value, -180.0F, 180.0F); }
    public int getWeight() { return weight; }
    public void setWeight(int value) { weight = Mth.clamp(value, 1, 100); }
    public int getPointId() { return pointId; }
}
