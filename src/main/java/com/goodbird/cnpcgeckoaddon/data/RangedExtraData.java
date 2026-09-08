package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
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

    private String projectileEntity = "";
    private int keepDistance = 0;

    public CompoundTag writeToNBT(CompoundTag nbttagcompound) {
        nbttagcompound.putString("GeckoProjectileEntity", projectileEntity);
        nbttagcompound.putInt("GeckoKeepDistance", keepDistance);
        return nbttagcompound;
    }

    public void readFromNBT(CompoundTag nbttagcompound) {
        setProjectileEntity(nbttagcompound.getString("GeckoProjectileEntity"));
        setKeepDistance(nbttagcompound.getInt("GeckoKeepDistance"));
    }

    public String getProjectileEntity() {
        return projectileEntity;
    }

    public void setProjectileEntity(String projectileEntity) {
        this.projectileEntity = projectileEntity == null ? "" : projectileEntity.trim();
    }

    public int getKeepDistance() {
        return keepDistance;
    }

    public void setKeepDistance(int keepDistance) {
        this.keepDistance = Mth.clamp(keepDistance, 0, MAX_KEEP_DISTANCE);
    }
}
