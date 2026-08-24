package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.block.BossChestBlock;
import com.goodbird.cnpcgeckoaddon.data.BossChestStyles;
import com.goodbird.cnpcgeckoaddon.tile.BossChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a boss chest as the ordinary chest it is, wearing whichever skin its block state
 * says.
 *
 * <p>The geometry is the vanilla chest layer, baked from {@code ModelLayers.CHEST}, so a
 * skin is nothing but a 64x64 texture laid out exactly like the vanilla one. Nothing is
 * added to the vanilla chest atlas: that atlas is stitched from {@code minecraft:entity/chest}
 * and reaching into it from another namespace causes trouble that is hard to trace, so the
 * texture is bound directly instead.</p>
 */
public class BossChestRenderer implements BlockEntityRenderer<BossChestBlockEntity> {

    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");

    /** What a skin looks like until somebody draws it. */
    private static final ResourceLocation FALLBACK =
            ResourceLocation.withDefaultNamespace("textures/entity/chest/normal.png");

    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;

    /**
     * Resolved once per skin. The dispatcher builds every block entity renderer again on a
     * resource reload, so an instance-level cache cannot go stale.
     */
    private final Map<BossChestStyles.Skin, ResourceLocation> textures =
            new EnumMap<>(BossChestStyles.Skin.class);

    public BossChestRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    @Override
    public void render(BossChestBlockEntity chest, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = chest.getBlockState();
        if (!state.hasProperty(BossChestBlock.STYLE) || !state.hasProperty(ChestBlock.FACING)) {
            return;
        }

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
        pose.translate(-0.5F, -0.5F, -0.5F);

        // The same easing vanilla uses, so a boss chest opens at the speed players expect.
        float openness = chest.getOpenNess(partialTick);
        openness = 1.0F - openness;
        openness = 1.0F - openness * openness * openness;

        VertexConsumer consumer = buffers.getBuffer(
                RenderType.entityCutoutNoCull(texture(state.getValue(BossChestBlock.STYLE))));
        this.lid.xRot = -(openness * ((float) Math.PI / 2.0F));
        this.lock.xRot = this.lid.xRot;
        this.lid.render(pose, consumer, light, overlay);
        this.lock.render(pose, consumer, light, overlay);
        this.bottom.render(pose, consumer, light, overlay);
        pose.popPose();
    }

    /**
     * The skin's own texture, or the plain vanilla chest when the artwork has not shipped.
     *
     * <p>A missing texture would otherwise render as the black and magenta checkerboard,
     * which reads as a broken install rather than as art that is still being drawn.</p>
     */
    private ResourceLocation texture(BossChestStyles.Skin skin) {
        return textures.computeIfAbsent(skin, key -> {
            ResourceLocation own = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
                    "textures/entity/chest/" + key.getSerializedName() + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(own).isPresent()) {
                return own;
            }
            LOGGER.warn("Boss chest skin {} has no texture at {}, drawing the plain chest instead",
                    key.getSerializedName(), own);
            return FALLBACK;
        });
    }
}
