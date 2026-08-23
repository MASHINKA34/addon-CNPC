package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Replaces the flat vanilla red boss bar with the detailed infernal HUD sprite.
 *
 * <p>CustomNPCs exposes its selected boss-bar color through the vanilla boss event,
 * so RED is the opt-in style selector and other boss-bar colors remain untouched.</p>
 */
public final class InfernalBossBarOverlay {
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_infernal_frame.png");
    private static final ResourceLocation FILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_infernal_fill.png");

    private static final int VANILLA_BAR_WIDTH = 182;
    private static final int TEXTURE_WIDTH = 364;
    private static final int TEXTURE_HEIGHT = 77;
    private static final int TRACK_X = 34;
    private static final int TRACK_Y = 27;
    private static final int TRACK_WIDTH = 296;
    private static final int TRACK_HEIGHT = 24;

    private InfernalBossBarOverlay() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderInfernalBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!CustomBossBarOverlay.isTracked(event.getBossEvent().getId())) {
            return;
        }
        if (event.getBossEvent().getColor() != BossEvent.BossBarColor.RED) {
            return;
        }

        event.setCanceled(true);

        GuiGraphics graphics = event.getGuiGraphics();
        int renderWidth = Math.min(TEXTURE_WIDTH, Math.max(1, graphics.guiWidth() - 8));
        float scale = renderWidth / (float) TEXTURE_WIDTH;
        int renderHeight = Math.max(1, Math.round(TEXTURE_HEIGHT * scale));

        // NeoForge reports the left edge of the 182 px vanilla bar. Preserve its center.
        int centerX = event.getX() + VANILLA_BAR_WIDTH / 2;
        int x = centerX - renderWidth / 2;
        int y = Math.max(2, event.getY() - 8);
        event.setIncrement(renderHeight + 4);

        // GUI textures normally use nearest-neighbor sampling. Enforce it here as
        // well so a resource pack or an earlier render cannot blur the pixel grid.
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        textures.getTexture(FRAME_TEXTURE).setFilter(false, false);
        textures.getTexture(FILL_TEXTURE).setFilter(false, false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                FRAME_TEXTURE,
                x,
                y,
                renderWidth,
                renderHeight,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);

        int trackX = x + Math.round(TRACK_X * scale);
        int trackY = y + Math.round(TRACK_Y * scale);
        int trackWidth = Math.max(1, Math.round(TRACK_WIDTH * scale));
        int trackHeight = Math.max(1, Math.round(TRACK_HEIGHT * scale));
        int healthWidth = Mth.clamp(
                Math.round(trackWidth * event.getBossEvent().getProgress()),
                0,
                trackWidth);
        if (healthWidth > 0) {
            // The complete red-to-yellow pattern is fitted to current health so the hot
            // yellow leading edge remains visible at every non-zero health percentage.
            graphics.blit(
                    FILL_TEXTURE,
                    trackX,
                    trackY,
                    healthWidth,
                    trackHeight,
                    0.0F,
                    0.0F,
                    TRACK_WIDTH,
                    TRACK_HEIGHT,
                    TRACK_WIDTH,
                    TRACK_HEIGHT);
        }
        RenderSystem.disableBlend();

        Minecraft minecraft = Minecraft.getInstance();
        int textY = y
                + Math.round((TRACK_Y + TRACK_HEIGHT / 2.0F) * scale)
                - minecraft.font.lineHeight / 2;
        graphics.drawCenteredString(
                minecraft.font,
                event.getBossEvent().getName(),
                centerX,
                textY,
                0xFFFFFF);
    }
}
