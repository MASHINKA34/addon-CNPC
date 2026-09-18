package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.network.BossCaptureClientBridge;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import com.goodbird.cnpcgeckoaddon.utils.GuardSelfTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Mirrors the server anchor locally so rejected movement does not cause rubber-banding. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossCaptureClient {
    private static State state;

    static {
        BossCaptureClientBridge.setHandler(BossCaptureClient::accept);
    }

    private BossCaptureClient() {
    }

    private record State(double x, double y, double z, long startedAt, long endsAt,
                         long liftEndsAt, double targetY, float yaw, float pitch,
                         boolean allowLook) {
    }

    private static void accept(boolean active, double x, double y, double z, long startedAt,
                               long endsAt, long liftEndsAt, double targetY, float yaw,
                               float pitch, boolean allowLook) {
        state = active ? new State(x, y, z, startedAt, endsAt, liftEndsAt, targetY,
                yaw, pitch, allowLook) : null;
        apply();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        EventGuard.handle("client.capture.tick", event, BossCaptureClient::handleTick);
    }

    private static void handleTick(ClientTickEvent.Post event) {
        // The self test's wire: /cnpcgecko selftest client fails this tick once, on this client.
        GuardSelfTest.trip(GuardSelfTest.CLIENT);
        // A hold that cannot be applied this tick cannot be applied the next one either: it is
        // let go of locally, and the server's own pin is left to do the holding.
        CrashGuard.tick("client.capture.apply", BossCaptureClient::apply, BossCaptureClient::forget);
    }

    private static void forget() {
        state = null;
    }

    private static void apply() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        State current = state;
        if (current == null || level == null || player == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime >= current.endsAt) {
            state = null;
            return;
        }
        double progress = current.liftEndsAt <= current.startedAt ? 0.0D
                : Mth.clamp((gameTime - current.startedAt)
                / (double) (current.liftEndsAt - current.startedAt), 0.0D, 1.0D);
        double desiredY = Mth.lerp(progress, current.y, current.targetY);
        player.setPos(current.x, desiredY, current.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        if (!current.allowLook) {
            player.setYRot(current.yaw);
            player.setYHeadRot(current.yaw);
            player.setXRot(current.pitch);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        EventGuard.handle("client.capture.logout", event, BossCaptureClient::handleLogout);
    }

    private static void handleLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
    }
}
