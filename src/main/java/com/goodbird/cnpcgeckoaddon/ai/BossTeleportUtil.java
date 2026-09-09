package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The blink every boss teleport is made of: a spot it can stand on, and the hop itself.
 *
 * <p>Shared by the path and the cast spots, so the sound, the landing, the navigation reset
 * and the stationary pin all happen the same way wherever the boss is sent. A hop that
 * forgot one of them is a boss that plays no sound, or one the pin yanks straight back to
 * where it left.</p>
 */
final class BossTeleportUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private BossTeleportUtil() {
    }

    /**
     * Where the boss can actually stand at or just above {@code (x, y, z)}, or null when nowhere.
     *
     * <p>The CustomNPCs pather stores the block that was clicked, and a builder reading a
     * spot off the floor writes the floor block the same way. For a normal floor click that
     * block is one below the feet, while the path's first point already stores feet Y, so
     * the exact coordinate is tried first for compatibility and the spot is then
     * transparently lifted by one.</p>
     */
    static Vec3 findSafeDestination(ServerLevel level, EntityNPCInterface npc, double x, double y, double z) {
        for (int yOffset = 0; yOffset <= 1; yOffset++) {
            double lifted = y + yOffset;
            BlockPos blockPos = BlockPos.containing(x, lifted, z);
            AABB destinationBox = npc.getBoundingBox().move(x - npc.getX(), lifted - npc.getY(), z - npc.getZ());
            if (level.hasChunkAt(blockPos)
                    && level.getWorldBorder().isWithinBounds(blockPos)
                    && level.noCollision(npc, destinationBox)) {
                return new Vec3(x, lifted, z);
            }
        }
        return null;
    }

    /**
     * Blinks the boss onto {@code destination}, with the sound at both ends when it is on.
     *
     * @param what the destination as the log names it: "path point 3", "BEAM cast spot"
     * @return false when CustomNPCs vetoed the hop from a script hook; the boss stays put
     */
    static boolean teleport(ServerLevel level, EntityNPCInterface npc, TeleportPathController boss,
                            Vec3 destination, boolean sound, String what) {
        try {
            if (sound) {
                level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            npc.teleportTo(destination.x, destination.y, destination.z);
            npc.fallDistance = 0.0F;
            npc.setDeltaMovement(Vec3.ZERO);
            npc.getNavigation().stop();
            // The npc is standing on the destination now, so the pin is taken from it rather
            // than set by hand - one place decides what "where the boss is" means.
            boss.rememberCurrentPosition();
            npc.gameEvent(GameEvent.TELEPORT);
            if (sound) {
                level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            return true;
        } catch (Throwable error) {
            // CustomNPCs is free to veto or break a teleport from a script hook. The boss
            // stays where it is, but somebody debugging a boss that never moves deserves to
            // find this in the log.
            LOGGER.warn("Boss {} could not teleport to {}: {}",
                    npc.getName().getString(), what, error.getMessage());
            boss.rememberCurrentPosition();
            return false;
        }
    }
}
