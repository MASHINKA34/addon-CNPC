package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.BossMechanicUtil;
import noppes.npcs.ai.EntityAIPounceTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps teleporting bosses from using CustomNPCs' vertical leap movement. */
@Mixin(value = EntityAIPounceTarget.class, remap = false)
public abstract class MixinEntityAIPounceTarget {
    @Shadow
    private EntityNPCInterface npc;

    @Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$disableBossPounce(CallbackInfoReturnable<Boolean> cir) {
        if (BossMechanicUtil.keepsStationary(npc)) cir.setReturnValue(false);
    }
}
