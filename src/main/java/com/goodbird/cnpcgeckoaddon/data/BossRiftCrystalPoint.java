package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;

/**
 * One zone of a reality rift's crystals: where it is, how far into it counts, and what the
 * crystal hovering over it is made of when it is not made of what the rift says.
 *
 * <p>Laid out the way a summon's point and a platform's zone are - an offset from the middle of
 * the rift's platform, or a fixed spot in the rift dimension - and sharing their numbering, so
 * the same two coordinate modes mean the same thing on every screen. The radius, the block and
 * the colour are each "the rift's own" by default, so a builder who only wants three crystals
 * somewhere else types three sets of coordinates and nothing more.</p>
 */
public final class BossRiftCrystalPoint {
    public static final int COORDINATE_ARENA_OFFSET = BossMinionSpawnPoint.COORDINATE_ARENA_OFFSET;
    public static final int COORDINATE_FIXED = BossMinionSpawnPoint.COORDINATE_FIXED;
    public static final int MAX_COORDINATE = BossMinionSpawnPoint.MAX_COORDINATE;
    /** Nought is the rift's own collecting radius; anything else is this zone's alone. */
    public static final int MAX_RADIUS_TENTHS = BossRiftSettings.MAX_CRYSTAL_RADIUS_TENTHS;
    /** Below nought is the rift's own tint; a colour of its own is six hex digits like any other. */
    public static final int NO_COLOR = -1;

    private static final String ENABLED_KEY = "Enabled";
    private static final String COORDINATE_MODE_KEY = "CoordinateMode";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final String RADIUS_KEY = "RadiusTenths";
    private static final String BLOCK_KEY = "BlockOverride";
    private static final String COLOR_KEY = "ColorOverride";
    private static final String POINT_ID_KEY = "PointId";

    private boolean enabled = true;
    private int coordinateMode = COORDINATE_ARENA_OFFSET;
    private int x;
    private int y;
    private int z;
    /** Nought leaves the zone as wide as the rift's own collecting radius. */
    private int radiusTenths;
    /** Empty leaves the crystal made of the rift's own block. */
    private String blockOverride = "";
    /** {@link #NO_COLOR} leaves the crystal painted in the rift's own tint. */
    private int colorOverride = NO_COLOR;
    private int pointId;

    BossRiftCrystalPoint(int pointId) {
        this.pointId = Math.max(1, pointId);
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putInt(COORDINATE_MODE_KEY, coordinateMode);
        tag.putInt(X_KEY, x);
        tag.putInt(Y_KEY, y);
        tag.putInt(Z_KEY, z);
        tag.putInt(RADIUS_KEY, radiusTenths);
        tag.putString(BLOCK_KEY, blockOverride);
        tag.putInt(COLOR_KEY, colorOverride);
        tag.putInt(POINT_ID_KEY, pointId);
        return tag;
    }

    static BossRiftCrystalPoint readFromNBT(CompoundTag tag, int fallbackPointId) {
        int savedId = tag.contains(POINT_ID_KEY) ? tag.getInt(POINT_ID_KEY) : fallbackPointId;
        BossRiftCrystalPoint point = new BossRiftCrystalPoint(savedId > 0 ? savedId : fallbackPointId);
        point.enabled = !tag.contains(ENABLED_KEY) || tag.getBoolean(ENABLED_KEY);
        point.setCoordinateMode(tag.getInt(COORDINATE_MODE_KEY));
        point.setPosition(tag.getInt(X_KEY), tag.getInt(Y_KEY), tag.getInt(Z_KEY));
        point.setRadiusTenths(tag.getInt(RADIUS_KEY));
        point.setBlockOverride(tag.getString(BLOCK_KEY));
        point.setColorOverride(tag.contains(COLOR_KEY) ? tag.getInt(COLOR_KEY) : NO_COLOR);
        return point;
    }

    void assignPointId(int value) { pointId = Math.max(1, value); }

    /** A zone switched off keeps its place in the list and never gets a crystal. */
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
        this.x = coordinate(x);
        this.y = coordinate(y);
        this.z = coordinate(z);
    }

    public int getRadiusTenths() { return radiusTenths; }

    public void setRadiusTenths(int value) { radiusTenths = Mth.clamp(value, 0, MAX_RADIUS_TENTHS); }

    public String getBlockOverride() { return blockOverride; }

    public void setBlockOverride(String value) { blockOverride = clean(value); }

    public int getColorOverride() { return colorOverride; }

    /** Anything below nought is "no colour of my own"; a colour is kept inside the six digits. */
    public void setColorOverride(int value) {
        colorOverride = value < 0 ? NO_COLOR : Math.min(value, BossRiftSettings.MAX_COLOR);
    }

    public int getPointId() { return pointId; }

    private static int coordinate(int value) {
        return Mth.clamp(value, -MAX_COORDINATE, MAX_COORDINATE);
    }
}
