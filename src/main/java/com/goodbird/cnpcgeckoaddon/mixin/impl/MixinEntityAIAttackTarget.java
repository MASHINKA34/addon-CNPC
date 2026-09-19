package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.BossMechanicUtil;
import com.goodbird.cnpcgeckoaddon.ai.NpcRangedAi;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /**
     * Stands the chase down while the addon's ranged AI owns the npc.
     *
     * <p>Both goals move the npc, and this one is the better-placed of the two, so without
     * this the npc would run its target down instead of holding the distance it was given.
     * The one distance it is let back in at is the one the builder asked for it: a target
     * nearer than the window while the npc is set to fight in melee. Both sides ask the same
     * question, so exactly one of the two goals is holding the npc at any distance.</p>
     */
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$standDownForAddonRangedAi(CallbackInfoReturnable<Boolean> cir) {
        if (cnpcgeckoaddon$addonHoldsTheFight(npc)) {
            cir.setReturnValue(false);
        }
    }

    /** And drops a chase already under way when the switch is turned on mid fight. */
    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$endChaseForAddonRangedAi(CallbackInfoReturnable<Boolean> cir) {
        if (cnpcgeckoaddon$addonHoldsTheFight(npc)) {
            cir.setReturnValue(false);
        }
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

    /**
     * Whether the addon's ranged AI is holding this npc at a distance rather than letting it
     * close. A failed question leaves the chase to CustomNPCs, which is how it always ran.
     */
    @Unique
    private static boolean cnpcgeckoaddon$addonHoldsTheFight(EntityNPCInterface npc) {
        try {
            return NpcRangedAi.runsRangedAi(npc) && !NpcRangedAi.yieldsToMelee(npc);
        } catch (Throwable error) {
            CrashGuard.caught("mixin.ai.melee_ranged_addon_ai", error);
            return false;
        }
    }
}
