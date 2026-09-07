package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.TELEGRAPH_INTERVAL_TICKS;

final class BossTelegraphRuntime {
    private static final double TELEGRAPH_MELEE_HALF_ANGLE = 60.0D;
    private static final double TELEGRAPH_SPAWN_RING_RADIUS = 1.0D;
    private static final int TELEGRAPH_MAX_SPAWN_RINGS = 8;
    private static final float TELEGRAPH_SOUND_VOLUME = 0.8F;
    private static final float TELEGRAPH_SOUND_PITCH = 0.6F;

    record Cast(BossAbility action, long executesAt, int targetId, List<Integer> extraTargets, Vec3 axis, float yaw) {
        Cast {
            extraTargets = List.copyOf(extraTargets);
        }

        LivingEntity target(ServerLevel level) {
            return level.getEntity(targetId) instanceof LivingEntity living && living.isAlive() ? living : null;
        }
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    private final BossCoverRuntime coverRuntime;
    private final BossHuntRuntime huntRuntime;
    private final BossLeapRuntime leap;
    private final BossMinionSpawnRuntime minionSpawns;

    BossTelegraphRuntime(TeleportPathController boss, EntityNPCInterface npc, BossCoverRuntime coverRuntime,
                         BossHuntRuntime huntRuntime, BossLeapRuntime leap, BossMinionSpawnRuntime minionSpawns) {
        this.boss = boss;
        this.npc = npc;
        this.coverRuntime = coverRuntime;
        this.huntRuntime = huntRuntime;
        this.leap = leap;
        this.minionSpawns = minionSpawns;
    }

    void tick(ServerLevel level, TeleportPathData data, long gameTime, Cast cast) {
        if (cast.action() == BossAbility.NONE || gameTime >= cast.executesAt()
                || gameTime % TELEGRAPH_INTERVAL_TICKS != 0L) {
            return;
        }
        paint(level, data, cast);
    }

    void paint(ServerLevel level, TeleportPathData data, Cast cast) {
        int ability = cast.action().kind();
        if (ability < 0) {
            return;
        }
        boolean warns = telegraphs(data, ability);
        boolean fieldEdge = cast.action() == BossAbility.GRAVITY;
        boolean cover = cast.action() == BossAbility.COVER;
        if (cover) {
            coverRuntime.announceCountdown(level, cast.executesAt());
        }
        if (cast.action() == BossAbility.HUNT) {
            huntRuntime.announce(cast.target(level));
        }
        if (!warns && !fieldEdge && !cover) {
            return;
        }
        if (level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(),
                BossTelegraphUtil.AUDIENCE_RANGE, false) == null) {
            return;
        }
        DustParticleOptions dust = BossTelegraphUtil.dust(ability);
        if (fieldEdge || cover || data.isTelegraphZone()) {
            drawTelegraphZone(level, data, ability, dust, cast);
        }
        if (cover || (warns && data.isTelegraphAura())) {
            BossTelegraphUtil.aura(level, npc, dust);
        }
    }

    void announce(TeleportPathData data, BossAbility action) {
        int ability = action.kind();
        if (ability < 0 || !telegraphs(data, ability)) {
            return;
        }
        if (data.isTelegraphSound() && npc.level() instanceof ServerLevel level) {
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE,
                    TELEGRAPH_SOUND_VOLUME, TELEGRAPH_SOUND_PITCH);
        }
        if (!data.isTelegraphAnnounce()) {
            return;
        }
        Component name = Component.translatable(BossAbilityKind.LABELS[ability])
                .withStyle(style -> style.withColor(BossTelegraphUtil.textColor(ability)));
        for (ServerPlayer player : boss.timerBossEvent().getPlayers()) {
            player.displayClientMessage(name, true);
        }
    }

    private void drawTelegraphZone(ServerLevel level, TeleportPathData data, int ability,
                                   DustParticleOptions dust, Cast cast) {
        BossPhaseData phase = data.getPhase(boss.currentPhaseIndex());
        switch (cast.action()) {
            case GROUND_ATTACK -> BossTelegraphUtil.ring(level, npc.position(),
                    phase.areaAttack().getRadius(), dust);
            case LINE_ATTACK -> {
                if (cast.axis() != null) {
                    BossTelegraphUtil.corridor(level, npc.position(), cast.axis(),
                            phase.lineAttack().getLength(), phase.lineAttack().getWidth(),
                            phase.lineAttack().getSideWidth(), dust,
                            BossTelegraphUtil.fadedDust(ability));
                }
            }
            case BOULDER -> {
                if (cast.axis() != null) {
                    BossTelegraphUtil.corridor(level, npc.position(), cast.axis(),
                            phase.boulder().getRange(), phase.boulder().getScale() / 10.0D,
                            0.0D, dust, BossTelegraphUtil.fadedDust(ability));
                }
            }
            case MELEE_ATTACK -> BossTelegraphUtil.arc(level, npc.position(),
                    phase.meleeAttack().getRange(), npc.getYRot(), TELEGRAPH_MELEE_HALF_ANGLE, dust);
            case RANGED_ATTACK, FLUID_SPIT, CAPTURE, HUNT ->
                    drawTelegraphTargetZone(level, data, cast.target(level), dust);
            case HOOK, GEYSER, MARK, COCOON -> {
                drawTelegraphTargetZone(level, data, cast.target(level), dust);
                for (int id : cast.extraTargets()) {
                    if (level.getEntity(id) instanceof LivingEntity victim) {
                        drawTelegraphTargetZone(level, data, victim, dust);
                    }
                }
            }
            case SUMMON -> drawTelegraphSpawnRings(level, phase, dust);
            case GRAVITY -> BossTelegraphUtil.ring(level, npc.position(), phase.gravity().getRadius(), dust);
            case BEAM -> {
                BossTelegraphUtil.ring(level, npc.position(), phase.beam().getLength(), dust);
                BossBeamScheduler.paintStart(level, npc, cast.yaw(), phase.beam().getCount(),
                        phase.beam().getLength(), phase.beam().isStopsAtWalls());
            }
            case COVER -> coverRuntime.drawShelters(level, dust);
            case TETHER -> {
                if (phase.tether().getAnchor() == BossPhaseData.TETHER_ANCHOR_BOSS) {
                    BossTelegraphUtil.ring(level, npc.position(), phase.tether().getBreakDistance(), dust);
                } else if (!data.isTelegraphAura()) {
                    BossTelegraphUtil.aura(level, npc, dust);
                }
            }
            case LEAP -> {
                BossPhaseData leaping = leap.phaseOf(data);
                Vec3 landing = leap.destination();
                if (leaping != null && landing != null) {
                    BossTelegraphUtil.ring(level, landing, leaping.leap().getImpactRadius(), dust);
                }
            }
            default -> {
            }
        }
    }

    private void drawTelegraphTargetZone(ServerLevel level, TeleportPathData data,
                                         LivingEntity target, DustParticleOptions dust) {
        if (target == null) {
            return;
        }
        drawTelegraphLine(level, target, dust);
        BossTelegraphUtil.ring(level, target.position(), data.getTelegraphZoneRadius(), dust);
    }

    private void drawTelegraphLine(ServerLevel level, LivingEntity target, DustParticleOptions dust) {
        if (target == null) {
            return;
        }
        BossTelegraphUtil.line(level, new Vec3(npc.getX(), npc.getEyeY() - 0.2D, npc.getZ()),
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), dust);
    }

    private void drawTelegraphSpawnRings(ServerLevel level, BossPhaseData phase,
                                         DustParticleOptions dust) {
        int drawn = 0;
        if (phase.summon().getSpawnMode() != BossPhaseData.MINION_SPAWN_RANDOM_RADIUS) {
            for (BossMinionSpawnPoint point : phase.summon().getSpawnPoints().entries()) {
                if (drawn >= TELEGRAPH_MAX_SPAWN_RINGS) {
                    break;
                }
                if (point.isEnabled()) {
                    BossTelegraphUtil.ring(level, minionSpawns.pointAnchor(point),
                            TELEGRAPH_SPAWN_RING_RADIUS, dust);
                    drawn++;
                }
            }
        }
        if (drawn == 0) {
            BossTelegraphUtil.ring(level, npc.position(), phase.summon().getRadius(), dust);
        }
    }

    private boolean telegraphs(TeleportPathData data, int ability) {
        if (!data.isTelegraphEnabled() || !data.isTelegraphAbility(ability)) {
            return false;
        }
        if (ability != BossAbilityKind.LEAP) {
            return true;
        }
        BossPhaseData phase = leap.phaseOf(data);
        return phase == null || phase.leap().isTelegraph();
    }

    int lead(TeleportPathData data, BossAbility action, int actionDelay) {
        int ability = action.kind();
        if (ability < 0 || !telegraphs(data, ability)) {
            return 0;
        }
        return Math.max(0, data.getTelegraphLeadTicks() - actionDelay);
    }

}
