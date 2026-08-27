package com.goodbird.cnpcgeckoaddon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import software.bernie.geckolib.animation.RawAnimation;

public class PacketSyncTileAnimation implements CustomPacketPayload {
    public static final Type<PacketSyncTileAnimation> TYPE = NetworkWrapper.typeOf(PacketSyncTileAnimation.class);

    private BlockPos pos;
    private RawAnimation builder;

    public PacketSyncTileAnimation(BlockPos pos, RawAnimation builder) {
        this.pos = pos;
        this.builder = builder;
    }

    public PacketSyncTileAnimation(){

    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        RawAnimationSerializer.write(buf, builder);
    }

    public static PacketSyncTileAnimation decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new PacketSyncTileAnimation(pos, RawAnimationSerializer.read(buf));
    }

    public static void handle(PacketSyncTileAnimation packet) {
        // Handed through the bridge so this class never mentions the client-only lookup:
        // a packet class is loaded on the dedicated server too.
        ManualAnimationClientBridge.acceptTile(packet.pos, packet.builder);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
