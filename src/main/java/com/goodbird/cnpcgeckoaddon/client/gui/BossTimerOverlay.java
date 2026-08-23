package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.network.BossTimerClientBridge;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossTimer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The countdown strip drawn under a boss health bar.
 *
 * <p>Holds what the server last said about each bar and runs the clock forward itself
 * between packets, so the digits tick every second instead of jumping in five-tick steps.</p>
 *
 * <p>Styled bars are drawn from {@link CustomBossBarOverlay}, which owns their geometry;
 * plain ones are picked up by the event handler here. Either way the strip falls back to
 * flat rectangles when the artwork for a style is missing, so a boss stays playable.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossTimerOverlay {

    /** One bar's countdown as the client currently believes it. */
    public static final class TimerState {
        public int remainingTicks;
        public int totalTicks;
        public byte state;
        /** Client tick the last packet landed on, so the same tick is never counted twice. */
        public long lastPacketClientTick;
    }

    private static final int VANILLA_BAR_WIDTH = 182;
    private static final int VANILLA_BAR_HEIGHT = 5;
    /** Tall enough for the digits - the vanilla bar it hangs under is only five pixels. */
    private static final int PLAIN_TRACK_HEIGHT = 9;
    private static final int PLAIN_GAP = 2;
    private static final float SMALL_TEXT_SCALE = 0.5F;

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int FLAT_BORDER = 0xFF0B0B0B;
    private static final int FLAT_TRACK = 0xC0000000;
    /** RGB only - {@link #flatColor} puts the alpha on, because rage pulses. */
    private static final int FLAT_COUNTDOWN_RGB = 0xE8B23A;
    private static final int FLAT_RAGE_RGB = 0xE23A2E;
    private static final int FLAT_IMMUNE_RGB = 0x8FD4FF;

    private static final Map<UUID, TimerState> TIMERS = new HashMap<>();
    private static long clientTick;

    static {
        BossTimerClientBridge.setHandler(BossTimerOverlay::accept);
    }

    private BossTimerOverlay() {
    }

    /** @return the countdown to draw under this bar, or null when it has none */
    public static TimerState get(UUID eventId) {
        TimerState timer = TIMERS.get(eventId);
        return timer == null || timer.state == PacketSyncBossTimer.STATE_NONE ? null : timer;
    }

    private static void accept(UUID eventId, BossTimerClientBridge.Timer packet) {
        if (packet.state() == PacketSyncBossTimer.STATE_NONE) {
            TIMERS.remove(eventId);
            return;
        }
        TimerState timer = TIMERS.computeIfAbsent(eventId, id -> new TimerState());
        // Hard reset rather than a nudge: the server is the only authority on the clock, and
        // a client that drifted ahead has to snap back even if that means a visible jump.
        timer.remainingTicks = packet.remainingTicks();
        timer.totalTicks = packet.totalTicks();
        timer.state = packet.state();
        timer.lastPacketClientTick = clientTick;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        clientTick++;
        for (Iterator<TimerState> timers = TIMERS.values().iterator(); timers.hasNext(); ) {
            TimerState timer = timers.next();
            if (timer.state == PacketSyncBossTimer.STATE_NONE) {
                timers.remove();
            } else if (timer.lastPacketClientTick != clientTick && timer.remainingTicks > 0) {
                timer.remainingTicks--;
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TIMERS.clear();
        BossTimerClientBridge.clear();
    }

    /**
     * Hangs a flat countdown under a bar nobody restyled, so the mechanic is readable even
     * with the bar style left on {@code none}. The bar itself is left to vanilla.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void renderPlain(CustomizeGuiOverlayEvent.BossEventProgress event) {
        UUID eventId = event.getBossEvent().getId();
        if (CustomBossBarOverlay.isTracked(eventId)) {
            return;
        }
        TimerState timer = get(eventId);
        if (timer == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int trackX = event.getX();
        int trackY = event.getY() + VANILLA_BAR_HEIGHT + PLAIN_GAP;
        drawFlat(graphics, timer, trackX, trackY, VANILLA_BAR_WIDTH, PLAIN_TRACK_HEIGHT);
        drawLabel(graphics, timer, trackX, trackY, VANILLA_BAR_WIDTH, PLAIN_TRACK_HEIGHT);

        // Only the space actually added: the vanilla increment already covers the bar itself.
        event.setIncrement(event.getIncrement() + PLAIN_GAP + PLAIN_TRACK_HEIGHT);
    }

    /**
     * Draws the strip for a styled bar. {@code x}/{@code y} are its top-left corner, which
     * sits flush under the health bar and shares its width and scale.
     *
     * @return the height the strip took, or 0 when this bar has no countdown
     */
    public static int draw(GuiGraphics graphics, int x, int y, int renderWidth, float scale,
                           BossBarStyles.Style style, TimerState timer) {
        if (timer == null || timer.state == PacketSyncBossTimer.STATE_NONE) {
            return 0;
        }

        int height = Math.max(1, Math.round(style.timerHeight() * scale));
        int trackX = x + Math.round(style.timerTrackX() * scale);
        int trackY = y + Math.round(style.timerTrackY() * scale);
        int trackWidth = Math.max(1, Math.round(style.timerTrackWidth() * scale));
        int trackHeight = Math.max(1, Math.round(style.timerTrackHeight() * scale));

        if (!drawArtwork(graphics, timer, style, x, y, renderWidth, height,
                trackX, trackY, trackWidth, trackHeight)) {
            drawFlat(graphics, timer, trackX, trackY, trackWidth, trackHeight);
        }
        drawLabel(graphics, timer, trackX, trackY, trackWidth, trackHeight);
        return height;
    }

    /**
     * @return false when a style is missing any of its three timer textures, which is the
     *         normal state of a style whose artwork has not been drawn yet
     */
    private static boolean drawArtwork(GuiGraphics graphics, TimerState timer, BossBarStyles.Style style,
                                       int x, int y, int renderWidth, int height,
                                       int trackX, int trackY, int trackWidth, int trackHeight) {
        ResourceLocation background = texture(style.id(), "timer_background.png");
        ResourceLocation fill = texture(style.id(), "timer_fill.png");
        ResourceLocation frame = texture(style.id(), "timer_frame.png");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getResourceManager().getResource(background).isEmpty()
                || minecraft.getResourceManager().getResource(fill).isEmpty()
                || minecraft.getResourceManager().getResource(frame).isEmpty()) {
            return false;
        }

        TextureManager textures = minecraft.getTextureManager();
        textures.getTexture(background).setFilter(false, false);
        textures.getTexture(fill).setFilter(false, false);
        textures.getTexture(frame).setFilter(false, false);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blitLayer(graphics, background, x, y, renderWidth, height, style);

        float progress = progress(timer);
        int fillWidth = Mth.clamp(Math.round(trackWidth * progress), 0, trackWidth);
        int sourceWidth = Mth.clamp(Math.round(style.timerTrackWidth() * progress), 0, style.timerTrackWidth());
        if (fillWidth > 0 && sourceWidth > 0) {
            float[] tint = tint(timer);
            graphics.setColor(tint[0], tint[1], tint[2], tint[3]);
            graphics.blit(
                    fill,
                    trackX,
                    trackY,
                    fillWidth,
                    trackHeight,
                    style.timerTrackX(),
                    style.timerTrackY(),
                    sourceWidth,
                    style.timerTrackHeight(),
                    style.timerWidth(),
                    style.timerHeight()
            );
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        blitLayer(graphics, frame, x, y, renderWidth, height, style);
        RenderSystem.disableBlend();
        return true;
    }

    /** The strip without artwork: a dark track with a coloured bar inside it. */
    private static void drawFlat(GuiGraphics graphics, TimerState timer,
                                 int trackX, int trackY, int trackWidth, int trackHeight) {
        graphics.fill(trackX - 1, trackY - 1, trackX + trackWidth + 1, trackY + trackHeight + 1, FLAT_BORDER);
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, FLAT_TRACK);
        int fillWidth = Mth.clamp(Math.round(trackWidth * progress(timer)), 0, trackWidth);
        if (fillWidth > 0) {
            graphics.fill(trackX, trackY, trackX + fillWidth, trackY + trackHeight, flatColor(timer));
        }
    }

    /**
     * Centres the caption in the track, or shrinks it above the right end when the track is
     * too thin to hold a line of text - the infernal timer is three pixels tall.
     */
    private static void drawLabel(GuiGraphics graphics, TimerState timer,
                                  int trackX, int trackY, int trackWidth, int trackHeight) {
        Font font = Minecraft.getInstance().font;
        String text = caption(timer, font, trackWidth);
        if (trackHeight >= font.lineHeight) {
            int textX = trackX + (trackWidth - font.width(text)) / 2;
            int textY = trackY + (trackHeight - font.lineHeight) / 2;
            graphics.drawString(font, text, textX, textY, TEXT_COLOR, true);
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1.0F);
        int textX = Math.round((trackX + trackWidth) / SMALL_TEXT_SCALE) - font.width(text);
        int textY = Math.round((trackY - 1) / SMALL_TEXT_SCALE) - font.lineHeight;
        graphics.drawString(font, text, textX, textY, TEXT_COLOR, true);
        graphics.pose().popPose();
    }

    private static String caption(TimerState timer, Font font, int trackWidth) {
        if (timer.state == PacketSyncBossTimer.STATE_RAGE) {
            return I18n.get("cnpcgeckoaddon.boss.rage_active");
        }
        String clock = clock(timer.remainingTicks);
        if (timer.state != PacketSyncBossTimer.STATE_INVULNERABLE) {
            return clock;
        }
        // The remaining seconds are worth more than the word, so they are what survives a
        // track too narrow to hold both.
        String both = I18n.get("cnpcgeckoaddon.boss.invulnerable_active") + " " + clock;
        return font.width(both) <= trackWidth ? both : clock;
    }

    private static String clock(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static float progress(TimerState timer) {
        if (timer.state == PacketSyncBossTimer.STATE_RAGE) {
            return 1.0F;
        }
        return timer.totalTicks <= 0
                ? 0.0F
                : Mth.clamp(timer.remainingTicks / (float) timer.totalTicks, 0.0F, 1.0F);
    }

    /**
     * The shader colour the fill is multiplied by. The timer artwork is painted as a dark
     * neutral ramp - unlike the health fills, which carry their own colour - so the state is
     * what colours it, and channels above 1 are what stop the result coming out muddy.
     */
    private static float[] tint(TimerState timer) {
        return switch (timer.state) {
            case PacketSyncBossTimer.STATE_RAGE -> new float[]{1.60F, 0.45F, 0.35F, pulse()};
            case PacketSyncBossTimer.STATE_INVULNERABLE -> new float[]{0.95F, 1.35F, 1.60F, 1.0F};
            default -> new float[]{1.45F, 1.05F, 0.42F, 1.0F};
        };
    }

    private static int flatColor(TimerState timer) {
        int rgb = switch (timer.state) {
            case PacketSyncBossTimer.STATE_RAGE -> FLAT_RAGE_RGB;
            case PacketSyncBossTimer.STATE_INVULNERABLE -> FLAT_IMMUNE_RGB;
            default -> FLAT_COUNTDOWN_RGB;
        };
        float alpha = timer.state == PacketSyncBossTimer.STATE_RAGE ? pulse() : 1.0F;
        return (Math.round(alpha * 255.0F) << 24) | rgb;
    }

    /**
     * A rage bar breathes rather than blinks: about one and a half beats a second, never
     * fading far enough to leave the player unsure whether the bar is still there.
     */
    private static float pulse() {
        float phase = (System.currentTimeMillis() % 700L) / 700.0F;
        return 0.55F + 0.45F * Mth.sin(phase * Mth.PI);
    }

    private static void blitLayer(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                  int width, int height, BossBarStyles.Style style) {
        graphics.blit(texture, x, y, width, height, 0.0F, 0.0F,
                style.timerWidth(), style.timerHeight(), style.timerWidth(), style.timerHeight());
    }

    private static ResourceLocation texture(String styleId, String fileName) {
        return ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID,
                "textures/gui/boss_bar/" + styleId + "/" + fileName);
    }
}
