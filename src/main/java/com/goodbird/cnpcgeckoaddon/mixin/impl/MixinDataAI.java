package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.data.NpcCarryData;
import com.goodbird.cnpcgeckoaddon.data.NpcImmunityData;
import com.goodbird.cnpcgeckoaddon.data.NpcLaunchPadData;
import com.goodbird.cnpcgeckoaddon.data.SoundReactionData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcImmunityData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcLaunchPadData;
import com.goodbird.cnpcgeckoaddon.mixin.ISoundReactionData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataAI.class)
public class MixinDataAI implements ISoundReactionData, ITeleportPathData, INpcCarryData,
        INpcImmunityData, INpcLaunchPadData {

    @Unique
    private final SoundReactionData cnpcgeckoaddon$soundReactionData = new SoundReactionData();

    @Unique
    private final TeleportPathData cnpcgeckoaddon$teleportPathData = new TeleportPathData();

    @Unique
    private final NpcCarryData cnpcgeckoaddon$npcCarryData = new NpcCarryData();

    @Unique
    private final NpcImmunityData cnpcgeckoaddon$npcImmunityData = new NpcImmunityData();

    @Unique
    private final NpcLaunchPadData cnpcgeckoaddon$npcLaunchPadData = new NpcLaunchPadData();

    /**
     * Each block of settings behind a guard of its own. This is the head of CustomNPCs' own
     * save: an exception out of here and the npc is written without its ai at all, the
     * addon's five blocks and everything of CustomNPCs' with them. Guarded one by one, a
     * block that cannot be written costs that block - the rest, and the npc, are saved.
     * The method references name the data classes' own methods, so nothing synthetic is
     * merged into {@code DataAI} for them.
     */
    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$saveSoundReaction(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CrashGuard.run("npc_data.save.sound_reaction", tag, cnpcgeckoaddon$soundReactionData::writeToNBT);
        CrashGuard.run("npc_data.save.boss", tag, cnpcgeckoaddon$teleportPathData::writeToNBT);
        CrashGuard.run("npc_data.save.carry", tag, cnpcgeckoaddon$npcCarryData::writeToNBT);
        CrashGuard.run("npc_data.save.immunity", tag, cnpcgeckoaddon$npcImmunityData::writeToNBT);
        CrashGuard.run("npc_data.save.launch_pad", tag, cnpcgeckoaddon$npcLaunchPadData::writeToNBT);
    }

    /**
     * The same on the way in, where an exception would stop CustomNPCs reading the rest of
     * the npc: a block that cannot be read keeps what it had, and the others are still read.
     */
    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    private void cnpcgeckoaddon$loadSoundReaction(CompoundTag tag, CallbackInfo ci) {
        CrashGuard.run("npc_data.load.sound_reaction", tag, cnpcgeckoaddon$soundReactionData::readFromNBT);
        CrashGuard.run("npc_data.load.boss", tag, cnpcgeckoaddon$teleportPathData::readFromNBT);
        CrashGuard.run("npc_data.load.carry", tag, cnpcgeckoaddon$npcCarryData::readFromNBT);
        CrashGuard.run("npc_data.load.immunity", tag, cnpcgeckoaddon$npcImmunityData::readFromNBT);
        CrashGuard.run("npc_data.load.launch_pad", tag, cnpcgeckoaddon$npcLaunchPadData::readFromNBT);
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

    @Override
    @Unique
    public NpcImmunityData cnpcgeckoaddon$getNpcImmunityData() {
        return cnpcgeckoaddon$npcImmunityData;
    }

    @Override
    @Unique
    public NpcLaunchPadData cnpcgeckoaddon$getNpcLaunchPadData() {
        return cnpcgeckoaddon$npcLaunchPadData;
    }
}
