package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** A local, short-lived world outline for the administrator editing an aggro zone. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossAggroZonePreview {
    private static final int PREVIEW_TICKS = 200;
    private static final double CORNER_MARKER_INSET = 0.3D;

    private static ClientLevel previewLevel;
    private static AABB bounds;
    private static BlockPos corner1;
    private static BlockPos corner2;
    private static long expiresAt;
    private static boolean valid;

    private BossAggroZonePreview() {
    }

    public static void show(TeleportPathData data) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        previewLevel = level;
        corner1 = new BlockPos(data.getAggroZoneX1(), data.getAggroZoneY1(), data.getAggroZoneZ1());
        corner2 = new BlockPos(data.getAggroZoneX2(), data.getAggroZoneY2(), data.getAggroZoneZ2());

        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.max(Math.min(corner1.getY(), corner2.getY()), level.getMinBuildHeight());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.min(Math.max(corner1.getY(), corner2.getY()), level.getMaxBuildHeight() - 1);
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        valid = minY <= maxY;
        if (valid) {
            bounds = inclusiveBlockBox(minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            // An invalid vertical volume is still shown in red so the editor can find and
            // replace stale coordinates copied from a different dimension.
            bounds = inclusiveBlockBox(minX, Math.min(corner1.getY(), corner2.getY()), minZ,
                    maxX, Math.max(corner1.getY(), corner2.getY()), maxZ);
        }
        expiresAt = level.getGameTime() + PREVIEW_TICKS;
    }

    private static AABB inclusiveBlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new AABB(minX, minY, minZ, (double) maxX + 1.0D,
                (double) maxY + 1.0D, (double) maxZ + 1.0D);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || bounds == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        // The level object changes on a dimension transfer, which prevents old absolute
        // coordinates from leaking into the next world's render.
        if (level == null || level != previewLevel || level.getGameTime() >= expiresAt) {
            clear();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType lines = RenderType.lines();
        VertexConsumer consumer = buffers.getBuffer(lines);

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poseStack, consumer, bounds,
                valid ? 0.15F : 1.0F, valid ? 1.0F : 0.15F, 0.15F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, consumer, marker(corner1), 1.0F, 0.35F, 0.1F, 1.0F);
        LevelRenderer.renderLineBox(poseStack, consumer, marker(corner2), 0.15F, 0.45F, 1.0F, 1.0F);
        poseStack.popPose();

        // This batch belongs solely to the preview; flushing it prevents line vertices from
        // surviving into another world render stage.
        buffers.endBatch(lines);
    }

    private static AABB marker(BlockPos pos) {
        return new AABB(pos.getX() + CORNER_MARKER_INSET, pos.getY() + CORNER_MARKER_INSET,
                pos.getZ() + CORNER_MARKER_INSET, pos.getX() + 1.0D - CORNER_MARKER_INSET,
                pos.getY() + 1.0D - CORNER_MARKER_INSET, pos.getZ() + 1.0D - CORNER_MARKER_INSET);
    }

    private static void clear() {
        previewLevel = null;
        bounds = null;
        corner1 = null;
        corner2 = null;
        expiresAt = 0L;
        valid = false;
    }
}
