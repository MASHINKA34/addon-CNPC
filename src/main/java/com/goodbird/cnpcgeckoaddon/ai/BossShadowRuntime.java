package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossShadowSettings;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The shadow copies: the cast that stands them up, the clock that ends them, and what the
 * boss does with them in between.
 *
 * <p>Owned by {@link TeleportPathController}. The copies themselves are ordinary npcs with
 * controllers of their own; what lives here is only the boss' side of them - which ones are
 * its, when their time is up, and how they go. Nothing here is saved: a copy that made it
 * into a save file is turned away as it loads, and a boss that comes back from one starts
 * with no copies, exactly as it started with none the first time.</p>
 */
final class BossShadowRuntime {

    /** How far below the ring's height the floor may be before a spot on the ring is given up. */
    private static final int RING_FLOOR_SEARCH = 6;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The copies standing right now, by UUID; a copy is looked up fresh every tick. */
    private final List<UUID> copies = new ArrayList<>();
    /**
     * The settings the standing copies were cast with, held until the last of them is gone,
     * and null between casts. A copy cast under one set of rules ends under the same set,
     * whatever the phase's screen says by then.
     */
    private BossShadowSettings cast;
    private long castAt = NOT_SCHEDULED;

    /** A spot a copy is stood up on, and the way it faces there. */
    private record Spot(Vec3 at, float yaw) {
    }

    BossShadowRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.SHADOW, phase) || gameTime < boss.abilityScheduleAt(BossAbility.SHADOW)) {
            return false;
        }
        BossShadowSettings shadow = phase.shadow();
        if (hasCopies() && !shadow.isFinaleOnRecast()) {
            // Copies standing, and nothing this cast could do about them until their time runs
            // out: a second wave on top of the first is exactly what the count is there to stop.
            boss.setAbilityScheduleAt(BossAbility.SHADOW, gameTime + boss.retryTicks());
            return false;
        }
        boss.beginAction(BossAbility.SHADOW, shadow.getAnimation(), shadow.getActionDelayTicks(),
                gameTime, null, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.SHADOW, gameTime + shadow.getActionDelayTicks()
                + boss.rageDown(shadow.getCooldownTicks()));
        return true;
    }

    /** The end of the wind-up: copies stand up, or - with copies already standing - they end. */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        if (hasCopies()) {
            finale(level, gameTime);
            return;
        }
        spawn(level, phase, gameTime);
    }

    /** The clock on the copies, run every tick of the boss whether or not it is busy. */
    void tick(ServerLevel level, long gameTime) {
        if (cast == null) {
            return;
        }
        prune(level);
        if (!hasCopies()) {
            forget();
            return;
        }
        if (cast.getLifetimeTicks() > 0 && gameTime >= castAt + cast.getLifetimeTicks()) {
            if (cast.isFinaleOnLifetime()) {
                finale(level, gameTime);
            } else {
                // Their time is up but the end was promised to the next cast, which never came.
                vanishAll(level);
            }
        }
    }

    /** Whether any copy of this boss is standing; what the rotation and the finish gate wait on. */
    boolean hasCopies() {
        return !copies.isEmpty();
    }

    /** How many copies stand right now, as of the last tick's look. */
    int aliveCount() {
        return copies.size();
    }

    /** Whether the boss' bar is down for the copies' sake right now. */
    boolean hidesBossBar() {
        return cast != null && cast.isHideBossBar() && hasCopies();
    }

    /** Takes every copy away with no finale, for every ending of a fight. */
    void clear(ServerLevel level) {
        vanishAll(level);
    }

    /** A phase that is over takes its copies with it, when the phase they were cast in said so. */
    void onPhaseChange(ServerLevel level) {
        if (cast != null && cast.isClearOnPhaseChange()) {
            clear(level);
        }
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime) {
        return "Shadows: " + copies.size() + " alive";
    }

    private void finale(ServerLevel level, long gameTime) {
        vanishAll(level);
    }

    private void spawn(ServerLevel level, BossPhaseData phase, long gameTime) {
        BossShadowSettings shadow = phase.shadow();
        int phaseIndex = boss.currentPhaseIndex();
        LivingEntity target = boss.hasCombatTarget() ? npc.getTarget() : null;
        List<Spot> spots = new ArrayList<>(shadow.getCount());
        // The builder's points first, in their order; a point with nowhere to stand is skipped.
        for (BossMinionSpawnPoint point : shadow.getPoints().entries()) {
            if (spots.size() >= shadow.getCount()) {
                break;
            }
            if (!point.isEnabled()) {
                continue;
            }
            Vec3 anchor = boss.pointAnchor(point);
            Vec3 at = BossTeleportUtil.findSafeDestination(level, npc, anchor.x, anchor.y, anchor.z);
            if (at != null) {
                spots.add(new Spot(at, point.getYaw()));
            }
        }
        // Then the ring round the boss for whatever the points did not place.
        int missing = shadow.getCount() - spots.size();
        if (missing > 0) {
            spots.addAll(ringSpots(level, shadow.getSpawnRadius(), missing));
        }
        for (Spot spot : spots) {
            EntityNPCInterface copy = BossShadowUtil.spawnCopy(level, npc, phase, phaseIndex, spot.at(),
                    spot.yaw(), target);
            if (copy == null) {
                continue;
            }
            copies.add(copy.getUUID());
            cueAt(level, copy, shadow.getSpawnSound(), shadow.getSpawnParticles());
        }
        if (copies.isEmpty()) {
            // Nothing stood up, so there is nothing to hold the settings for: the bar stays.
            return;
        }
        cast = shadow.copy();
        castAt = gameTime;
    }

    /** Spots spread evenly round the boss, each on the floor nearest the boss' own height. */
    private List<Spot> ringSpots(ServerLevel level, double radius, int wanted) {
        List<Spot> spots = new ArrayList<>(wanted);
        double start = npc.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < wanted; i++) {
            double angle = ringAngle(start, i, wanted);
            double x = npc.getX() + Math.cos(angle) * radius;
            double z = npc.getZ() + Math.sin(angle) * radius;
            BlockPos floor = BossFloorUtil.findFloor(level, x, npc.getY() + 1.0D, z, RING_FLOOR_SEARCH);
            if (floor == null) {
                continue;
            }
            Vec3 at = BossTeleportUtil.findSafeDestination(level, npc, x, floor.getY() + 1.0D, z);
            if (at != null) {
                spots.add(new Spot(at, npc.getYRot()));
            }
        }
        return spots;
    }

    /** The {@code index}th of {@code count} directions round a circle, starting from {@code start}. */
    static double ringAngle(double start, int index, int count) {
        return start + Math.PI * 2.0D * index / Math.max(1, count);
    }

    /** Drops every copy that is no longer in the world or no longer alive. */
    private void prune(ServerLevel level) {
        for (Iterator<UUID> it = copies.iterator(); it.hasNext(); ) {
            Entity copy = level.getEntity(it.next());
            if (copy == null || !copy.isAlive() || copy.isRemoved()) {
                it.remove();
            }
        }
    }

    /** The copies still in the world, resolved fresh. */
    private List<EntityNPCInterface> aliveCopies(ServerLevel level) {
        List<EntityNPCInterface> alive = new ArrayList<>(copies.size());
        for (UUID id : copies) {
            if (level.getEntity(id) instanceof EntityNPCInterface copy && copy.isAlive() && !copy.isRemoved()) {
                alive.add(copy);
            }
        }
        return alive;
    }

    private void vanishAll(ServerLevel level) {
        BossParticleCue puff = cast == null ? null : cast.getVanishParticles();
        for (EntityNPCInterface copy : aliveCopies(level)) {
            vanish(level, copy, puff);
        }
        forget();
    }

    /** One copy gone with its puff: discarded a tick on, so no drop, no death and no resurrection. */
    private static void vanish(ServerLevel level, EntityNPCInterface copy, BossParticleCue puff) {
        cueAt(level, copy, null, puff);
        BossCloneRespawnGuard.retire(copy);
    }

    private void forget() {
        copies.clear();
        cast = null;
        castAt = NOT_SCHEDULED;
    }

    private static void cueAt(ServerLevel level, Entity at, BossSoundCue sound, BossParticleCue particles) {
        if (sound != null) {
            sound.play(level, at.getX(), at.getY(), at.getZ(), SoundSource.HOSTILE);
        }
        if (particles != null) {
            particles.emit(level, at.getX(), at.getY(0.5D), at.getZ(),
                    at.getBbWidth() * 0.5D, at.getBbHeight() * 0.5D, at.getBbWidth() * 0.5D, 0.02D);
        }
    }
}
