package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the boulder as one scaled block, spinning as it travels.
 *
 * <p>Deliberately the only class that knows the boulder looks like a block: the entity
 * carries nothing but a block id and a size, so swapping this for a GeckoLib model later
 * means replacing this renderer in the registry and touching nothing of the mechanic.</p>
 */
public class RenderBossBoulder extends EntityRenderer<EntityBossBoulder> {
    private final BlockRenderDispatcher blockRenderer;

    public RenderBossBoulder(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(EntityBossBoulder boulder, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float size = boulder.diameter();
        poseStack.pushPose();
        poseStack.translate(0.0F, size * 0.5F, 0.0F);
        // Local +Z onto the travel direction first, so the spin below rolls the block
        // forward instead of sideways.
        poseStack.mulPose(Axis.YP.rotationDegrees(-boulder.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(boulder.rollAngle(partialTick)));
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        blockRenderer.renderSingleBlock(boulder.getBlockState(), poseStack, buffer,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(boulder, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityBossBoulder boulder) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
