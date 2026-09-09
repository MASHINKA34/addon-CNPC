package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.BossMechanicUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import noppes.npcs.ai.EntityAIRangedAttack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla ranged movement/look behavior, but fires only through configured boss timers. */
@Mixin(value = EntityAIRangedAttack.class, remap = false)
public abstract class MixinEntityAIRangedAttack {
    @Shadow
    @Final
    private EntityNPCInterface npc;

    /**
     * The same as the melee chase: the approach stands aside while the boss walks to a cast spot.
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;moveTo(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean cnpcgeckoaddon$holdApproachForCastSpot(PathNavigation navigation, Entity target, double speed) {
        return !BossMechanicUtil.isBoundForCastSpot(npc) && navigation.moveTo(target, speed);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"))
    private void cnpcgeckoaddon$suppressVanillaProjectile(EntityNPCInterface npc,
                                                           LivingEntity target, float indirect) {
        if (!BossMechanicUtil.replacesVanillaAttacks(npc)) npc.performRangedAttack(target, indirect);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void cnpcgeckoaddon$suppressVanillaRangedSwing(EntityNPCInterface npc, InteractionHand hand) {
        if (!BossMechanicUtil.replacesVanillaAttacks(npc)) npc.swing(hand);
    }
}
