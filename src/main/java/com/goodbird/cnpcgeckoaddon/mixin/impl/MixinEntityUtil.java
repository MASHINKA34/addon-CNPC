package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import com.goodbird.cnpcgeckoaddon.utils.NpcTextureUtils;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;

@Mixin(EntityUtil.class)
public class MixinEntityUtil {

    @Inject(method = "Copy", at = @At("TAIL"), remap = false)
    private static void copy(LivingEntity copied, LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof EntityCustomModel && copied instanceof EntityNPCInterface) {
            EntityCustomModel modelEntity = (EntityCustomModel) entity;
            EntityNPCInterface npc = (EntityNPCInterface) copied;
            npc.noCulling = true;
            IDataDisplay display = (IDataDisplay) npc.display;
            modelEntity.textureResLoc = NpcTextureUtils.getNpcTexture((EntityNPCInterface) copied);
            // Copy runs once per rendered frame and the model/animation strings come from
            // NBT or from scripts, so a malformed value must not throw here - keep the last
            // valid location instead and let ModelCustom fall back to its "not found" model.
            modelEntity.modelResLoc = AnimationFileUtil.parseOr(
                    display.getCustomModelData().getModel(), modelEntity.modelResLoc);
            modelEntity.animResLoc = AnimationFileUtil.parseOr(
                    display.getCustomModelData().getAnimFile(), modelEntity.animResLoc);
            modelEntity.idleAnim = display.getCustomModelData().getIdleAnim();
            modelEntity.walkAnim = display.getCustomModelData().getWalkAnim();
            modelEntity.attackAnims = display.getCustomModelData().getAttackAnims();
            modelEntity.hurtAnim = display.getCustomModelData().getHurtAnim();
            modelEntity.deathAnim = display.getCustomModelData().getDeathAnim();
            modelEntity.size = npc.display.getSize();
            modelEntity.updateCombatState(npc.isKilled() || npc.deathTime > 0, npc.swinging, npc.swingTime, npc.hurtTime);
            if(display.getCustomModelData().isHurtTintEnabled()){
                modelEntity.hurtTime = npc.hurtTime;
                modelEntity.deathTime = display.getCustomModelData().getDeathAnim().isEmpty() ? npc.deathTime : 0;
            } else {
                modelEntity.hurtTime = 0;
                modelEntity.deathTime = 0;
            }
            if(npc.inventory.getLeftHand()!=null) {
                modelEntity.leftHeldItem = npc.inventory.getLeftHand().getMCItemStack();
            }
            modelEntity.headBoneName = display.getCustomModelData().getHeadBoneName();
            AnimatableManager animationData = modelEntity.getAnimatableInstanceCache().getManagerForId(modelEntity.getUUID().hashCode());
            for(Object obj : animationData.getAnimationControllers().values()){
                AnimationController controller = (AnimationController) obj;
                controller.transitionLength(display.getCustomModelData().getTransitionLengthTicks());
            }
            float width = display.getCustomModelData().getEffectiveWidth();
            float height = display.getCustomModelData().getEffectiveHeight();
            if(height!=modelEntity.getBbHeight() || width != modelEntity.getBbWidth()){
                modelEntity.setSize(width, height);
                npc.refreshDimensions();
            }
        }
    }
}
