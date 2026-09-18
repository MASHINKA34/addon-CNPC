package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataRanged.class)
public class MixinDataRanged implements IRangedData {

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Unique
    private final RangedExtraData cnpcgeckoaddon$rangedExtraData = new RangedExtraData();

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$saveRangedExtra(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        cnpcgeckoaddon$rangedExtraData.writeToNBT(nbttagcompound);
    }

    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$loadRangedExtra(CompoundTag nbttagcompound, CallbackInfo ci) {
        cnpcgeckoaddon$rangedExtraData.readFromNBT(nbttagcompound);
        // Both ways a projectile id reaches the server end here - the world being read and the
        // editor's save - so this is where it is found out whether the entity is a projectile
        // at all. Does nothing on a client, where no entity may be created to look at.
        ProjectileEntityUtil.validate(npc, cnpcgeckoaddon$rangedExtraData);
    }

    @Unique
    public RangedExtraData getRangedExtraData() {
        return cnpcgeckoaddon$rangedExtraData;
    }
}
