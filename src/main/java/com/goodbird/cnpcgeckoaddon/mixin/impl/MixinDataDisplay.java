package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
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
    private static final ResourceLocation cnpcgeckoaddon$MODEL_ENTITY =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "custommodelentity");

    @Shadow(remap = false)
    EntityNPCInterface npc;
    @Unique
    private final CustomModelData cnpcgeckoaddon$customModelData = new CustomModelData();

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$saveCustomModel(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        // The head of CustomNPCs' own save: what escapes here costs the npc its whole display.
        try {
            if (hasCustomModel()) {
                cnpcgeckoaddon$customModelData.writeToNBT(nbttagcompound);
            }
        } catch (Throwable error) {
            CrashGuard.caught("npc_data.save.custom_model", error);
        }
    }

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$loadCustomModel(CompoundTag nbttagcompound, CallbackInfo ci) {
        try {
            cnpcgeckoaddon$customModelData.readFromNBT(nbttagcompound);
        } catch (Throwable error) {
            CrashGuard.caught("npc_data.load.custom_model", error);
        }
    }

    @Unique
    public CustomModelData getCustomModelData(){
        return cnpcgeckoaddon$customModelData;
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
        if (cnpcgeckoaddon$MODEL_ENTITY.equals(entityName)) {
            return true;
        }
        return ((EntityCustomNpc) npc).modelData.getEntity(npc) instanceof EntityCustomModel;
    }
}
