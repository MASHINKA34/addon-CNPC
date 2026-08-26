package com.goodbird.cnpcgeckoaddon.world;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server side state of the {@code /cnpcgecko carry} builder tool.
 *
 * <p>Nothing here is ever written to NBT: after a restart nobody is carrying anything.</p>
 */
public final class NpcCarryManager {
    private static final Set<UUID> CARRY_MODE = new HashSet<>();

    private NpcCarryManager() {
    }

    /** @return true when carry mode is on for this player after the toggle */
    public static boolean toggleMode(ServerPlayer player) {
        if (CARRY_MODE.add(player.getUUID())) {
            return true;
        }
        CARRY_MODE.remove(player.getUUID());
        return false;
    }

    public static boolean isCarryMode(ServerPlayer player) {
        return CARRY_MODE.contains(player.getUUID());
    }
}
