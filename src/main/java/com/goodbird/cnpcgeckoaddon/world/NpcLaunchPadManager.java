package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.ai.ArcPhysics;
import com.goodbird.cnpcgeckoaddon.ai.BossCaptureManager;
import com.goodbird.cnpcgeckoaddon.ai.BossCocoonManager;
import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
import com.goodbird.cnpcgeckoaddon.data.NpcLaunchPadData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcLaunchPadData;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
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
 * <p>A launched player is remembered until they land, for two things: a pad does not throw
 * somebody already in the air on a throw, and their landing can be forgiven its fall damage.
 * None of that is saved - a pause is seconds long and a flight shorter, so a restart simply
 * forgets both. The one thing that is saved is when a pad a boss summoned has to go.</p>
 */
public final class NpcLaunchPadManager {
    /**
     * Game time a summoned pad goes away at, in its persistent data: written on its first tick
     * with a lifetime, and saved with it, so a restart halfway through keeps the clock running.
     */
    public static final String DIES_AT_KEY = "GeckoLaunchDiesAt";

    /**
     * One throw, until the player lands or the game time it runs out at passes.
     *
     * @param landsFrom the first game time a landing can belong to this throw: nobody comes down
     *                  before the top of the arc, so a fall reported earlier is one their client
     *                  finished before the push reached it
     */
    private record Flight(long landsFrom, long until, boolean noFallDamage) {
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
        if (expire(level, pad, data, gameTime)) {
            return;
        }
        // A carried pad would throw its own carrier out of their hands.
        if (!pad.isAlive() || pad.isKilled() || NpcCarryManager.isCarried(pad)) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }
        AABB touch = pad.getBoundingBox().inflate(data.getTouchMarginTenths() / 10.0D);
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

    /**
     * Takes a pad a boss summoned out of the arena once its lifetime is up.
     *
     * <p>Only a minion: a pad placed by hand is part of the map, and the lifetime on it is read
     * as meant for the clones made from it. It goes the way a totem goes - discarded, because a
     * kill would run the clone's death scripts and drop its loot first.</p>
     *
     * @return whether the pad is gone
     */
    private static boolean expire(ServerLevel level, EntityNPCInterface pad, NpcLaunchPadData data, long gameTime) {
        if (data.getLifetimeTicks() <= 0 || pad.isRemoved() || !BossMinionUtil.isMinion(pad)) {
            return false;
        }
        // Read through the util: asking the entity itself hangs an empty tag on every npc it sees.
        CompoundTag saved = PersistentDataUtil.read(pad);
        if (!saved.contains(DIES_AT_KEY, Tag.TAG_LONG)) {
            pad.getPersistentData().putLong(DIES_AT_KEY, gameTime + data.getLifetimeTicks());
            return false;
        }
        if (gameTime < saved.getLong(DIES_AT_KEY)) {
            return false;
        }
        data.getExpireParticles().emit(level, pad.getX(), pad.getY(0.5D), pad.getZ(),
                pad.getBbWidth() * 0.5D, pad.getBbHeight() * 0.5D, pad.getBbWidth() * 0.5D, 0.02D);
        pad.discard();
        return true;
    }

    /**
     * A launched player has come down: their flight is over, and the answer is whether this
     * landing is one to spare the fall damage of.
     *
     * <p>Called from the fall events. Past the flight's time it is an unrelated fall, and hurts.</p>
     */
    public static boolean land(LivingEntity entity) {
        // Server state; the fall events fire on the client too, and on an integrated server that
        // is the same static map from another thread.
        if (entity.level().isClientSide || FLIGHTS.isEmpty()) {
            return false;
        }
        Flight flight = FLIGHTS.get(entity.getUUID());
        if (flight == null) {
            return false;
        }
        long gameTime = entity.level().getGameTime();
        if (gameTime < flight.landsFrom()) {
            // The end of a hop they threw themselves into the pad with. The flight is still ahead.
            return flight.noFallDamage();
        }
        FLIGHTS.remove(entity.getUUID());
        return flight.noFallDamage() && gameTime < flight.until();
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
        long landsFrom = gameTime + (long) Math.ceil(ArcPhysics.riseTicks(arc.velocity().y));
        FLIGHTS.put(player.getUUID(), new Flight(landsFrom,
                gameTime + arc.flightTicks() + data.getLandingGraceTicks(), data.isNoFallDamage()));

        // The switch stays over both cues, the way the one label on the screen reads: a pad
        // told to be quiet was always a pad that kicked up nothing either.
        if (data.isSound()) {
            data.getLaunchSound().play(level, player.getX(), player.getY(), player.getZ(),
                    SoundSource.NEUTRAL);
            data.getLaunchParticles().emit(level, player.getX(), player.getY() + 0.1D, player.getZ(),
                    0.3D, 0.05D, 0.3D, 0.05D);
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
