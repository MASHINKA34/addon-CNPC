package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** One stable spawn slot in a boss' protection-totem formation. */
public final class BossTotemEntry {
    public static final int COORDINATE_ARENA_OFFSET = 0;
    public static final int COORDINATE_FIXED = 1;
    public static final int MAX_COORDINATE = 30000000;

    /** Anything that hurts breaks this totem, the way every totem behaved before the setting. */
    public static final int VULNERABILITY_ANY = 0;
    /** Only the abilities ticked in the mask below get through; everything else bounces. */
    public static final int VULNERABILITY_LISTED_ABILITIES = 1;

    private static final int ALL_ABILITIES = (1 << BossAbilityKind.COUNT) - 1;

    private static final String ENABLED_KEY = "Enabled";
    private static final String CLONE_TAB_KEY = "CloneTab";
    private static final String CLONE_NAME_KEY = "CloneName";
    private static final String COORDINATE_MODE_KEY = "CoordinateMode";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String Z_KEY = "Z";
    private static final String YAW_KEY = "Yaw";
    private static final String BEAM_STYLE_KEY = "BeamStyle";
    private static final String BEAM_WIDTH_KEY = "BeamWidth";
    private static final String SLOT_KEY = "Slot";
    private static final String VULNERABILITY_MODE_KEY = "VulnerabilityMode";
    private static final String VULNERABILITY_MASK_KEY = "VulnerabilityMask";

    private boolean enabled = true;
    private int cloneTab = 1;
    private String cloneName = "";
    private int coordinateMode = COORDINATE_ARENA_OFFSET;
    private int x;
    private int y;
    private int z;
    private float yaw;
    private String beamStyleOverride = "";
    private int beamWidthPercentOverride;
    private int slotId;
    private int vulnerabilityMode = VULNERABILITY_ANY;
    private int vulnerabilityMask;

    BossTotemEntry(int slotId) {
        this.slotId = Math.max(1, slotId);
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putInt(CLONE_TAB_KEY, cloneTab);
        tag.putString(CLONE_NAME_KEY, cloneName);
        tag.putInt(COORDINATE_MODE_KEY, coordinateMode);
        tag.putInt(X_KEY, x);
        tag.putInt(Y_KEY, y);
        tag.putInt(Z_KEY, z);
        tag.putFloat(YAW_KEY, yaw);
        tag.putString(BEAM_STYLE_KEY, beamStyleOverride);
        tag.putInt(BEAM_WIDTH_KEY, beamWidthPercentOverride);
        tag.putInt(SLOT_KEY, slotId);
        tag.putInt(VULNERABILITY_MODE_KEY, vulnerabilityMode);
        tag.putInt(VULNERABILITY_MASK_KEY, vulnerabilityMask);
        return tag;
    }

    static BossTotemEntry readFromNBT(CompoundTag tag, int fallbackSlotId) {
        int savedSlot = tag.contains(SLOT_KEY) ? tag.getInt(SLOT_KEY) : fallbackSlotId;
        BossTotemEntry entry = new BossTotemEntry(savedSlot > 0 ? savedSlot : fallbackSlotId);
        entry.enabled = !tag.contains(ENABLED_KEY) || tag.getBoolean(ENABLED_KEY);
        entry.setCloneTab(tag.contains(CLONE_TAB_KEY) ? tag.getInt(CLONE_TAB_KEY) : 1);
        entry.setCloneName(tag.getString(CLONE_NAME_KEY));
        entry.setCoordinateMode(tag.getInt(COORDINATE_MODE_KEY));
        entry.setPosition(tag.getInt(X_KEY), tag.getInt(Y_KEY), tag.getInt(Z_KEY));
        entry.setYaw(tag.getFloat(YAW_KEY));
        entry.setBeamStyleOverride(tag.getString(BEAM_STYLE_KEY));
        entry.setBeamWidthPercentOverride(tag.getInt(BEAM_WIDTH_KEY));
        // A missing pair reads back as mode 0 with an empty mask, which is the old behaviour:
        // the mask is only ever asked for once the mode says to.
        entry.setVulnerabilityMode(tag.getInt(VULNERABILITY_MODE_KEY));
        entry.setVulnerabilityMask(tag.getInt(VULNERABILITY_MASK_KEY));
        return entry;
    }

    void assignSlotId(int value) {
        slotId = Math.max(1, value);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getCloneTab() { return cloneTab; }
    public void setCloneTab(int value) { cloneTab = Mth.clamp(value, 1, 9); }
    public String getCloneName() { return cloneName; }
    public void setCloneName(String value) { cloneName = value == null ? "" : value.trim(); }
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
    public float getYaw() { return yaw; }
    public void setYaw(float value) { yaw = Mth.clamp(value, -180.0F, 180.0F); }
    public String getBeamStyleOverride() { return beamStyleOverride; }
    public void setBeamStyleOverride(String value) {
        String trimmed = value == null ? "" : value.trim();
        beamStyleOverride = trimmed.isEmpty() ? "" : HookCordStyles.normalize(trimmed);
    }
    public int getBeamWidthPercentOverride() { return beamWidthPercentOverride; }
    public void setBeamWidthPercentOverride(int value) {
        beamWidthPercentOverride = value == 0 ? 0 : Mth.clamp(value, 25, 400);
    }
    public int getSlotId() { return slotId; }

    public int getVulnerabilityMode() { return vulnerabilityMode; }
    public void setVulnerabilityMode(int value) {
        vulnerabilityMode = Mth.clamp(value, VULNERABILITY_ANY, VULNERABILITY_LISTED_ABILITIES);
    }

    /** The whole list, one bit per {@link BossAbilityKind}, read only in the listed mode. */
    public int getVulnerabilityMask() { return vulnerabilityMask; }
    public void setVulnerabilityMask(int value) { vulnerabilityMask = value & ALL_ABILITIES; }

    public boolean isVulnerableTo(int ability) {
        return ability >= 0 && ability < BossAbilityKind.COUNT
                && (vulnerabilityMask & 1 << ability) != 0;
    }

    public void setVulnerableTo(int ability, boolean value) {
        if (ability < 0 || ability >= BossAbilityKind.COUNT) {
            return;
        }
        if (value) {
            vulnerabilityMask |= 1 << ability;
        } else {
            vulnerabilityMask &= ~(1 << ability);
        }
    }
}
