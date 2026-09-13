package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * One platform a phase can set alight: a box between two corners, in either order and both
 * inclusive, the way the arena hazard's box and the aggro zone are measured.
 *
 * <p>Laid out the way a summon's point is - an offset from where the boss stood when it
 * activated, or fixed world blocks - and sharing its numbering, so the same two coordinate
 * modes and the same "use my position" mean the same thing on every screen.</p>
 */
public final class BossPlatformZone {
    public static final int COORDINATE_ARENA_OFFSET = BossMinionSpawnPoint.COORDINATE_ARENA_OFFSET;
    public static final int COORDINATE_FIXED = BossMinionSpawnPoint.COORDINATE_FIXED;
    public static final int MAX_COORDINATE = BossPhaseData.MAX_HAZARD_COORDINATE;
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 100;

    private static final String ENABLED_KEY = "Enabled";
    private static final String COORDINATE_MODE_KEY = "CoordinateMode";
    private static final String X1_KEY = "X1";
    private static final String Y1_KEY = "Y1";
    private static final String Z1_KEY = "Z1";
    private static final String X2_KEY = "X2";
    private static final String Y2_KEY = "Y2";
    private static final String Z2_KEY = "Z2";
    private static final String WEIGHT_KEY = "Weight";
    private static final String ZONE_ID_KEY = "ZoneId";

    private boolean enabled = true;
    private int coordinateMode = COORDINATE_ARENA_OFFSET;
    private int x1;
    private int y1;
    private int z1;
    private int x2;
    private int y2;
    private int z2;
    /** How likely a random pick is to land on this platform, against the others' weights. */
    private int weight = MIN_WEIGHT;
    private int zoneId;

    BossPlatformZone(int zoneId) {
        this.zoneId = Math.max(1, zoneId);
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putInt(COORDINATE_MODE_KEY, coordinateMode);
        tag.putInt(X1_KEY, x1);
        tag.putInt(Y1_KEY, y1);
        tag.putInt(Z1_KEY, z1);
        tag.putInt(X2_KEY, x2);
        tag.putInt(Y2_KEY, y2);
        tag.putInt(Z2_KEY, z2);
        tag.putInt(WEIGHT_KEY, weight);
        tag.putInt(ZONE_ID_KEY, zoneId);
        return tag;
    }

    static BossPlatformZone readFromNBT(CompoundTag tag, int fallbackZoneId) {
        int savedId = tag.contains(ZONE_ID_KEY) ? tag.getInt(ZONE_ID_KEY) : fallbackZoneId;
        BossPlatformZone zone = new BossPlatformZone(savedId > 0 ? savedId : fallbackZoneId);
        zone.enabled = !tag.contains(ENABLED_KEY) || tag.getBoolean(ENABLED_KEY);
        zone.setCoordinateMode(tag.getInt(COORDINATE_MODE_KEY));
        zone.setCorner1(tag.getInt(X1_KEY), tag.getInt(Y1_KEY), tag.getInt(Z1_KEY));
        zone.setCorner2(tag.getInt(X2_KEY), tag.getInt(Y2_KEY), tag.getInt(Z2_KEY));
        zone.setWeight(tag.contains(WEIGHT_KEY) ? tag.getInt(WEIGHT_KEY) : MIN_WEIGHT);
        return zone;
    }

    void assignZoneId(int value) { zoneId = Math.max(1, value); }

    /** A platform switched off keeps its place in the list and is never picked. */
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean value) { enabled = value; }

    public int getCoordinateMode() { return coordinateMode; }

    public void setCoordinateMode(int value) {
        coordinateMode = Mth.clamp(value, COORDINATE_ARENA_OFFSET, COORDINATE_FIXED);
    }

    public int getX1() { return x1; }

    public int getY1() { return y1; }

    public int getZ1() { return z1; }

    public int getX2() { return x2; }

    public int getY2() { return y2; }

    public int getZ2() { return z2; }

    public void setCorner1(int x, int y, int z) {
        x1 = coordinate(x);
        y1 = coordinate(y);
        z1 = coordinate(z);
    }

    public void setCorner2(int x, int y, int z) {
        x2 = coordinate(x);
        y2 = coordinate(y);
        z2 = coordinate(z);
    }

    public int getWeight() { return weight; }

    public void setWeight(int value) { weight = Mth.clamp(value, MIN_WEIGHT, MAX_WEIGHT); }

    public int getZoneId() { return zoneId; }

    private static int coordinate(int value) {
        return Mth.clamp(value, -MAX_COORDINATE, MAX_COORDINATE);
    }
}
