package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;

/** Adds, refreshes, or removes one keyed animated link between two boss entities. */
public final class PacketSyncBossLink implements CustomPacketPayload {
    public static final byte KIND_PROTECTION_TOTEM = 0;
    public static final byte KIND_CAPTURE = 1;
    /** A leash: boss to victim, stake to victim, or victim to victim. */
    public static final byte KIND_TETHER = 2;
    public static final Type<PacketSyncBossLink> TYPE = NetworkWrapper.typeOf(PacketSyncBossLink.class);

    private final byte linkKind;
    private final int sourceEntityId;
    private final int targetEntityId;
    private final int slotOrChannel;
    private final String styleId;
    private final int durationTicks;
    private final int widthPercent;
    private final int sagPercent;
    private final boolean drawHead;

    public PacketSyncBossLink(byte linkKind, int sourceEntityId, int targetEntityId,
                              int slotOrChannel, String styleId, int durationTicks,
                              int widthPercent, int sagPercent, boolean drawHead) {
        this.linkKind = (byte) Mth.clamp(linkKind, KIND_PROTECTION_TOTEM, KIND_TETHER);
        this.sourceEntityId = sourceEntityId;
        this.targetEntityId = targetEntityId;
        this.slotOrChannel = slotOrChannel;
        this.styleId = HookCordStyles.normalize(styleId);
        this.durationTicks = Math.max(0, durationTicks);
        this.widthPercent = Mth.clamp(widthPercent, 25, 400);
        this.sagPercent = Mth.clamp(sagPercent, 0, 200);
        this.drawHead = drawHead;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(linkKind);
        buffer.writeVarInt(sourceEntityId);
        buffer.writeVarInt(targetEntityId);
        buffer.writeVarInt(slotOrChannel);
        buffer.writeUtf(styleId, 64);
        buffer.writeVarInt(durationTicks);
        buffer.writeVarInt(widthPercent);
        buffer.writeVarInt(sagPercent);
        buffer.writeBoolean(drawHead);
    }

    public static PacketSyncBossLink decode(FriendlyByteBuf buffer) {
        return new PacketSyncBossLink(buffer.readByte(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(PacketSyncBossLink packet) {
        BossLinkClientBridge.accept(packet.linkKind, packet.sourceEntityId, packet.targetEntityId,
                packet.slotOrChannel, packet.styleId, packet.durationTicks, packet.widthPercent,
                packet.sagPercent, packet.drawHead);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
