package com.goodbird.cnpcgeckoaddon.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The presets screen's request and the server's answer, written and read back: a load's Keep health
 * link has to reach the server as it was set, and the line about the group the preset carried has to
 * come back with the line about the load.
 */
class PacketBossPresetTest {

    @Test
    @DisplayName("a request comes back with its action, its name and its keep health link exactly")
    void requestRoundTrip() {
        for (int action : new int[]{PacketBossPresetRequest.LIST, PacketBossPresetRequest.SAVE,
                PacketBossPresetRequest.LOAD}) {
            for (boolean keepLink : new boolean[]{false, true}) {
                PacketBossPresetRequest back = roundTrip(new PacketBossPresetRequest(action, "twin_boss", keepLink));
                assertEquals(action, back.action());
                assertEquals("twin_boss", back.name());
                assertEquals(keepLink, back.keepLink(), "action " + action);
            }
        }
    }

    @Test
    @DisplayName("an answer to a load carries the group's line next to the load's")
    void replyRoundTrip() {
        PacketBossPresetReply sent = new PacketBossPresetReply(PacketBossPresetRequest.LOAD, true,
                "cnpcgeckoaddon.boss.preset_loaded", List.of("demo", "Boss"),
                "cnpcgeckoaddon.boss.preset_link_dropped", List.of("twins"), true, List.of("demo", "other"), 42);
        PacketBossPresetReply back = roundTrip(sent);
        assertEquals(sent, back);

        PacketBossPresetReply none = roundTrip(new PacketBossPresetReply(PacketBossPresetRequest.LIST, true, "",
                List.of(), null, null, true, List.of(), -1));
        assertEquals("", none.linkKey(), "no group, no second line");
        assertEquals(List.of(), none.linkArgs());
    }

    @Test
    @DisplayName("the group's name is cut where a name in the first line would be, not refused")
    void aLongGroupIsCut() {
        String group = "g".repeat(300);
        PacketBossPresetReply back = roundTrip(new PacketBossPresetReply(PacketBossPresetRequest.LOAD, true,
                "cnpcgeckoaddon.boss.preset_loaded", List.of("demo", "Boss"),
                "cnpcgeckoaddon.boss.preset_link_kept", List.of(group), true, List.of(), 7));
        assertEquals(1, back.linkArgs().size());
        assertEquals(group.substring(0, 128), back.linkArgs().getFirst());
    }

    private static PacketBossPresetRequest roundTrip(PacketBossPresetRequest packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buffer);
        PacketBossPresetRequest back = PacketBossPresetRequest.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "the reader left bytes behind, so the two halves of the codec disagree");
        return back;
    }

    private static PacketBossPresetReply roundTrip(PacketBossPresetReply packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        PacketBossPresetReply.encode(packet, buffer);
        PacketBossPresetReply back = PacketBossPresetReply.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "the reader left bytes behind, so the two halves of the codec disagree");
        return back;
    }
}
