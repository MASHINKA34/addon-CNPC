package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import noppes.npcs.entity.EntityNPCInterface;

/** Draws CustomNPC boss bars with the high-detail ghost-dungeon skin. */
public final class GhostDungeonBossBarOverlay {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_ghost_dungeon.png");
    private static final ResourceLocation FILL = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_ghost_dungeon_fill.png");
    private static final ResourceLocation FILL_CAP = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_ghost_dungeon_fill_cap.png");

    private static final int TEXTURE_WIDTH = 1329;
    private static final int TEXTURE_HEIGHT = 261;
    private static final int PREFERRED_WIDTH = TEXTURE_WIDTH / 3;

    private static final int LANE_TEXTURE_X = 104;
    private static final int LANE_TEXTURE_Y = 108;
    private static final int LANE_TEXTURE_WIDTH = 1121;
    private static final int LANE_TEXTURE_HEIGHT = 105;
    private static final int CAP_TEXTURE_WIDTH = 90;
    private static final int NAME_TEXTURE_CENTER_Y = 160;

    private GhostDungeonBossBarOverlay() {
    }

    @SubscribeEvent
    public static void renderGhostDungeonBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bossEvent = event.getBossEvent();
        if (!CustomBossBarOverlay.isTracked(bossEvent.getId())) {
            return;
        }
        if (!belongsToCustomNpc(bossEvent)) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int targetWidth = Math.min(PREFERRED_WIDTH, Math.max(1, graphics.guiWidth() - 8));
        float scale = targetWidth / (float) TEXTURE_WIDTH;
        int targetHeight = Math.max(1, Math.round(TEXTURE_HEIGHT * scale));
        int renderX = (graphics.guiWidth() - targetWidth) / 2;
        int renderY = Math.max(0, event.getY() - 12);

        int laneX = renderX + Math.round(LANE_TEXTURE_X * scale);
        int laneY = renderY + Math.round(LANE_TEXTURE_Y * scale);
        int laneWidth = Math.max(1, Math.round(LANE_TEXTURE_WIDTH * scale));
        int laneHeight = Math.max(1, Math.round(LANE_TEXTURE_HEIGHT * scale));
        int fillWidth = Mth.clamp(Math.round(laneWidth * bossEvent.getProgress()), 0, laneWidth);
        int capWidth = Math.max(1, Math.round(CAP_TEXTURE_WIDTH * scale));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                FRAME,
                renderX,
                renderY,
                targetWidth,
                targetHeight,
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);

        drawHealth(graphics, laneX, laneY, laneWidth, laneHeight, fillWidth, capWidth);
        RenderSystem.disableBlend();

        drawBossName(graphics, bossEvent, renderX, renderY, targetWidth, scale);
        event.setIncrement(targetHeight + 4);
        event.setCanceled(true);
    }

    private static void drawHealth(
            GuiGraphics graphics,
            int laneX,
            int laneY,
            int laneWidth,
            int laneHeight,
            int fillWidth,
            int capWidth) {
        if (fillWidth <= 0) {
            return;
        }

        if (fillWidth <= capWidth) {
            int sourceWidth = Math.max(1, Math.round(LANE_TEXTURE_WIDTH * (fillWidth / (float) laneWidth)));
            graphics.blit(
                    FILL,
                    laneX,
                    laneY,
                    fillWidth,
                    laneHeight,
                    0.0F,
                    0.0F,
                    sourceWidth,
                    LANE_TEXTURE_HEIGHT,
                    LANE_TEXTURE_WIDTH,
                    LANE_TEXTURE_HEIGHT);
            return;
        }

        int bodyWidth = fillWidth - capWidth + Math.max(1, Math.round(4.0F * laneWidth / LANE_TEXTURE_WIDTH));
        int sourceBodyWidth = Math.max(1, Math.round(LANE_TEXTURE_WIDTH * (bodyWidth / (float) laneWidth)));
        graphics.blit(
                FILL,
                laneX,
                laneY,
                bodyWidth,
                laneHeight,
                0.0F,
                0.0F,
                sourceBodyWidth,
                LANE_TEXTURE_HEIGHT,
                LANE_TEXTURE_WIDTH,
                LANE_TEXTURE_HEIGHT);
        graphics.blit(
                FILL_CAP,
                laneX + fillWidth - capWidth,
                laneY,
                capWidth,
                laneHeight,
                0.0F,
                0.0F,
                CAP_TEXTURE_WIDTH,
                LANE_TEXTURE_HEIGHT,
                CAP_TEXTURE_WIDTH,
                LANE_TEXTURE_HEIGHT);
    }

    private static void drawBossName(
            GuiGraphics graphics,
            LerpingBossEvent bossEvent,
            int renderX,
            int renderY,
            int renderWidth,
            float scale) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int nameWidth = font.width(bossEvent.getName());
        int nameX = renderX + (renderWidth - nameWidth) / 2;
        int nameY = renderY + Math.round(NAME_TEXTURE_CENTER_Y * scale) - font.lineHeight / 2;
        graphics.drawString(font, bossEvent.getName(), nameX, nameY, 0xD8F9FF, true);
    }

    private static boolean belongsToCustomNpc(LerpingBossEvent bossEvent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }

        String bossName = bossEvent.getName().getString();
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof EntityNPCInterface npc
                    && npc.display.getBossbar() > 0
                    && npc.getDisplayName().getString().equals(bossName)) {
                return true;
            }
        }
        return false;
    }
}
