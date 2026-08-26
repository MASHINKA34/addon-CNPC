package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.world.NpcCarryManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes a carried npc out of the collision system for as long as it is held.
 *
 * <p>Both answers are read from live carry state rather than stored on the npc, so there is
 * nothing here that a save could catch halfway and write to disk.</p>
 */
@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNPCInterfaceCarry extends PathfinderMob {

    protected MixinEntityNPCInterfaceCarry(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** A wandering npc is pushable, and would shove its own carrier around while held. */
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$carriedNpcPushesNobody(CallbackInfoReturnable<Boolean> cir) {
        if (NpcCarryManager.isCarried(this)) {
            cir.setReturnValue(false);
        }
    }

    /** An npc with a solid hitbox would otherwise be a wall floating in front of the eyes. */
    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$carriedNpcBlocksNobody(CallbackInfoReturnable<Boolean> cir) {
        if (NpcCarryManager.isCarried(this)) {
            cir.setReturnValue(false);
        }
    }
}
