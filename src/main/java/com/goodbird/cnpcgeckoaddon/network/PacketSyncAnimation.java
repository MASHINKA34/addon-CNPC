package com.goodbird.cnpcgeckoaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import software.bernie.geckolib.animation.RawAnimation;

public class PacketSyncAnimation implements CustomPacketPayload {
    public static final Type<PacketSyncAnimation> TYPE = NetworkWrapper.typeOf(PacketSyncAnimation.class);

    private int id;
    private RawAnimation builder;

    public PacketSyncAnimation(int entityId, RawAnimation builder) {
        this.id = entityId;
        this.builder = builder;
    }

    public PacketSyncAnimation(){

    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(id);
        RawAnimationSerializer.write(buf, builder);
    }

    public static PacketSyncAnimation decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        return new PacketSyncAnimation(id, RawAnimationSerializer.read(buf));
    }

    public static void handle(PacketSyncAnimation packet) {
        // Handed through the bridge so this class never mentions the client-only lookup:
        // a packet class is loaded on the dedicated server too.
        ManualAnimationClientBridge.acceptEntity(packet.id, packet.builder);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
