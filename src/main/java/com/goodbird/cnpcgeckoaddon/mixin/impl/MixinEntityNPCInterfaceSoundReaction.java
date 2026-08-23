package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.SoundInvestigationGoal;
import com.goodbird.cnpcgeckoaddon.ai.SoundReactionController;
import com.goodbird.cnpcgeckoaddon.mixin.ISoundReactiveNpc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(EntityNPCInterface.class)
public abstract class MixinEntityNPCInterfaceSoundReaction extends PathfinderMob implements ISoundReactiveNpc {

    @Shadow(remap = false)
    private int taskCount;

    @Unique
    private SoundReactionController cnpcgeckoaddon$soundReactionController;

    protected MixinEntityNPCInterfaceSoundReaction(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    @Unique
    public SoundReactionController cnpcgeckoaddon$getSoundReactionController() {
        if (cnpcgeckoaddon$soundReactionController == null) {
            cnpcgeckoaddon$soundReactionController =
                    new SoundReactionController((EntityNPCInterface) (Object) this);
        }
        return cnpcgeckoaddon$soundReactionController;
    }

    /** Entity invokes this virtual method when it enters, leaves, or changes listener sections. */
    @Override
    public void updateDynamicGameEventListener(
            BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
        super.updateDynamicGameEventListener(listenerConsumer);
        if (level() instanceof ServerLevel serverLevel) {
            listenerConsumer.accept(cnpcgeckoaddon$getSoundReactionController().dynamicListener(), serverLevel);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void cnpcgeckoaddon$tickSoundReaction(CallbackInfo ci) {
        if (!level().isClientSide) {
            cnpcgeckoaddon$getSoundReactionController().tick();
        }
    }

    @Inject(method = "setResponse", at = @At("TAIL"), remap = false)
    private void cnpcgeckoaddon$addSoundInvestigationGoal(CallbackInfo ci) {
        SoundReactionController controller = cnpcgeckoaddon$getSoundReactionController();
        goalSelector.addGoal(taskCount++,
                new SoundInvestigationGoal((EntityNPCInterface) (Object) this, controller));
    }
}
