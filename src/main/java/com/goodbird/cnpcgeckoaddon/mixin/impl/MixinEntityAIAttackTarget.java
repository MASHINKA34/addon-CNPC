package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.BossMechanicUtil;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps vanilla chase/look behavior, but replaces its swing and damage with configured boss attacks.
 *
 * <p>Every question the redirects ask the addon is asked behind a {@code try} written out - this
 * is every npc's melee goal, every tick - and a question that fails is answered the way
 * CustomNPCs would act without the addon: the call it redirected is made.</p>
 */
@Mixin(value = EntityAIAttackTarget.class, remap = false)
public abstract class MixinEntityAIAttackTarget {
    @Shadow
    private EntityNPCInterface npc;

    /**
     * The chase lets go of the navigation while the boss walks to a cast spot: the goal
     * re-paths to its target every few ticks, and a walk tugged back toward the target on
     * every one of them never gets anywhere.
     */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;moveTo(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean cnpcgeckoaddon$holdChaseForCastSpot(PathNavigation navigation, Entity target, double speed) {
        return !cnpcgeckoaddon$boundForCastSpot(npc) && navigation.moveTo(target, speed);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void cnpcgeckoaddon$suppressVanillaMeleeSwing(EntityNPCInterface npc, InteractionHand hand) {
        if (!cnpcgeckoaddon$replacesAttacks(npc)) npc.swing(hand);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean cnpcgeckoaddon$suppressVanillaMeleeDamage(EntityNPCInterface npc, Entity target) {
        return !cnpcgeckoaddon$replacesAttacks(npc) && npc.doHurtTarget(target);
    }

    @Unique
    private static boolean cnpcgeckoaddon$boundForCastSpot(EntityNPCInterface npc) {
        try {
            return BossMechanicUtil.isBoundForCastSpot(npc);
        } catch (Throwable error) {
            CrashGuard.caught("mixin.ai.melee_cast_spot", error);
            return false;
        }
    }

    @Unique
    private static boolean cnpcgeckoaddon$replacesAttacks(EntityNPCInterface npc) {
        try {
            return BossMechanicUtil.replacesVanillaAttacks(npc);
        } catch (Throwable error) {
            CrashGuard.caught("mixin.ai.melee_attack", error);
            return false;
        }
    }
}
