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

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@EventBusSubscriber(modid=CNPCGeckoAddon.MODID)
public class NetworkWrapper {

    @FunctionalInterface
    public interface ServerHandler<MSG> {
        void accept(MSG packet, MinecraftServer server, ServerPlayer player);
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registerPacket(registrar.versioned("2"), PacketSyncAnimation.TYPE,PacketSyncAnimation::encode,PacketSyncAnimation::decode,PacketSyncAnimation::handle);
        registerPacket(registrar.versioned("2"), PacketSyncTileAnimation.TYPE,PacketSyncTileAnimation::encode,PacketSyncTileAnimation::decode,PacketSyncTileAnimation::handle);
        registerPacket(registrar, PacketSyncBossBarStyle.TYPE, PacketSyncBossBarStyle::encode,
                PacketSyncBossBarStyle::decode, PacketSyncBossBarStyle::handle);
        registerPacket(registrar, PacketSyncBossTimer.TYPE, PacketSyncBossTimer::encode,
                PacketSyncBossTimer::decode, PacketSyncBossTimer::handle);
        registerPacket(registrar, PacketSyncHookCord.TYPE, PacketSyncHookCord::encode,
                PacketSyncHookCord::decode, PacketSyncHookCord::handle);
        registerPacket(registrar, PacketSyncBossLink.TYPE, PacketSyncBossLink::encode,
                PacketSyncBossLink::decode, PacketSyncBossLink::handle);
        registerPacket(registrar, PacketSyncBossCaptureState.TYPE, PacketSyncBossCaptureState::encode,
                PacketSyncBossCaptureState::decode, PacketSyncBossCaptureState::handle);
        registerPacket(registrar, PacketRestoreBossTotems.TYPE, PacketRestoreBossTotems::encode,
                PacketRestoreBossTotems::decode, PacketRestoreBossTotems::handle);
        registerPacket(registrar, PacketNpcCarryThrow.TYPE, PacketNpcCarryThrow::encode,
                PacketNpcCarryThrow::decode, PacketNpcCarryThrow::handle);
        registrar.playToClient(PacketSyncNpcCarryState.TYPE,
                CustomPacketPayload.codec(PacketSyncNpcCarryState::encode, PacketSyncNpcCarryState::decode),
                (packet, context) -> PacketSyncNpcCarryState.handle(packet));
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

    public static <MSG extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar , CustomPacketPayload.Type<MSG> type, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder, ServerHandler<MSG> handle) {
        registrar.playToServer(
                type,
                CustomPacketPayload.codec(encoder::accept, decoder::apply),
                (packet, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    MinecraftServer server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    context.enqueueWork(() -> handle.accept(packet, server, player));
                }
        );
    }

    public static <MSG extends CustomPacketPayload> void registerPacket(PayloadRegistrar registrar , CustomPacketPayload.Type<MSG> type, BiConsumer<MSG, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, MSG> decoder, Consumer<MSG> handle) {
        registrar.commonToClient(type, CustomPacketPayload.codec(encoder::accept, decoder::apply),
                (packet, context) -> context.enqueueWork(() -> handle.accept(packet)));
    }

    public static <MSG extends CustomPacketPayload> void send(ServerPlayer player, MSG msg) {
        PacketDistributor.sendToPlayer(player, msg);
    }

    public static <MSG extends CustomPacketPayload> void sendToServer(MSG msg) {
        PacketDistributor.sendToServer(msg);
    }


    /**
     * Sends to everyone with the entity loaded, which for a world effect is exactly the set
     * of players who can see it - bystanders included, and nobody a continent away.
     */
    public static <MSG extends CustomPacketPayload> void sendToTracking(Entity entity, MSG msg) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, msg);
    }
}
