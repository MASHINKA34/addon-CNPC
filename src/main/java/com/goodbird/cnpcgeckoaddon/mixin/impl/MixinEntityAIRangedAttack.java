package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.BossMechanicUtil;
import com.goodbird.cnpcgeckoaddon.ai.NpcRangedAi;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import noppes.npcs.ai.EntityAIRangedAttack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        return !cnpcgeckoaddon$boundForCastSpot(npc) && navigation.moveTo(target, speed);
    }

    /**
     * Stands this whole goal down for an npc whose ranged fight the addon runs.
     *
     * <p>Asked rather than the goal being left out of the list: the switch is edited from a
     * screen, and the list is only rebuilt when CustomNPCs decides to. A goal already running
     * when the switch is turned on stops here on its next tick - which does clear the npc's
     * target, because that is what this goal's own {@code stop} does; whatever the npc was
     * fighting picks it back up on the tick after.</p>
     */
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$standDownForAddonRangedAi(CallbackInfoReturnable<Boolean> cir) {
        if (cnpcgeckoaddon$addonRunsRangedAi(npc)) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"))
    private void cnpcgeckoaddon$suppressVanillaProjectile(EntityNPCInterface npc,
                                                           LivingEntity target, float indirect) {
        if (!cnpcgeckoaddon$replacesAttacks(npc)) npc.performRangedAttack(target, indirect);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void cnpcgeckoaddon$suppressVanillaRangedSwing(EntityNPCInterface npc, InteractionHand hand) {
        if (!cnpcgeckoaddon$replacesAttacks(npc)) npc.swing(hand);
    }

    /** Asked behind a try written out, for the melee goal's reason; a failed question means "no". */
    @Unique
    private static boolean cnpcgeckoaddon$boundForCastSpot(EntityNPCInterface npc) {
        try {
            return BossMechanicUtil.isBoundForCastSpot(npc);
        } catch (Throwable error) {
            CrashGuard.caught("mixin.ai.ranged_cast_spot", error);
            return false;
        }
    }

    @Unique
    private static boolean cnpcgeckoaddon$replacesAttacks(EntityNPCInterface npc) {
        try {
            return BossMechanicUtil.replacesVanillaAttacks(npc);
        } catch (Throwable error) {
            CrashGuard.caught("mixin.ai.ranged_attack", error);
            return false;
        }
    }

    /** The same, for the addon's own ranged AI; a failed question leaves the goal to CustomNPCs. */
    @Unique
    private static boolean cnpcgeckoaddon$addonRunsRangedAi(EntityNPCInterface npc) {
        try {
            return NpcRangedAi.runsRangedAi(npc);
        } catch (Throwable error) {
            CrashGuard.caught("mixin.ai.ranged_addon_ai", error);
            return false;
        }
    }
}
