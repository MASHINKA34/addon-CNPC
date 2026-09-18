package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossHurricaneHold;
import com.goodbird.cnpcgeckoaddon.network.BossSpinClientBridge;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossSpinState;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Runs the ride a storm has the local player on, between the server's packets.
 *
 * <p>The server rejects the player's own movement while they are held, so without this the
 * client would walk them off the circle every tick and be snapped back with each packet. It
 * runs the same sums the server does - the eye walked on by its speed, the angle turned by
 * the spin, the lift - and puts the player where the server is about to say they are. The turn
 * of the view is the same thing seen from inside: the yaw is turned with the ride, which is
 * what makes the screen spin.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class BossSpinClient {
    private static State state;

    static {
        BossSpinClientBridge.setHandler(BossSpinClient::accept);
    }

    private BossSpinClient() {
    }

    private static final class State {
        private double centreX;
        private double centreZ;
        private final double velocityX;
        private final double velocityZ;
        private final double startY;
        private final double targetY;
        private final long liftStartedAt;
        private final long liftEndsAt;
        private final double orbitRadius;
        private double angle;
        private final float spinDegrees;
        private final boolean spinView;
        private final long endsAt;

        private State(PacketSyncBossSpinState packet) {
            centreX = packet.centreX();
            centreZ = packet.centreZ();
            velocityX = packet.velocityX();
            velocityZ = packet.velocityZ();
            startY = packet.startY();
            targetY = packet.targetY();
            liftStartedAt = packet.liftStartedAt();
            liftEndsAt = packet.liftEndsAt();
            orbitRadius = packet.orbitRadius();
            angle = packet.angle();
            spinDegrees = packet.spinDegrees();
            spinView = packet.spinView();
            endsAt = packet.endsAt();
        }
    }

    private static void accept(PacketSyncBossSpinState packet) {
        state = packet.isActive() ? new State(packet) : null;
        // Put straight where the packet says, without a tick's worth of ride on top.
        apply(false);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        EventGuard.handle("client.spin.tick", event, BossSpinClient::handleTick);
    }

    private static void handleTick(ClientTickEvent.Post event) {
        // The capture's rule: a ride that fails its tick is let go of here, and the server,
        // which carries the victim anyway, is left to do the carrying.
        CrashGuard.tick("client.spin.apply", BossSpinClient::advance, BossSpinClient::forget);
    }

    private static void advance() {
        apply(true);
    }

    private static void forget() {
        state = null;
    }

    /**
     * Puts the player on the ride.
     *
     * @param advance whether a tick has passed since the last word: the eye moves on, the
     *                angle turns and, when the view spins, so does the camera
     */
    private static void apply(boolean advance) {
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
        if (advance) {
            current.centreX += current.velocityX;
            current.centreZ += current.velocityZ;
            current.angle = BossHurricaneHold.advanceAngle(current.angle, current.spinDegrees);
        }
        double y = BossHurricaneHold.liftY(current.startY, current.targetY, current.liftStartedAt,
                current.liftEndsAt, gameTime);
        Vec3 pos = BossHurricaneHold.orbitPoint(current.centreX, y, current.centreZ,
                current.orbitRadius, current.angle);
        player.setPos(pos);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        if (advance && current.spinView) {
            // The player still steers the view with the mouse; the ride's turn goes on top of it.
            float yaw = BossHurricaneHold.spinYaw(player.getYRot(), current.spinDegrees);
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        EventGuard.handle("client.spin.logout", event, BossSpinClient::handleLogout);
    }

    private static void handleLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
    }

    /** A respawn or a change of world is a new player: whatever ride the old one was on is over. */
    @SubscribeEvent
    public static void respawn(ClientPlayerNetworkEvent.Clone event) {
        EventGuard.handle("client.spin.respawn", event, BossSpinClient::handleRespawn);
    }

    private static void handleRespawn(ClientPlayerNetworkEvent.Clone event) {
        state = null;
    }
}
