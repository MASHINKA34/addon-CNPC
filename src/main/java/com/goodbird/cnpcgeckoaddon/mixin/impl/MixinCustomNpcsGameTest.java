package com.goodbird.cnpcgeckoaddon.mixin.impl;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.storage.LevelResource;
import noppes.npcs.CustomNpcs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(value = CustomNpcs.class, remap = false)
public abstract class MixinCustomNpcsGameTest {
    @Inject(method = "getLevelSaveDirectory(Ljava/lang/String;)Ljava/io/File;",
            at = @At("HEAD"), cancellable = true)
    private static void cnpcgeckoaddon$gameTestSaveDirectory(String name, CallbackInfoReturnable<File> cir) {
        if (CustomNpcs.Server instanceof GameTestServer server) {
            Path directory = server.getWorldPath(new LevelResource("customnpcs"));
            if (name != null) {
                directory = directory.resolve(name);
            }
            try {
                cir.setReturnValue(Files.createDirectories(directory).toFile());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
