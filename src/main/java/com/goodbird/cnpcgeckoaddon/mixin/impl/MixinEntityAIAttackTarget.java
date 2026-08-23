package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.BossMechanicUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla chase/look behavior, but replaces its swing and damage with configured boss attacks. */
@Mixin(value = EntityAIAttackTarget.class, remap = false)
public abstract class MixinEntityAIAttackTarget {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void cnpcgeckoaddon$suppressVanillaMeleeSwing(EntityNPCInterface npc, InteractionHand hand) {
        if (!BossMechanicUtil.replacesVanillaAttacks(npc)) npc.swing(hand);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean cnpcgeckoaddon$suppressVanillaMeleeDamage(EntityNPCInterface npc, Entity target) {
        return !BossMechanicUtil.replacesVanillaAttacks(npc) && npc.doHurtTarget(target);
    }
}
