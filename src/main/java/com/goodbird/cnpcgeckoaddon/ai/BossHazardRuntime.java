package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The arena itself turning dangerous for a phase: a safe circle closing in, or a box that
 * starts to burn.
 *
 * <p>Owned by {@link TeleportPathController}, which arms it on every phase it enters and
 * ticks it above its own combat gates. Nothing here is saved - a server that goes down mid
 * phase starts the hazard over from the next time the phase is entered.</p>
 */
final class BossHazardRuntime {

    /**
     * How far past the safe circle's first edge an arena hazard still burns: the arena's
     * surroundings, not the world. Somebody who died and came back at a bed across the map
     * is out of the fight, not standing in the fire, and must not be bled there until the
     * boss gets round to resetting.
     */
    private static final double RING_REACH = 32.0D;
    /** Half a flash: the warning edge is painted for this many ticks, then not for as many. */
    private static final int BLINK_TICKS = 4;

    /**
     * The arena hazard of the phase being fought, frozen on the tick the phase began.
     *
     * <p>Read back from here rather than off the phase again, the way a take cover strike
     * keeps its settings: the ring's centre is where the boss stood as the phase opened, and
     * a box a builder drags about mid-fight must not move under the people already standing
     * clear of it.</p>
     */
    private static final class ArenaHazard {
        private final int mode;
        /** Game time the edge starts flashing at; never after {@link #opensAt}. */
        private final long warnsAt;
        /** Game time the arena turns dangerous at. */
        private final long opensAt;
        /** Ring: what the safe circle closes in on. Its height is the floor the edge is drawn on. */
        private final Vec3 centre;
        private final double startRadius;
        private final double endRadius;
        private final int shrinkTicks;
        /** Box: the volume that burns, or null when its corners leave no room between them. */
        private final AABB box;
        /** Box: the height its outline is drawn at - inside the box, and as near the boss as it gets. */
        private final double floorY;
        /** What one dose hits for before the enrage bonus, which is read fresh on every dose. */
        private final int damage;
        private final int intervalTicks;
        private final BossEffectSet effects;
        /** Game time the next dose goes out at; the first is owed the moment the hazard opens. */
        private long nextHitAt;

        private ArenaHazard(BossPhaseData phase, long gameTime, Vec3 centre, AABB box, double floorY) {
            mode = phase.hazard().getMode();
            opensAt = gameTime + phase.hazard().getDelayTicks();
            warnsAt = Math.max(gameTime, opensAt - phase.hazard().getWarnTicks());
            this.centre = centre;
            startRadius = phase.hazard().getStartRadius();
            endRadius = phase.hazard().getEndRadius();
            shrinkTicks = phase.hazard().getShrinkTicks();
            this.box = box;
            this.floorY = floorY;
            damage = phase.hazard().getDamage();
            intervalTicks = phase.hazard().getIntervalTicks();
            effects = phase.hazard().getEffects();
            nextHitAt = opensAt;
        }

        /** How wide the safe circle is on this tick: closing from the start to the end, then held. */
        private double ringRadius(long gameTime) {
            if (gameTime <= opensAt) {
                return startRadius;
            }
            double progress = Math.min(1.0D, (double) (gameTime - opensAt) / shrinkTicks);
            return Mth.lerp(progress, startRadius, endRadius);
        }

        /**
         * Whether this spot is in the fire: inside the box, or outside the circle.
         *
         * <p>The circle is measured flat. It is a shape on the floor, and a balcony over the
         * fire is still over the fire.</p>
         */
        private boolean burns(Vec3 position, long gameTime) {
            if (mode == BossPhaseData.HAZARD_MODE_BOX) {
                return box != null && box.contains(position);
            }
            double dx = position.x - centre.x;
            double dz = position.z - centre.z;
            double distanceSquared = dx * dx + dz * dz;
            double radius = ringRadius(gameTime);
            double reach = startRadius + RING_REACH;
            return distanceSquared > radius * radius && distanceSquared <= reach * reach;
        }
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The arena hazard of the phase being fought, or null while the arena is safe. */
    private ArenaHazard hazard;

    BossHazardRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /**
     * Arms the arena hazard of the phase the boss is fighting in, and drops the last one.
     *
     * <p>Whatever the new phase brings, the old hazard goes: a phase change resets the
     * arena. The clock runs from here, so a boss left wounded and pulled again in a later
     * phase gives that phase's grace from the pull, not from whenever it first got there.</p>
     */
    void arm(ServerLevel level, long gameTime, BossPhaseData phase) {
        hazard = null;
        if (!phase.hazard().isEnabled()) {
            return;
        }
        if (phase.hazard().getMode() == BossPhaseData.HAZARD_MODE_BOX) {
            AABB box = boxBounds(level, phase);
            double floorY = box == null ? npc.getY() : Mth.clamp(npc.getY(), box.minY, box.maxY - 1.0D);
            hazard = new ArenaHazard(phase, gameTime, null, box, floorY);
            return;
        }
        Vec3 centre = phase.hazard().getCenterMode() == BossPhaseData.HAZARD_CENTER_POINT
                // The middle of the block, so a spot picked by standing on it is that spot.
                ? new Vec3(phase.hazard().getCenterX() + 0.5D, npc.getY(), phase.hazard().getCenterZ() + 0.5D)
                : npc.position();
        hazard = new ArenaHazard(phase, gameTime, centre, null, centre.y);
    }

    void clear() {
        hazard = null;
    }

    /**
     * The box a hazard burns in, cut to this dimension's real build height the way the
     * aggro zone's is. Its corners are read the same way too: either order, both inclusive.
     */
    private static AABB boxBounds(ServerLevel level, BossPhaseData phase) {
        int minY = Math.max(Math.min(phase.hazard().getY1(), phase.hazard().getY2()), level.getMinBuildHeight());
        int maxY = Math.min(Math.max(phase.hazard().getY1(), phase.hazard().getY2()), level.getMaxBuildHeight() - 1);
        if (minY > maxY) {
            return null;
        }
        int minX = Math.min(phase.hazard().getX1(), phase.hazard().getX2());
        int minZ = Math.min(phase.hazard().getZ1(), phase.hazard().getZ2());
        int maxX = Math.max(phase.hazard().getX1(), phase.hazard().getX2());
        int maxZ = Math.max(phase.hazard().getZ1(), phase.hazard().getZ2());
        // The upper AABB bounds are exclusive, so adding one includes every block of corner 2.
        return new AABB(minX, minY, minZ, (double) maxX + 1.0D, (double) maxY + 1.0D, (double) maxZ + 1.0D);
    }

    /**
     * Runs the arena hazard of the phase being fought: the warning, then the fire.
     *
     * <p>Ticked above the combat-only and busy gates on purpose, the way the telegraph is:
     * the arena does not stop burning because the boss lost sight of its target for a moment
     * or is held in an animation. It stops when the phase ends, or the fight does.</p>
     */
    void tick(ServerLevel level, TeleportPathData data, long gameTime) {
        ArenaHazard hazard = this.hazard;
        if (hazard == null) {
            return;
        }
        // Switched off mid-fight, the hazard goes out at once rather than burning on until
        // the phase ends; everything else it was armed with stays as it was.
        if (!boss.isEncounterRunning() || !data.getPhase(boss.currentPhaseIndex()).hazard().isEnabled()) {
            this.hazard = null;
            return;
        }
        if (gameTime < hazard.warnsAt) {
            return;
        }
        boolean open = gameTime >= hazard.opensAt;
        if (gameTime % TeleportPathController.TELEGRAPH_INTERVAL_TICKS == 0L) {
            if (!open) {
                announceCountdown(level, hazard, gameTime);
            }
            paint(level, hazard, gameTime, open);
        }
        if (!open || gameTime < hazard.nextHitAt) {
            return;
        }
        hazard.nextHitAt = gameTime + hazard.intervalTicks;
        for (LivingEntity victim : victims(level, hazard, gameTime)) {
            // No knockback: the fire is the ground, and the ground does not shove.
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.HAZARD, npc, boss.rageUp(hazard.damage),
                    hazard.effects, 0, 0.0D, 0.0D);
        }
    }

    /**
     * The edge of the fire, painted whatever the warning settings say.
     *
     * <p>It is the mechanic rather than a warning about one - where to be standing, or not -
     * so it goes down the way the gravity field's edge does: an edge nobody can see is not a
     * warning left off, it is a trap. Flashing until the hazard opens, painted in bursts
     * with gaps as long between them, and steady from then on.</p>
     */
    private void paint(ServerLevel level, ArenaHazard hazard, long gameTime, boolean open) {
        if (!open && (gameTime / BLINK_TICKS) % 2L != 0L) {
            return;
        }
        DustParticleOptions dust = BossTelegraphUtil.dust(BossAbilityKind.HAZARD);
        if (hazard.mode == BossPhaseData.HAZARD_MODE_BOX) {
            AABB box = hazard.box;
            if (box != null && hasAudience(level, box.getCenter(),
                    Math.max(box.getXsize(), box.getZsize()) * 0.5D)) {
                BossTelegraphUtil.rectangle(level, box.minX, box.minZ, box.maxX, box.maxZ,
                        hazard.floorY, dust);
            }
        } else if (hasAudience(level, hazard.centre, hazard.startRadius)) {
            BossTelegraphUtil.edgeRing(level, hazard.centre, hazard.ringRadius(gameTime), dust);
        }
    }

    /**
     * Decoration only, so a hazard with nobody near enough to see its edge costs nothing.
     * The shape's own reach is added on: its edge can be a long way from its middle.
     */
    private static boolean hasAudience(ServerLevel level, Vec3 centre, double reach) {
        return level.getNearestPlayer(centre.x, centre.y, centre.z,
                BossTelegraphUtil.AUDIENCE_RANGE + reach, false) != null;
    }

    /**
     * The name and the time left, in the action bar of everyone this fight belongs to.
     *
     * <p>Sent on every repaint rather than once, the way the take cover countdown is: the
     * line is what says how long there is to get clear. It goes to every participant and
     * not only to whoever has a bar up, because the fire reaches them wherever they stand.</p>
     */
    private void announceCountdown(ServerLevel level, ArenaHazard hazard, long gameTime) {
        // Rounded up, so the last second reads as one rather than as none. The numbers go
        // in through %s: vanilla's translation formatter takes that one placeholder and
        // nothing else, and a %d would leave the raw template on the screen.
        int seconds = (int) Math.max(1L, (hazard.opensAt - gameTime + 19L) / 20L);
        Component line = Component.translatable("cnpcgeckoaddon.boss.hazard_countdown",
                        Component.translatable(BossAbilityKind.LABELS[BossAbilityKind.HAZARD]), seconds)
                .withStyle(style -> style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.HAZARD)));
        Set<ServerPlayer> audience = new LinkedHashSet<>(boss.timerBossEvent().getPlayers());
        for (UUID playerId : boss.encounterParticipants()) {
            if (level.getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                audience.add(player);
            }
        }
        for (ServerPlayer player : audience) {
            player.displayClientMessage(line, true);
        }
    }

    /**
     * Everyone standing in the fire on this tick, judged by this boss.
     *
     * <p>Players have to belong to this fight, the way they do for a mark: the arena is a
     * problem set to the party, and a passer-by cannot be made to pay for it. Npcs come in
     * by the ordinary victim rules and by the species the boss is set to fight, and anyone
     * hidden by their own totems is passed over the way every arena-wide sweep passes them.
     * The boss is never in its own fire, and by the same rules neither are its minions or
     * its totems.</p>
     */
    private List<LivingEntity> victims(ServerLevel level, ArenaHazard hazard, long gameTime) {
        AABB sweep = hazard.mode == BossPhaseData.HAZARD_MODE_BOX
                ? hazard.box
                : new AABB(hazard.centre, hazard.centre).inflate(hazard.startRadius + RING_REACH);
        if (sweep == null) {
            return List.of();
        }
        TeleportPathData data = boss.settings();
        return level.getEntitiesOfClass(LivingEntity.class, sweep, target ->
                target != npc && target.isAlive() && hazard.burns(target.position(), gameTime)
                        && (!(target instanceof Player player) || boss.isEncounterParticipant(player))
                        && boss.matchesAbilityTargetKind(target, data)
                        && !BossMechanicUtil.hiddenByTotems(target)
                        && boss.isAbilityTarget(target, BossAbilityKind.HAZARD));
    }
}
