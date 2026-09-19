package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossRiftState;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every reality rift that is open, and everyone it took.
 *
 * <p>A rift belongs to its boss and lives here rather than on the boss' controller, because its
 * players are a dimension away from the boss for its whole length: it is ticked with the rift
 * dimension's level, and it asks after the boss by UUID when it needs it. A boss that is gone -
 * dead, reset, unloaded - takes its rift with it, and everyone taken comes back without an outcome.</p>
 *
 * <p>Nothing here is saved. The one trace a trip leaves on disk is a record on the player, kept
 * under {@code PlayerPersisted} - the level and the spot they were taken from - so a player who
 * logs out in a rift, or is in one when the server stops, is put back where they stood when they
 * next come in, whatever became of the rift meanwhile.</p>
 */
public final class BossRiftManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** The record a taken player carries: where to put them back. */
    public static final String RECORD_KEY = "CNPCGeckoRiftReturn";
    private static final String RECORD_LEVEL = "Level";
    private static final String RECORD_X = "X";
    private static final String RECORD_Y = "Y";
    private static final String RECORD_Z = "Z";
    private static final String RECORD_YAW = "Yaw";
    private static final String RECORD_PITCH = "Pitch";
    private static final String RECORD_BOSS = "Boss";

    /** How far from the centre, at random, each taken player lands. */
    private static final double LANDING_SPREAD = 2.0D;
    /** The countdown in the action bar, once a second. */
    private static final int STATUS_INTERVAL_TICKS = 20;
    /** The look of the rift sent again every five seconds, for a client that missed it. */
    private static final int SYNC_INTERVAL_TICKS = 100;
    /**
     * Keeps the boss' own chunk loaded and ticking while its players are away: a solo rift leaves
     * nobody on the arena, and the boss would unload with its chunk and take the rift with it.
     * The lifespan is only a net under a rift that somehow never closes; every rift takes its
     * ticket off itself.
     */
    private static final TicketType<UUID> BOSS_TICKET = TicketType.create("cnpcgeckoaddon_rift",
            Comparator.<UUID>naturalOrder(), BossRiftSettings.MAX_TIME_LIMIT_TICKS + 1200);
    /** Two chunks of ticket radius: the boss' own chunk entity-ticking. */
    private static final int BOSS_TICKET_DISTANCE = 2;

    private static final Map<UUID, Rift> BY_BOSS = new LinkedHashMap<>();
    private static final Map<UUID, Trip> BY_PLAYER = new HashMap<>();
    /** Players the rift is moving right now, whose change of dimension is its own doing. */
    private static final Set<UUID> MOVING = new HashSet<>();
    /** Failures' hits on the arena still waiting for the players their rift sent back to land, by boss. */
    private static final Map<UUID, Strike> STRIKES = new LinkedHashMap<>();

    /** One taken player: where they came from, and whose rift took them. */
    record Trip(UUID playerId, UUID bossId, ResourceKey<Level> fromLevel, Vec3 from, float yaw, float pitch) {
    }

    /**
     * A failure's hit on the arena, owed until the players its rift sent back have landed.
     *
     * @param settings the rules the rift opened under
     * @param landing  the players it sent back, whom the hit waits for
     * @param closedAt the tick the rift closed on
     */
    record Strike(UUID bossId, ResourceKey<Level> bossLevel, BossRiftSettings settings, List<UUID> landing,
                  long closedAt) {
    }

    /** One open rift. */
    static final class Rift {
        final UUID bossId;
        final String bossName;
        final ResourceKey<Level> bossLevel;
        final ChunkPos bossChunk;
        /** What the phase said when the rift opened; it closes under the same rules. */
        final BossRiftSettings settings;
        final boolean solo;
        final int exitMode;
        final BlockPos centre;
        final Vec3 landing;
        final long startedAt;
        final long endsAt;
        /** Everyone still in it, online or not. */
        final Set<UUID> inside = new LinkedHashSet<>();
        /** The minions still standing, by UUID; a dead one is taken off as it dies. */
        final Set<UUID> minions = new LinkedHashSet<>();
        /** Where each minion was last seen, to tell one that is gone from one whose chunk is. */
        final Map<UUID, BlockPos> minionSpots = new HashMap<>();
        /** The tick the minions are stood up on: the one after the players landed. */
        final long minionsAt;
        boolean minionsReady;
        int minionsSpawned;
        int taken;
        int deaths;
        long nextStatusAt;
        long nextSyncAt;
        long nextLoopAt;
        long nextAmbientAt;

        Rift(EntityNPCInterface boss, ServerLevel arena, BossRiftSettings settings, boolean solo, int exitMode,
             BlockPos centre, long gameTime) {
            this.bossId = boss.getUUID();
            this.bossName = boss.getName().getString();
            this.bossLevel = arena.dimension();
            this.bossChunk = new ChunkPos(boss.blockPosition());
            this.settings = settings;
            this.solo = solo;
            this.exitMode = exitMode;
            this.centre = centre;
            this.landing = BossRiftDimension.standingSpot(centre);
            this.startedAt = gameTime;
            this.endsAt = gameTime + settings.getTimeLimitTicks();
            this.minionsAt = gameTime + 1L;
            this.nextStatusAt = gameTime;
            this.nextSyncAt = gameTime + SYNC_INTERVAL_TICKS;
            this.nextLoopAt = gameTime + settings.getLoopIntervalTicks();
            this.nextAmbientAt = gameTime;
        }
    }

    private BossRiftManager() {
    }

    /** Whether any rift is open, or any failure's hit still owed, so an idle server skips the walk below. */
    public static boolean hasPending() {
        return !BY_BOSS.isEmpty() || !STRIKES.isEmpty();
    }

    /** Whether a failed rift of this boss still owes the arena its hit, waiting for its players to land. */
    public static boolean owesStrike(UUID bossId) {
        return STRIKES.containsKey(bossId);
    }

    /** Whether this boss has a rift open. */
    public static boolean isActive(UUID bossId) {
        return BY_BOSS.containsKey(bossId);
    }

    /** Whether this player is in a rift right now. */
    public static boolean isTaken(UUID playerId) {
        return BY_PLAYER.containsKey(playerId);
    }

    /**
     * What this boss takes of every hit right now: the group's share while a group's rift is open,
     * all of it otherwise.
     */
    public static int damagePercent(UUID bossId) {
        Rift rift = BY_BOSS.get(bossId);
        return rift == null ? 100 : BossRiftOutcome.damagePercent(rift.solo, rift.settings.getGroupDamagePercent());
    }

    /** Whether a minion belongs to a rift that is still open, so a chunk loading it back lets it in. */
    public static boolean isLiveMinion(UUID minionId) {
        for (Rift rift : BY_BOSS.values()) {
            if (rift.minions.contains(minionId)) {
                return true;
            }
        }
        return false;
    }

    /** A rift minion dying: one fewer between its rift's players and the way home. */
    public static void onMinionDeath(Entity minion) {
        for (Rift rift : BY_BOSS.values()) {
            if (rift.minions.remove(minion.getUUID())) {
                rift.minionSpots.remove(minion.getUUID());
                return;
            }
        }
    }

    /** Where a rift of these settings puts its platform: the builder's spot, or the boss' slot. */
    public static BlockPos centreOf(BossRiftSettings settings, UUID bossId) {
        if (settings.isPrebuilt()) {
            return new BlockPos(settings.getArenaX(), settings.getArenaY(), settings.getArenaZ());
        }
        return BossRiftDimension.slotCentre(BossRiftDimension.resolveSlot(settings.getSlot(), bossId),
                settings.getPlatformY());
    }

    /**
     * Lays the platform in its slot unless it is standing already: its centre block holding the
     * floor block is the sign, so a platform a builder has since decorated is never laid over.
     *
     * @param force lay it whatever stands there, for the command that asks for a fresh one
     * @return whether anything was laid
     */
    public static boolean ensurePlatform(ServerLevel rift, BlockPos centre, BossRiftSettings settings, boolean force) {
        BlockState floor = BossRiftDimension.blockOrDefault(settings.getPlatformBlock(),
                Blocks.END_STONE.defaultBlockState(), "platform");
        if (!force && BossRiftDimension.hasPlatform(rift, centre, floor)) {
            return false;
        }
        BlockState wall = BossRiftDimension.blockOrDefault(settings.getWallBlock(),
                Blocks.OBSIDIAN.defaultBlockState(), "wall");
        BlockState light = BossRiftDimension.blockOrDefault(settings.getLightBlock(),
                Blocks.SEA_LANTERN.defaultBlockState(), "light");
        BossRiftDimension.build(rift, centre, settings.getPlatformRadius(), settings.getWallHeight(),
                settings.getLightSpacing(), settings.isPlatformRoof(), floor, wall, light);
        return true;
    }

    /**
     * Opens a rift and takes these players into it.
     *
     * @param live  the phase's rift settings, copied here so the rift keeps its own
     * @param solo  whether the cast was settled as a solo one when it began
     * @return how many were taken; nought when the rift did not open at all
     */
    public static int start(ServerLevel arena, EntityNPCInterface boss, BossRiftSettings live,
                            List<ServerPlayer> victims, boolean solo, long gameTime) {
        if (victims.isEmpty() || BY_BOSS.containsKey(boss.getUUID())) {
            return 0;
        }
        ServerLevel riftLevel = BossRiftDimension.level(arena.getServer());
        if (riftLevel == null) {
            return 0;
        }
        BossRiftSettings settings = live.copy();
        int exitMode = BossRiftSettings.effectiveExitMode(settings.getExitMode(), BossRiftSettings.CRYSTALS_AVAILABLE);
        if (exitMode != settings.getExitMode()) {
            BossRiftDimension.warn("crystals", "Reality rift of {}: its way out needs crystals, which are not in "
                    + "the game yet, so it runs as 'survive the time'", boss.getName().getString());
        }
        BlockPos centre = centreOf(settings, boss.getUUID());
        if (!settings.isPrebuilt()) {
            ensurePlatform(riftLevel, centre, settings, false);
        }
        Rift rift = new Rift(boss, arena, settings, solo, exitMode, centre, gameTime);
        // Open before anyone is taken: whatever fails halfway through the takes leaves a rift the
        // cleanup can find, rather than trips pointing at one nobody knows.
        BY_BOSS.put(rift.bossId, rift);
        for (ServerPlayer player : victims) {
            if (!BY_PLAYER.containsKey(player.getUUID()) && player.level() != riftLevel) {
                take(riftLevel, rift, boss, player);
            }
        }
        if (rift.inside.isEmpty()) {
            BY_BOSS.remove(rift.bossId, rift);
            return 0;
        }
        arena.getChunkSource().addRegionTicket(BOSS_TICKET, rift.bossChunk, BOSS_TICKET_DISTANCE, rift.bossId);
        return rift.inside.size();
    }

    private static void take(ServerLevel riftLevel, Rift rift, EntityNPCInterface boss, ServerPlayer player) {
        UUID id = player.getUUID();
        Trip trip = new Trip(id, rift.bossId, player.level().dimension(), player.position(),
                player.getYRot(), player.getXRot());
        writeRecord(player, trip);
        BY_PLAYER.put(id, trip);
        Vec3 landing = rift.landing.add(spread(player), 0.0D, spread(player));
        if (!move(player, riftLevel, landing, player.getYRot(), player.getXRot())) {
            // Another mod refused the trip: the player stays where they are, with nothing to undo.
            BY_PLAYER.remove(id);
            clearRecord(player);
            return;
        }
        if (!BY_PLAYER.containsKey(id)) {
            // Died on the way in, and the death already let go of the trip and its record.
            return;
        }
        rift.inside.add(id);
        rift.taken++;
        BossRiftSettings settings = rift.settings;
        settings.getEnterSound().play(riftLevel, landing.x, landing.y, landing.z, SoundSource.HOSTILE);
        settings.getEnterParticles().emitDust(riftLevel, landing.x, landing.y + 1.0D, landing.z,
                0.4D, 0.8D, 0.4D, 0.05D, BossAbilityKind.RIFT);
        BossAbilityDamageUtil.applyEffects(player, BossAbilityKind.RIFT, boss, settings.getEffects());
        sync(player, rift);
    }

    /** Tells a taken player's client how the rift looks: the tint, the pulse, the fog and the end. */
    private static void sync(ServerPlayer player, Rift rift) {
        BossRiftSettings settings = rift.settings;
        NetworkWrapper.send(player, new PacketSyncBossRiftState(true, settings.getTintColor(), settings.getTintAlpha(),
                settings.getTintPulseTicks(), settings.getFogColor(), settings.getFogDistance(), rift.endsAt));
    }

    /** Tells a client its player is out of the rift: the tint and the fog go. */
    private static void unsync(ServerPlayer player) {
        NetworkWrapper.send(player, PacketSyncBossRiftState.inactive());
    }

    private static double spread(ServerPlayer player) {
        return (player.getRandom().nextDouble() * 2.0D - 1.0D) * LANDING_SPREAD;
    }

    /**
     * Moves a player on the rift's behalf, so the change of dimension it causes is not read as the
     * player leaving the rift some other way.
     *
     * @return whether the player is now in that level
     */
    private static boolean move(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
        MOVING.add(player.getUUID());
        try {
            player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
        } finally {
            MOVING.remove(player.getUUID());
        }
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        return player.level() == level;
    }

    /** Runs every open rift, and lands every hit a failure owes, on the rift dimension's own tick. */
    public static void tick(ServerLevel level) {
        if (!hasPending() || !BossRiftDimension.isRift(level)) {
            return;
        }
        long gameTime = level.getGameTime();
        for (Rift rift : List.copyOf(BY_BOSS.values())) {
            tickRift(level, rift, gameTime);
        }
        // After the rifts, so a failure that sent nobody back hits on the tick it closed on.
        tickStrikes(level.getServer(), gameTime);
    }

    private static void tickRift(ServerLevel level, Rift rift, long gameTime) {
        MinecraftServer server = level.getServer();
        EntityNPCInterface boss = findBoss(server, rift);
        if (boss == null) {
            // The boss died, reset or unloaded without its controller saying so: whoever it took
            // comes back, and nobody is owed anything.
            finish(server, rift, BossRiftOutcome.Result.EMPTY);
            return;
        }
        // An arena left with nobody on it stops ticking its entities after fifteen seconds; the
        // boss has to go on standing there, pinned, for its players to come back to.
        if (boss.level() instanceof ServerLevel arena) {
            arena.resetEmptyTime();
        }
        // A tick after the players, so they are standing on the platform - and its chunks are
        // loaded round them - before anything is stood up to fight them.
        if (!rift.minionsReady && gameTime >= rift.minionsAt) {
            rift.minionsReady = true;
            if (needsMinions(rift)) {
                spawnMinions(level, rift, boss);
            }
        }
        int minionsLeft = needsMinions(rift) ? minionsLeft(level, rift) : 0;
        boolean status = gameTime >= rift.nextStatusAt;
        boolean resync = gameTime >= rift.nextSyncAt;
        boolean ambient = gameTime >= rift.nextAmbientAt;
        boolean loop = gameTime >= rift.nextLoopAt;
        for (UUID id : List.copyOf(rift.inside)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                // Offline: kept, and put back where they came from when they log in.
                continue;
            }
            if (player.level() != level) {
                // Left the rift some way the dimension change did not report: let go, no way back.
                drop(player, false);
                continue;
            }
            if (!player.isAlive()) {
                if (!BY_PLAYER.containsKey(id)) {
                    // A death that left no trip behind is a death the rift has already counted.
                    rift.inside.remove(id);
                }
                continue;
            }
            guard(level, rift, player);
            if (ambient) {
                rift.settings.getAmbientParticles().emitDust(level, player.getX(), player.getY(1.0D), player.getZ(),
                        1.5D, 1.0D, 1.5D, 0.02D, BossAbilityKind.RIFT);
            }
            if (loop) {
                rift.settings.getLoopSound().play(level, player.getX(), player.getY(), player.getZ(),
                        SoundSource.AMBIENT);
            }
            if (status) {
                player.displayClientMessage(statusLine(rift, gameTime, minionsLeft), true);
            }
            if (resync) {
                sync(player, rift);
            }
        }
        if (status) {
            rift.nextStatusAt = gameTime + STATUS_INTERVAL_TICKS;
        }
        if (resync) {
            rift.nextSyncAt = gameTime + SYNC_INTERVAL_TICKS;
        }
        if (ambient) {
            rift.nextAmbientAt = gameTime + rift.settings.getAmbientIntervalTicks();
        }
        if (loop) {
            rift.nextLoopAt = gameTime + rift.settings.getLoopIntervalTicks();
        }
        BossRiftOutcome.Result result = BossRiftOutcome.judge(rift.exitMode, gameTime, rift.endsAt, rift.taken,
                rift.inside.size(), rift.deaths, rift.settings.isFailOnDeath(), rift.minionsReady,
                rift.minionsSpawned, minionsLeft, crystalsLeft(rift));
        if (result != BossRiftOutcome.Result.RUNNING) {
            finish(server, rift, result);
        }
    }

    /** The leash and the fall guard: whoever strays past either is put back on the centre. */
    private static void guard(ServerLevel level, Rift rift, ServerPlayer player) {
        BossRiftSettings settings = rift.settings;
        double dx = player.getX() - rift.landing.x;
        double dz = player.getZ() - rift.landing.z;
        int leash = settings.getLeashRadius();
        int depth = settings.getFallGuardDepth();
        boolean strayed = leash > 0 && dx * dx + dz * dz > (double) leash * leash;
        boolean falling = depth > 0 && player.getY() < rift.centre.getY() - depth;
        if (strayed || falling) {
            move(player, level, rift.landing, player.getYRot(), player.getXRot());
        }
    }

    /** The countdown line: the time left, and whatever the way out is counting. */
    private static Component statusLine(Rift rift, long gameTime, int minionsLeft) {
        MutableComponent line = Component.translatable("cnpcgeckoaddon.boss.rift_status_time",
                String.valueOf(BossRiftOutcome.secondsLeft(gameTime, rift.endsAt)));
        if (needsMinions(rift) && rift.minionsSpawned > 0) {
            line.append("  ").append(Component.translatable("cnpcgeckoaddon.boss.rift_status_minions",
                    minionsLeft + "/" + rift.minionsSpawned));
        }
        return line.withStyle(style -> style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.RIFT)));
    }

    private static boolean needsMinions(Rift rift) {
        return rift.exitMode == BossRiftSettings.EXIT_MINIONS || rift.exitMode == BossRiftSettings.EXIT_BOTH;
    }

    /**
     * Stands the rift's minions up: on the builder's points in turn - offsets from the platform's
     * centre, or fixed spots in the rift dimension - or on a ring round the centre when the phase
     * lists none, each one set on the nearest of the taken players.
     */
    private static void spawnMinions(ServerLevel level, Rift rift, EntityNPCInterface boss) {
        BossRiftSettings settings = rift.settings;
        List<BossMinionSpawnPoint> points = new ArrayList<>();
        for (BossMinionSpawnPoint point : settings.getMinionPoints().entries()) {
            if (point.isEnabled()) {
                points.add(point);
            }
        }
        int count = settings.getMinionCount();
        double turn = level.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < count; i++) {
            String cloneName = settings.getMinionCloneName();
            int cloneTab = settings.getMinionCloneTab();
            float yaw = Float.NaN;
            Vec3 spot;
            if (!points.isEmpty()) {
                BossMinionSpawnPoint point = points.get(i % points.size());
                spot = point.getCoordinateMode() == BossMinionSpawnPoint.COORDINATE_FIXED
                        ? new Vec3(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D)
                        : rift.landing.add(point.getX(), point.getY(), point.getZ());
                if (!point.getCloneNameOverride().isEmpty()) {
                    cloneName = point.getCloneNameOverride();
                }
                if (point.getCloneTabOverride() > 0) {
                    cloneTab = point.getCloneTabOverride();
                }
                yaw = point.getYaw();
            } else {
                double angle = turn + Math.PI * 2.0D * i / count;
                double radius = settings.getMinionRadius();
                spot = rift.landing.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            }
            Entity minion = BossRiftMinionUtil.spawn(level, boss, cloneName, cloneTab, spot, yaw,
                    nearestInside(level, rift, spot));
            if (minion != null) {
                rift.minions.add(minion.getUUID());
                rift.minionSpots.put(minion.getUUID(), minion.blockPosition());
                rift.minionsSpawned++;
            }
        }
        if (rift.minionsSpawned == 0) {
            BossRiftDimension.warn("minions:" + rift.bossId, "Reality rift of {}: none of its minions could be "
                    + "stood up, so it runs as 'survive the time'", rift.bossName);
        }
    }

    private static LivingEntity nearestInside(ServerLevel level, Rift rift, Vec3 spot) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID id : rift.inside) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level && player.isAlive()) {
                double distance = player.distanceToSqr(spot);
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    /**
     * How many of the rift's minions still stand. One whose chunk is not loaded is counted as
     * standing - it may well be - and one missing from a chunk whose entities are all there was
     * taken away some other road than death, and is counted as gone.
     */
    private static int minionsLeft(ServerLevel level, Rift rift) {
        int left = 0;
        for (UUID id : List.copyOf(rift.minions)) {
            Entity minion = level.getEntity(id);
            if (minion != null) {
                if (minion.isAlive() && !minion.isRemoved()) {
                    left++;
                    rift.minionSpots.put(id, minion.blockPosition());
                } else {
                    rift.minions.remove(id);
                    rift.minionSpots.remove(id);
                }
                continue;
            }
            BlockPos last = rift.minionSpots.get(id);
            if (last != null && level.areEntitiesLoaded(ChunkPos.asLong(last))) {
                rift.minions.remove(id);
                rift.minionSpots.remove(id);
            } else {
                left++;
            }
        }
        return left;
    }

    /**
     * Crystals still to gather. Always none until prompt 90 puts crystals into the rift; the
     * ways out that ask for them run as the survival one meanwhile.
     */
    static int crystalsLeft(Rift rift) {
        return 0;
    }

    /** Closes a rift: everyone still in it comes back, and the boss gets what the result says. */
    private static void finish(MinecraftServer server, Rift rift, BossRiftOutcome.Result result) {
        if (!BY_BOSS.remove(rift.bossId, rift)) {
            return;
        }
        List<UUID> returned = new ArrayList<>();
        for (UUID id : List.copyOf(rift.inside)) {
            Trip trip = BY_PLAYER.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            // An offline player keeps their record, and comes back with it when they log in.
            if (trip != null && player != null && sendBack(server, player, trip.fromLevel(), trip.from(),
                    trip.yaw(), trip.pitch(), rift.settings)) {
                returned.add(id);
            }
        }
        rift.inside.clear();
        if (rift.settings.isMinionRemoveOnEnd()) {
            removeMinions(server, rift);
        }
        rift.minions.clear();
        ServerLevel arena = server.getLevel(rift.bossLevel);
        if (arena != null) {
            arena.getChunkSource().removeRegionTicket(BOSS_TICKET, rift.bossChunk, BOSS_TICKET_DISTANCE, rift.bossId);
        }
        // Last, with everyone on the way home. A failure's hit on the arena is only owed here, not
        // dealt: vanilla keeps a player who has just changed dimension out of harm's way until their
        // client confirms the teleport, and the hit is meant for the players sent back as well as
        // for whoever stayed. It lands once they have landed; see tickStrikes.
        if (result == BossRiftOutcome.Result.SUCCESS || result == BossRiftOutcome.Result.FAILURE) {
            EntityNPCInterface boss = findBoss(server, rift);
            TeleportPathController controller = controllerOf(boss);
            if (controller != null && boss.level() instanceof ServerLevel bossLevel) {
                controller.onRiftFinished(bossLevel, result, rift.solo, rift.settings);
                if (result == BossRiftOutcome.Result.FAILURE
                        && BossRiftOutcome.hitsArena(BossRiftOutcome.penalties(rift.settings))) {
                    STRIKES.put(rift.bossId, new Strike(rift.bossId, rift.bossLevel, rift.settings, returned,
                            bossLevel.getGameTime()));
                }
            }
        }
    }

    /** Lands every failure's hit whose players have landed, or that has waited as long as it may. */
    private static void tickStrikes(MinecraftServer server, long gameTime) {
        if (STRIKES.isEmpty()) {
            return;
        }
        for (Strike strike : List.copyOf(STRIKES.values())) {
            if (!BossRiftOutcome.strikesNow(gameTime, strike.closedAt(), stillLanding(server, strike))) {
                continue;
            }
            // Off the table before it is dealt, so a hit that throws is not dealt again next tick.
            STRIKES.remove(strike.bossId(), strike);
            EntityNPCInterface boss = findBoss(server, strike.bossLevel(), strike.bossId());
            TeleportPathController controller = controllerOf(boss);
            if (controller != null && boss.level() instanceof ServerLevel bossLevel) {
                controller.onRiftStrike(bossLevel, strike.settings());
            }
        }
    }

    /** How many of the players a hit waits for are still between dimensions; one gone offline is not waited for. */
    private static int stillLanding(MinecraftServer server, Strike strike) {
        int landing = 0;
        for (UUID id : strike.landing()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isChangingDimension()) {
                landing++;
            }
        }
        return landing;
    }

    /** Takes the rift's minions away, the way a boss' minions are taken: a puff, and gone. */
    private static void removeMinions(MinecraftServer server, Rift rift) {
        ServerLevel riftLevel = server.getLevel(BossRiftDimension.KEY);
        if (riftLevel == null) {
            return;
        }
        for (UUID id : rift.minions) {
            Entity minion = riftLevel.getEntity(id);
            if (minion == null || minion.isRemoved()) {
                // Not loaded: its chunk lets it back in only while its rift is open, which is no more.
                continue;
            }
            rift.settings.getEnterParticles().emitDust(riftLevel, minion.getX(), minion.getY(0.5D), minion.getZ(),
                    minion.getBbWidth() * 0.5D, minion.getBbHeight() * 0.5D, minion.getBbWidth() * 0.5D, 0.02D,
                    BossAbilityKind.RIFT);
            minion.discard();
        }
    }

    /**
     * Puts a player back where a record says, lets go of the record, and hands them back to the
     * fight they were taken from.
     */
    private static boolean sendBack(MinecraftServer server, ServerPlayer player, ResourceKey<Level> levelKey, Vec3 pos,
                                    float yaw, float pitch, BossRiftSettings settings) {
        ServerLevel home = server.getLevel(levelKey);
        Vec3 spot = pos;
        if (home == null) {
            // The level they came from is gone - a dimension a mod took with it: the world's spawn.
            home = server.overworld();
            spot = Vec3.atBottomCenterOf(home.getSharedSpawnPos());
        }
        // The rift is over for them either way: its tint goes.
        unsync(player);
        if (!move(player, home, spot, yaw, pitch)) {
            // Another mod refused the way back. The record stays, so a relog or rift return tries again.
            LOGGER.warn("Reality rift: could not put {} back in {}; their return record is kept",
                    player.getName().getString(), home.dimension().location());
            return false;
        }
        clearRecord(player);
        if (settings != null) {
            settings.getExitSound().play(home, spot.x, spot.y, spot.z, SoundSource.HOSTILE);
        }
        rejoinFight(player);
        return true;
    }

    /** Signs a returned player back up with the boss that took them, for its bar and its count. */
    private static void rejoinFight(ServerPlayer player) {
        for (TeleportPathController controller : TeleportPathController.liveControllers()) {
            if (controller.npc().level() == player.level() && controller.isEncounterParticipant(player)) {
                controller.trackParticipant(player);
            }
        }
    }

    /** Lets a player go without taking them anywhere: they left, or died, some other way. */
    private static void drop(ServerPlayer player, boolean died) {
        unsync(player);
        BY_PLAYER.remove(player.getUUID());
        // Out of whichever rift holds them, found by the rift rather than by the trip: one that
        // lost its trip to a failure halfway would otherwise keep them inside until its limit.
        for (Rift rift : BY_BOSS.values()) {
            if (rift.inside.remove(player.getUUID()) && died) {
                rift.deaths++;
            }
        }
        clearRecord(player);
    }

    /** Lets go of a trip whose rift is no longer open: what a failure halfway through a close can leave. */
    private static void forgetStaleTrip(UUID playerId) {
        Trip trip = BY_PLAYER.get(playerId);
        if (trip != null && (trip.bossId() == null || !BY_BOSS.containsKey(trip.bossId()))) {
            BY_PLAYER.remove(playerId);
        }
    }

    /**
     * How many of this boss' rift's players are online: still in its fight, a dimension away. The
     * party's health scaling counts them, so a rift does not shrink the boss for as long as it runs.
     */
    public static int onlineInside(MinecraftServer server, UUID bossId) {
        Rift rift = BY_BOSS.get(bossId);
        if (rift == null || server == null) {
            return 0;
        }
        int online = 0;
        for (UUID id : rift.inside) {
            if (server.getPlayerList().getPlayer(id) != null) {
                online++;
            }
        }
        return online;
    }

    private static EntityNPCInterface findBoss(MinecraftServer server, Rift rift) {
        return findBoss(server, rift.bossLevel, rift.bossId);
    }

    private static EntityNPCInterface findBoss(MinecraftServer server, ResourceKey<Level> levelKey, UUID bossId) {
        ServerLevel level = server.getLevel(levelKey);
        Entity entity = level == null ? null : level.getEntity(bossId);
        return entity instanceof EntityNPCInterface npc && npc.isAlive() && !npc.isRemoved() ? npc : null;
    }

    /**
     * Closes this boss' rift, if it has one, with everyone brought back and no outcome; and calls
     * off a failure's hit still waiting for its players to land.
     */
    public static void clearBoss(Entity boss) {
        if ((BY_BOSS.isEmpty() && STRIKES.isEmpty()) || boss == null) {
            return;
        }
        STRIKES.remove(boss.getUUID());
        Rift rift = BY_BOSS.get(boss.getUUID());
        MinecraftServer server = boss.getServer();
        if (rift != null && server != null) {
            finish(server, rift, BossRiftOutcome.Result.EMPTY);
        }
    }

    /**
     * Closes every rift a level going away takes with it: every one of them when it is the rift
     * dimension, and the ones whose boss stands in it otherwise. No outcome either way, and no hit
     * owed for a failure to a boss in that level.
     */
    public static void clearLevel(ServerLevel level) {
        boolean riftLevel = BossRiftDimension.isRift(level);
        for (Rift rift : List.copyOf(BY_BOSS.values())) {
            if (riftLevel || rift.bossLevel.equals(level.dimension())) {
                finish(level.getServer(), rift, BossRiftOutcome.Result.EMPTY);
            }
        }
        STRIKES.values().removeIf(strike -> strike.bossLevel().equals(level.dimension()));
        if (riftLevel) {
            // Nothing can be left open once the rift dimension is gone; whatever a failure left in
            // the tables goes too, so a singleplayer world does not hand it to the next one.
            BY_BOSS.clear();
            BY_PLAYER.clear();
            STRIKES.clear();
            MOVING.clear();
        }
    }

    /**
     * A player coming in: back into their rift if it is still open, or back to where they were
     * taken from if it closed - or the server stopped - while they were away.
     */
    public static void handleLogin(ServerPlayer player) {
        Trip live = BY_PLAYER.get(player.getUUID());
        Rift open = live == null ? null : BY_BOSS.get(live.bossId());
        if (open != null && BossRiftDimension.isRift(player.level())) {
            // The client forgot the rift's look when it logged out.
            sync(player, open);
            return;
        }
        returnStranded(player);
    }

    /**
     * A player respawning with a record and no rift: put back where they came from if they came
     * back in the rift dimension, and the record dropped either way - a normal respawn elsewhere
     * is where they belong now.
     */
    public static void handleRespawn(ServerPlayer player) {
        if (BY_PLAYER.containsKey(player.getUUID()) || !PersistentDataUtil.hasPlayerPersisted(player, RECORD_KEY)) {
            return;
        }
        if (BossRiftDimension.isRift(player.level())) {
            returnStranded(player);
        } else {
            clearRecord(player);
        }
    }

    /** A player logging out keeps their place in the rift, and their record on disk. */
    public static void handleLogout(ServerPlayer player) {
        // Nothing to do: the trip stays in the table and the record in the player's saved data.
    }

    /**
     * A player changing dimension some other way than through the rift - a command, a portal -
     * while in one: they leave it for good, with no way back and no record.
     */
    public static void handleDimensionChange(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        if (MOVING.contains(player.getUUID()) || !BossRiftDimension.KEY.equals(from)) {
            return;
        }
        if (BY_PLAYER.containsKey(player.getUUID()) || PersistentDataUtil.hasPlayerPersisted(player, RECORD_KEY)) {
            drop(player, false);
        }
    }

    /** A player dying in a rift: out of it, record and all, for the vanilla respawn to take over. */
    public static void handleDeath(ServerPlayer player) {
        if (BY_PLAYER.containsKey(player.getUUID())) {
            drop(player, true);
        } else if (BossRiftDimension.isRift(player.level())) {
            clearRecord(player);
        }
    }

    /**
     * Puts one player back by their record, if they have one and no rift holds them.
     *
     * @return whether they were put back
     */
    public static boolean returnStranded(ServerPlayer player) {
        forgetStaleTrip(player.getUUID());
        if (BY_PLAYER.containsKey(player.getUUID())) {
            return false;
        }
        Trip saved = readRecord(player);
        if (saved == null) {
            clearRecord(player);
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        return sendBack(server, player, saved.fromLevel(), saved.from(), saved.yaw(), saved.pitch(), null);
    }

    /** Every online player with a record and no rift holding them, put back. */
    public static int returnStranded(MinecraftServer server) {
        int returned = 0;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (PersistentDataUtil.hasPlayerPersisted(player, RECORD_KEY) && returnStranded(player)) {
                returned++;
            }
        }
        return returned;
    }

    /**
     * Takes somebody to a spot in the rift dimension outside any rift, for a builder looking a
     * platform over: they get a record like anybody taken, so {@code rift return}, a relog or
     * a death brings them back. A record they already have is kept - it points where they
     * first came from.
     *
     * @return whether they are in the rift dimension now
     */
    public static boolean visit(ServerPlayer player, Vec3 spot) {
        MinecraftServer server = player.getServer();
        ServerLevel riftLevel = server == null ? null : BossRiftDimension.level(server);
        if (riftLevel == null || BY_PLAYER.containsKey(player.getUUID())) {
            return false;
        }
        boolean recorded = !BossRiftDimension.isRift(player.level());
        if (recorded) {
            writeRecord(player, new Trip(player.getUUID(), null, player.level().dimension(), player.position(),
                    player.getYRot(), player.getXRot()));
        }
        if (move(player, riftLevel, spot, player.getYRot(), player.getXRot())) {
            return true;
        }
        if (recorded) {
            clearRecord(player);
        }
        return false;
    }

    /** Read-only status used by the boss diagnostic command, or null when this boss has no rift open. */
    public static String status(UUID bossId, long gameTime) {
        Rift rift = BY_BOSS.get(bossId);
        if (rift == null) {
            return null;
        }
        String mode = switch (rift.exitMode) {
            case BossRiftSettings.EXIT_MINIONS -> "minions";
            case BossRiftSettings.EXIT_CRYSTALS -> "crystals";
            case BossRiftSettings.EXIT_BOTH -> "minions and crystals";
            default -> "survive";
        };
        String rule = rift.solo ? "solo"
                : "group (boss takes " + BossRiftOutcome.damagePercent(false, rift.settings.getGroupDamagePercent())
                + "% meanwhile)";
        return "Rift: active, mode " + mode + ", " + rift.inside.size() + " inside, "
                + BossRiftOutcome.secondsLeft(gameTime, rift.endsAt) + " s left, " + rule;
    }

    private static void writeRecord(ServerPlayer player, Trip trip) {
        CompoundTag record = new CompoundTag();
        record.putString(RECORD_LEVEL, trip.fromLevel().location().toString());
        record.putDouble(RECORD_X, trip.from().x);
        record.putDouble(RECORD_Y, trip.from().y);
        record.putDouble(RECORD_Z, trip.from().z);
        record.putFloat(RECORD_YAW, trip.yaw());
        record.putFloat(RECORD_PITCH, trip.pitch());
        record.putString(RECORD_BOSS, trip.bossId() == null ? "" : trip.bossId().toString());
        PersistentDataUtil.putPlayerPersisted(player, RECORD_KEY, record);
    }

    /** The trip a player's record describes, or null for no record or one that names no level. */
    private static Trip readRecord(ServerPlayer player) {
        CompoundTag record = PersistentDataUtil.getPlayerPersisted(player, RECORD_KEY);
        ResourceLocation level = ResourceLocation.tryParse(record.getString(RECORD_LEVEL));
        if (record.isEmpty() || level == null) {
            if (!record.isEmpty()) {
                LOGGER.warn("Reality rift: {} carries a return record with no level in it; dropping it",
                        player.getName().getString());
            }
            return null;
        }
        UUID boss = null;
        try {
            String text = record.getString(RECORD_BOSS);
            boss = text.isEmpty() ? null : UUID.fromString(text);
        } catch (IllegalArgumentException malformed) {
            // Only ever informative: the way back does not need to know whose rift it was.
        }
        return new Trip(player.getUUID(), boss, ResourceKey.create(Registries.DIMENSION, level),
                new Vec3(record.getDouble(RECORD_X), record.getDouble(RECORD_Y), record.getDouble(RECORD_Z)),
                record.getFloat(RECORD_YAW), record.getFloat(RECORD_PITCH));
    }

    private static void clearRecord(ServerPlayer player) {
        PersistentDataUtil.removePlayerPersisted(player, RECORD_KEY);
    }

    /** The controller of whatever boss this is, or null. */
    static TeleportPathController controllerOf(Entity entity) {
        return entity instanceof IBossController holder ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
    }
}
