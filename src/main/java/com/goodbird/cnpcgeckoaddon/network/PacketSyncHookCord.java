package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One textured cord between a boss and one of its hook victims.
 *
 * <p>Sent once when the pull starts and again with a zero duration if the pull is cut short.
 * The client counts the ticks down itself, so nothing is sent while the cord just hangs
 * there. Re-hooking the same victim simply overwrites the entry.</p>
 */
public final class PacketSyncHookCord implements CustomPacketPayload {
    public static final Type<PacketSyncHookCord> TYPE = NetworkWrapper.typeOf(PacketSyncHookCord.class);

    private final int bossEntityId;
    private final int victimEntityId;
    private final String styleId;
    private final int durationTicks;

    public PacketSyncHookCord(int bossEntityId, int victimEntityId, String styleId, int durationTicks) {
        this.bossEntityId = bossEntityId;
        this.victimEntityId = victimEntityId;
        this.styleId = HookCordStyles.normalize(styleId);
        this.durationTicks = Math.max(0, durationTicks);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(bossEntityId);
        buffer.writeVarInt(victimEntityId);
        buffer.writeUtf(styleId, 64);
        buffer.writeVarInt(durationTicks);
    }

    public static PacketSyncHookCord decode(FriendlyByteBuf buffer) {
        return new PacketSyncHookCord(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64),
                buffer.readVarInt());
    }

    public static void handle(PacketSyncHookCord packet) {
        HookCordClientBridge.accept(packet.bossEntityId, packet.victimEntityId, packet.styleId,
                packet.durationTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
