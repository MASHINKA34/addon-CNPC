package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells a taken player's client how the rift looks from inside - the screen's tint and its pulse,
 * the fog's colour and reach - and when it closes; and, sent inactive, that it is over.
 */
public final class PacketSyncBossRiftState implements CustomPacketPayload {
    public static final Type<PacketSyncBossRiftState> TYPE = NetworkWrapper.typeOf(PacketSyncBossRiftState.class);

    private final boolean active;
    private final int tintColor;
    private final int tintAlpha;
    private final int pulseTicks;
    private final int fogColor;
    private final int fogDistance;
    private final long endsAt;

    public PacketSyncBossRiftState(boolean active, int tintColor, int tintAlpha, int pulseTicks,
                                   int fogColor, int fogDistance, long endsAt) {
        this.active = active;
        this.tintColor = tintColor;
        this.tintAlpha = tintAlpha;
        this.pulseTicks = pulseTicks;
        this.fogColor = fogColor;
        this.fogDistance = fogDistance;
        this.endsAt = endsAt;
    }

    /** The packet that clears a client's rift state. */
    public static PacketSyncBossRiftState inactive() {
        return new PacketSyncBossRiftState(false, 0, 0, 0, 0, 0, 0L);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeInt(tintColor);
        buffer.writeVarInt(tintAlpha);
        buffer.writeVarInt(pulseTicks);
        buffer.writeInt(fogColor);
        buffer.writeVarInt(fogDistance);
        buffer.writeLong(endsAt);
    }

    public static PacketSyncBossRiftState decode(FriendlyByteBuf buffer) {
        return new PacketSyncBossRiftState(buffer.readBoolean(), buffer.readInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readInt(), buffer.readVarInt(), buffer.readLong());
    }

    public static void handle(PacketSyncBossRiftState packet) {
        CrashGuard.run("packet.sync_boss_rift_state", packet, PacketSyncBossRiftState::handleGuarded);
    }

    private static void handleGuarded(PacketSyncBossRiftState packet) {
        BossRiftClientBridge.accept(packet.active, packet.tintColor, packet.tintAlpha, packet.pulseTicks,
                packet.fogColor, packet.fogDistance, packet.endsAt);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
