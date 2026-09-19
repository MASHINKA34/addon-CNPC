package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossRiftDimension;
import com.goodbird.cnpcgeckoaddon.network.BossRiftClientBridge;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * How a reality rift looks from inside: the screen washed in the rift's colour, pulsing if the
 * phase says so, and the fog closing in in a colour of its own. The sky with nothing in it is
 * {@link BossRiftSkyEffects}, registered with the renderers on the mod bus.
 *
 * <p>Only while the server says a rift holds this player and the player really is in the rift
 * dimension: a packet that never came - a lost connection, a crash on the way out - leaves nothing
 * on the screen once the player is anywhere else. Logging out forgets it all.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossRiftClient {

    /** How far the pulse dips below the tint's own strength, and rises back to it. */
    private static final float PULSE_DEPTH = 0.35F;
    /** Where the fog starts, as a share of where it ends: a clear patch round the player. */
    private static final float FOG_NEAR_SHARE = 0.2F;

    private static State state;

    static {
        BossRiftClientBridge.setHandler(BossRiftClient::accept);
    }

    private BossRiftClient() {
    }

    private record State(int tintColor, int tintAlpha, int pulseTicks, int fogColor, int fogDistance, long endsAt) {
    }

    private static void accept(boolean active, int tintColor, int tintAlpha, int pulseTicks, int fogColor,
                               int fogDistance, long endsAt) {
        state = active ? new State(tintColor, tintAlpha, pulseTicks, fogColor, fogDistance, endsAt) : null;
    }

    /** The state to draw, or null: none held, or the player is not in the rift dimension. */
    private static State shown() {
        State current = state;
        if (current == null) {
            return null;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !BossRiftDimension.KEY.equals(level.dimension())) {
            // Out of the rift is out of it, whatever packet did not arrive.
            state = null;
            return null;
        }
        return current;
    }

    /** The tint, laid over the world right after the camera's own overlays and under the rest of the HUD. */
    @SubscribeEvent
    public static void renderTint(final RenderGuiLayerEvent.Post event) {
        EventGuard.handle("client.rift.render_tint", event, BossRiftClient::handleRenderTint);
    }

    private static void handleRenderTint(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CAMERA_OVERLAYS.equals(event.getName())) {
            return;
        }
        State current = shown();
        if (current == null || current.tintAlpha() <= 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int alpha = Mth.clamp(Math.round(alphaAt(current, time) * 255.0F), 0, 255);
        if (alpha <= 0) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | current.tintColor() & 0xFFFFFF);
    }

    /** The tint's strength at this moment: the phase's own, or a slow swell under it on the pulse. */
    private static float alphaAt(State current, float time) {
        float full = Mth.clamp(current.tintAlpha(), 0, 100) / 100.0F;
        if (current.pulseTicks() <= 0) {
            return full;
        }
        float wave = 0.5F + 0.5F * Mth.sin(time * Mth.TWO_PI / current.pulseTicks());
        return full * (1.0F - PULSE_DEPTH * (1.0F - wave));
    }

    @SubscribeEvent
    public static void fogColor(final ViewportEvent.ComputeFogColor event) {
        EventGuard.handle("client.rift.fog_color", event, BossRiftClient::handleFogColor);
    }

    private static void handleFogColor(ViewportEvent.ComputeFogColor event) {
        State current = shown();
        if (current == null) {
            return;
        }
        int color = current.fogColor();
        event.setRed((color >> 16 & 0xFF) / 255.0F);
        event.setGreen((color >> 8 & 0xFF) / 255.0F);
        event.setBlue((color & 0xFF) / 255.0F);
    }

    @SubscribeEvent
    public static void renderFog(final ViewportEvent.RenderFog event) {
        EventGuard.handle("client.rift.render_fog", event, BossRiftClient::handleRenderFog);
    }

    private static void handleRenderFog(ViewportEvent.RenderFog event) {
        State current = shown();
        // Water, lava and powder snow keep their own fog: the rift's is the air's.
        if (current == null || current.fogDistance() <= 0 || event.getType() != FogType.NONE) {
            return;
        }
        float far = Math.min(event.getFarPlaneDistance(), current.fogDistance());
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(far * FOG_NEAR_SHARE);
        // Only a cancelled event has its distances applied.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(final ClientPlayerNetworkEvent.LoggingOut event) {
        EventGuard.handle("client.rift.logout", event, BossRiftClient::handleLogout);
    }

    private static void handleLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
    }
}
