package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a summon puts its clones: the configured points, the order they are taken in, and
 * the ring of open ground the fallback drops the rest on.
 *
 * <p>Owned by {@link TeleportPathController}. Everything here is the placement question on
 * its own - what a summon costs, when it may run and what animation plays over it all stay
 * with the ability itself. The one thing that outlives a single summon is the round robin
 * cursor, which is why this is an object per boss rather than a pile of static helpers.</p>
 */
final class BossMinionSpawnRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** Tries at finding open ground in the fallback ring before the summon gives up on one. */
    private static final int RANDOM_PLACEMENT_ATTEMPTS = 12;
    /** Half the width and the height of the box a minion has to fit in to be placed. */
    private static final double SPAWN_HALF_WIDTH = 0.35D;
    private static final double SPAWN_HEIGHT = 1.8D;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** Phase index -> the last point that successfully spawned in round-robin order. */
    private final Map<Integer, Integer> roundRobinCursor = new HashMap<>();
    private final Set<String> reportedBrokenClones = new HashSet<>();
    private final Set<String> reportedBlockedPoints = new HashSet<>();

    BossMinionSpawnRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Forgets which point came last, for every ending of a fight. */
    void clearCursor() {
        roundRobinCursor.clear();
    }

    /** The same, plus the one-line-per-problem log book, for a boss going right back to idle. */
    void clear() {
        clearCursor();
        reportedBrokenClones.clear();
        reportedBlockedPoints.clear();
    }

    /** Runs one summon: the configured points first, then the ring for whatever is left. */
    void summon(ServerLevel level, BossPhaseData phase) {
        // Capped at the ceiling it is subtracted from: past that the difference is never
        // positive anyway, and the walk does not have to finish counting a full arena.
        int available = phase.getMaxAliveMinions()
                - BossMinionUtil.countAlive(level, npc, phase.getMaxAliveMinions());
        int amount = Math.min(phase.getMinionCount(), Math.max(available, 0));
        if (amount <= 0) return;

        int spawned = 0;
        if (phase.getMinionSpawnMode() != BossPhaseData.MINION_SPAWN_RANDOM_RADIUS) {
            spawned = spawnConfigured(level, phase, amount);
        }

        boolean useRandom = phase.getMinionSpawnMode() == BossPhaseData.MINION_SPAWN_RANDOM_RADIUS
                || phase.getMinionSpawnMode() == BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM;
        if (!useRandom || phase.getMinionCloneName().isEmpty()) {
            return;
        }
        for (int i = spawned; i < amount; i++) {
            Vec3 position = findRingPosition(level, phase.getMinionRadius());
            if (position == null) continue;
            spawnClone(level, phase.getMinionCloneName(), phase.getMinionCloneTab(),
                    position, Float.NaN, boss.currentPhaseIndex(), -1);
        }
    }

    private int spawnConfigured(ServerLevel level, BossPhaseData phase, int desired) {
        int phaseIndex = boss.currentPhaseIndex();
        List<BossMinionSpawnPoint> points = orderedPoints(level, phase);
        int spawned = 0;
        for (BossMinionSpawnPoint point : points) {
            if (spawned >= desired) {
                break;
            }
            Vec3 anchor = pointAnchor(point);
            Vec3 position = findConfiguredPosition(level, anchor,
                    phase.getMinionPointSearchRadius(), phaseIndex, point.getPointId());
            if (position == null) {
                continue;
            }
            String cloneName = point.getCloneNameOverride().isEmpty()
                    ? phase.getMinionCloneName() : point.getCloneNameOverride();
            int cloneTab = point.getCloneTabOverride() == 0
                    ? phase.getMinionCloneTab() : point.getCloneTabOverride();
            Entity minion = spawnClone(level, cloneName, cloneTab, position,
                    point.getYaw(), phaseIndex, point.getPointId());
            if (minion != null) {
                spawned++;
                if (phase.getMinionSpawnOrder() == BossPhaseData.MINION_ORDER_ROUND_ROBIN) {
                    roundRobinCursor.put(phaseIndex, point.getPointId());
                }
            }
        }
        return spawned;
    }

    private List<BossMinionSpawnPoint> orderedPoints(ServerLevel level, BossPhaseData phase) {
        int phaseIndex = boss.currentPhaseIndex();
        List<BossMinionSpawnPoint> candidates = new ArrayList<>();
        Set<Integer> occupied = phase.isMinionReuseOccupiedPoints()
                ? Set.of() : BossMinionUtil.occupiedSlots(level, npc, phaseIndex);
        for (BossMinionSpawnPoint point : phase.getMinionSpawnPoints().entries()) {
            if (!point.isEnabled()) {
                continue;
            }
            String cloneName = point.getCloneNameOverride().isEmpty()
                    ? phase.getMinionCloneName() : point.getCloneNameOverride();
            if (cloneName.isEmpty()) {
                continue;
            }
            if (occupied.contains(point.getPointId())) {
                warnBlockedPoint(phaseIndex, point.getPointId(), "slot already has a living minion");
                continue;
            }
            candidates.add(point);
        }

        if (phase.getMinionSpawnOrder() == BossPhaseData.MINION_ORDER_RANDOM) {
            return weightedRandomPoints(candidates);
        }
        if (phase.getMinionSpawnOrder() != BossPhaseData.MINION_ORDER_ROUND_ROBIN
                || candidates.size() < 2) {
            return candidates;
        }

        Integer lastPointId = roundRobinCursor.get(phaseIndex);
        if (lastPointId == null) {
            return candidates;
        }
        List<BossMinionSpawnPoint> configured = phase.getMinionSpawnPoints().entries();
        int lastIndex = -1;
        for (int i = 0; i < configured.size(); i++) {
            if (configured.get(i).getPointId() == lastPointId) {
                lastIndex = i;
                break;
            }
        }
        if (lastIndex < 0) {
            return candidates;
        }
        Set<Integer> candidateIds = new HashSet<>();
        for (BossMinionSpawnPoint point : candidates) {
            candidateIds.add(point.getPointId());
        }
        List<BossMinionSpawnPoint> rotated = new ArrayList<>(candidates.size());
        for (int offset = 1; offset <= configured.size(); offset++) {
            BossMinionSpawnPoint point = configured.get((lastIndex + offset) % configured.size());
            if (candidateIds.contains(point.getPointId())) {
                rotated.add(point);
            }
        }
        return rotated;
    }

    private List<BossMinionSpawnPoint> weightedRandomPoints(List<BossMinionSpawnPoint> candidates) {
        List<BossMinionSpawnPoint> remaining = new ArrayList<>(candidates);
        List<BossMinionSpawnPoint> ordered = new ArrayList<>(candidates.size());
        while (!remaining.isEmpty()) {
            int totalWeight = 0;
            for (BossMinionSpawnPoint point : remaining) {
                totalWeight += point.getWeight();
            }
            int roll = npc.getRandom().nextInt(totalWeight);
            int selected = 0;
            for (int i = 0; i < remaining.size(); i++) {
                roll -= remaining.get(i).getWeight();
                if (roll < 0) {
                    selected = i;
                    break;
                }
            }
            ordered.add(remaining.remove(selected));
        }
        return ordered;
    }

    /** Where one point sits in the world: an absolute spot, or an offset from the arena. */
    Vec3 pointAnchor(BossMinionSpawnPoint point) {
        if (point.getCoordinateMode() == BossMinionSpawnPoint.COORDINATE_FIXED) {
            return new Vec3(point.getX() + 0.5D, point.getY(), point.getZ() + 0.5D);
        }
        return new Vec3(boss.homeX() + point.getX(), boss.homeY() + point.getY(),
                boss.homeZ() + point.getZ());
    }

    private Vec3 findConfiguredPosition(ServerLevel level, Vec3 anchor, int radius,
                                        int phaseIndex, int pointId) {
        BlockPos anchorBlock = BlockPos.containing(anchor);
        if (!level.hasChunkAt(anchorBlock)) {
            warnBlockedPoint(phaseIndex, pointId, "anchor chunk is not loaded");
            return null;
        }

        List<int[]> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    offsets.add(new int[] {x, z});
                }
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> offset[0]).thenComparingInt(offset -> offset[1]));

        boolean foundLoaded = false;
        boolean foundInsideWorld = false;
        boolean foundUnoccupied = false;
        for (int[] offset : offsets) {
            Vec3 candidate = anchor.add(offset[0], 0.0D, offset[1]);
            BlockPos feet = BlockPos.containing(candidate);
            if (!level.hasChunkAt(feet)) {
                continue;
            }
            foundLoaded = true;
            if (!level.getWorldBorder().isWithinBounds(feet)
                    || candidate.y < level.getMinBuildHeight()
                    || candidate.y + SPAWN_HEIGHT >= level.getMaxBuildHeight()) {
                continue;
            }
            foundInsideWorld = true;
            AABB box = spawnBox(candidate);
            if (!level.noCollision(box)
                    || !level.getEntities((Entity) null, box,
                    entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) {
                continue;
            }
            foundUnoccupied = true;
            BlockPos support = feet.below();
            if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                continue;
            }
            return candidate;
        }

        String reason = !foundLoaded ? "search chunks are not loaded"
                : !foundInsideWorld ? "outside the world border or build height"
                : !foundUnoccupied ? "spawn box is occupied" : "no solid support";
        warnBlockedPoint(phaseIndex, pointId, reason);
        return null;
    }

    /** The room one clone needs to stand in. Shared with the cocoon guard, which needs the same. */
    static AABB spawnBox(Vec3 position) {
        return new AABB(position.x - SPAWN_HALF_WIDTH, position.y, position.z - SPAWN_HALF_WIDTH,
                position.x + SPAWN_HALF_WIDTH, position.y + SPAWN_HEIGHT, position.z + SPAWN_HALF_WIDTH);
    }

    private Entity spawnClone(ServerLevel level, String cloneName, int cloneTab, Vec3 position,
                              float yaw, int phaseIndex, int slotIndex) {
        if (cloneName == null || cloneName.isBlank()) {
            return null;
        }
        String cloneKey = cloneTab + ":" + cloneName;
        try {
            IEntity<?> wrapper = NpcAPI.Instance().getClones().spawn(position.x, position.y, position.z,
                    cloneTab, cloneName, NpcAPI.Instance().getIWorld(level));
            if (wrapper == null || wrapper.getMCEntity() == null) {
                warnBrokenClone(cloneKey, "clone returned no entity");
                return null;
            }
            Entity minion = wrapper.getMCEntity();
            BossMinionUtil.markAsMinion(minion, npc, phaseIndex, slotIndex);
            BossCloneRespawnGuard.suppressSelfRespawn(minion);
            if (Float.isFinite(yaw)) {
                minion.setYRot(yaw);
                if (minion instanceof Mob mob) {
                    mob.setYHeadRot(yaw);
                    mob.yBodyRot = yaw;
                }
            }
            if (minion instanceof Mob mob && boss.hasCombatTarget() && mob.canAttack(npc.getTarget())) {
                mob.setTarget(npc.getTarget());
            }
            return minion;
        } catch (Throwable error) {
            warnBrokenClone(cloneKey, error.getMessage());
            return null;
        }
    }

    private void warnBrokenClone(String cloneKey, String reason) {
        if (reportedBrokenClones.add(cloneKey)) {
            LOGGER.warn("Cannot summon CustomNPC clone {} for boss {}: {}", cloneKey,
                    npc.getName().getString(), reason);
        }
    }

    private void warnBlockedPoint(int phaseIndex, int pointId, String reason) {
        String key = phaseIndex + ":" + pointId + ":" + reason;
        if (reportedBlockedPoints.add(key)) {
            LOGGER.warn("Cannot place minion point {} in boss {} phase {}: {}", pointId,
                    npc.getName().getString(), phaseIndex + 1, reason);
        }
    }

    /** A spot on open ground round the boss, for the summon that was given no points. */
    private Vec3 findRingPosition(ServerLevel level, int radius) {
        for (int attempt = 0; attempt < RANDOM_PLACEMENT_ATTEMPTS; attempt++) {
            double angle = npc.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 1.0D + npc.getRandom().nextDouble() * Math.max(radius - 1.0D, 0.0D);
            double x = npc.getX() + Math.cos(angle) * distance;
            double y = npc.getY();
            double z = npc.getZ() + Math.sin(angle) * distance;
            BlockPos pos = BlockPos.containing(x, y, z);
            Vec3 candidate = new Vec3(x, y, z);
            if (level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                    && level.noCollision(spawnBox(candidate))) {
                return candidate;
            }
        }
        return null;
    }
}
