package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.network.HookCordClientBridge;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Draws the textured cord between a boss and everyone its hook is dragging. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class HookCordRenderer {
    private static final Logger LOGGER = LogManager.getLogger("cnpcgeckoaddon");
    /** Matches the spacing the server's spark trail has always used. */
    private static final double SEGMENTS_PER_BLOCK = 2.0D;
    private static final int MIN_SEGMENTS = 2;
    private static final int MAX_SEGMENTS = 64;
    /** The head is drawn a little heavier than the cord so it reads as a tip, not a knot. */
    private static final double HEAD_SCALE = 2.0D;

    private static final List<Cord> CORDS = new ArrayList<>();
    private static final Set<String> WARNED_STYLES = new HashSet<>();
    /** Sparks belong to a tick, not a frame, so the fallback only emits when the tick turns. */
    private static long lastFallbackTick = Long.MIN_VALUE;

    static {
        HookCordClientBridge.setHandler(HookCordRenderer::accept);
    }

    private HookCordRenderer() {
    }

    private static final class Cord {
        private final int bossId;
        private final int victimId;
        private final HookCordStyles.Style style;
        private final long expiresAt;

        private Cord(int bossId, int victimId, HookCordStyles.Style style, long expiresAt) {
            this.bossId = bossId;
            this.victimId = victimId;
            this.style = style;
            this.expiresAt = expiresAt;
        }
    }

    public static void accept(int bossId, int victimId, String styleId, int durationTicks) {
        // Re-hooking the same victim replaces the cord rather than stacking a second one.
        CORDS.removeIf(cord -> cord.bossId == bossId && cord.victimId == victimId);
        ClientLevel level = Minecraft.getInstance().level;
        if (durationTicks <= 0 || level == null || !HookCordStyles.isTextured(styleId)) {
            return;
        }
        CORDS.add(new Cord(bossId, victimId, HookCordStyles.get(styleId),
                level.getGameTime() + durationTicks));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        CORDS.clear();
        WARNED_STYLES.clear();
    }

    /**
     * Every style is drawn in one stage on purpose. A translucent cord is just a translucent
     * entity part, and AFTER_ENTITIES is where vanilla draws those; AFTER_TRANSLUCENT_BLOCKS
     * is documented as unreliable for translucency, so splitting the styles across the two
     * would buy nothing and sort worse.
     */
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CORDS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            CORDS.clear();
            return;
        }

        long gameTime = level.getGameTime();
        boolean fallbackTick = gameTime != lastFallbackTick;
        lastFallbackTick = gameTime;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Iterator<Cord> iterator = CORDS.iterator();
        while (iterator.hasNext()) {
            Cord cord = iterator.next();
            Entity boss = level.getEntity(cord.bossId);
            Entity victim = level.getEntity(cord.victimId);
            if (gameTime >= cord.expiresAt || boss == null || victim == null) {
                iterator.remove();
                continue;
            }

            // Both ends are interpolated, or the cord would snap between tick positions while
            // the boss and the victim themselves move smoothly.
            Vec3 from = boss.getEyePosition(partialTick).subtract(0.0D, 0.2D, 0.0D);
            Vec3 to = victim.getPosition(partialTick).add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
            if (to.distanceToSqr(from) < 1.0E-6D) {
                continue;
            }

            ResourceLocation cordTexture = texture(cord.style.id(), "cord.png");
            ResourceLocation headTexture = texture(cord.style.id(), "head.png");
            if (minecraft.getResourceManager().getResource(cordTexture).isEmpty()
                    || minecraft.getResourceManager().getResource(headTexture).isEmpty()) {
                warnOnce(cord.style.id());
                if (fallbackTick) {
                    drawSparks(level, from, to);
                }
                continue;
            }
            drawCord(poseStack, buffers, level, cord.style, from, to, cameraPos, gameTime,
                    cordTexture, headTexture);
        }
        poseStack.popPose();
    }

    private static void drawCord(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                 ClientLevel level, HookCordStyles.Style style, Vec3 from, Vec3 to,
                                 Vec3 cameraPos, long gameTime, ResourceLocation cordTexture,
                                 ResourceLocation headTexture) {
        double length = to.distanceTo(from);
        int segments = Mth.clamp((int) (length * SEGMENTS_PER_BLOCK), MIN_SEGMENTS, MAX_SEGMENTS);
        int frames = Math.max(1, style.frames());
        int baseFrame = Math.floorMod(gameTime / Math.max(1, style.frameTicks()), frames);
        double halfWidth = style.width() / 2.0D;
        double sag = style.sag() * length;

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
            // Flowing styles walk the frame along the cord as well, so the pattern crawls
            // toward the victim instead of blinking on the spot.
            int frame = (baseFrame + (style.flow() ? i : 0)) % frames;
            quad(pose, consumer, previous, next, right, toCamera,
                    frame / (float) frames, (frame + 1) / (float) frames,
                    light(level, style, middle));
            previous = next;
        }

        // The vanilla entity batches were flushed before this stage, so ours has to be too.
        buffers.endBatch(renderType);
        drawHead(poseStack, buffers, level, style, to, lastDirection, cameraPos, baseFrame,
                frames, headTexture);
    }

    private static void drawHead(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                 ClientLevel level, HookCordStyles.Style style, Vec3 tip,
                                 Vec3 direction, Vec3 cameraPos, int frame, int frames,
                                 ResourceLocation headTexture) {
        double size = style.width() * HEAD_SCALE;
        Vec3 tail = tip.subtract(direction.scale(size));
        Vec3 middle = tip.add(tail).scale(0.5D);
        Vec3 toCamera = cameraPos.subtract(middle).normalize();
        Vec3 right = billboardRight(direction, toCamera, size / 2.0D);

        RenderType renderType = style.translucent()
                ? RenderType.entityTranslucent(headTexture)
                : RenderType.entityCutoutNoCull(headTexture);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        // The frame runs down the strip, so the tail takes its top edge and the point its
        // bottom - that is the way round the artwork is drawn.
        quad(poseStack.last(), consumer, tail, tip, right, toCamera,
                frame / (float) frames, (frame + 1) / (float) frames, light(level, style, middle));
        buffers.endBatch(renderType);
    }

    /** A point along the cord, dipped toward the middle so it hangs instead of ruling a line. */
    private static Vec3 sagged(Vec3 from, Vec3 to, double t, double sag) {
        Vec3 straight = from.add(to.subtract(from).scale(t));
        return sag == 0.0D ? straight : straight.subtract(0.0D, sag * Math.sin(Math.PI * t), 0.0D);
    }

    /**
     * The half-width offset that turns a segment into a ribbon facing the camera. Looking
     * straight down the cord leaves the cross product undefined, so it falls back to any
     * perpendicular rather than collapsing the quad into nothing.
     */
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

    /** What the cord looks like before its artwork ships: the same trail the server draws. */
    private static void drawSparks(ClientLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = Mth.clamp((int) (step.length() * SEGMENTS_PER_BLOCK), 1, MAX_SEGMENTS);
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(step.scale((double) i / points));
            level.addParticle(ParticleTypes.CRIT, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void warnOnce(String styleId) {
        if (WARNED_STYLES.add(styleId)) {
            LOGGER.warn("No hook cord textures for style '{}', falling back to particles", styleId);
        }
    }

    private static ResourceLocation texture(String styleId, String fileName) {
        return ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
                "textures/entity/hook/" + styleId + "/" + fileName);
    }
}
