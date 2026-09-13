package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.data.TelegraphLineStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.TelegraphShape;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * One tick's worth of the shapes a boss is warning with, on one channel.
 *
 * <p>The frame replaces whatever the client held for the same boss and channel, so the shapes
 * of a tick never flicker against each other and a frame with no shapes in it is how a warning
 * is put out at once rather than left to run out its lifetime.</p>
 *
 * <p>Compact on purpose: this goes out every other tick for as long as a boss is winding up.
 * A byte for everything that is a choice out of a short list, and floats for the geometry -
 * which is also what the shapes are built out of, so nothing is lost on the way.</p>
 */
public final class PacketSyncBossTelegraph implements CustomPacketPayload {
    public static final Type<PacketSyncBossTelegraph> TYPE =
            NetworkWrapper.typeOf(PacketSyncBossTelegraph.class);

    /** A frame can carry no more than this; a tick that draws more loses the tail of it. */
    public static final int MAX_SHAPES = 64;

    private final int ownerId;
    private final byte channel;
    private final String styleId;
    private final int widthTenths;
    private final int motion;
    private final int fillPercent;
    /** How far the wind-up has got, from 0 to 1, or -1 for a warning with no end. */
    private final float progress;
    private final int ttlTicks;
    private final List<TelegraphShape> shapes;

    public PacketSyncBossTelegraph(int ownerId, byte channel, String styleId, int widthTenths,
                                   int motion, int fillPercent, float progress, int ttlTicks,
                                   List<TelegraphShape> shapes) {
        this.ownerId = ownerId;
        this.channel = channel;
        this.styleId = TelegraphLineStyles.normalize(styleId);
        this.widthTenths = Mth.clamp(widthTenths, TeleportPathData.MIN_TELEGRAPH_LINE_WIDTH,
                TeleportPathData.MAX_TELEGRAPH_LINE_WIDTH);
        this.motion = Mth.clamp(motion, TeleportPathData.TELEGRAPH_MOTION_STATIC,
                TeleportPathData.TELEGRAPH_MOTION_TRACE);
        this.fillPercent = Mth.clamp(fillPercent, TeleportPathData.MIN_TELEGRAPH_LINE_FILL,
                TeleportPathData.MAX_TELEGRAPH_LINE_FILL);
        this.progress = progress < 0.0F ? -1.0F : Mth.clamp(progress, 0.0F, 1.0F);
        this.ttlTicks = Mth.clamp(ttlTicks, 1, 200);
        this.shapes = shapes.size() <= MAX_SHAPES
                ? List.copyOf(shapes) : List.copyOf(shapes.subList(0, MAX_SHAPES));
    }

    public int ownerId() {
        return ownerId;
    }

    public byte channel() {
        return channel;
    }

    public String styleId() {
        return styleId;
    }

    public int widthTenths() {
        return widthTenths;
    }

    public int motion() {
        return motion;
    }

    public int fillPercent() {
        return fillPercent;
    }

    public float progress() {
        return progress;
    }

    public int ttlTicks() {
        return ttlTicks;
    }

    public List<TelegraphShape> shapes() {
        return shapes;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ownerId);
        buffer.writeByte(channel);
        buffer.writeByte(TelegraphLineStyles.indexOf(styleId));
        buffer.writeByte(widthTenths);
        buffer.writeByte(motion);
        buffer.writeByte(fillPercent);
        buffer.writeFloat(progress);
        buffer.writeVarInt(ttlTicks);
        buffer.writeByte(shapes.size());
        for (TelegraphShape shape : shapes) {
            writeShape(buffer, shape);
        }
    }

    public static PacketSyncBossTelegraph decode(FriendlyByteBuf buffer) {
        int ownerId = buffer.readVarInt();
        byte channel = buffer.readByte();
        String styleId = TelegraphLineStyles.byIndex(buffer.readByte());
        int widthTenths = buffer.readByte();
        int motion = buffer.readByte();
        int fillPercent = buffer.readByte();
        float progress = buffer.readFloat();
        int ttlTicks = buffer.readVarInt();
        int count = Math.min(MAX_SHAPES, buffer.readByte() & 0xFF);
        List<TelegraphShape> shapes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            shapes.add(readShape(buffer));
        }
        return new PacketSyncBossTelegraph(ownerId, channel, styleId, widthTenths, motion,
                fillPercent, progress, ttlTicks, shapes);
    }

    /**
     * Only the numbers the figure really has: a ring is four floats and a lane is eight, and
     * writing the widest of them for every shape would be half a frame of nothing.
     */
    private static void writeShape(FriendlyByteBuf buffer, TelegraphShape shape) {
        buffer.writeByte(shape.kind());
        buffer.writeBoolean(shape.faded());
        buffer.writeInt(shape.rgb());
        buffer.writeFloat(shape.x());
        buffer.writeFloat(shape.y());
        buffer.writeFloat(shape.z());
        buffer.writeFloat(shape.a());
        int rest = extraFloats(shape.kind());
        if (rest > 1) {
            buffer.writeFloat(shape.b());
        }
        if (rest > 2) {
            buffer.writeFloat(shape.c());
        }
        if (rest > 3) {
            buffer.writeFloat(shape.d());
        }
        if (rest > 4) {
            buffer.writeFloat(shape.e());
        }
    }

    private static TelegraphShape readShape(FriendlyByteBuf buffer) {
        byte kind = buffer.readByte();
        boolean faded = buffer.readBoolean();
        int rgb = buffer.readInt();
        float x = buffer.readFloat();
        float y = buffer.readFloat();
        float z = buffer.readFloat();
        float a = buffer.readFloat();
        int rest = extraFloats(kind);
        float b = rest > 1 ? buffer.readFloat() : 0.0F;
        float c = rest > 2 ? buffer.readFloat() : 0.0F;
        float d = rest > 3 ? buffer.readFloat() : 0.0F;
        float e = rest > 4 ? buffer.readFloat() : 0.0F;
        return new TelegraphShape(kind, rgb, faded, x, y, z, a, b, c, d, e);
    }

    /** How many of the five numbers after the anchor this kind of figure uses. */
    private static int extraFloats(byte kind) {
        return switch (kind) {
            case TelegraphShape.KIND_RING -> 1;
            case TelegraphShape.KIND_RECTANGLE -> 2;
            case TelegraphShape.KIND_ARC, TelegraphShape.KIND_SECTOR, TelegraphShape.KIND_LINK -> 3;
            case TelegraphShape.KIND_CORRIDOR -> 5;
            default -> 5;
        };
    }

    public static void handle(PacketSyncBossTelegraph packet) {
        BossTelegraphClientBridge.accept(packet.ownerId, packet.channel, packet.styleId,
                packet.widthTenths, packet.motion, packet.fillPercent, packet.progress,
                packet.ttlTicks, packet.shapes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
