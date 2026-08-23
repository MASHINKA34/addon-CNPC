package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityCustomNpc;
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
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(packet.id);
        if(!(entity instanceof EntityCustomNpc)) return;
        EntityCustomNpc npc = (EntityCustomNpc) entity;
        if(npc.modelData==null || !(npc.modelData.getEntity(npc) instanceof EntityCustomModel)) return;
        EntityCustomModel entityCustomModel = (EntityCustomModel) npc.modelData.getEntity(npc);
        entityCustomModel.manualAnim = packet.builder;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
