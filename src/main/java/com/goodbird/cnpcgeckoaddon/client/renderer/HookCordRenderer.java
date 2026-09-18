package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.network.HookCordClientBridge;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
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

/** Draws hook cords through the same filmstrip geometry used by persistent boss links. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class HookCordRenderer {
    private static final List<Cord> CORDS = new ArrayList<>();
    private static long lastFallbackTick = Long.MIN_VALUE;

    static {
        HookCordClientBridge.setHandler(HookCordRenderer::accept);
    }

    private HookCordRenderer() {
    }

    private record Cord(int bossId, int victimId, HookCordStyles.Style style, long expiresAt) {
    }

    public static void accept(int bossId, int victimId, String styleId, int durationTicks) {
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
        EventGuard.handle("client.hook_cord.logout", event, HookCordRenderer::handleLogout);
    }

    private static void handleLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CORDS.clear();
        AnimatedLinkRenderUtil.clearWarnings();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        EventGuard.handle("client.hook_cord.render", event, HookCordRenderer::handleRender);
    }

    private static void handleRender(RenderLevelStageEvent event) {
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
        // Popped in a finally: the level renderer throws on a pose stack left unbalanced, so a
        // draw that fails behind the guard must still hand the stack back the way it got it.
        try {
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
                Vec3 from = boss.getEyePosition(partialTick).subtract(0.0D, 0.2D, 0.0D);
                Vec3 to = victim.getPosition(partialTick).add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
                if (to.distanceToSqr(from) < 1.0E-6D) {
                    continue;
                }
                if (!AnimatedLinkRenderUtil.hasTextures(minecraft, cord.style, true)) {
                    if (fallbackTick) {
                        AnimatedLinkRenderUtil.drawParticles(level, from, to, ParticleTypes.CRIT);
                    }
                    continue;
                }
                // These are the old renderer's exact effective settings.
                AnimatedLinkRenderUtil.render(poseStack, buffers, level, cord.style, from, to,
                        cameraPos, gameTime, 100, 100, true);
            }
        } finally {
            poseStack.popPose();
        }
    }
}
