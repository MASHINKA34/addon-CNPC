package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossCaptureState;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossLink;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns transient, server-authoritative captures. Nothing here is persisted.
 *
 * <p>A held player has to be fought for: their own client keeps sending movement, so the
 * hold only sticks because {@link #handleMovePacket} drops those packets. Everything else
 * is server-side already and simply gets pinned each tick, the way a boss pins its totems.</p>
 */
public final class BossCaptureManager {
    private static final double POSITION_EPSILON_SQUARED = 1.0E-8D;
    private static final double COLLISION_STEP = 0.05D;
    private static final Map<UUID, CaptureRuntime> BY_VICTIM = new HashMap<>();
    private static final Map<UUID, CaptureRuntime> BY_BOSS = new HashMap<>();

    private BossCaptureManager() {
    }

    private static final class CaptureRuntime {
        private final UUID victimId;
        private final int victimEntityId;
        private final String victimName;
        private final UUID bossId;
        private final int bossEntityId;
        private final ResourceKey<Level> levelKey;
        private final int originPhaseIndex;
        private final Vec3 anchor;
        private final long startedAt;
        private final long endsAt;
        private final long liftEndsAt;
        private final int mode;
        private double targetY;
        private final float lockedYaw;
        private final float lockedPitch;
        private final boolean allowLook;
        private final int beamChannel;
        private final String beamStyle;
        private final int beamWidthPercent;
        private final int beamSagPercent;

        private CaptureRuntime(EntityNPCInterface boss, LivingEntity victim, BossPhaseData phase,
                               int phaseIndex, long gameTime, double targetY, int liftTicks) {
            this.victimId = victim.getUUID();
            this.victimEntityId = victim.getId();
            this.victimName = victim.getName().getString();
            this.bossId = boss.getUUID();
            this.bossEntityId = boss.getId();
            this.levelKey = victim.level().dimension();
            this.originPhaseIndex = phaseIndex;
            this.anchor = victim.position();
            this.startedAt = gameTime;
            this.endsAt = gameTime + phase.getCaptureDurationTicks();
            this.liftEndsAt = gameTime + liftTicks;
            this.mode = phase.getCaptureMode();
            this.targetY = targetY;
            this.lockedYaw = victim.getYRot();
            this.lockedPitch = victim.getXRot();
            this.allowLook = phase.isCaptureAllowLook();
            this.beamChannel = 0;
            this.beamStyle = phase.getCaptureBeamStyle();
            this.beamWidthPercent = phase.getCaptureBeamWidthPercent();
            this.beamSagPercent = phase.getCaptureBeamSagPercent();
        }
    }

    /** Atomically claims both the victim and boss, preventing overlapping captures. */
    public static boolean start(EntityNPCInterface boss, LivingEntity victim,
                                BossPhaseData phase, int phaseIndex, long gameTime) {
        if (BY_VICTIM.containsKey(victim.getUUID()) || BY_BOSS.containsKey(boss.getUUID())
                || victim.level() != boss.level() || !(victim.level() instanceof ServerLevel level)
                || !level.noCollision(victim, victim.getBoundingBox())) {
            return false;
        }
        int liftTicks = Math.min(phase.getCaptureLiftTicks(), phase.getCaptureDurationTicks());
        double height = phase.getCaptureMode() == BossPhaseData.CAPTURE_MODE_LIFT
                ? safeLiftHeight(level, victim, victim.getBoundingBox(), phase.getCaptureLiftHeight()) : 0.0D;
        CaptureRuntime capture = new CaptureRuntime(boss, victim, phase, phaseIndex, gameTime,
                victim.getY() + height, liftTicks);
        BY_VICTIM.put(capture.victimId, capture);
        BY_BOSS.put(capture.bossId, capture);
        if (!hold(victim, capture, gameTime)) {
            BY_VICTIM.remove(capture.victimId);
            BY_BOSS.remove(capture.bossId);
            return false;
        }
        if (victim instanceof ServerPlayer player) {
            syncState(player, capture, true);
        }
        syncLink(boss, victim, capture, phase.getCaptureDurationTicks());
        return true;
    }

    public static boolean isCaptured(UUID victimId) {
        return BY_VICTIM.containsKey(victimId);
    }

    public static boolean hasCaptureForBoss(UUID bossId) {
        return BY_BOSS.containsKey(bossId);
    }

    public static String capturedVictimName(UUID bossId) {
        CaptureRuntime capture = BY_BOSS.get(bossId);
        return capture == null ? null : capture.victimName;
    }

    public static long remainingTicks(UUID bossId, long gameTime) {
        CaptureRuntime capture = BY_BOSS.get(bossId);
        return capture == null ? 0L : Math.max(0L, capture.endsAt - gameTime);
    }

    /** Gives a late observer the same remaining beam lifetime as current viewers. */
    public static void syncLinkForTracking(ServerPlayer viewer, Entity tracked) {
        if (!(viewer.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        for (CaptureRuntime capture : BY_VICTIM.values()) {
            if (!capture.levelKey.equals(level.dimension())
                    || tracked.getId() != capture.bossEntityId
                    && tracked.getId() != capture.victimEntityId) {
                continue;
            }
            Entity boss = level.getEntity(capture.bossId);
            Entity victim = level.getEntity(capture.victimId);
            int remaining = (int) Math.max(0L, capture.endsAt - gameTime);
            if (boss != null && victim != null && remaining > 0) {
                NetworkWrapper.send(viewer, linkPacket(capture, remaining));
            }
        }
    }

    /** Drops client movement while still accepting look packets when the phase allows it. */
    public static boolean handleMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        CaptureRuntime capture = BY_VICTIM.get(player.getUUID());
        if (capture == null || !player.level().dimension().equals(capture.levelKey)) {
            return false;
        }
        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        float yaw = packet.getYRot(player.getYRot());
        float pitch = packet.getXRot(player.getXRot());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return false;
        }
        if (capture.allowLook && packet.hasRotation()) {
            player.setYRot(Mth.wrapDegrees(yaw));
            player.setYHeadRot(player.getYRot());
            player.setXRot(Mth.wrapDegrees(pitch));
        } else if (!capture.allowLook) {
            player.setYRot(capture.lockedYaw);
            player.setYHeadRot(capture.lockedYaw);
            player.setXRot(capture.lockedPitch);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setKnownMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        return true;
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        for (CaptureRuntime capture : BY_VICTIM.values().toArray(CaptureRuntime[]::new)) {
            if (!capture.levelKey.equals(level.dimension())) {
                continue;
            }
            LivingEntity victim = level.getEntity(capture.victimId) instanceof LivingEntity found
                    ? found : null;
            Entity bossEntity = level.getEntity(capture.bossId);
            if (!isUsable(victim, bossEntity, capture) || gameTime >= capture.endsAt) {
                release(level, capture);
                continue;
            }
            if (capture.mode == BossPhaseData.CAPTURE_MODE_LIFT) {
                double safeHeight = safeLiftHeight(level, victim,
                        victim.getBoundingBox().move(capture.anchor.subtract(victim.position())),
                        Math.max(0.0D, capture.targetY - capture.anchor.y));
                if (safeHeight < 0.0D) {
                    release(level, capture);
                    continue;
                }
                double safeTargetY = capture.anchor.y + safeHeight;
                if (safeTargetY + 1.0E-4D < capture.targetY) {
                    capture.targetY = safeTargetY;
                    if (victim instanceof ServerPlayer player) {
                        syncState(player, capture, true);
                    }
                }
            }
            if (!hold(victim, capture, gameTime)) {
                release(level, capture);
            }
        }
    }

    private static boolean isUsable(LivingEntity victim, Entity bossEntity, CaptureRuntime capture) {
        if (victim == null || victim.isRemoved() || !victim.isAlive()
                || victim instanceof Player player && (player.isCreative() || player.isSpectator())
                || !victim.level().dimension().equals(capture.levelKey)
                || !(bossEntity instanceof EntityNPCInterface boss) || boss.isRemoved() || !boss.isAlive()) {
            return false;
        }
        if (!(boss instanceof IBossController holder)) {
            return false;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        return controller != null && controller.isCaptureEnabledForPhase(capture.originPhaseIndex);
    }

    private static boolean hold(LivingEntity victim, CaptureRuntime capture, long gameTime) {
        double desiredY = desiredY(capture, gameTime);
        AABB desiredBox = victim.getBoundingBox().move(
                capture.anchor.x - victim.getX(), desiredY - victim.getY(), capture.anchor.z - victim.getZ());
        if (!victim.level().noCollision(victim, desiredBox)) {
            return false;
        }
        Vec3 desired = new Vec3(capture.anchor.x, desiredY, capture.anchor.z);
        boolean moved = victim.position().distanceToSqr(desired) > POSITION_EPSILON_SQUARED;
        boolean hadMotion = victim.getDeltaMovement().lengthSqr() > POSITION_EPSILON_SQUARED;
        if (moved) {
            victim.setPos(desired);
        }
        if (!capture.allowLook) {
            victim.setYRot(capture.lockedYaw);
            victim.setYHeadRot(capture.lockedYaw);
            victim.setXRot(capture.lockedPitch);
        }
        // Gravity still pulls on the victim during its own tick; the pin simply runs after
        // it every time, so what the trackers broadcast is always the anchored position.
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0F;
        victim.hurtMarked |= moved || hadMotion;
        if (victim instanceof ServerPlayer player) {
            player.setKnownMovement(Vec3.ZERO);
        }
        // A path left running would re-apply movement on the victim's own next tick, so it
        // is cut here for the same reason a pinned totem has its navigation stopped.
        if (victim instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return true;
    }

    private static double desiredY(CaptureRuntime capture, long gameTime) {
        if (capture.mode != BossPhaseData.CAPTURE_MODE_LIFT || capture.liftEndsAt <= capture.startedAt) {
            return capture.anchor.y;
        }
        double progress = Mth.clamp((gameTime - capture.startedAt)
                / (double) (capture.liftEndsAt - capture.startedAt), 0.0D, 1.0D);
        return Mth.lerp(progress, capture.anchor.y, capture.targetY);
    }

    private static double safeLiftHeight(ServerLevel level, LivingEntity victim,
                                         AABB anchorBox, double requestedHeight) {
        if (!level.noCollision(victim, anchorBox)) {
            return -1.0D;
        }
        double safe = 0.0D;
        for (double height = Math.min(COLLISION_STEP, requestedHeight);
             height <= requestedHeight + 1.0E-7D; height += COLLISION_STEP) {
            double candidate = Math.min(height, requestedHeight);
            if (!level.noCollision(victim, anchorBox.move(0.0D, candidate, 0.0D))) {
                break;
            }
            safe = candidate;
            if (candidate >= requestedHeight) {
                break;
            }
        }
        return Math.max(0.0D, safe);
    }

    public static void releaseVictim(LivingEntity victim) {
        CaptureRuntime capture = BY_VICTIM.get(victim.getUUID());
        if (capture == null) {
            return;
        }
        ServerLevel level = victim.getServer() == null ? null : victim.getServer().getLevel(capture.levelKey);
        release(level, capture, victim);
    }

    public static void releaseByBoss(EntityNPCInterface boss) {
        CaptureRuntime capture = BY_BOSS.get(boss.getUUID());
        if (capture == null) {
            return;
        }
        release(boss.level() instanceof ServerLevel level ? level : null, capture);
    }

    public static void clearLevel(ServerLevel level) {
        for (CaptureRuntime capture : BY_VICTIM.values().toArray(CaptureRuntime[]::new)) {
            if (capture.levelKey.equals(level.dimension())) {
                release(level, capture);
            }
        }
    }

    private static void release(ServerLevel level, CaptureRuntime capture) {
        release(level, capture, null);
    }

    private static void release(ServerLevel level, CaptureRuntime capture, LivingEntity knownVictim) {
        if (!BY_VICTIM.remove(capture.victimId, capture)) {
            return;
        }
        BY_BOSS.remove(capture.bossId, capture);
        LivingEntity victim = knownVictim;
        if (victim == null && level != null
                && level.getEntity(capture.victimId) instanceof LivingEntity found) {
            victim = found;
        }
        if (victim != null) {
            victim.setDeltaMovement(0.0D, -0.05D, 0.0D);
            victim.fallDistance = 0.0F;
            victim.hurtMarked = true;
            if (victim instanceof ServerPlayer player) {
                player.setKnownMovement(Vec3.ZERO);
                syncState(player, capture, false);
            }
        }
        if (level != null) {
            Entity boss = level.getEntity(capture.bossId);
            Entity tracked = level.getEntity(capture.victimId);
            PacketSyncBossLink packet = linkPacket(capture, 0);
            if (boss != null) {
                NetworkWrapper.sendToTracking(boss, packet);
            }
            if (tracked != null) {
                NetworkWrapper.sendToTracking(tracked, packet);
            }
            if (victim instanceof ServerPlayer player) {
                NetworkWrapper.send(player, packet);
            }
        }
    }

    private static void syncState(ServerPlayer player, CaptureRuntime capture, boolean active) {
        NetworkWrapper.send(player, new PacketSyncBossCaptureState(active, capture.anchor.x,
                capture.anchor.y, capture.anchor.z, capture.startedAt, capture.endsAt,
                capture.liftEndsAt, capture.targetY, capture.lockedYaw, capture.lockedPitch,
                capture.allowLook));
    }

    private static void syncLink(EntityNPCInterface boss, LivingEntity victim,
                                 CaptureRuntime capture, int durationTicks) {
        PacketSyncBossLink packet = linkPacket(capture, durationTicks);
        NetworkWrapper.sendToTracking(boss, packet);
        NetworkWrapper.sendToTracking(victim, packet);
        if (victim instanceof ServerPlayer player) {
            NetworkWrapper.send(player, packet);
        }
    }

    private static PacketSyncBossLink linkPacket(CaptureRuntime capture, int durationTicks) {
        return new PacketSyncBossLink(PacketSyncBossLink.KIND_CAPTURE, capture.bossEntityId,
                capture.victimEntityId, capture.beamChannel, capture.beamStyle, durationTicks,
                capture.beamWidthPercent, capture.beamSagPercent, false);
    }
}
