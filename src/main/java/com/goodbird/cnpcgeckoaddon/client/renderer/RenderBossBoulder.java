package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BoulderStyles;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws the boulder either as one scaled block or as a drawn stone, spinning as it travels.
 *
 * <p>Deliberately the only class that knows what the boulder looks like: the entity carries
 * nothing but a block id, a skin id and a size, so swapping this for a GeckoLib model later
 * means replacing this renderer in the registry and touching nothing of the mechanic.</p>
 */
public class RenderBossBoulder extends EntityRenderer<EntityBossBoulder> {

    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");

    /** Model units across a whole boulder: the plain cube every entity texture is cut for. */
    private static final float MODEL_SIZE = 16.0F;
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;

    private final BlockRenderDispatcher blockRenderer;
    /** One lumpy stone, drawn for every skin: a style is exactly one drawing, nothing more. */
    private final ModelPart stone;

    /**
     * Resolved once per skin. The dispatcher builds every entity renderer again on a
     * resource reload, so an instance-level cache cannot go stale.
     */
    private final Map<String, Optional<ResourceLocation>> textures = new HashMap<>();

    public RenderBossBoulder(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
        stone = LayerDefinition.create(stoneMesh(), TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot();
    }

    /**
     * Three copies of the same cube, each turned and nudged off centre.
     *
     * <p>That is what stops the stone reading as a cube once it spins: the corners of the
     * two turned copies push out through the flat faces of the first, so the silhouette
     * keeps changing as it rolls. They are shrunk rather than resized, so all three still
     * carry the plain 16-cube unwrap and one drawing dresses the whole lump.</p>
     *
     * <p>The three sizes and offsets are picked so that no corner of a turned copy reaches
     * past the hitbox face at 8 units - the widest is 7.95 - while still standing a good
     * unit and a third proud of the core. The stone therefore fills the size the ability
     * was set to without ever being drawn outside the box that does the hitting.</p>
     */
    private static MeshDefinition stoneMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        addShell(root, "core", -1.4F, PartPose.ZERO);
        addShell(root, "ridge", -3.0F,
                PartPose.offsetAndRotation(0.9F, -0.7F, 0.3F, 0.28F, 0.40F, 0.16F));
        addShell(root, "flank", -3.6F,
                PartPose.offsetAndRotation(-0.9F, 0.8F, -0.7F, -0.34F, 0.60F, -0.22F));
        return mesh;
    }

    private static void addShell(PartDefinition root, String name, float shrink, PartPose pose) {
        root.addOrReplaceChild(name, CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-MODEL_SIZE / 2.0F, -MODEL_SIZE / 2.0F, -MODEL_SIZE / 2.0F,
                        MODEL_SIZE, MODEL_SIZE, MODEL_SIZE, new CubeDeformation(shrink)), pose);
    }

    @Override
    public void render(EntityBossBoulder boulder, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        BoulderStyles.Style style = BoulderStyles.get(boulder.styleId());
        ResourceLocation texture = texture(style.id()).orElse(null);
        float size = boulder.diameter();
        poseStack.pushPose();
        poseStack.translate(0.0F, size * 0.5F, 0.0F);
        // Local +Z onto the travel direction first, so the spin below rolls the stone
        // forward instead of sideways.
        poseStack.mulPose(Axis.YP.rotationDegrees(-boulder.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(boulder.rollAngle(partialTick)));
        if (texture == null) {
            poseStack.scale(size, size, size);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            blockRenderer.renderSingleBlock(boulder.getBlockState(), poseStack, buffer,
                    packedLight, OverlayTexture.NO_OVERLAY);
        } else {
            drawStone(poseStack, buffer, style, texture, size, packedLight);
        }
        poseStack.popPose();
        super.render(boulder, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void drawStone(PoseStack poseStack, MultiBufferSource buffer,
                           BoulderStyles.Style style, ResourceLocation texture, float size,
                           int packedLight) {
        // Model parts are built upside down and mirrored, so the same flip every entity
        // model gets is what puts the unwrap's top face on top. No division by the model
        // size: a part already draws itself at a sixteenth of its own units, so a cube this
        // wide is exactly one block before this scale and exactly the diameter after it.
        poseStack.scale(-size, -size, size);
        RenderType renderType = style.translucent()
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutoutNoCull(texture);
        // A magma or spectral stone has to read as lit from inside, dark dungeon or not.
        int light = style.glowing() ? LightTexture.FULL_BRIGHT : packedLight;
        stone.render(poseStack, buffer.getBuffer(renderType), light, OverlayTexture.NO_OVERLAY);
    }

    /**
     * The skin's own texture, or nothing at all when the artwork has not shipped.
     *
     * <p>A missing texture would otherwise render as the black and magenta checkerboard,
     * which reads as a broken install rather than as art that is still being drawn; falling
     * back to the plain block leaves something that still looks like a boulder.</p>
     */
    private Optional<ResourceLocation> texture(String styleId) {
        return textures.computeIfAbsent(styleId, id -> {
            if (!BoulderStyles.isTextured(id)) {
                return Optional.empty();
            }
            ResourceLocation own = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
                    "textures/entity/boulder/" + id + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(own).isPresent()) {
                return Optional.of(own);
            }
            LOGGER.warn("Boulder look {} has no texture at {}, drawing the plain block instead",
                    id, own);
            return Optional.empty();
        });
    }

    @Override
    public ResourceLocation getTextureLocation(EntityBossBoulder boulder) {
        return texture(BoulderStyles.normalize(boulder.styleId()))
                .orElse(TextureAtlas.LOCATION_BLOCKS);
    }
}
