package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

/** Shared filmstrip-ribbon geometry for hooks and every persistent boss link. */
public final class AnimatedLinkRenderUtil {
    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");
    private static final double SEGMENTS_PER_BLOCK = 2.0D;
    private static final int MIN_SEGMENTS = 2;
    private static final int MAX_SEGMENTS = 64;
    private static final double HEAD_SCALE = 2.0D;
    private static final Set<String> WARNED_TEXTURES = new HashSet<>();

    private AnimatedLinkRenderUtil() {
    }

    /** Returns false for the intentional particle style as well as unavailable artwork. */
    public static boolean hasTextures(Minecraft minecraft, HookCordStyles.Style style,
                                      boolean drawHead) {
        if (!HookCordStyles.isTextured(style.id())) {
            return false;
        }
        ResourceLocation cord = texture(style.id(), "cord.png");
        ResourceLocation head = texture(style.id(), "head.png");
        boolean present = minecraft.getResourceManager().getResource(cord).isPresent()
                && (!drawHead || minecraft.getResourceManager().getResource(head).isPresent());
        if (!present && WARNED_TEXTURES.add(style.id() + ":" + drawHead)) {
            LOGGER.warn("No animated link textures for style '{}', falling back to particles", style.id());
        }
        return present;
    }

    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                              ClientLevel level, HookCordStyles.Style style, Vec3 from, Vec3 to,
                              Vec3 cameraPos, long gameTime, int widthPercent, int sagPercent,
                              boolean drawHead) {
        ResourceLocation cordTexture = texture(style.id(), "cord.png");
        ResourceLocation headTexture = texture(style.id(), "head.png");
        double length = to.distanceTo(from);
        int segments = Mth.clamp((int) (length * SEGMENTS_PER_BLOCK), MIN_SEGMENTS, MAX_SEGMENTS);
        int frames = Math.max(1, style.frames());
        int baseFrame = Math.floorMod(gameTime / Math.max(1, style.frameTicks()), frames);
        double halfWidth = style.width() * Mth.clamp(widthPercent, 25, 400) / 200.0D;
        double sag = style.sag() * length * Mth.clamp(sagPercent, 0, 200) / 100.0D;

        RenderType renderType = style.translucent()
                ? RenderType.entityTranslucent(cordTexture)
                : RenderType.entityCutoutNoCull(cordTexture);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();

        Vec3 previous = sagged(from, to, 0.0D, sag);
        Vec3 lastDirection = to.subtract(from).normalize();
        for (int i = 0; i < segments; i++) {
            Vec3 next = sagged(from, to, (i + 1) / (double) segments, sag);
            Vec3 direction = next.subtract(previous);
            if (direction.lengthSqr() < 1.0E-9D) {
                previous = next;
                continue;
            }
            direction = direction.normalize();
            lastDirection = direction;
            Vec3 middle = previous.add(next).scale(0.5D);
            Vec3 toCamera = cameraPos.subtract(middle).normalize();
            Vec3 right = billboardRight(direction, toCamera, halfWidth);
            int frame = (baseFrame + (style.flow() ? i : 0)) % frames;
            quad(pose, consumer, previous, next, right, toCamera,
                    frame / (float) frames, (frame + 1) / (float) frames,
                    light(level, style, middle));
            previous = next;
        }

        buffers.endBatch(renderType);
        if (drawHead) {
            drawHead(poseStack, buffers, level, style, to, lastDirection, cameraPos,
                    baseFrame, frames, headTexture, widthPercent);
        }
    }

    public static void drawParticles(ClientLevel level, Vec3 from, Vec3 to,
                                     ParticleOptions particle) {
        Vec3 step = to.subtract(from);
        int points = Mth.clamp((int) (step.length() * SEGMENTS_PER_BLOCK), 1, MAX_SEGMENTS);
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(step.scale((double) i / points));
            level.addParticle(particle, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void clearWarnings() {
        WARNED_TEXTURES.clear();
    }

    private static void drawHead(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                 ClientLevel level, HookCordStyles.Style style, Vec3 tip,
                                 Vec3 direction, Vec3 cameraPos, int frame, int frames,
                                 ResourceLocation headTexture, int widthPercent) {
        double size = style.width() * Mth.clamp(widthPercent, 25, 400) / 100.0D * HEAD_SCALE;
        Vec3 tail = tip.subtract(direction.scale(size));
        Vec3 middle = tip.add(tail).scale(0.5D);
        Vec3 toCamera = cameraPos.subtract(middle).normalize();
        Vec3 right = billboardRight(direction, toCamera, size / 2.0D);

        RenderType renderType = style.translucent()
                ? RenderType.entityTranslucent(headTexture)
                : RenderType.entityCutoutNoCull(headTexture);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        quad(poseStack.last(), consumer, tail, tip, right, toCamera,
                frame / (float) frames, (frame + 1) / (float) frames, light(level, style, middle));
        buffers.endBatch(renderType);
    }

    private static Vec3 sagged(Vec3 from, Vec3 to, double t, double sag) {
        Vec3 straight = from.add(to.subtract(from).scale(t));
        return sag == 0.0D ? straight : straight.subtract(0.0D, sag * Math.sin(Math.PI * t), 0.0D);
    }

    private static Vec3 billboardRight(Vec3 direction, Vec3 toCamera, double halfWidth) {
        Vec3 right = direction.cross(toCamera);
        if (right.lengthSqr() < 1.0E-9D) {
            right = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-9D) {
                right = new Vec3(1.0D, 0.0D, 0.0D);
            }
        }
        return right.normalize().scale(halfWidth);
    }

    private static int light(ClientLevel level, HookCordStyles.Style style, Vec3 at) {
        return style.glowing()
                ? LightTexture.FULL_BRIGHT
                : LevelRenderer.getLightColor(level, BlockPos.containing(at));
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, Vec3 top, Vec3 bottom,
                             Vec3 right, Vec3 normal, float v0, float v1, int light) {
        vertex(pose, consumer, top.subtract(right), 0.0F, v0, normal, light);
        vertex(pose, consumer, bottom.subtract(right), 0.0F, v1, normal, light);
        vertex(pose, consumer, bottom.add(right), 1.0F, v1, normal, light);
        vertex(pose, consumer, top.add(right), 1.0F, v0, normal, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at, float u,
                               float v, Vec3 normal, int light) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static ResourceLocation texture(String styleId, String fileName) {
        return ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
                "textures/entity/hook/" + styleId + "/" + fileName);
    }
}
