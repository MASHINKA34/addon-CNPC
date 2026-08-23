package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.network.BossBarStyleClientBridge;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class CustomBossBarOverlay {
    private static final int VANILLA_WIDTH = 182;
    private static final Map<UUID, String> STYLES = new HashMap<>();

    static {
        BossBarStyleClientBridge.setHandler(CustomBossBarOverlay::updateStyle);
    }

    private CustomBossBarOverlay() {
    }

    public static void updateStyle(UUID eventId, String styleId) {
        String normalized = BossBarStyles.normalize(styleId);
        if (BossBarStyles.NONE.equals(normalized)) {
            STYLES.remove(eventId);
        } else {
            STYLES.put(eventId, normalized);
        }
    }

    public static boolean isTracked(UUID eventId) {
        return STYLES.containsKey(eventId);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        String styleId = STYLES.get(event.getBossEvent().getId());
        if (styleId == null) {
            return;
        }

        BossBarStyles.Style style = BossBarStyles.get(styleId);
        ResourceLocation background = texture(style.id(), "background.png");
        ResourceLocation fill = texture(style.id(), "fill.png");
        ResourceLocation frame = texture(style.id(), "frame.png");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getResourceManager().getResource(background).isEmpty()
                || minecraft.getResourceManager().getResource(fill).isEmpty()
                || minecraft.getResourceManager().getResource(frame).isEmpty()) {
            event.setIncrement(0);
            event.setCanceled(true);
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int renderWidth = Math.min(style.preferredWidth(), Math.max(1, graphics.guiWidth() - 8));
        float scale = renderWidth / (float) style.textureWidth();
        int renderHeight = Math.max(1, Math.round(style.textureHeight() * scale));
        int centerX = event.getX() + VANILLA_WIDTH / 2;
        int barX = Mth.clamp(centerX - renderWidth / 2, 4, graphics.guiWidth() - renderWidth - 4);
        int barY = event.getY();
        int trackX = barX + Math.round(style.trackX() * scale);
        int trackY = barY + Math.round(style.trackY() * scale);
        int trackWidth = Math.max(1, Math.round(style.trackWidth() * scale));
        int trackHeight = Math.max(1, Math.round(style.trackHeight() * scale));

        TextureManager textures = minecraft.getTextureManager();
        textures.getTexture(background).setFilter(false, false);
        textures.getTexture(fill).setFilter(false, false);
        textures.getTexture(frame).setFilter(false, false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blitLayer(graphics, background, barX, barY, renderWidth, renderHeight, style);

        float progress = Mth.clamp(event.getBossEvent().getProgress(), 0.0F, 1.0F);
        int fillWidth = Mth.clamp(Math.round(trackWidth * progress), 0, trackWidth);
        int sourceWidth = Mth.clamp(Math.round(style.trackWidth() * progress), 0, style.trackWidth());
        if (fillWidth > 0 && sourceWidth > 0) {
            graphics.blit(
                    fill,
                    trackX,
                    trackY,
                    fillWidth,
                    trackHeight,
                    style.trackX(),
                    style.trackY(),
                    sourceWidth,
                    style.trackHeight(),
                    style.textureWidth(),
                    style.textureHeight()
            );
        }

        blitLayer(graphics, frame, barX, barY, renderWidth, renderHeight, style);
        RenderSystem.disableBlend();

        Font font = minecraft.font;
        String name = fitName(font, event.getBossEvent().getName().getString(), Math.max(1, trackWidth - 8));
        int textWidth = font.width(name);
        int textX = trackX + (trackWidth - textWidth) / 2;
        int textY = trackY + (trackHeight - font.lineHeight) / 2;
        graphics.drawString(font, name, textX, textY, 0xFFFFFF, true);

        event.setIncrement(renderHeight + 4);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        STYLES.clear();
    }

    private static void blitLayer(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                  int width, int height, BossBarStyles.Style style) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F,
                style.textureWidth(), style.textureHeight(), style.textureWidth(), style.textureHeight());
    }

    private static ResourceLocation texture(String styleId, String fileName) {
        return ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
                "textures/gui/boss_bar/" + styleId + "/" + fileName);
    }

    private static String fitName(Font font, String name, int maxWidth) {
        if (font.width(name) <= maxWidth) {
            return name;
        }
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return font.plainSubstrByWidth(ellipsis, maxWidth);
        }
        return font.plainSubstrByWidth(name, maxWidth - ellipsisWidth) + ellipsis;
    }
}
