package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataDisplay.class)
public class MixinDataDisplay implements IDataDisplay {

    @Unique
    private static final ResourceLocation customNPC_Gecko_Addon$MODEL_ENTITY =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "custommodelentity");

    @Shadow(remap = false)
    EntityNPCInterface npc;
    @Unique
    private final CustomModelData customNPC_Gecko_Addon$customModelData = new CustomModelData();

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    public void writeToNBT(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        if(hasCustomModel())
            customNPC_Gecko_Addon$customModelData.writeToNBT(nbttagcompound);
    }

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    public void readFromNBT(CompoundTag nbttagcompound, CallbackInfo ci){
        customNPC_Gecko_Addon$customModelData.readFromNBT(nbttagcompound);
    }

    @Unique
    public CustomModelData getCustomModelData(){
        return customNPC_Gecko_Addon$customModelData;
    }

    @Unique
    public boolean hasCustomModel() {
        if (!(npc instanceof EntityCustomNpc) || ((EntityCustomNpc) npc).modelData == null) {
            return false;
        }
        // Compare the configured model entity id instead of asking for a live instance.
        // ModelData#getEntity can transiently fail (no level yet, a model entity from a
        // mod that is temporarily missing) and it latches that failure, so keying NBT
        // saving on it would silently wipe the whole gecko configuration of the NPC.
        ResourceLocation entityName = ((EntityCustomNpc) npc).modelData.getEntityName();
        if (customNPC_Gecko_Addon$MODEL_ENTITY.equals(entityName)) {
            return true;
        }
        return ((EntityCustomNpc) npc).modelData.getEntity(npc) instanceof EntityCustomModel;
    }
}
