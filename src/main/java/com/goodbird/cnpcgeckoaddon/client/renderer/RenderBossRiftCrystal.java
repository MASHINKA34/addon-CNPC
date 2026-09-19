package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.entity.EntityBossRiftCrystal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a rift crystal: one block, scaled, painted in the rift's colour, turning on the spot and
 * riding up and down.
 *
 * <p>Deliberately the only class that knows what a crystal looks like, the boulder's way: the
 * entity carries a block id, a colour and four numbers, so swapping this for a model later means
 * replacing this renderer in the registry and touching nothing of the mechanic.</p>
 *
 * <p>The turn and the bob are worked out here from the tick the crystal was stood up on rather
 * than sent: nothing about a crystal ever changes after its spawn packet, and a score of them
 * hanging on a platform for a minute would otherwise be a score of packets a tick.</p>
 */
public class RenderBossRiftCrystal extends EntityRenderer<EntityBossRiftCrystal> {

    private final BlockRenderDispatcher blockRenderer;

    public RenderBossRiftCrystal(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(EntityBossRiftCrystal crystal, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float size = crystal.scale();
        int light = crystal.glows() ? LightTexture.FULL_BRIGHT : packedLight;
        poseStack.pushPose();
        // The bob first, then the turn about the crystal's own upright axis, then the block: it is
        // drawn from its corner, so half of it back in x and z puts it round that axis, and its
        // base stays on the entity's own spot - which is where its hitbox starts as well, so what
        // is drawn is what the collecting measures to.
        poseStack.translate(0.0D, crystal.bobOffset(partialTick), 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(crystal.spinAngle(partialTick)));
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        blockRenderer.renderSingleBlock(crystal.blockState(), poseStack,
                new TintedBuffer(buffer, crystal.tint()), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(crystal, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityBossRiftCrystal crystal) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /**
     * A buffer source that hands out tinting consumers, so one block of any shape comes out in
     * the rift's colour.
     *
     * <p>The block renderer decides for itself which render types the block's model needs and
     * asks the source for each of them; there is no colour argument to pass. So the tint is
     * applied a vertex at a time on the way through, which also means it multiplies whatever
     * the block's own texture and biome colour already were, rather than replacing them: a
     * purple amethyst reads as purple amethyst, a green one as a green stone of the same shape.</p>
     */
    private record TintedBuffer(MultiBufferSource delegate, int rgb) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new TintedConsumer(delegate.getBuffer(renderType), rgb);
        }
    }

    /** One vertex stream with the tint multiplied into every colour that passes through it. */
    private record TintedConsumer(VertexConsumer delegate, int rgb) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(scale(red, rgb >> 16 & 0xFF), scale(green, rgb >> 8 & 0xFF),
                    scale(blue, rgb & 0xFF), alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        private static int scale(int channel, int tint) {
            return channel * tint / 255;
        }
    }
}
