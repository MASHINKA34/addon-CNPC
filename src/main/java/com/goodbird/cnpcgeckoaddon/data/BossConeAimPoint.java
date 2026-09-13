package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * One point of the arena a cone strike is aimed at: the block lying on the middle of the cone,
 * wherever the boss swings it from.
 *
 * <p>Laid out the way a summon's point is - an offset from the arena or a fixed block - and
 * sharing its numbering, so the same "use my position" and the same two coordinate modes mean
 * the same thing on both screens.</p>
 */
public final class BossConeAimPoint {
    public static final int COORDINATE_ARENA_OFFSET = BossMinionSpawnPoint.COORDINATE_ARENA_OFFSET;
    public static final int COORDINATE_FIXED = BossMinionSpawnPoint.COORDINATE_FIXED;
    public static final int MAX_COORDINATE = BossMinionSpawnPoint.MAX_COORDINATE;

    private static final String ENABLED_KEY = "Enabled";
    private static final String COORDINATE_MODE_KEY = "CoordinateMode";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final String POINT_ID_KEY = "PointId";

    private boolean enabled = true;
    private int coordinateMode = COORDINATE_ARENA_OFFSET;
    private int x;
    private int y;
    private int z;
    private int pointId;

    BossConeAimPoint(int pointId) {
        this.pointId = Math.max(1, pointId);
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putInt(COORDINATE_MODE_KEY, coordinateMode);
        tag.putInt(X_KEY, x);
        tag.putInt(Y_KEY, y);
        tag.putInt(Z_KEY, z);
        tag.putInt(POINT_ID_KEY, pointId);
        return tag;
    }

    static BossConeAimPoint readFromNBT(CompoundTag tag, int fallbackPointId) {
        int savedId = tag.contains(POINT_ID_KEY) ? tag.getInt(POINT_ID_KEY) : fallbackPointId;
        BossConeAimPoint point = new BossConeAimPoint(savedId > 0 ? savedId : fallbackPointId);
        point.enabled = !tag.contains(ENABLED_KEY) || tag.getBoolean(ENABLED_KEY);
        point.setCoordinateMode(tag.getInt(COORDINATE_MODE_KEY));
        point.setPosition(tag.getInt(X_KEY), tag.getInt(Y_KEY), tag.getInt(Z_KEY));
        return point;
    }

    void assignPointId(int value) { pointId = Math.max(1, value); }

    /** A point switched off keeps its place in the list and is skipped by every cast. */
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

    public int getPointId() { return pointId; }
}
