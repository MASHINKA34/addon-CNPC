package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.client.model.ModelCustom;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;

public class RenderCustomModel extends GeoEntityRenderer<EntityCustomModel> {

    public RenderCustomModel(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ModelCustom());
        addRenderLayer(new CustomModelHeldItemLayer(this));
    }

    @Override
    public RenderType getRenderType(EntityCustomModel animatable, ResourceLocation texture, @org.jetbrains.annotations.Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(getTextureLocation(animatable));
    }

    @Override
    protected void applyRotations(EntityCustomModel entityLiving, PoseStack matrixStackIn, float ageInTicks, float rotationYaw,
                                  float partialTicks, float nativeScale) {
        Pose pose = entityLiving.getPose();

        // The shake offset has to be folded into the yaw *before* it is applied,
        // otherwise the adjusted value is simply discarded and shaking is invisible.
        if (isShaking(entityLiving))
            rotationYaw += (float)(Math.cos(entityLiving.tickCount * 3.25d) * Math.PI * 0.4d);

        if (pose != Pose.SLEEPING) {
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
        }

        if (entityLiving.deathTime > 0) {
            float f = ((float) entityLiving.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }

            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(f * this.getDeathMaxRotation(entityLiving)));
        } else if (entityLiving.isAutoSpinAttack()) {
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(-90.0F - entityLiving.getXRot()));
            matrixStackIn
                    .mulPose(Axis.YP.rotationDegrees(((float) entityLiving.tickCount + partialTicks) * -75.0F));
        } else if (pose == Pose.SLEEPING) {
            Direction direction = entityLiving.getBedOrientation();
            float f1 = direction != null ? RenderUtil.getDirectionAngle(direction) : rotationYaw;
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(f1));
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(this.getDeathMaxRotation(entityLiving)));
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(270.0F));
        } else if (entityLiving.hasCustomName()) {
            String s = ChatFormatting.stripFormatting(entityLiving.getName().getString());
            if ("Dinnerbone".equals(s) || "Grumm".equals(s)) {
                matrixStackIn.translate(0.0D, entityLiving.getBbHeight() + 0.1F, 0.0D);
                matrixStackIn.mulPose(Axis.ZP.rotationDegrees(180.0F));
            }
        }

    }

    @Override
    public void preRender(PoseStack poseStack, EntityCustomModel animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int renderColor) {
        withScale((float) animatable.size / 5.0F);
        model.getBone("held_item").ifPresent(bone -> bone.setHidden(true));
        model.getBone("left_held_item").ifPresent(bone -> bone.setHidden(true));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, renderColor);
    }
}
