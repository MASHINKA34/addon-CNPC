package com.goodbird.cnpcgeckoaddon.network;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import noppes.npcs.CustomNpcs;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@EventBusSubscriber(bus=EventBusSubscriber.Bus.MOD, modid=CNPCGeckoAddon.MODID)
public class NetworkWrapper {


    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registerPacket(registrar, PacketSyncAnimation.TYPE,PacketSyncAnimation::encode,PacketSyncAnimation::decode,PacketSyncAnimation::handle);
        registerPacket(registrar, PacketSyncTileAnimation.TYPE,PacketSyncTileAnimation::encode,PacketSyncTileAnimation::decode,PacketSyncTileAnimation::handle);
        registerPacket(registrar, PacketSyncBossBarStyle.TYPE, PacketSyncBossBarStyle::encode,
                PacketSyncBossBarStyle::decode, PacketSyncBossBarStyle::handle);
        registerPacket(registrar, PacketSyncBossTimer.TYPE, PacketSyncBossTimer::encode,
                PacketSyncBossTimer::decode, PacketSyncBossTimer::handle);
        registerPacket(registrar, PacketSyncHookCord.TYPE, PacketSyncHookCord::encode,
                PacketSyncHookCord::decode, PacketSyncHookCord::handle);
    }

    /**
     * Builds the payload id for a packet class. Locale.ROOT matters: the default locale
     * would turn "I" into a dotless "ı" on Turkish systems, which is not a legal
     * ResourceLocation character and would abort mod loading there.
     */
    public static <MSG extends CustomPacketPayload> CustomPacketPayload.Type<MSG> typeOf(Class<MSG> messageType) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                CNPCGeckoAddon.MODID, messageType.getSimpleName().toLowerCase(Locale.ROOT)));
    }

    public static <MSG extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar , CustomPacketPayload.Type<MSG> type, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder, TriConsumer<MSG, MinecraftServer, ServerPlayer> handle) {
        registrar.commonToServer(
                type,
                CustomPacketPayload.codec(encoder::accept, decoder::apply),
                (packet, context) -> handle.accept(packet, context.player().getServer(), (ServerPlayer) context.player())
        );
    }

    public static <MSG extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar , CustomPacketPayload.Type<MSG> type, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder, Consumer<MSG> handle) {
        registrar.commonToClient(type, CustomPacketPayload.codec(encoder::accept, decoder::apply),
                (packet, context) -> context.enqueueWork(() -> handle.accept(packet)));
    }

    public static <MSG extends CustomPacketPayload> void send(ServerPlayer player, MSG msg) {
        PacketDistributor.sendToPlayer(player, msg);
    }


    /**
     * Sends to everyone with the entity loaded, which for a world effect is exactly the set
     * of players who can see it - bystanders included, and nobody a continent away.
     */
    public static <MSG extends CustomPacketPayload> void sendToTracking(Entity entity, MSG msg) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, msg);
    }


    public static <MSG extends CustomPacketPayload> void sendAll(MSG msg) {
        MinecraftServer server = CustomNpcs.Server;
        if (server == null) {
            // Called from a script or a boss tick before the server is up, or client-side.
            return;
        }
        for(ServerPlayer player: server.getPlayerList().getPlayers()) {
            send(player, msg);
        }
    }
}
