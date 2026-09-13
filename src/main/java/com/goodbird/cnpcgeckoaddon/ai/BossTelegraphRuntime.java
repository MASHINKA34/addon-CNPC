package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

final class BossTelegraphRuntime {

    record Cast(BossAbility action, long executesAt, long startedAt, int targetId,
                List<Integer> extraTargets, Vec3 axis, float yaw) {
        Cast {
            extraTargets = List.copyOf(extraTargets);
        }

        LivingEntity target(ServerLevel level) {
            return level.getEntity(targetId) instanceof LivingEntity living && living.isAlive() ? living : null;
        }

        /**
         * How far the wind-up has got, from the tick the boss committed to the tick it lands.
         *
         * <p>{@link BossTelegraphPaint#NO_END} for a cast with nothing to count towards, which
         * is what a mark drawn between the swings of a series has: the boss is not winding
         * anything up, it is already swinging.</p>
         */
        float progress(long gameTime) {
            if (startedAt == TeleportPathController.NOT_SCHEDULED || executesAt <= startedAt) {
                return BossTelegraphPaint.NO_END;
            }
            return Mth.clamp((float) (gameTime - startedAt) / (executesAt - startedAt), 0.0F, 1.0F);
        }
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    private final BossCoverRuntime coverRuntime;
    private final BossHuntRuntime huntRuntime;
    private final BossLeapRuntime leap;
    private final BossDashRuntime dash;
    private final BossConeRuntime cone;
    private final BossPlatformRuntime platform;
    private final BossMinionSpawnRuntime minionSpawns;

    BossTelegraphRuntime(TeleportPathController boss, EntityNPCInterface npc, BossCoverRuntime coverRuntime,
                         BossHuntRuntime huntRuntime, BossLeapRuntime leap, BossDashRuntime dash,
                         BossConeRuntime cone, BossPlatformRuntime platform, BossMinionSpawnRuntime minionSpawns) {
        this.boss = boss;
        this.npc = npc;
        this.coverRuntime = coverRuntime;
        this.huntRuntime = huntRuntime;
        this.leap = leap;
        this.dash = dash;
        this.cone = cone;
        this.platform = platform;
        this.minionSpawns = minionSpawns;
    }

    void tick(ServerLevel level, TeleportPathData data, long gameTime, Cast cast) {
        if (cast.action() == BossAbility.NONE || gameTime >= cast.executesAt()
                || gameTime % data.tuning().telegraphIntervalTicks() != 0L) {
            return;
        }
        paint(level, data, gameTime, cast);
    }

    void paint(ServerLevel level, TeleportPathData data, long gameTime, Cast cast) {
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
                BossTelegraphUtil.audienceRange(npc), false) == null) {
            return;
        }
        BossTelegraphPaint paint = BossTelegraphPaint.of(data, npc,
                BossTelegraphPaint.CHANNEL_CAST, ability, cast.progress(gameTime));
        if (fieldEdge || cover || data.isTelegraphZone()) {
            drawTelegraphZone(level, data, paint, cast);
        }
        if (cover || (warns && data.isTelegraphAura())) {
            // The boss lit up in its own colour is not a shape on the floor, so it stays dust
            // whatever the zone is drawn with.
            BossTelegraphUtil.aura(level, npc, paint.dust());
        }
    }

    void announce(TeleportPathData data, BossAbility action) {
        int ability = action.kind();
        if (ability < 0 || !telegraphs(data, ability)) {
            return;
        }
        if (data.isTelegraphSound() && npc.level() instanceof ServerLevel level) {
            // The switch above is still the master one: the cue only says what it sounds like.
            data.tuning().telegraphSound().play(level, npc.getX(), npc.getY(), npc.getZ(),
                    SoundSource.HOSTILE);
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

    private void drawTelegraphZone(ServerLevel level, TeleportPathData data,
                                   BossTelegraphPaint paint, Cast cast) {
        BossPhaseData phase = data.getPhase(boss.currentPhaseIndex());
        switch (cast.action()) {
            case GROUND_ATTACK -> BossTelegraphUtil.ring(level, npc.position(),
                    phase.areaAttack().getRadius(), paint);
            case LINE_ATTACK -> {
                if (cast.axis() != null) {
                    BossTelegraphUtil.corridor(level, npc.position(), cast.axis(),
                            phase.lineAttack().getLength(), phase.lineAttack().getWidth(),
                            phase.lineAttack().getSideWidth(), paint);
                }
            }
            case BOULDER -> {
                if (cast.axis() != null) {
                    BossTelegraphUtil.corridor(level, npc.position(), cast.axis(),
                            phase.boulder().getRange(), phase.boulder().getScale() / 10.0D,
                            0.0D, paint);
                }
            }
            // The lane as far as the run will really go: a home leash that cuts it short cuts
            // the mark short too, so nobody dodges out of a stretch the boss never reaches.
            case DASH -> {
                if (cast.axis() != null) {
                    BossTelegraphUtil.corridor(level, npc.position(), cast.axis(),
                            dash.previewReach(data, phase, cast.axis()), phase.dash().getWidth(),
                            0.0D, paint);
                }
            }
            // Every cone the cast lands, laid from where the boss stands now: the first bright and
            // the ones a series strikes after it faded, unless they all land together.
            case CONE -> {
                List<Vec3> axes = cone.axesFor(cast.axis());
                int bright = phase.cone().getPointIntervalTicks() == 0 ? axes.size() : 1;
                drawConeSectors(level, phase, axes, bright, paint);
            }
            // The outline of every platform the cast sets alight, the arena hazard's box; the fuse
            // after the wind-up flashes the same outline whatever the warnings say.
            case PLATFORM -> platform.drawCommitted(level, paint);
            case MELEE_ATTACK -> BossTelegraphUtil.arc(level, npc.position(),
                    phase.meleeAttack().getRange(), npc.getYRot(),
                    data.tuning().telegraphMeleeHalfAngle(), paint);
            case RANGED_ATTACK, FLUID_SPIT, CAPTURE, HUNT ->
                    drawTelegraphTargetZone(level, data, cast.target(level), paint);
            case HOOK, GEYSER, MARK, COCOON -> {
                drawTelegraphTargetZone(level, data, cast.target(level), paint);
                for (int id : cast.extraTargets()) {
                    if (level.getEntity(id) instanceof LivingEntity victim) {
                        drawTelegraphTargetZone(level, data, victim, paint);
                    }
                }
            }
            case SUMMON -> drawTelegraphSpawnRings(level, data, phase, paint);
            case GRAVITY -> BossTelegraphUtil.ring(level, npc.position(), phase.gravity().getRadius(), paint);
            // The ring the volley will fall in, and the dead zone at the boss' feet where it
            // cannot: nothing is aimed at anybody, so the shape is the whole warning.
            case BOULDER_RAIN -> {
                BossTelegraphUtil.ring(level, npc.position(), phase.boulderRain().getRadius(), paint);
                if (phase.boulderRain().getMinRadius() > 0) {
                    BossTelegraphUtil.ring(level, npc.position(), phase.boulderRain().getMinRadius(), paint);
                }
            }
            case BEAM -> {
                BossTelegraphUtil.ring(level, npc.position(), phase.beam().getLength(), paint);
                // The beams themselves are not shapes on the floor, so they stay dust.
                BossBeamScheduler.paintStart(level, npc, cast.yaw(), phase.beam().getCount(),
                        phase.beam().getLength(), phase.beam().isStopsAtWalls());
            }
            case COVER -> coverRuntime.drawShelters(level, paint);
            case TETHER -> {
                if (phase.tether().getAnchor() == BossPhaseData.TETHER_ANCHOR_BOSS) {
                    BossTelegraphUtil.ring(level, npc.position(), phase.tether().getBreakDistance(), paint);
                } else if (!data.isTelegraphAura()) {
                    BossTelegraphUtil.aura(level, npc, paint.dust());
                }
            }
            case LEAP -> {
                BossPhaseData leaping = leap.phaseOf(data);
                Vec3 landing = leap.destination();
                if (leaping != null && landing != null) {
                    BossTelegraphUtil.ring(level, landing, leaping.leap().getImpactRadius(), paint);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Marks the cone a series strikes next.
     *
     * <p>Nothing is wound up between two cones of a series, so the controller asks for this on
     * the paint clock rather than through {@link #tick}. Only the next one: the wind-up already
     * showed the whole series, and a series of sixteen long cones repainted every other tick for
     * as long as it lasts is a flood of particles. The warning switches rule it the way they rule
     * the wind-up's mark, minus the aura: the boss is already swinging.</p>
     */
    void paintConeSeries(ServerLevel level, TeleportPathData data) {
        Vec3 next = cone.nextAxis();
        if (next == null || !telegraphs(data, BossAbilityKind.CONE) || !data.isTelegraphZone()
                || level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(),
                BossTelegraphUtil.audienceRange(npc), false) == null) {
            return;
        }
        // Nothing is being wound up between two cones of a series, so the mark has no end to
        // count towards and stands as it is however the wind-up's own was told to move.
        drawConeSectors(level, data.getPhase(boss.currentPhaseIndex()), List.of(next), 1,
                BossTelegraphPaint.of(data, npc, BossTelegraphPaint.CHANNEL_CAST,
                        BossAbilityKind.CONE, BossTelegraphPaint.NO_END));
    }

    /** The fans laid from the boss along {@code axes}, the first {@code bright} of them in full colour. */
    private void drawConeSectors(ServerLevel level, BossPhaseData phase, List<Vec3> axes, int bright,
                                 BossTelegraphPaint paint) {
        double length = phase.cone().getLength();
        double halfAngle = phase.cone().getAngle() * 0.5D;
        for (int i = 0; i < axes.size(); i++) {
            float yaw = BossConeRuntime.yawOf(axes.get(i));
            if (i < bright) {
                BossTelegraphUtil.sector(level, npc.position(), length, yaw, halfAngle, paint);
            } else {
                BossTelegraphUtil.fadedSector(level, npc.position(), length, yaw, halfAngle, paint);
            }
        }
    }

    private void drawTelegraphTargetZone(ServerLevel level, TeleportPathData data,
                                         LivingEntity target, BossTelegraphPaint paint) {
        if (target == null) {
            return;
        }
        drawTelegraphLine(level, target, paint);
        BossTelegraphUtil.ring(level, target.position(), data.getTelegraphZoneRadius(), paint);
    }

    private void drawTelegraphLine(ServerLevel level, LivingEntity target, BossTelegraphPaint paint) {
        if (target == null) {
            return;
        }
        BossTelegraphUtil.line(level, new Vec3(npc.getX(), npc.getEyeY() - 0.2D, npc.getZ()),
                target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), paint);
    }

    private void drawTelegraphSpawnRings(ServerLevel level, TeleportPathData data,
                                         BossPhaseData phase, BossTelegraphPaint paint) {
        int drawn = 0;
        int rings = data.tuning().telegraphSpawnRings();
        if (phase.summon().getSpawnMode() != BossPhaseData.MINION_SPAWN_RANDOM_RADIUS) {
            for (BossMinionSpawnPoint point : phase.summon().getSpawnPoints().entries()) {
                if (drawn >= rings) {
                    break;
                }
                if (point.isEnabled()) {
                    BossTelegraphUtil.ring(level, minionSpawns.pointAnchor(point),
                            data.tuning().telegraphSpawnRingRadius(), paint);
                    drawn++;
                }
            }
        }
        if (drawn == 0) {
            BossTelegraphUtil.ring(level, npc.position(), phase.summon().getRadius(), paint);
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
