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
 * Renders green boss events with the moss-cave frame instead of stretching the
 * 260x37 artwork into vanilla's 182x5 strip.
 */
public final class MossCaveBossBarOverlay {
    private static final int TEXTURE_WIDTH = 260;
    private static final int TEXTURE_HEIGHT = 37;
    private static final int INTERIOR_X = 31;
    private static final int INTERIOR_Y = 11;
    private static final int INTERIOR_WIDTH = 200;
    private static final int INTERIOR_HEIGHT = 14;
    private static final int EDGE_WIDTH = 7;
    private static final int EDGE_CENTER = 4;
    private static final int VERTICAL_OFFSET = -5;
    private static final int NAME_Y = 14;

    private static final ResourceLocation EMPTY = texture("boss_bar_moss_cave_empty.png");
    private static final ResourceLocation PROGRESS = texture("boss_bar_moss_cave_progress.png");
    private static final ResourceLocation EDGE = texture("boss_bar_moss_cave_edge.png");
    private static final ResourceLocation FRAME = texture("boss_bar_moss_cave_frame.png");

    private MossCaveBossBarOverlay() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossBar(final CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!CustomBossBarOverlay.isTracked(event.getBossEvent().getId())) {
            return;
        }
        if (event.getBossEvent().getColor() != BossEvent.BossBarColor.GREEN) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        int drawX = graphics.guiWidth() / 2 - TEXTURE_WIDTH / 2;
        int drawY = Math.max(0, event.getY() + VERTICAL_OFFSET);
        int filledWidth = Mth.clamp(
                Math.round(event.getBossEvent().getProgress() * INTERIOR_WIDTH),
                0,
                INTERIOR_WIDTH
        );

        TextureManager textures = minecraft.getTextureManager();
        textures.getTexture(EMPTY).setFilter(false, false);
        textures.getTexture(PROGRESS).setFilter(false, false);
        textures.getTexture(EDGE).setFilter(false, false);
        textures.getTexture(FRAME).setFilter(false, false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        blitWhole(graphics, EMPTY, drawX, drawY);
        if (filledWidth > 0) {
            graphics.blit(
                    PROGRESS,
                    drawX + INTERIOR_X,
                    drawY + INTERIOR_Y,
                    INTERIOR_X,
                    INTERIOR_Y,
                    filledWidth,
                    INTERIOR_HEIGHT,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT
            );
        }
        if (filledWidth > EDGE_CENTER && filledWidth < INTERIOR_WIDTH - EDGE_CENTER) {
            graphics.blit(
                    EDGE,
                    drawX + INTERIOR_X + filledWidth - EDGE_CENTER,
                    drawY + INTERIOR_Y,
                    0,
                    0,
                    EDGE_WIDTH,
                    INTERIOR_HEIGHT,
                    EDGE_WIDTH,
                    INTERIOR_HEIGHT
            );
        }
        blitWhole(graphics, FRAME, drawX, drawY);

        graphics.drawCenteredString(
                minecraft.font,
                event.getBossEvent().getName(),
                graphics.guiWidth() / 2,
                drawY + NAME_Y,
                0xE9FFE9
        );

        RenderSystem.disableBlend();
        event.setIncrement(TEXTURE_HEIGHT + 4);
        event.setCanceled(true);
    }

    private static void blitWhole(
            final GuiGraphics graphics,
            final ResourceLocation texture,
            final int x,
            final int y
    ) {
        graphics.blit(
                texture,
                x,
                y,
                0,
                0,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
    }

    private static ResourceLocation texture(final String fileName) {
        return ResourceLocation.fromNamespaceAndPath(
                CNPCGeckoAddon.MODID,
                "textures/gui/" + fileName
        );
    }
}
