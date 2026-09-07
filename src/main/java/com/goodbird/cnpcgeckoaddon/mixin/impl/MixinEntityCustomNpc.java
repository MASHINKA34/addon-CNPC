package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import noppes.npcs.ModelData;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityCustomNpc.class)
public class MixinEntityCustomNpc extends EntityNPCInterface {

    @Shadow(remap = false)
    public ModelData modelData;

    public MixinEntityCustomNpc(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void cnpcgeckoaddon$limitModelHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (modelData == null || !(modelData.getEntity(this) instanceof EntityCustomModel)) {
            return;
        }
        EntityDimensions dimensions = cir.getReturnValue();
        float width = CustomModelData.clampHitboxSize(dimensions.width());
        float height = CustomModelData.clampHitboxSize(dimensions.height());
        if (width != dimensions.width() || height != dimensions.height()) {
            cir.setReturnValue(EntityDimensions.scalable(width, height));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cnpcgeckoaddon$syncModelEntitySize(CallbackInfo ci) {
        IDataDisplay display = (IDataDisplay) this.display;
        Entity entity = this.modelData.getEntity(this);
        if (!(entity instanceof EntityCustomModel)) return;
        EntityCustomModel modelEntity = (EntityCustomModel) entity;
        float width = display.getCustomModelData().getEffectiveWidth();
        float height = display.getCustomModelData().getEffectiveHeight();
        if (height != modelEntity.getBbHeight() || width != modelEntity.getBbWidth()) {
            modelEntity.setSize(width, height);
            this.refreshDimensions();
        }
    }
}
