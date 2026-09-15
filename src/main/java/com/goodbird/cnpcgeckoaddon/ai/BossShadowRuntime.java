package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossShadowSettings;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossLink;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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
    /** How long a copy's beam runs before it is drawn in: the moment the boss gets its health and its stack. */
    private static final int ABSORB_TICKS = 20;
    /** The absorb beam is drawn as the capture's, at its default width and with no sag. */
    private static final int ABSORB_BEAM_WIDTH_PERCENT = 100;
    private static final int ABSORB_BEAM_SAG_PERCENT = 0;

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
    /** When the boss next trades places with a copy; only read while the cast swaps at all. */
    private long nextSwapAt = NOT_SCHEDULED;
    /** The copies being drawn back into the boss right now: each pays out when its beam has run. */
    private final List<PendingAbsorb> absorbing = new ArrayList<>();
    /** What the copies taken back left the boss holding; outlives the copies and the cast. */
    private final BossShadowAbsorb.Stacks stacks = new BossShadowAbsorb.Stacks();

    /** One copy on its way back into the boss, and the tick it pays out on. */
    private record PendingAbsorb(UUID copy, long at) {
    }

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
        payOutAbsorbs(level, gameTime);
        if (!hasCopies()) {
            forget();
            return;
        }
        if (copies.isEmpty()) {
            // Everything left is on its way back in: no clock and no swap for a copy mid beam.
            return;
        }
        if (cast.getLifetimeTicks() > 0 && gameTime >= castAt + cast.getLifetimeTicks()) {
            if (cast.isFinaleOnLifetime()) {
                finale(level, gameTime);
            } else {
                // Their time is up but the end was promised to the next cast, which never came.
                vanishAll(level);
            }
            return;
        }
        if (cast.isSwapEnabled() && BossShadowSwap.due(gameTime, nextSwapAt)) {
            // Counted from now whether or not the swap goes: a boss that is busy this time is
            // not owed a swap the tick it is free, which would be a swap mid-recovery.
            nextSwapAt = BossShadowSwap.nextSwapAt(gameTime, cast.getSwapIntervalTicks());
            trySwap(level);
        }
    }

    /**
     * Trades places with one copy, picked at random: position, facing, head, body, pitch,
     * motion and fall, and nothing else. No sound and no puff on purpose - the swap is the
     * trick, and a trick that announces itself is none. The targets stay where they are: a
     * copy that was after somebody goes on after them from where the boss stood.
     */
    private void trySwap(ServerLevel level) {
        List<EntityNPCInterface> alive = aliveCopies(level);
        if (alive.isEmpty()) {
            return;
        }
        EntityNPCInterface copy = alive.get(BossShadowSwap.pick(npc.getRandom(), alive.size()));
        TeleportPathController copyController = controllerOf(copy);
        boolean copyIdle = copyController == null || copyController.isIdleForSwap();
        if (!BossShadowSwap.allowed(cast.isSwapOnlyIdle(), boss.isIdleForSwap(), copyIdle)) {
            return;
        }
        BossShadowSwap.Pose[] poses = BossShadowSwap.swapped(BossShadowSwap.Pose.of(npc),
                BossShadowSwap.Pose.of(copy));
        BossShadowSwap.Pose bossGets = poses[0];
        BossShadowSwap.Pose copyGets = poses[1];
        // The boss goes through the shared blink so its stationary pin follows it; a hop that
        // CustomNPCs vetoes leaves both where they were.
        if (!BossTeleportUtil.teleport(level, npc, boss, bossGets.position(), false, "shadow swap")) {
            return;
        }
        bossGets.applyTo(npc);
        copy.teleportTo(copyGets.position().x, copyGets.position().y, copyGets.position().z);
        copy.getNavigation().stop();
        copyGets.applyTo(copy);
        if (copyController != null) {
            // The copy's own pin, or it would be yanked straight back to where the boss now stands.
            copyController.rememberCurrentPosition();
        }
    }

    private static TeleportPathController controllerOf(EntityNPCInterface copy) {
        return copy instanceof IBossController holder ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
    }

    /** Whether any copy of this boss is standing or being drawn in; what the rotation and the finish gate wait on. */
    boolean hasCopies() {
        return !copies.isEmpty() || !absorbing.isEmpty();
    }

    /** What the stacks make of a number an ability hits for; the rage has already had its say on it. */
    int scaleDamage(int value, long gameTime) {
        return stacks.scale(value, gameTime);
    }

    /** What the stacks multiply the boss' own swing by right now: one with none held. */
    double absorbMultiplier(long gameTime) {
        return stacks.multiplier(gameTime);
    }

    /**
     * Makes a boss that took its copies back hit as hard as its stacks say, on the swing it
     * deals with its own body. The rage's rule: only the boss' own body, never a projectile
     * and never a hit an ability is landing, since those read the stacks where they read the
     * rage - a hit scaled there must not be scaled a second time here.
     *
     * @return the damage this hit should land for, unchanged when no stacks are held
     */
    static float scaleOwnAttack(DamageSource source, float amount) {
        if (amount <= 0.0F || BossAbilityDamageUtil.isApplyingHit()
                || BossAbilityDamageUtil.currentAbility() != BossAbilityDamageUtil.NO_ABILITY
                || !(source.getEntity() instanceof EntityNPCInterface npc)
                || source.getDirectEntity() != npc
                || !(npc instanceof IBossController holder)) {
            return amount;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null) {
            return amount;
        }
        double multiplier = controller.absorbMultiplier();
        return multiplier <= 1.0D ? amount : (float) (amount * multiplier);
    }

    /** How many copies stand right now, as of the last tick's look. */
    int aliveCount() {
        return copies.size();
    }

    /** Whether the boss' bar is down for the copies' sake right now. */
    boolean hidesBossBar() {
        return cast != null && cast.isHideBossBar() && hasCopies();
    }

    /** Takes every copy away with no finale and drops the stacks, for every ending of a fight. */
    void clear(ServerLevel level) {
        BossParticleCue puff = cast == null ? null : cast.getVanishParticles();
        for (PendingAbsorb pending : absorbing) {
            if (level.getEntity(pending.copy()) instanceof EntityNPCInterface copy && copy.isAlive()) {
                vanish(level, copy, puff);
            }
        }
        absorbing.clear();
        vanishAll(level);
        stacks.clear();
    }

    /** A phase that is over takes its copies with it, when the phase they were cast in said so. */
    void onPhaseChange(ServerLevel level) {
        if (cast != null && cast.isClearOnPhaseChange()) {
            clear(level);
        }
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime) {
        String swap = cast != null && cast.isSwapEnabled() && !copies.isEmpty()
                ? Long.toString(Math.max(0L, nextSwapAt - gameTime)) : "-";
        String held = stacks.count(gameTime) == 0 ? "0"
                : stacks.count(gameTime) + " (" + stacks.ticksLeft(gameTime) + " ticks left)";
        return "Shadows: " + copies.size() + " alive, swap in " + swap + ", stacks " + held;
    }

    private void finale(ServerLevel level, long gameTime) {
        switch (cast.getFinale()) {
            case BossShadowSettings.FINALE_ABSORB -> absorbAll(level, gameTime);
            default -> vanishAll(level);
        }
    }

    /**
     * Starts drawing every standing copy back into the boss: the beam and the cue now, the
     * health and the stack when the beam has run. A copy killed in between gives nothing.
     */
    private void absorbAll(ServerLevel level, long gameTime) {
        for (EntityNPCInterface copy : aliveCopies(level)) {
            if (HookCordStyles.isTextured(cast.getAbsorbBeam())) {
                PacketSyncBossLink beam = new PacketSyncBossLink(PacketSyncBossLink.KIND_CAPTURE, copy.getId(),
                        npc.getId(), 0, cast.getAbsorbBeam(), ABSORB_TICKS, ABSORB_BEAM_WIDTH_PERCENT,
                        ABSORB_BEAM_SAG_PERCENT, false);
                NetworkWrapper.sendToTracking(npc, beam);
                NetworkWrapper.sendToTracking(copy, beam);
            }
            cueAt(level, copy, cast.getAbsorbSound(), cast.getAbsorbParticles());
            absorbing.add(new PendingAbsorb(copy.getUUID(), gameTime + ABSORB_TICKS));
        }
        copies.clear();
    }

    /** The copies whose beam has run: each heals the boss and adds its stack, then goes. */
    private void payOutAbsorbs(ServerLevel level, long gameTime) {
        for (Iterator<PendingAbsorb> it = absorbing.iterator(); it.hasNext(); ) {
            PendingAbsorb pending = it.next();
            if (gameTime < pending.at()) {
                continue;
            }
            it.remove();
            if (!(level.getEntity(pending.copy()) instanceof EntityNPCInterface copy) || !copy.isAlive()) {
                continue;
            }
            // heal() rather than setHealth(): the health link listens for the heal event, and
            // a partner sharing the boss' pool is owed its share of every copy taken back.
            float amount = BossShadowAbsorb.healAmount(npc.getMaxHealth(), cast.getAbsorbHealPercent());
            if (amount > 0.0F) {
                npc.heal(amount);
            }
            stacks.add(gameTime, cast.getAbsorbDamagePercent(), cast.getAbsorbMaxStacks(), cast.getAbsorbBuffTicks());
            vanish(level, copy, cast.getVanishParticles());
        }
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
        nextSwapAt = BossShadowSwap.nextSwapAt(gameTime, shadow.getSwapIntervalTicks());
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
        nextSwapAt = NOT_SCHEDULED;
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
