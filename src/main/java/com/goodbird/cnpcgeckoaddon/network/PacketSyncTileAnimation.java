package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.entity.BlockEntity;
import noppes.npcs.blocks.tiles.TileScripted;
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
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(packet.pos);
        if(!(entity instanceof TileScripted)) return;
        TileScripted tile = (TileScripted) entity;
        // renderTile can hold any BlockEntity the scripted block was told to display,
        // so only reuse it when it really is ours.
        if(!(tile.renderTile instanceof TileEntityCustomModel)){
            tile.renderTile = new TileEntityCustomModel(tile);
        }
        TileEntityCustomModel geckoTile = (TileEntityCustomModel) tile.renderTile;
        geckoTile.manualAnim = packet.builder;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
