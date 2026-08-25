package com.goodbird.cnpcgeckoaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Starts, adjusts, or releases the victim's client-side prediction lock. */
public final class PacketSyncBossCaptureState implements CustomPacketPayload {
    public static final Type<PacketSyncBossCaptureState> TYPE = NetworkWrapper.typeOf(PacketSyncBossCaptureState.class);

    private final boolean active;
    private final double x;
    private final double y;
    private final double z;
    private final long startedAt;
    private final long endsAt;
    private final long liftEndsAt;
    private final double targetY;
    private final float yaw;
    private final float pitch;
    private final boolean allowLook;

    public PacketSyncBossCaptureState(boolean active, double x, double y, double z,
                                      long startedAt, long endsAt, long liftEndsAt,
                                      double targetY, float yaw, float pitch, boolean allowLook) {
        this.active = active;
        this.x = x;
        this.y = y;
        this.z = z;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.liftEndsAt = liftEndsAt;
        this.targetY = targetY;
        this.yaw = yaw;
        this.pitch = pitch;
        this.allowLook = allowLook;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeLong(startedAt);
        buffer.writeLong(endsAt);
        buffer.writeLong(liftEndsAt);
        buffer.writeDouble(targetY);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeBoolean(allowLook);
    }

    public static PacketSyncBossCaptureState decode(FriendlyByteBuf buffer) {
        return new PacketSyncBossCaptureState(buffer.readBoolean(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readLong(), buffer.readLong(),
                buffer.readLong(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat(),
                buffer.readBoolean());
    }

    public static void handle(PacketSyncBossCaptureState packet) {
        BossCaptureClientBridge.accept(packet.active, packet.x, packet.y, packet.z,
                packet.startedAt, packet.endsAt, packet.liftEndsAt, packet.targetY,
                packet.yaw, packet.pitch, packet.allowLook);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
