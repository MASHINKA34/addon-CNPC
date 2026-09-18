package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Starts, adjusts or ends the ride a held player's own client runs between the server's words.
 *
 * <p>A storm's victim is carried round a moving eye every tick, and a client told only where
 * it was ten ticks ago would see itself jerked along in steps. So the packet carries the ride
 * itself - the eye, how it moves, the lift, the circle and the turn - and the client runs the
 * same sums the server does until the next packet corrects it.</p>
 */
public final class PacketSyncBossSpinState implements CustomPacketPayload {
    public static final Type<PacketSyncBossSpinState> TYPE = NetworkWrapper.typeOf(PacketSyncBossSpinState.class);

    private final boolean active;
    private final double centreX;
    private final double centreZ;
    private final double velocityX;
    private final double velocityZ;
    private final double startY;
    private final double targetY;
    private final long liftStartedAt;
    private final long liftEndsAt;
    private final double orbitRadius;
    private final double angle;
    private final float spinDegrees;
    private final boolean spinView;
    private final long endsAt;

    public PacketSyncBossSpinState(boolean active, double centreX, double centreZ, double velocityX,
                                   double velocityZ, double startY, double targetY, long liftStartedAt,
                                   long liftEndsAt, double orbitRadius, double angle, float spinDegrees,
                                   boolean spinView, long endsAt) {
        this.active = active;
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.velocityX = velocityX;
        this.velocityZ = velocityZ;
        this.startY = startY;
        this.targetY = targetY;
        this.liftStartedAt = liftStartedAt;
        this.liftEndsAt = liftEndsAt;
        this.orbitRadius = orbitRadius;
        this.angle = angle;
        this.spinDegrees = spinDegrees;
        this.spinView = spinView;
        this.endsAt = endsAt;
    }

    /** The one that lets a client go. */
    public static PacketSyncBossSpinState released() {
        return new PacketSyncBossSpinState(false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0L, 0L,
                0.0D, 0.0D, 0.0F, false, 0L);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeDouble(centreX);
        buffer.writeDouble(centreZ);
        buffer.writeDouble(velocityX);
        buffer.writeDouble(velocityZ);
        buffer.writeDouble(startY);
        buffer.writeDouble(targetY);
        buffer.writeLong(liftStartedAt);
        buffer.writeLong(liftEndsAt);
        buffer.writeDouble(orbitRadius);
        buffer.writeDouble(angle);
        buffer.writeFloat(spinDegrees);
        buffer.writeBoolean(spinView);
        buffer.writeLong(endsAt);
    }

    public static PacketSyncBossSpinState decode(FriendlyByteBuf buffer) {
        return new PacketSyncBossSpinState(buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readLong(), buffer.readLong(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readBoolean(), buffer.readLong());
    }

    public static void handle(PacketSyncBossSpinState packet) {
        CrashGuard.run("packet.sync_boss_spin_state", packet, PacketSyncBossSpinState::handleGuarded);
    }

    private static void handleGuarded(PacketSyncBossSpinState packet) {
        BossSpinClientBridge.accept(packet);
    }

    public boolean isActive() {
        return active;
    }

    /** The eye of the storm, flat: what the circle is drawn round. */
    public double centreX() {
        return centreX;
    }

    public double centreZ() {
        return centreZ;
    }

    /** How far the eye moves a tick, so the client can walk it on between packets. */
    public double velocityX() {
        return velocityX;
    }

    public double velocityZ() {
        return velocityZ;
    }

    /** The lift: from where the victim was caught up to the storm's carrying height. */
    public double startY() {
        return startY;
    }

    public double targetY() {
        return targetY;
    }

    public long liftStartedAt() {
        return liftStartedAt;
    }

    public long liftEndsAt() {
        return liftEndsAt;
    }

    public double orbitRadius() {
        return orbitRadius;
    }

    /** Where on the circle the victim is, in degrees, as of this packet. */
    public double angle() {
        return angle;
    }

    /** How far round the circle - and, when the view spins, round the camera - each tick goes. */
    public float spinDegrees() {
        return spinDegrees;
    }

    public boolean spinView() {
        return spinView;
    }

    /** When the hold is over on its own, so a client whose release packet was lost still lets go. */
    public long endsAt() {
        return endsAt;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
