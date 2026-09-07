package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * Who the boss is fighting: the aggro zone that starts the fight, and the retargeting that
 * keeps it on whoever is nearest.
 *
 * <p>Owned by {@link TeleportPathController} and ticked ahead of everything that reads the
 * target, so whatever the vanilla aggro, a script or another player's swing did to it since
 * the last tick is settled here first. A running hunt owns the target outright and both
 * halves stand aside for it.</p>
 */
final class BossTargetingRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    private long nextRetargetAt = NOT_SCHEDULED;
    private long nextZoneCheckAt = NOT_SCHEDULED;

    BossTargetingRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Forgets both clocks, so the next tick of a fresh fight rechecks at once. */
    void reset() {
        nextRetargetAt = NOT_SCHEDULED;
        nextZoneCheckAt = NOT_SCHEDULED;
    }

    /**
     * Starts combat when an eligible player enters the configured block volume. The spatial
     * check walks the dedicated server player list rather than every entity or every section,
     * so a distant or accidentally huge absolute box never loads chunks or scans empty space.
     */
    void updateAggroZone(ServerLevel level, TeleportPathData data, long gameTime) {
        if (!data.isAggroZoneEnabled()) {
            nextZoneCheckAt = NOT_SCHEDULED;
            return;
        }
        if (nextZoneCheckAt != NOT_SCHEDULED && gameTime < nextZoneCheckAt) {
            return;
        }
        nextZoneCheckAt = gameTime + data.getAggroZoneRecheckTicks();

        AABB zone = zoneBounds(level, data);
        List<ServerPlayer> candidates = zone == null
                ? List.of() : eligibleZonePlayers(level, data, zone);
        for (ServerPlayer player : candidates) {
            // Everyone who crossed the trigger together belongs to the fight, even when
            // only one of them is chosen as the NPC's immediate target.
            boss.trackParticipant(player);
        }

        // Membership was still taken above; only the choice is the hunt's for as long as it
        // runs, and a prey who leaves the zone ends it rather than being swapped out here.
        if (boss.isHunting()) {
            return;
        }
        LivingEntity current = npc.getTarget();
        boolean currentIsCandidate = current instanceof ServerPlayer player && candidates.contains(player);
        if (data.isAggroZoneKeepInside() && current instanceof Player && !currentIsCandidate) {
            setTargetIfChanged(selectZoneTarget(candidates, data));
            return;
        }
        if (!hasValidZoneCombatTarget(current, data)) {
            ServerPlayer selected = selectZoneTarget(candidates, data);
            if (selected != null) {
                setTargetIfChanged(selected);
            }
        }
    }

    List<ServerPlayer> eligibleZonePlayers(ServerLevel level, TeleportPathData data,
                                                         AABB zone) {
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.level() == level && zone.contains(player.position())
                    && isTargetable(player, data)) {
                candidates.add(player);
            }
        }
        return candidates;
    }

    /** Intersects Y with this dimension's real build height instead of an obsolete 0..255 range. */
    AABB zoneBounds(ServerLevel level, TeleportPathData data) {
        int minY = Math.max(Math.min(data.getAggroZoneY1(), data.getAggroZoneY2()),
                level.getMinBuildHeight());
        int maxY = Math.min(Math.max(data.getAggroZoneY1(), data.getAggroZoneY2()),
                level.getMaxBuildHeight() - 1);
        if (minY > maxY) {
            return null;
        }
        int minX = Math.min(data.getAggroZoneX1(), data.getAggroZoneX2());
        int minZ = Math.min(data.getAggroZoneZ1(), data.getAggroZoneZ2());
        int maxX = Math.max(data.getAggroZoneX1(), data.getAggroZoneX2());
        int maxZ = Math.max(data.getAggroZoneZ1(), data.getAggroZoneZ2());
        // The upper AABB bounds are exclusive, so adding one includes every block of corner 2.
        return new AABB(minX, minY, minZ, (double) maxX + 1.0D,
                (double) maxY + 1.0D, (double) maxZ + 1.0D);
    }

    ServerPlayer selectZoneTarget(List<ServerPlayer> candidates, TeleportPathData data) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (data.getAggroZoneTargetMode() == TeleportPathData.AGGRO_ZONE_TARGET_RANDOM) {
            return candidates.get(npc.getRandom().nextInt(candidates.size()));
        }
        ServerPlayer nearest = candidates.getFirst();
        double nearestDistance = npc.distanceToSqr(nearest);
        for (int i = 1; i < candidates.size(); i++) {
            ServerPlayer candidate = candidates.get(i);
            double distance = npc.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    boolean hasValidZoneCombatTarget(LivingEntity target, TeleportPathData data) {
        if (!boss.hasCombatTarget()) {
            return false;
        }
        return !(target instanceof Player player) || isTargetable(player, data);
    }

    void setTargetIfChanged(LivingEntity target) {
        if (npc.getTarget() != target) {
            npc.setTarget(target);
        }
    }

    /**
     * Locks the boss onto the closest reachable enemy. Without this a boss keeps chasing
     * whoever aggroed it first, which lets a group trivially kite it with one player.
     */
    void updateNearest(ServerLevel level, TeleportPathData data, long gameTime) {
        if (!data.isTargetNearestPlayer()) {
            nextRetargetAt = NOT_SCHEDULED;
            return;
        }
        // The hunt owns the target for as long as it runs, and the nearest player is exactly
        // who the boss is meant to be ignoring.
        if (boss.isHunting()) {
            return;
        }
        if (nextRetargetAt != NOT_SCHEDULED && gameTime < nextRetargetAt) {
            return;
        }
        nextRetargetAt = gameTime + data.getTargetRecheckTicks();

        boolean restrictToZone = data.isAggroZoneEnabled() && data.isAggroZoneKeepInside();
        AABB zoneConstraint = restrictToZone ? zoneBounds(level, data) : null;
        double radius = data.getTargetSearchRadius();
        double radiusSquared = radius * radius;
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Iterable<? extends Player> players = !restrictToZone
                ? level.players() : zoneConstraint == null
                ? List.of() : eligibleZonePlayers(level, data, zoneConstraint);
        for (Player player : players) {
            double distance = npc.distanceToSqr(player);
            if ((!restrictToZone && distance > radiusSquared) || distance >= nearestDistance) {
                continue;
            }
            if (!isTargetable(player, data)) {
                continue;
            }
            nearest = player;
            nearestDistance = distance;
        }
        for (LivingEntity candidate : nearbyNonPlayers(level, data, radius, restrictToZone,
                zoneConstraint)) {
            double distance = npc.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        LivingEntity current = npc.getTarget();
        if (nearest == null) {
            // Only release players: a mob target was picked by the CustomNPCs faction AI
            // and dropping it here would fight with that system every recheck.
            if ((restrictToZone || !data.isKeepTargetOutOfRange()) && current instanceof Player) {
                npc.setTarget(null);
            }
            return;
        }
        if (current != nearest) {
            npc.setTarget(nearest);
        }
    }

    /**
     * The non-player half of the retarget search.
     *
     * <p>Players keep their own scan because inside an aggro zone the zone, not the search
     * radius, is their range, and walking a builder-sized zone section by section would
     * cost far more than the player list it replaced. Everything else is looked up in a
     * box around the boss and then, when the zone holds the fight, trimmed down to it.</p>
     */
    List<LivingEntity> nearbyNonPlayers(ServerLevel level, TeleportPathData data,
                                                      double radius, boolean restrictToZone,
                                                      AABB zoneConstraint) {
        if (data.getAbilityTargetKind() == TeleportPathData.ABILITY_TARGET_PLAYERS
                || restrictToZone && zoneConstraint == null) {
            return List.of();
        }
        double radiusSquared = radius * radius;
        AABB box = new AABB(npc.position(), npc.position()).inflate(radius + 1.0D);
        return level.getEntitiesOfClass(LivingEntity.class, box, candidate ->
                candidate != npc && !(candidate instanceof Player)
                        && npc.distanceToSqr(candidate) <= radiusSquared
                        && (!restrictToZone || zoneConstraint.contains(candidate.position()))
                        && boss.matchesAbilityTargetKind(candidate, data)
                        && isTargetable(candidate, data));
    }

    /**
     * Whether the retarget search may lock the boss onto this candidate.
     *
     * <p>Defers to {@link TeleportPathController#isAreaTarget} so the boss can never decide to chase something
     * its own attacks would refuse to hit, its minions and totems included.</p>
     */
    boolean isTargetable(LivingEntity candidate, TeleportPathData data) {
        if (!candidate.isAlive() || candidate.isRemoved() || !boss.isAreaTarget(candidate)) {
            return false;
        }
        return !data.isTargetRequiresLineOfSight() || npc.getSensing().hasLineOfSight(candidate);
    }
}
