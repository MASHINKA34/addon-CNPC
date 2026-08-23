package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public final class PacketSyncBossBarStyle implements CustomPacketPayload {
    public static final Type<PacketSyncBossBarStyle> TYPE = NetworkWrapper.typeOf(PacketSyncBossBarStyle.class);

    private final UUID eventId;
    private final String styleId;

    public PacketSyncBossBarStyle(UUID eventId, String styleId) {
        this.eventId = eventId;
        this.styleId = BossBarStyles.normalize(styleId);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(eventId);
        buffer.writeUtf(styleId, 64);
    }

    public static PacketSyncBossBarStyle decode(FriendlyByteBuf buffer) {
        return new PacketSyncBossBarStyle(buffer.readUUID(), buffer.readUtf(64));
    }

    public static void handle(PacketSyncBossBarStyle packet) {
        BossBarStyleClientBridge.accept(packet.eventId, packet.styleId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
