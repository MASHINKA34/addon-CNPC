package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class CustomModelHeldItemLayer extends GeoRenderLayer<EntityCustomModel> {
    public CustomModelHeldItemLayer(GeoRenderer<EntityCustomModel> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, EntityCustomModel animatable, GeoBone bone,
                              RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        ItemStack stack = stackForBone(bone, animatable);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        try {
            poseStack.scale(0.7F, 0.7F, 0.7F);
            poseStack.translate(bone.getPivotX() / 12.0F, bone.getPivotY() * 3.0F / 35.0F, -0.4F);
            poseStack.mulPose(Axis.XP.rotationDegrees(215.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            renderStack(poseStack, animatable, stack, bufferSource, packedLight, packedOverlay);
        } finally {
            poseStack.popPose();
        }
    }

    protected ItemStack stackForBone(GeoBone bone, EntityCustomModel animatable) {
        return switch (bone.getName()) {
            case "held_item" -> animatable.getMainHandItem();
            case "left_held_item" -> animatable.leftHeldItem;
            default -> ItemStack.EMPTY;
        };
    }

    protected void renderStack(PoseStack poseStack, EntityCustomModel animatable, ItemStack stack,
                               MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack, ItemDisplayContext.FIXED,
                false, poseStack, bufferSource, animatable.level(), packedLight, packedOverlay, animatable.getId());
    }
}
