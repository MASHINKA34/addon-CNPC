package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.world.NpcCarryManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A right click into the air with an empty hand, which the server otherwise never hears of.
 *
 * <p>The client only sends a use packet when there is an item to use, so the one click a
 * carrier throws with most - hands full, nothing held - reaches the server through this and
 * nothing else. It carries no data on purpose: everything about the throw is decided from
 * what the server already knows about the carrier, and a client cannot ask for more than
 * "I clicked".</p>
 */
public record PacketNpcCarryThrow() implements CustomPacketPayload {
    public static final Type<PacketNpcCarryThrow> TYPE = NetworkWrapper.typeOf(PacketNpcCarryThrow.class);

    public static void encode(PacketNpcCarryThrow packet, FriendlyByteBuf buffer) {
    }

    public static PacketNpcCarryThrow decode(FriendlyByteBuf buffer) {
        return new PacketNpcCarryThrow();
    }

    public static void handle(PacketNpcCarryThrow packet, MinecraftServer server, ServerPlayer player) {
        NpcCarryManager.throwIntoAir(player);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
