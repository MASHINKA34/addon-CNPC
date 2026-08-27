package com.goodbird.cnpcgeckoaddon.network;

import net.minecraft.core.BlockPos;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Keeps the two animation packets from naming client-only classes directly.
 *
 * <p>The same trick as {@link HookCordClientBridge}: the packet classes are loaded on both
 * sides, so the client-side application - which has to reach for {@code Minecraft} - lives
 * in a client class that registers itself here while the client mod loads. Nothing is
 * buffered: the handlers are in place long before a server can send an animation, and an
 * animation that arrived before that would be stale by the next one anyway.</p>
 */
public final class ManualAnimationClientBridge {

    @FunctionalInterface
    public interface EntityHandler {
        void accept(int entityId, RawAnimation animation);
    }

    @FunctionalInterface
    public interface TileHandler {
        void accept(BlockPos pos, RawAnimation animation);
    }

    private static EntityHandler entityHandler;
    private static TileHandler tileHandler;

    private ManualAnimationClientBridge() {
    }

    public static void setHandlers(EntityHandler entity, TileHandler tile) {
        entityHandler = entity;
        tileHandler = tile;
    }

    public static void acceptEntity(int entityId, RawAnimation animation) {
        if (entityHandler != null) {
            entityHandler.accept(entityId, animation);
        }
    }

    public static void acceptTile(BlockPos pos, RawAnimation animation) {
        if (tileHandler != null) {
            tileHandler.accept(pos, animation);
        }
    }
}
