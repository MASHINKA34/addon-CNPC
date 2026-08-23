package com.goodbird.cnpcgeckoaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * The countdown drawn on one boss bar.
 *
 * <p>Only sent to the players actually watching that bar, and only when the state changes
 * or every few ticks - the client subtracts the remaining ticks itself in between, so a
 * per-tick packet would be pure noise.</p>
 */
public final class PacketSyncBossTimer implements CustomPacketPayload {
    public static final Type<PacketSyncBossTimer> TYPE = NetworkWrapper.typeOf(PacketSyncBossTimer.class);

    /** Counting down to the enrage. */
    public static final byte STATE_COUNTDOWN = 0;
    /** The boss has already enraged; nothing is counting any more. */
    public static final byte STATE_RAGE = 1;
    /** An immune phase is running, and the ticks describe how much of it is left. */
    public static final byte STATE_INVULNERABLE = 2;
    /** Nothing to draw. */
    public static final byte STATE_NONE = 3;

    private final UUID eventId;
    private final int remainingTicks;
    private final int totalTicks;
    private final byte state;

    public PacketSyncBossTimer(UUID eventId, int remainingTicks, int totalTicks, byte state) {
        this.eventId = eventId;
        this.remainingTicks = Math.max(0, remainingTicks);
        this.totalTicks = Math.max(0, totalTicks);
        this.state = state;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(eventId);
        buffer.writeVarInt(remainingTicks);
        buffer.writeVarInt(totalTicks);
        buffer.writeByte(state);
    }

    public static PacketSyncBossTimer decode(FriendlyByteBuf buffer) {
        return new PacketSyncBossTimer(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByte());
    }

    public static void handle(PacketSyncBossTimer packet) {
        BossTimerClientBridge.accept(packet.eventId, packet.remainingTicks, packet.totalTicks, packet.state);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
