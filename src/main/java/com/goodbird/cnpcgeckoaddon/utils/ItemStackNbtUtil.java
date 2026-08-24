package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import noppes.npcs.CustomNpcs;

/**
 * Reads and writes item stacks stored inside boss settings.
 *
 * <p>Since 1.21 an item stack can only be (de)serialized against the registries of the
 * session it belongs to, because its components are registry entries themselves. The boss
 * settings are handed around as a bare {@link CompoundTag} that carries no registry access,
 * and they travel between the editing GUI and the server, so the lookup is taken from
 * whichever side is running the code - exactly what CustomNPCs does for its own drops, only
 * without an entity to ask.</p>
 */
public final class ItemStackNbtUtil {

    private ItemStackNbtUtil() {
    }

    /**
     * @param fallback the tag this stack was last read from, written back when no registries
     *                 are reachable - handing back a stale copy is still better than dropping
     *                 a configured item on the floor
     */
    public static CompoundTag save(ItemStack stack, CompoundTag fallback) {
        HolderLookup.Provider registries = registries();
        if (registries == null) {
            return fallback.copy();
        }
        Tag saved = stack.saveOptional(registries);
        return saved instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public static ItemStack load(CompoundTag tag) {
        HolderLookup.Provider registries = registries();
        if (registries == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(registries, tag);
    }

    private static HolderLookup.Provider registries() {
        MinecraftServer server = CustomNpcs.Server;
        if (server != null) {
            return server.registryAccess();
        }
        // No server in this process means a client connected to a remote one, where the
        // level carries the registries the server sent over.
        return FMLEnvironment.dist == Dist.CLIENT ? ClientRegistries.get() : null;
    }

    /** Its own class so the client-only lookup is never loaded on a dedicated server. */
    private static final class ClientRegistries {

        private ClientRegistries() {
        }

        static HolderLookup.Provider get() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? null : level.registryAccess();
        }
    }
}
