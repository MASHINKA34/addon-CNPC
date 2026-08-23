package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
 * Replaces BLUE vanilla/CustomNPC boss bars with the high-resolution sculk bar.
 *
 * <p>The artwork is rendered at its native 256x44 GUI-pixel size. The base contains the
 * empty channel and ornamental frame, while the fill is clipped to the live boss health.
 * Keeping those textures separate prevents either end sensor from being stretched or cut.</p>
 */
public final class SculkBossBarOverlay {
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_sculk_base.png");
    private static final ResourceLocation FILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_sculk_fill.png");
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CNPCGeckoAddon.MODID, "textures/gui/boss_bar_sculk_overlay.png");

    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 44;
    private static final int VANILLA_BAR_WIDTH = 182;

    private static final int INNER_X = 29;
    private static final int INNER_Y = 14;
    private static final int INNER_WIDTH = 198;
    private static final int INNER_HEIGHT = 18;
    private static final int NEXT_BAR_SPACING = TEXTURE_HEIGHT + 4;

    private SculkBossBarOverlay() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderSculkBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!CustomBossBarOverlay.isTracked(event.getBossEvent().getId())) {
            return;
        }
        if (event.getBossEvent().getColor() != BossEvent.BossBarColor.BLUE) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft minecraft = Minecraft.getInstance();
        int x = event.getX() - (TEXTURE_WIDTH - VANILLA_BAR_WIDTH) / 2;
        int y = event.getY();

        // PNG GUI textures are normally nearest-filtered already. Setting it explicitly
        // protects the pixel grid from resource packs or a previous texture-state change.
        TextureManager textures = minecraft.getTextureManager();
        textures.getTexture(BASE_TEXTURE).setFilter(false, false);
        textures.getTexture(FILL_TEXTURE).setFilter(false, false);
        textures.getTexture(OVERLAY_TEXTURE).setFilter(false, false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.blit(BASE_TEXTURE, x, y, 0.0F, 0.0F,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        int fillWidth = Mth.clamp(
                Math.round(event.getBossEvent().getProgress() * INNER_WIDTH),
                0,
                INNER_WIDTH);
        if (fillWidth > 0) {
            graphics.blit(FILL_TEXTURE, x + INNER_X, y + INNER_Y,
                    (float) INNER_X, (float) INNER_Y,
                    fillWidth, INNER_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }

        graphics.blit(OVERLAY_TEXTURE, x, y, 0.0F, 0.0F,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        Font font = minecraft.font;
        int nameX = graphics.guiWidth() / 2 - font.width(event.getBossEvent().getName()) / 2;
        int nameY = y + INNER_Y + (INNER_HEIGHT - font.lineHeight) / 2;
        graphics.drawString(font, event.getBossEvent().getName(), nameX, nameY, 0xE8FFFF, true);

        RenderSystem.disableBlend();

        event.setIncrement(NEXT_BAR_SPACING);
        event.setCanceled(true);
    }
}
