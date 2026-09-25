package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * One vent a phase can fire: a box in the arena's floor, ceiling or a wall, the face of it the
 * blast, the flame or the wall comes out of, and how far out from that face it reaches.
 *
 * <p>The box is measured the way a platform's is - two corners, either order, both inclusive,
 * as an offset from where the boss stood when it activated or as fixed blocks - and shares the
 * platform's save keys for the parts they have in common, so the same "use my position" and
 * the same two coordinate modes mean the same thing on every screen.</p>
 *
 * <p>A wall's face is named after the wall the vent sits in, not the way it fires: a vent in
 * the north wall fires south, into the arena.</p>
 */
public final class BossVentZone {
    public static final int COORDINATE_ARENA_OFFSET = BossMinionSpawnPoint.COORDINATE_ARENA_OFFSET;
    public static final int COORDINATE_FIXED = BossMinionSpawnPoint.COORDINATE_FIXED;
    public static final int MAX_COORDINATE = BossPhaseData.MAX_HAZARD_COORDINATE;

    /** In the floor, firing up. */
    public static final int FACE_FLOOR = 0;
    /** In the ceiling, firing down. */
    public static final int FACE_CEILING = 1;
    /** In the north wall, firing south. */
    public static final int FACE_NORTH = 2;
    /** In the south wall, firing north. */
    public static final int FACE_SOUTH = 3;
    /** In the west wall, firing east. */
    public static final int FACE_WEST = 4;
    /** In the east wall, firing west. */
    public static final int FACE_EAST = 5;

    public static final String[] FACE_LABELS = {
            "cnpcgeckoaddon.boss.vent_zone_face.floor",
            "cnpcgeckoaddon.boss.vent_zone_face.ceiling",
            "cnpcgeckoaddon.boss.vent_zone_face.north",
            "cnpcgeckoaddon.boss.vent_zone_face.south",
            "cnpcgeckoaddon.boss.vent_zone_face.west",
            "cnpcgeckoaddon.boss.vent_zone_face.east"
    };

    /** The vent does what the phase's default mode says. */
    public static final int MODE_PHASE = -1;

    public static final String[] MODE_LABELS = {
            "cnpcgeckoaddon.boss.vent_zone_mode.phase",
            "cnpcgeckoaddon.boss.vent_zone_mode.burst",
            "cnpcgeckoaddon.boss.vent_zone_mode.flame",
            "cnpcgeckoaddon.boss.vent_zone_mode.wall"
    };

    public static final int MIN_REACH = 1;
    public static final int MAX_REACH = 32;
    public static final int DEFAULT_REACH = 4;
    public static final int MAX_DELAY_TICKS = 1200;
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
    private static final String FACE_KEY = "Face";
    private static final String REACH_KEY = "Reach";
    private static final String MODE_OVERRIDE_KEY = "ModeOverride";
    private static final String DELAY_KEY = "DelayTicks";
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
    private int face = FACE_FLOOR;
    /** Blocks out from the face the vent still reaches. */
    private int reach = DEFAULT_REACH;
    /** What this vent does, or {@link #MODE_PHASE} for whatever the phase's default is. */
    private int modeOverride = MODE_PHASE;
    /** How long after its volley this vent goes, so a volley can ripple along a wall. */
    private int delayTicks;
    /** How likely a random volley is to pick this vent, against the others' weights. */
    private int weight = MIN_WEIGHT;
    private int zoneId;

    BossVentZone(int zoneId) {
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
        tag.putInt(FACE_KEY, face);
        tag.putInt(REACH_KEY, reach);
        tag.putInt(MODE_OVERRIDE_KEY, modeOverride);
        tag.putInt(DELAY_KEY, delayTicks);
        tag.putInt(WEIGHT_KEY, weight);
        tag.putInt(ZONE_ID_KEY, zoneId);
        return tag;
    }

    static BossVentZone readFromNBT(CompoundTag tag, int fallbackZoneId) {
        int savedId = tag.contains(ZONE_ID_KEY) ? tag.getInt(ZONE_ID_KEY) : fallbackZoneId;
        BossVentZone zone = new BossVentZone(savedId > 0 ? savedId : fallbackZoneId);
        zone.enabled = !tag.contains(ENABLED_KEY) || tag.getBoolean(ENABLED_KEY);
        zone.setCoordinateMode(tag.getInt(COORDINATE_MODE_KEY));
        zone.setCorner1(tag.getInt(X1_KEY), tag.getInt(Y1_KEY), tag.getInt(Z1_KEY));
        zone.setCorner2(tag.getInt(X2_KEY), tag.getInt(Y2_KEY), tag.getInt(Z2_KEY));
        zone.setFace(tag.getInt(FACE_KEY));
        zone.setReach(tag.contains(REACH_KEY) ? tag.getInt(REACH_KEY) : DEFAULT_REACH);
        zone.setModeOverride(tag.contains(MODE_OVERRIDE_KEY) ? tag.getInt(MODE_OVERRIDE_KEY) : MODE_PHASE);
        zone.setDelayTicks(tag.getInt(DELAY_KEY));
        zone.setWeight(tag.contains(WEIGHT_KEY) ? tag.getInt(WEIGHT_KEY) : MIN_WEIGHT);
        return zone;
    }

    void assignZoneId(int value) { zoneId = Math.max(1, value); }

    /** A vent switched off keeps its place in the list and never fires. */
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

    /** The face the vent fires out of, one of the {@code FACE_*} numbers. */
    public int getFace() { return face; }

    public void setFace(int value) { face = Mth.clamp(value, FACE_FLOOR, FACE_EAST); }

    public int getReach() { return reach; }

    public void setReach(int value) { reach = Mth.clamp(value, MIN_REACH, MAX_REACH); }

    /** This vent's own mode, or {@link #MODE_PHASE} when it takes the phase's. */
    public int getModeOverride() { return modeOverride; }

    public void setModeOverride(int value) {
        modeOverride = Mth.clamp(value, MODE_PHASE, BossVentSettings.MODE_WALL);
    }

    /** What this vent does in a phase whose default mode is {@code phaseMode}. */
    public int modeIn(int phaseMode) {
        return modeOverride == MODE_PHASE ? phaseMode : modeOverride;
    }

    public int getDelayTicks() { return delayTicks; }

    public void setDelayTicks(int value) { delayTicks = Mth.clamp(value, 0, MAX_DELAY_TICKS); }

    public int getWeight() { return weight; }

    public void setWeight(int value) { weight = Mth.clamp(value, MIN_WEIGHT, MAX_WEIGHT); }

    public int getZoneId() { return zoneId; }

    private static int coordinate(int value) {
        return Mth.clamp(value, -MAX_COORDINATE, MAX_COORDINATE);
    }
}
