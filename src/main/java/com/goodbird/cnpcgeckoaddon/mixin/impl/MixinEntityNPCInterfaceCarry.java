package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.world.NpcCarryManager;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNPCInterfaceCarry extends PathfinderMob implements INpcCarryState {
    @Unique
    private boolean cnpcgeckoaddon$carried;

    protected MixinEntityNPCInterfaceCarry(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean cnpcgeckoaddon$isCarried() {
        return cnpcgeckoaddon$carried;
    }

    @Override
    public void cnpcgeckoaddon$setCarried(boolean carried) {
        cnpcgeckoaddon$carried = carried;
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

    /**
     * Saves a held npc with the flags it had before it was picked up.
     *
     * <p>An autosave or a chunk unload lands in the middle of carries, and the flags a carry
     * borrows are all persisted ones. This is the tail of the whole save, so the values
     * written higher up by Entity and Mob are the ones being corrected here.</p>
     */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void cnpcgeckoaddon$saveCarriedNpcAsItWas(CompoundTag tag, CallbackInfo ci) {
        NpcCarryManager.restoreSavedFlags(this, tag);
    }
}
