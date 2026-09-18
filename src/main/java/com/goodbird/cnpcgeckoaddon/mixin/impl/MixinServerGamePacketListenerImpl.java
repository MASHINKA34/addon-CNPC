package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.ai.BossCaptureManager;
import com.goodbird.cnpcgeckoaddon.ai.BossCocoonManager;
import com.goodbird.cnpcgeckoaddon.ai.BossHurricaneScheduler;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rejects captured, cocooned or storm-held player position packets before vanilla movement validation applies them. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
                    + "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
                    + "Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER),
            cancellable = true)
    private void cnpcgeckoaddon$lockCapturedPlayer(ServerboundMovePlayerPacket packet,
                                                   CallbackInfo ci) {
        // Every movement packet of every player comes through here, so the guard is a try
        // written out. A hold that fails to answer lets the packet through the vanilla way.
        try {
            if (BossCaptureManager.handleMovePacket(player, packet)
                    || BossCocoonManager.handleMovePacket(player, packet)
                    || BossHurricaneScheduler.handleMovePacket(player, packet)) {
                ci.cancel();
            }
        } catch (Throwable error) {
            CrashGuard.caught("mixin.move_packet", error);
        }
    }
}
