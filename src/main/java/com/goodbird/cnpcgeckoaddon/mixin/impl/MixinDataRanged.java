package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataRanged.class)
public class MixinDataRanged implements IRangedData {

    @Unique
    private final RangedExtraData cnpcgeckoaddon$rangedExtraData = new RangedExtraData();

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    public void writeToNBT(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        cnpcgeckoaddon$rangedExtraData.writeToNBT(nbttagcompound);
    }

    @Inject(method = "load", at = @At("HEAD"), remap = false)
    public void readFromNBT(CompoundTag nbttagcompound, CallbackInfo ci) {
        cnpcgeckoaddon$rangedExtraData.readFromNBT(nbttagcompound);
    }

    @Unique
    public RangedExtraData getRangedExtraData() {
        return cnpcgeckoaddon$rangedExtraData;
    }
}
