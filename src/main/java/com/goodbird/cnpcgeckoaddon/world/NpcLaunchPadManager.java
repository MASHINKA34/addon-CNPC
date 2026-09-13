package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.ArcPhysics;
import com.goodbird.cnpcgeckoaddon.ai.BossCaptureManager;
import com.goodbird.cnpcgeckoaddon.ai.BossCocoonManager;
import com.goodbird.cnpcgeckoaddon.data.NpcLaunchPadData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcLaunchPadData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Throws whoever touches a launch pad npc along an arc onto the block the pad names.
 *
 * <p>Only players are thrown, and only with a speed: a player's own client moves them, so the
 * push is set on the server and handed over by the tracker, and the flight after it is the
 * client's physics - which {@link ArcPhysics} solves the push for. Npcs and mobs walk into a pad
 * and nothing happens to them.</p>
 *
 * <p>Nothing here is saved. A pause is seconds long and a flight shorter, so a restart simply
 * forgets both.</p>
 */
public final class NpcLaunchPadManager {
    /**
     * How far past the pad's own box a player still counts as touching it. A solid hitbox cannot
     * be walked into, so standing against it from outside has to be the touch.
     */
    private static final double TOUCH_MARGIN = 0.3D;
    /** How long past the solved flight a launched player is still treated as in the air. */
    private static final int LANDING_GRACE_TICKS = 40;
    /** The motes kicked up at the player's feet when they are thrown. */
    private static final int LAUNCH_PARTICLES = 12;

    /** One throw, until the player lands or the game time it runs out at passes. */
    private record Flight(long until, boolean noFallDamage) {
    }

    /** Pad id -> player id -> the game time from which that pad may throw that player again. */
    private static final Map<UUID, Map<UUID, Long>> READY_AT = new HashMap<>();
    /** Player id -> the throw they are on. */
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();

    private NpcLaunchPadManager() {
    }

    /** One server tick of one npc: nothing unless it is a pad, then everyone touching it. */
    public static void tick(EntityNPCInterface pad) {
        NpcLaunchPadData data = ((INpcLaunchPadData) pad.ais).cnpcgeckoaddon$getNpcLaunchPadData();
        if (!data.isEnabled() || !(pad.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        // A carried pad would throw its own carrier out of their hands.
        if (!pad.isAlive() || pad.isKilled() || NpcCarryManager.isCarried(pad)) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        AABB touch = pad.getBoundingBox().inflate(TOUCH_MARGIN);
        Vec3 landing = null;
        for (ServerPlayer player : players) {
            if (!touch.intersects(player.getBoundingBox()) || !isThrowable(player, gameTime)
                    || !isReady(pad, player, gameTime)) {
                continue;
            }
            if (landing == null) {
                landing = landingPoint(data, pad.blockPosition());
            }
            launch(level, pad, data, player, landing, gameTime);
        }
    }

    /**
     * The point a pad throws players at: the middle of the top of the block it names, counted
     * from the block the pad stands in unless the coordinates are absolute.
     */
    public static Vec3 landingPoint(NpcLaunchPadData data, BlockPos padBlock) {
        long x = data.getX();
        long y = data.getY();
        long z = data.getZ();
        if (data.getCoordinateMode() == NpcLaunchPadData.COORDINATE_NPC_OFFSET) {
            x += padBlock.getX();
            y += padBlock.getY();
            z += padBlock.getZ();
        }
        return new Vec3(x + 0.5D, y + 1.0D, z + 0.5D);
    }

    /** Whether this player can be thrown at all right now, by any pad. */
    private static boolean isThrowable(ServerPlayer player, long gameTime) {
        // The arc is solved for a body that falls: a rider, a flyer and a glider do not.
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying()) {
            return false;
        }
        // Held still every tick by the capture or the cocoon; a throw would only tell the client
        // one thing and the server another.
        if (BossCaptureManager.isCaptured(player.getUUID()) || BossCocoonManager.isCocooned(player.getUUID())) {
            return false;
        }
        // Something else gave them a speed this tick - a hit's knockback - and the tracker is
        // about to send it. That one goes first; a player still touching is thrown next tick.
        if (player.hurtMarked) {
            return false;
        }
        // One throw at a time. Their client only takes off once the push reaches it, and until
        // then the server still has them standing against the pad: thrown again from there, the
        // second push would land on top of a flight already under way.
        Flight flight = FLIGHTS.get(player.getUUID());
        return flight == null || gameTime >= flight.until();
    }

    private static boolean isReady(EntityNPCInterface pad, ServerPlayer player, long gameTime) {
        Map<UUID, Long> ready = READY_AT.get(pad.getUUID());
        Long readyAt = ready == null ? null : ready.get(player.getUUID());
        return readyAt == null || gameTime >= readyAt;
    }

    private static void launch(ServerLevel level, EntityNPCInterface pad, NpcLaunchPadData data,
                               ServerPlayer player, Vec3 landing, long gameTime) {
        double keep = horizontalKeep(player);
        ArcPhysics.Launch arc = ArcPhysics.launch(landing.x - player.getX(), landing.y - player.getY(),
                landing.z - player.getZ(), data.getArcHeight(), keep);
        player.setDeltaMovement(ArcPhysics.beforeServerTravel(arc.velocity(), keep));
        // Whatever they were already falling is not what the landing should be measured from.
        player.fallDistance = 0.0F;
        // Players simulate their own movement, so the server has to push the new velocity to
        // them explicitly. hurtMarked is what makes ServerEntity send it.
        player.hurtMarked = true;

        forgetExpired(gameTime);
        READY_AT.computeIfAbsent(pad.getUUID(), id -> new HashMap<>())
                .put(player.getUUID(), gameTime + data.getCooldownTicks());
        FLIGHTS.put(player.getUUID(), new Flight(gameTime + arc.flightTicks() + LANDING_GRACE_TICKS,
                data.isNoFallDamage()));

        if (data.isSound()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_FALL,
                    SoundSource.NEUTRAL, 1.0F, 1.2F);
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1D, player.getZ(),
                    LAUNCH_PARTICLES, 0.3D, 0.05D, 0.3D, 0.05D);
        }
    }

    /**
     * What the server's movement pass for this player keeps of a sideways speed - and what their
     * client keeps on the first tick of the flight, while it still has them on the ground: block
     * friction times the air drag on the ground, the air drag alone off it. Multiplied in floats,
     * the way that pass does, so undoing it is exact.
     */
    private static double horizontalKeep(ServerPlayer player) {
        if (!player.onGround()) {
            return 0.91F;
        }
        BlockPos below = player.getBlockPosBelowThatAffectsMyMovement();
        return player.level().getBlockState(below).getFriction(player.level(), below, player) * 0.91F;
    }

    /**
     * Drops the pauses and flights that have run out.
     *
     * <p>Run when something new is written rather than every tick: a stale entry answers the same
     * as a missing one, so all this does is keep a pad that was removed mid pause from leaving its
     * entries behind for good.</p>
     */
    private static void forgetExpired(long gameTime) {
        FLIGHTS.values().removeIf(flight -> gameTime >= flight.until());
        READY_AT.values().removeIf(players -> {
            players.values().removeIf(readyAt -> gameTime >= readyAt);
            return players.isEmpty();
        });
    }
}
