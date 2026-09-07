package com.goodbird.cnpcgeckoaddon.mixin.compat;

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

/**
 * Points CustomNPCs' save directory at the gametest server's own world.
 *
 * <p>Gated to gametest runs by {@link CompatMixinPlugin}: {@code GameTestServer} is a type
 * a packaged instance never builds, so in production this only ever added a branch to a
 * hot-ish CustomNPCs call that could not be taken. It lives in the compat config for the
 * same reason the third-party patches do - it is not part of what the addon does.</p>
 */
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
