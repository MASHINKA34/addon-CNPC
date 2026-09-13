package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.data.TelegraphLineStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.TelegraphShape;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The warning frame, written and read back.
 *
 * <p>Every figure writes only the numbers its own kind uses, which is what keeps a frame of
 * sixty-four shapes small enough to send every other tick - and is exactly the sort of codec
 * where one side gets a float ahead of the other and every shape after it lands somewhere
 * absurd. Nothing throws when that happens: the ring is simply drawn a hundred blocks away.</p>
 */
class PacketSyncBossTelegraphTest {

    private static final List<TelegraphShape> EVERY_KIND = List.of(
            TelegraphShape.ring(new Vec3(12.5D, 64.0D, -8.25D), 7.5D, 0xFF5926, false),
            TelegraphShape.rectangle(-3.5D, 1.25D, 5.5D, 9.75D, 70.0D, 0x00FF00, false),
            TelegraphShape.arc(new Vec3(1.5D, 65.0D, 2.5D), 4.5D, 137.5F, 60.0D, 0xF2EACC, false),
            TelegraphShape.sector(new Vec3(-1.5D, 65.0D, 2.5D), 9.0D, -45.0F, 22.5D, 0xFF9A3C, true),
            TelegraphShape.corridor(new Vec3(0.5D, 63.0D, 0.25D), new Vec3(0.6D, 0.0D, -0.8D),
                    18.0D, 3.5D, 2.25D, 0xFF3355, false),
            TelegraphShape.link(new Vec3(0.5D, 66.5D, 0.25D), new Vec3(-14.0D, 64.75D, 21.5D),
                    0x4DCCFF, false));

    @Test
    @DisplayName("a frame of every kind of figure comes back exactly as it was sent")
    void everyKindSurvivesTheWire() {
        PacketSyncBossTelegraph sent = new PacketSyncBossTelegraph(4213, (byte) 3,
                TelegraphLineStyles.DOTTED, 17, TeleportPathData.TELEGRAPH_MOTION_FILL, 65,
                0.375F, 3, EVERY_KIND);
        PacketSyncBossTelegraph back = roundTrip(sent);

        assertEquals(sent.ownerId(), back.ownerId());
        assertEquals(sent.channel(), back.channel());
        assertEquals(sent.styleId(), back.styleId());
        assertEquals(sent.widthTenths(), back.widthTenths());
        assertEquals(sent.motion(), back.motion());
        assertEquals(sent.fillPercent(), back.fillPercent());
        assertEquals(sent.progress(), back.progress(), 0.0F);
        assertEquals(sent.ttlTicks(), back.ttlTicks());
        assertEquals(sent.shapes(), back.shapes(),
                "a figure read back differently is a warning drawn somewhere else");
    }

    @Test
    @DisplayName("a frame with nothing in it is a warning put out, not a broken packet")
    void anEmptyFrameSurvives() {
        PacketSyncBossTelegraph back = roundTrip(new PacketSyncBossTelegraph(7, (byte) 1,
                TelegraphLineStyles.SOLID, 3, TeleportPathData.TELEGRAPH_MOTION_STATIC, 0,
                -1.0F, 3, List.of()));
        assertTrue(back.shapes().isEmpty());
        assertEquals(-1.0F, back.progress(), 0.0F, "a warning with no end has to stay endless");
    }

    @Test
    @DisplayName("a frame is cut to the shapes it may carry rather than overrunning the wire")
    void tooManyShapesAreDropped() {
        List<TelegraphShape> many = new ArrayList<>();
        for (int i = 0; i < PacketSyncBossTelegraph.MAX_SHAPES * 2; i++) {
            many.add(TelegraphShape.ring(new Vec3(i, 64.0D, 0.0D), 1.0D, 0xFFFFFF, false));
        }
        PacketSyncBossTelegraph back = roundTrip(new PacketSyncBossTelegraph(9, (byte) 0,
                TelegraphLineStyles.GLOW, 20, TeleportPathData.TELEGRAPH_MOTION_TRACE, 100,
                1.0F, 3, many));
        assertEquals(PacketSyncBossTelegraph.MAX_SHAPES, back.shapes().size());
        assertEquals(many.subList(0, PacketSyncBossTelegraph.MAX_SHAPES), back.shapes());
    }

    @Test
    @DisplayName("a style nobody knows reads back as the one that draws nothing")
    void anUnknownStyleFallsBack() {
        PacketSyncBossTelegraph back = roundTrip(new PacketSyncBossTelegraph(1, (byte) 6,
                "no_such_style", 3, 0, 0, 0.0F, 3, List.of()));
        assertEquals(TelegraphLineStyles.PARTICLES, back.styleId());
    }

    private static PacketSyncBossTelegraph roundTrip(PacketSyncBossTelegraph packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buffer);
        PacketSyncBossTelegraph back = PacketSyncBossTelegraph.decode(buffer);
        assertEquals(0, buffer.readableBytes(),
                "the reader left bytes behind, so the two halves of the codec disagree");
        return back;
    }
}
