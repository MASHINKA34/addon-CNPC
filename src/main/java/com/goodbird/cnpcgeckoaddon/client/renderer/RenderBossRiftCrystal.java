package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.client.model.ModelRiftCrystal;
import com.goodbird.cnpcgeckoaddon.data.RiftCrystalContract;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossRiftCrystal;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Draws a rift crystal: either the addon's own drawn model, turning and pulsing through its own
 * clip, or - the default, and the fallback whenever the model's files are not there - one block,
 * scaled, painted in the rift's colour, turning on the spot and riding up and down.
 *
 * <p>Deliberately the only class that knows what a crystal looks like, the boulder's way: the
 * entity carries a block id, a colour, a look, a skin and four numbers, and which of the two
 * ways it is drawn is settled here and nowhere else.</p>
 *
 * <p>The model is drawn through a {@link GeoEntityRenderer} held inside this one rather than by
 * this class becoming one: the block path is what a crystal falls back to when the artwork has
 * not shipped, and it has to keep working exactly as it did, which it cannot do from inside a
 * renderer that draws a model it has not got.</p>
 *
 * <p>Under the block look the turn and the bob are worked out here from the tick the crystal was
 * stood up on rather than sent: nothing about a crystal ever changes after its spawn packet, and
 * a score of them hanging on a platform for a minute would otherwise be a score of packets a
 * tick. Under the model look neither is applied at all - both are in the clip.</p>
 */
public class RenderBossRiftCrystal extends EntityRenderer<EntityBossRiftCrystal> {

    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcgeckoaddon");

    /** The next moment a crystal asking for a model nobody has drawn is worth a line in the log. */
    private static long nextMissingWarningAt;
    private static boolean warnedOnce;

    private final BlockRenderDispatcher blockRenderer;
    private final RiftCrystalGeoRenderer modelRenderer;

    public RenderBossRiftCrystal(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
        modelRenderer = new RiftCrystalGeoRenderer(context);
    }

    @Override
    public void render(EntityBossRiftCrystal crystal, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float size = crystal.scale();
        int light = crystal.glows() ? LightTexture.FULL_BRIGHT : packedLight;
        if (crystal.wantsModel()) {
            if (modelIsLoaded()) {
                // Its own renderer draws the name plate as well, so this one adds nothing after.
                modelRenderer.render(crystal, entityYaw, partialTick, poseStack, buffer, light);
                return;
            }
            warnModelMissing();
        }
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
        return crystal.wantsModel() && modelIsLoaded()
                ? ModelRiftCrystal.texture(crystal.skin()) : TextureAtlas.LOCATION_BLOCKS;
    }

    /**
     * Whether both halves of the artwork are loaded. Asked per frame rather than cached: the
     * two maps are already the cache, a resource reload refills them, and a crystal is not a
     * thing there are thousands of on screen.
     */
    static boolean modelIsLoaded() {
        return RiftCrystalContract.drawsModel(true,
                GeckoLibCache.getBakedModels().containsKey(ModelRiftCrystal.GEO),
                GeckoLibCache.getBakedAnimations().containsKey(ModelRiftCrystal.ANIMATIONS));
    }

    /**
     * One line in the log for a rift whose crystals were set to a model that has not shipped,
     * at most once every ten seconds for the whole class: it is a frame, so it happens again
     * sixty times a second, and the block drawn instead is a working crystal, not a failure.
     */
    private static void warnModelMissing() {
        long now = System.nanoTime();
        // Compared by difference: nanoTime is only meaningful that way, and may be negative.
        if (warnedOnce && now - nextMissingWarningAt < 0L) {
            return;
        }
        warnedOnce = true;
        nextMissingWarningAt = now + CrashGuard.LOG_INTERVAL_NANOS;
        LOGGER.warn("rift crystal model missing, drawing the block: {} and {} are not both loaded",
                ModelRiftCrystal.GEO, ModelRiftCrystal.ANIMATIONS);
    }

    /**
     * The drawn crystal itself: the rift's colour multiplied over the skin, the rift's size, and
     * the light the outer renderer worked out - full brightness for a glowing one.
     */
    private static final class RiftCrystalGeoRenderer extends GeoEntityRenderer<EntityBossRiftCrystal> {

        RiftCrystalGeoRenderer(EntityRendererProvider.Context context) {
            super(context, new ModelRiftCrystal());
        }

        @Override
        public Color getRenderColor(EntityBossRiftCrystal crystal, float partialTick, int packedLight) {
            // Multiplied over the drawing the same way the block look's tint is, so a purple
            // crystal reads as purple and a green one as a green crystal of the same shape.
            return Color.ofOpaque(crystal.tint());
        }

        @Override
        public void preRender(PoseStack poseStack, EntityBossRiftCrystal crystal, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay, int renderColor) {
            withScale(crystal.scale());
            super.preRender(poseStack, crystal, model, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, renderColor);
        }
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
