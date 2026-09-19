package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The reality rift as the boss casts it: who it picks, the wind-up, and the cut that sends them
 * away.
 *
 * <p>Owned by {@link TeleportPathController}. Only the cast lives here - the trip itself, the
 * pocket dimension's platform and the way back, outlives the cast by a long way and belongs to
 * the rift's own manager.</p>
 */
final class BossRiftRuntime {

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    /**
     * Whether the cast being wound up is a solo one: settled when the boss commits, from how
     * many are fighting on the arena at that moment, so somebody walking in during the wind-up
     * does not turn a solo check into a group one.
     */
    private boolean soloAtStart;

    BossRiftRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.RIFT, phase) || gameTime < boss.abilityScheduleAt(BossAbility.RIFT)) {
            return false;
        }
        BossRiftSettings rift = phase.rift();
        if (isActive()) {
            // One rift at a time: a second cut while the first is still open would split the
            // party over two tasks with one clock.
            boss.setAbilityScheduleAt(BossAbility.RIFT, gameTime + boss.retryTicks());
            return false;
        }
        List<LivingEntity> targets = boss.selectAbilityTargets(level, rift.getTargetMode(), reach(data),
                candidate -> isValidTarget(candidate, data), rift.getTargetCount());
        if (targets.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.RIFT, gameTime + boss.retryTicks());
            return false;
        }
        soloAtStart = boss.arenaParticipantCount(level, data) <= rift.getSoloMaxPlayers();
        boss.rememberExtraTargets(targets);
        boss.beginAction(BossAbility.RIFT, rift.getAnimation(), rift.getActionDelayTicks(), gameTime,
                targets.get(0), data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.RIFT, gameTime + rift.getActionDelayTicks()
                + boss.rageDown(rift.getCooldownTicks()));
        return true;
    }

    /** How far the boss looks for somebody to take: as far as it looks for anyone to fight. */
    private static double reach(TeleportPathData data) {
        return Math.max(1, data.getTargetSearchRadius());
    }

    /**
     * A player fighting this boss, on its arena and free to be taken: not held by a capture, a
     * cocoon or a storm, whose hold would be broken by the trip.
     */
    boolean isValidTarget(LivingEntity target, TeleportPathData data) {
        if (!(target instanceof ServerPlayer player) || player.level() != npc.level() || !player.isAlive()
                || player.isRemoved() || !boss.isAbilityTarget(player, BossAbilityKind.RIFT)
                || !boss.isEncounterParticipant(player)
                || BossCaptureManager.isCaptured(player.getUUID())
                || BossCocoonManager.isCocooned(player.getUUID())
                || BossHurricaneScheduler.isHeld(player.getUUID())) {
            return false;
        }
        double reach = reach(data);
        return npc.distanceToSqr(player) <= reach * reach;
    }

    /** Cuts the rift open and takes everyone this cast wound up on who is still there to take. */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        TeleportPathData data = boss.settings();
        List<ServerPlayer> victims = new ArrayList<>();
        LivingEntity primary = boss.pendingTarget(level);
        if (primary instanceof ServerPlayer player && isValidTarget(player, data)) {
            victims.add(player);
        }
        for (int id : boss.pendingExtraTargets()) {
            if (level.getEntity(id) instanceof ServerPlayer extra && isValidTarget(extra, data)
                    && !victims.contains(extra)) {
                victims.add(extra);
            }
        }
        if (victims.isEmpty()) {
            return;
        }
        // Signed up while they still stand on the arena: once through, they are a level away.
        for (ServerPlayer victim : victims) {
            boss.trackParticipant(victim);
        }
        BossRiftSettings rift = phase.rift();
        if (BossRiftManager.start(level, npc, rift, victims, soloAtStart, gameTime) <= 0) {
            return;
        }
        rift.getCutSound().play(level, npc.getX(), npc.getY(), npc.getZ(), SoundSource.HOSTILE);
        rift.getCutParticles().emitDust(level, npc.getX(), npc.getY(0.5D), npc.getZ(),
                npc.getBbWidth() * 0.8D, npc.getBbHeight() * 0.5D, npc.getBbWidth() * 0.8D, 0.1D,
                BossAbilityKind.RIFT);
    }

    /** Whether this boss has a rift open right now. */
    boolean isActive() {
        return BossRiftManager.isActive(npc.getUUID());
    }

    /** Closes this boss' rift, with everyone brought back and no outcome: the fight it was part of is over. */
    void clear() {
        BossRiftManager.clearBoss(npc);
    }

    /**
     * What the fight turns into once a rift has closed with its players home: a solo success
     * leaves the boss exposed, a group's success simply lifts the cut on its damage, and a failure
     * costs whatever the phase switched on - the enrage, a hit on everyone within reach with its
     * potions, the boss healing. Then the usual pause after anything the boss does.
     *
     * @param settings the rules the rift opened under, which it closes under too
     */
    void onFinished(ServerLevel level, BossRiftOutcome.Result result, boolean solo, BossRiftSettings settings) {
        long gameTime = level.getGameTime();
        TeleportPathData data = boss.settings();
        if (result == BossRiftOutcome.Result.SUCCESS) {
            settings.getSuccessSound().play(level, npc.getX(), npc.getY(), npc.getZ(), SoundSource.HOSTILE);
            if (BossRiftOutcome.exposes(result, solo, settings.getSoloVulnerableTicks())) {
                boss.exposeAfterRift(level, gameTime + settings.getSoloVulnerableTicks(),
                        settings.getSoloVulnerablePercent());
            }
        } else if (result == BossRiftOutcome.Result.FAILURE) {
            settings.getFailSound().play(level, npc.getX(), npc.getY(), npc.getZ(), SoundSource.HOSTILE);
            Set<BossRiftOutcome.Penalty> penalties = BossRiftOutcome.penalties(settings);
            // An enrage the boss has no timer for is taken off again by its own clock on the next
            // tick, and an enraged boss has nothing left to set off.
            if (penalties.contains(BossRiftOutcome.Penalty.RAGE) && data.isRageEnabled() && !boss.isRageActive()) {
                boss.beginRage(level, gameTime, data);
            }
            boolean damage = penalties.contains(BossRiftOutcome.Penalty.ARENA_DAMAGE);
            boolean effects = penalties.contains(BossRiftOutcome.Penalty.EFFECTS);
            if (damage || effects) {
                int amount = damage ? boss.damageUp(settings.getFailArenaDamage()) : 0;
                for (LivingEntity target : boss.getTargetsAround(level, npc.position(),
                        settings.getFailArenaRadius(), BossAbilityKind.RIFT)) {
                    if (boss.matchesAbilityTargetKind(target, data)) {
                        BossAbilityDamageUtil.hit(target, BossAbilityKind.RIFT, npc, amount,
                                effects ? settings.getFailEffects() : null, 0, 0.0D, 0.0D);
                    }
                }
            }
            if (penalties.contains(BossRiftOutcome.Penalty.HEAL)) {
                // Through heal(), so a boss sharing its health with others shares this too.
                npc.heal(BossRiftOutcome.healAmount(npc.getMaxHealth(), settings.getFailHealPercent()));
            }
        }
        boss.lockActionsUntil(gameTime + boss.postActionLockTicks());
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime) {
        String open = BossRiftManager.status(npc.getUUID(), gameTime);
        if (open != null) {
            return open;
        }
        BossPhaseData phase = boss.activePhase();
        if (phase == null || !phase.rift().isEnabled()) {
            return "Rift: disabled";
        }
        if (!phase.rift().canCast()) {
            return "Rift: no minion clone to stand up";
        }
        long remaining = boss.abilityCooldownLeft(BossAbility.RIFT, gameTime);
        return remaining > 0L ? "Rift: cooldown " + remaining : "Rift: ready";
    }

    /**
     * Whether the phase lets the boss cast this while its rift is open. Read off the phase as it
     * stands rather than off the cast: the rift ends with the phase, and a builder trying the
     * mask out sees the change on the next tick.
     */
    boolean allowsMeanwhile(int kind) {
        BossPhaseData phase = boss.activePhase();
        return phase != null && phase.rift().castsMeanwhile(kind);
    }

    /** Whether the cast now being wound up was settled as a solo one. */
    boolean isSoloAtStart() {
        return soloAtStart;
    }
}
