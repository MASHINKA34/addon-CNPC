package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.world.BossPresetManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The presets screen asking the server for the list of presets, or to save or load one for the npc
 * the player is editing. Which npc that is, is the server's own record - CustomNPCs' editing npc for
 * the player - never an id the client names, and only an operator gets anything but a refusal.
 * Every request is answered with a {@link PacketBossPresetReply}.
 */
public final class PacketBossPresetRequest implements CustomPacketPayload {
    public static final Type<PacketBossPresetRequest> TYPE = NetworkWrapper.typeOf(PacketBossPresetRequest.class);

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    public static final int LIST = 0;
    public static final int SAVE = 1;
    public static final int LOAD = 2;

    /**
     * The most of a name that travels: room for what was typed, which the server cleans and cuts
     * itself, rather than a name cut short on the way.
     */
    public static final int MAX_NAME_LENGTH = 64;

    private final int action;
    private final String name;

    public PacketBossPresetRequest(int action, String name) {
        this.action = action;
        String text = name == null ? "" : name;
        this.name = text.length() > MAX_NAME_LENGTH ? text.substring(0, MAX_NAME_LENGTH) : text;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
        buffer.writeUtf(name, MAX_NAME_LENGTH);
    }

    public static PacketBossPresetRequest decode(FriendlyByteBuf buffer) {
        return new PacketBossPresetRequest(buffer.readVarInt(), buffer.readUtf(MAX_NAME_LENGTH));
    }

    public static void handle(PacketBossPresetRequest packet, MinecraftServer server, ServerPlayer player) {
        CrashGuard.run("packet.boss_preset_request", () -> handleGuarded(packet, player));
    }

    private static void handleGuarded(PacketBossPresetRequest packet, ServerPlayer player) {
        String who = player.getGameProfile().getName();
        if (packet.action < LIST || packet.action > LOAD) {
            LOGGER.warn("Boss preset request from {} ignored: no action {}", who, packet.action);
            return;
        }
        LOGGER.debug("Boss preset request from {}: {} '{}'", who, actionName(packet.action), packet.name);
        EntityNPCInterface npc = null;
        BossPresetManager.Result result;
        if (!player.hasPermissions(2)) {
            result = BossPresetManager.denied(who, actionName(packet.action));
        } else if (packet.action == LIST) {
            result = BossPresetManager.list(who);
        } else {
            npc = editingNpc(player);
            result = packet.action == SAVE ? BossPresetManager.save(npc, packet.name, who)
                    : BossPresetManager.load(npc, packet.name, who);
        }
        BossPresetManager.Outcome outcome = result.outcome();
        // Every answer but a refusal carries the list as it is now, so the screen never shows a stale one.
        boolean listed = outcome != BossPresetManager.Outcome.DENIED && outcome != BossPresetManager.Outcome.LIST_FAILED;
        List<String> names = !listed ? List.of()
                : outcome == BossPresetManager.Outcome.LISTED ? result.names() : BossPresetManager.names();
        NetworkWrapper.send(player, new PacketBossPresetReply(packet.action, outcome.succeeded(), outcome.key(),
                result.args(), listed, names, npc == null ? -1 : npc.getId()));
    }

    /** The npc CustomNPCs has this player editing, as its own menu saves go to; null when there is none. */
    private static EntityNPCInterface editingNpc(ServerPlayer player) {
        EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
        return npc == null || npc.isRemoved() ? null : npc;
    }

    static String actionName(int action) {
        return switch (action) {
            case LIST -> "list";
            case SAVE -> "save";
            case LOAD -> "load";
            default -> "action " + action;
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
