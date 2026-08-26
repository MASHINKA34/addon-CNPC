package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;

import java.util.UUID;

public final class PacketSyncBossBarStyle implements CustomPacketPayload {
    public static final Type<PacketSyncBossBarStyle> TYPE = NetworkWrapper.typeOf(PacketSyncBossBarStyle.class);

    private final UUID eventId;
    private final String styleId;
    private final int scalePercent;

    public PacketSyncBossBarStyle(UUID eventId, String styleId, int scalePercent) {
        this.eventId = eventId;
        this.styleId = BossBarStyles.normalize(styleId);
        this.scalePercent = Mth.clamp(scalePercent, TeleportPathData.MIN_BOSS_BAR_SCALE_PERCENT,
                TeleportPathData.MAX_BOSS_BAR_SCALE_PERCENT);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(eventId);
        buffer.writeUtf(styleId, 64);
        buffer.writeVarInt(scalePercent);
    }

    public static PacketSyncBossBarStyle decode(FriendlyByteBuf buffer) {
        return new PacketSyncBossBarStyle(buffer.readUUID(), buffer.readUtf(64), buffer.readVarInt());
    }

    public static void handle(PacketSyncBossBarStyle packet) {
        BossBarStyleClientBridge.accept(packet.eventId, packet.styleId, packet.scalePercent);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
