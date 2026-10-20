package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's answer to a {@link PacketBossPresetRequest}: whether it went, the line about it as a
 * translation key with what the line names, the presets there are now, and the npc a load went into -
 * the one whose editor the client then closes without saving over the preset.
 *
 * @param action the request answered, one of {@link PacketBossPresetRequest#LIST} and the rest
 * @param key    the line's translation key, empty for a plain list
 * @param listed whether {@code names} is the folder as it is now, rather than nothing known
 * @param npcId  the npc saved from or loaded into, -1 for none
 */
public record PacketBossPresetReply(int action, boolean success, String key, List<String> args, boolean listed,
                                    List<String> names, int npcId) implements CustomPacketPayload {
    public static final Type<PacketBossPresetReply> TYPE = NetworkWrapper.typeOf(PacketBossPresetReply.class);

    private static final int MAX_KEY_LENGTH = 128;
    /** An argument is a preset's name or an npc's: past this it is cut, not refused. */
    private static final int MAX_ARG_LENGTH = 128;
    private static final int MAX_ARGS = 8;
    /** More presets than a screen could page through in an afternoon; the rest stay in the folder. */
    private static final int MAX_NAMES = 2048;
    private static final int MAX_NAME_LENGTH = 64;

    public PacketBossPresetReply {
        key = key == null ? "" : key;
        args = List.copyOf(args == null ? List.of() : args.subList(0, Math.min(args.size(), MAX_ARGS)));
        names = List.copyOf(names == null ? List.of() : names.subList(0, Math.min(names.size(), MAX_NAMES)));
    }

    public static void encode(PacketBossPresetReply packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.action);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.key, MAX_KEY_LENGTH);
        writeStrings(buffer, packet.args, MAX_ARG_LENGTH);
        buffer.writeBoolean(packet.listed);
        writeStrings(buffer, packet.names, MAX_NAME_LENGTH);
        buffer.writeInt(packet.npcId);
    }

    public static PacketBossPresetReply decode(FriendlyByteBuf buffer) {
        int action = buffer.readVarInt();
        boolean success = buffer.readBoolean();
        String key = buffer.readUtf(MAX_KEY_LENGTH);
        List<String> args = readStrings(buffer, MAX_ARGS, MAX_ARG_LENGTH);
        boolean listed = buffer.readBoolean();
        List<String> names = readStrings(buffer, MAX_NAMES, MAX_NAME_LENGTH);
        return new PacketBossPresetReply(action, success, key, args, listed, names, buffer.readInt());
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> strings, int maxLength) {
        buffer.writeVarInt(strings.size());
        for (String string : strings) {
            buffer.writeUtf(string.length() > maxLength ? string.substring(0, maxLength) : string, maxLength);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buffer, int maxCount, int maxLength) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maxCount) {
            throw new DecoderException("a boss preset reply with " + count + " strings, at most " + maxCount);
        }
        List<String> strings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            strings.add(buffer.readUtf(maxLength));
        }
        return strings;
    }

    public static void handle(PacketBossPresetReply packet) {
        CrashGuard.run("packet.boss_preset_reply", packet, PacketBossPresetReply::handleGuarded);
    }

    private static void handleGuarded(PacketBossPresetReply packet) {
        BossPresetClientBridge.accept(packet);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
