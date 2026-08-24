package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.ai.BossTotemUtil;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Set;

/** Operator-only request behind the totem editor's explicit restore button. */
public final class PacketRestoreBossTotems implements CustomPacketPayload {
    public static final Type<PacketRestoreBossTotems> TYPE = NetworkWrapper.typeOf(PacketRestoreBossTotems.class);

    private final int bossEntityId;

    public PacketRestoreBossTotems(int bossEntityId) {
        this.bossEntityId = bossEntityId;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(bossEntityId);
    }

    public static PacketRestoreBossTotems decode(FriendlyByteBuf buffer) {
        return new PacketRestoreBossTotems(buffer.readVarInt());
    }

    public static void handle(PacketRestoreBossTotems packet, MinecraftServer server,
                              ServerPlayer player) {
        server.execute(() -> {
            if (!player.hasPermissions(2)
                    || !(player.serverLevel().getEntity(packet.bossEntityId)
                    instanceof EntityNPCInterface npc)) {
                return;
            }
            BossTotemUtil.writeDeadSlots(npc, Set.of());
            if (npc instanceof IBossController holder) {
                TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
                if (controller != null) {
                    controller.restoreAllTotemsNow();
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
