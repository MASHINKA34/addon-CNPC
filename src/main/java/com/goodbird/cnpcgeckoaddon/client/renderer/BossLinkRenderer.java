package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.network.BossLinkClientBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Draws long-lived keyed boss links and expires stale server state locally. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossLinkRenderer {
    private static final List<Link> LINKS = new ArrayList<>();
    private static long lastFallbackTick = Long.MIN_VALUE;

    static {
        BossLinkClientBridge.setHandler(BossLinkRenderer::accept);
    }

    private BossLinkRenderer() {
    }

    private record Link(byte kind, int sourceId, int targetId, int channel,
                        HookCordStyles.Style style, long expiresAt, int widthPercent,
                        int sagPercent, boolean drawHead) {
    }

    public static void accept(byte kind, int sourceId, int targetId, int channel,
                              String styleId, int durationTicks, int widthPercent,
                              int sagPercent, boolean drawHead) {
        LINKS.removeIf(link -> link.kind == kind && link.sourceId == sourceId
                && link.targetId == targetId && link.channel == channel);
        ClientLevel level = Minecraft.getInstance().level;
        if (durationTicks <= 0 || level == null) {
            return;
        }
        LINKS.add(new Link(kind, sourceId, targetId, channel, HookCordStyles.get(styleId),
                level.getGameTime() + durationTicks, widthPercent, sagPercent, drawHead));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        LINKS.clear();
        AnimatedLinkRenderUtil.clearWarnings();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || LINKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            LINKS.clear();
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

        Iterator<Link> iterator = LINKS.iterator();
        while (iterator.hasNext()) {
            Link link = iterator.next();
            Entity source = level.getEntity(link.sourceId);
            Entity target = level.getEntity(link.targetId);
            if (gameTime >= link.expiresAt || source == null || target == null
                    || source.isRemoved() || target.isRemoved()) {
                iterator.remove();
                continue;
            }
            Vec3 from = source.getEyePosition(partialTick).subtract(0.0D, 0.1D, 0.0D);
            Vec3 to = target.getPosition(partialTick).add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
            if (to.distanceToSqr(from) < 1.0E-6D) {
                continue;
            }
            if (!AnimatedLinkRenderUtil.hasTextures(minecraft, link.style, link.drawHead)) {
                if (fallbackTick) {
                    AnimatedLinkRenderUtil.drawParticles(level, from, to, ParticleTypes.END_ROD);
                }
                continue;
            }
            AnimatedLinkRenderUtil.render(poseStack, buffers, level, link.style, from, to,
                    cameraPos, gameTime, link.widthPercent, link.sagPercent, link.drawHead);
        }
        poseStack.popPose();
    }
}
