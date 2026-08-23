package com.goodbird.cnpcgeckoaddon.mixin.compat;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "top.ribs.scguns.network.message.C2SMessageReload", remap = false)
public class MixinC2SMessageReload {

    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE", target = "Ltop/ribs/scguns/client/handler/ReloadHandler;loaded(Lnet/minecraft/world/entity/player/Player;)V"), remap = false)
    private static void cnpcgeckoaddon$skipClientReloadHandler(Player player) {
    }
}
