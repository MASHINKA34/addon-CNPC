package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.data.NpcCarryData;
import com.goodbird.cnpcgeckoaddon.data.SoundReactionData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryData;
import com.goodbird.cnpcgeckoaddon.mixin.ISoundReactionData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataAI.class)
public class MixinDataAI implements ISoundReactionData, ITeleportPathData, INpcCarryData {

    @Unique
    private final SoundReactionData cnpcgeckoaddon$soundReactionData = new SoundReactionData();

    @Unique
    private final TeleportPathData cnpcgeckoaddon$teleportPathData = new TeleportPathData();

    @Unique
    private final NpcCarryData cnpcgeckoaddon$npcCarryData = new NpcCarryData();

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$saveSoundReaction(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        cnpcgeckoaddon$soundReactionData.writeToNBT(tag);
        cnpcgeckoaddon$teleportPathData.writeToNBT(tag);
        cnpcgeckoaddon$npcCarryData.writeToNBT(tag);
    }

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$loadSoundReaction(CompoundTag tag, CallbackInfo ci) {
        cnpcgeckoaddon$soundReactionData.readFromNBT(tag);
        cnpcgeckoaddon$teleportPathData.readFromNBT(tag);
        cnpcgeckoaddon$npcCarryData.readFromNBT(tag);
    }

    @Override
    @Unique
    public SoundReactionData cnpcgeckoaddon$getSoundReactionData() {
        return cnpcgeckoaddon$soundReactionData;
    }

    @Override
    @Unique
    public TeleportPathData cnpcgeckoaddon$getTeleportPathData() {
        return cnpcgeckoaddon$teleportPathData;
    }

    @Override
    @Unique
    public NpcCarryData cnpcgeckoaddon$getNpcCarryData() {
        return cnpcgeckoaddon$npcCarryData;
    }
}
