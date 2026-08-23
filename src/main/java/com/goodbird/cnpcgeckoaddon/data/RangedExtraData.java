package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;

public class RangedExtraData {
    private String projectileEntity = "";
    private int keepDistance = 0;

    public CompoundTag writeToNBT(CompoundTag nbttagcompound) {
        nbttagcompound.putString("GeckoProjectileEntity", projectileEntity);
        nbttagcompound.putInt("GeckoKeepDistance", keepDistance);
        return nbttagcompound;
    }

    public void readFromNBT(CompoundTag nbttagcompound) {
        projectileEntity = nbttagcompound.getString("GeckoProjectileEntity");
        keepDistance = nbttagcompound.getInt("GeckoKeepDistance");
    }

    public String getProjectileEntity() {
        return projectileEntity;
    }

    public void setProjectileEntity(String projectileEntity) {
        this.projectileEntity = projectileEntity;
    }

    public int getKeepDistance() {
        return keepDistance;
    }

    public void setKeepDistance(int keepDistance) {
        this.keepDistance = keepDistance;
    }
}
