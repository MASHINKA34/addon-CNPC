package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record PacketSyncNpcCarryState(int entityId, UUID entityUuid, boolean carried) implements CustomPacketPayload {
    public static final Type<PacketSyncNpcCarryState> TYPE = NetworkWrapper.typeOf(PacketSyncNpcCarryState.class);

    public PacketSyncNpcCarryState(Entity entity, boolean carried) {
        this(entity.getId(), entity.getUUID(), carried);
    }

    public static void encode(PacketSyncNpcCarryState packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeUUID(packet.entityUuid());
        buffer.writeBoolean(packet.carried());
    }

    public static PacketSyncNpcCarryState decode(FriendlyByteBuf buffer) {
        return new PacketSyncNpcCarryState(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean());
    }

    public static void handle(PacketSyncNpcCarryState packet) {
        ClientTarget.apply(packet);
    }

    private static final class ClientTarget {

        private ClientTarget() {
        }

        static void apply(PacketSyncNpcCarryState packet) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                packet.apply(level.getEntity(packet.entityId()));
            }
        }
    }

    public void apply(Entity entity) {
        if (entity instanceof INpcCarryState state && entity.getId() == entityId
                && entity.getUUID().equals(entityUuid)) {
            state.cnpcgeckoaddon$setCarried(carried);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
