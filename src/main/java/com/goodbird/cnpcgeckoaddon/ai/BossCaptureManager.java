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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns transient, server-authoritative player captures. Nothing here is persisted. */
public final class BossCaptureManager {
    private static final double POSITION_EPSILON_SQUARED = 1.0E-8D;
    private static final double COLLISION_STEP = 0.05D;
    private static final Map<UUID, CaptureRuntime> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, CaptureRuntime> BY_BOSS = new HashMap<>();

    private BossCaptureManager() {
    }

    private static final class CaptureRuntime {
        private final UUID playerId;
        private final int playerEntityId;
        private final String playerName;
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

        private CaptureRuntime(EntityNPCInterface boss, ServerPlayer player, BossPhaseData phase,
                               int phaseIndex, long gameTime, double targetY, int liftTicks) {
            this.playerId = player.getUUID();
            this.playerEntityId = player.getId();
            this.playerName = player.getGameProfile().getName();
            this.bossId = boss.getUUID();
            this.bossEntityId = boss.getId();
            this.levelKey = player.level().dimension();
            this.originPhaseIndex = phaseIndex;
            this.anchor = player.position();
            this.startedAt = gameTime;
            this.endsAt = gameTime + phase.getCaptureDurationTicks();
            this.liftEndsAt = gameTime + liftTicks;
            this.mode = phase.getCaptureMode();
            this.targetY = targetY;
            this.lockedYaw = player.getYRot();
            this.lockedPitch = player.getXRot();
            this.allowLook = phase.isCaptureAllowLook();
            this.beamChannel = 0;
            this.beamStyle = phase.getCaptureBeamStyle();
            this.beamWidthPercent = phase.getCaptureBeamWidthPercent();
            this.beamSagPercent = phase.getCaptureBeamSagPercent();
        }
    }

    /** Atomically claims both the player and boss, preventing overlapping captures. */
    public static boolean start(EntityNPCInterface boss, ServerPlayer player,
                                BossPhaseData phase, int phaseIndex, long gameTime) {
        if (BY_PLAYER.containsKey(player.getUUID()) || BY_BOSS.containsKey(boss.getUUID())
                || player.level() != boss.level() || !(player.level() instanceof ServerLevel level)
                || !level.noCollision(player, player.getBoundingBox())) {
            return false;
        }
        int liftTicks = Math.min(phase.getCaptureLiftTicks(), phase.getCaptureDurationTicks());
        double height = phase.getCaptureMode() == BossPhaseData.CAPTURE_MODE_LIFT
                ? safeLiftHeight(level, player, player.getBoundingBox(), phase.getCaptureLiftHeight()) : 0.0D;
        CaptureRuntime capture = new CaptureRuntime(boss, player, phase, phaseIndex, gameTime,
                player.getY() + height, liftTicks);
        BY_PLAYER.put(capture.playerId, capture);
        BY_BOSS.put(capture.bossId, capture);
        if (!hold(player, capture, gameTime)) {
            BY_PLAYER.remove(capture.playerId);
            BY_BOSS.remove(capture.bossId);
            return false;
        }
        syncState(player, capture, true);
        syncLink(boss, player, capture, phase.getCaptureDurationTicks());
        return true;
    }

    public static boolean isCaptured(UUID playerId) {
        return BY_PLAYER.containsKey(playerId);
    }

    public static boolean hasCaptureForBoss(UUID bossId) {
        return BY_BOSS.containsKey(bossId);
    }

    public static String capturedPlayerName(UUID bossId) {
        CaptureRuntime capture = BY_BOSS.get(bossId);
        return capture == null ? null : capture.playerName;
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
        for (CaptureRuntime capture : BY_PLAYER.values()) {
            if (!capture.levelKey.equals(level.dimension())
                    || tracked.getId() != capture.bossEntityId
                    && tracked.getId() != capture.playerEntityId) {
                continue;
            }
            Entity boss = level.getEntity(capture.bossId);
            Entity victim = level.getEntity(capture.playerId);
            int remaining = (int) Math.max(0L, capture.endsAt - gameTime);
            if (boss != null && victim != null && remaining > 0) {
                NetworkWrapper.send(viewer, linkPacket(capture, remaining));
            }
        }
    }

    /** Drops client movement while still accepting look packets when the phase allows it. */
    public static boolean handleMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        CaptureRuntime capture = BY_PLAYER.get(player.getUUID());
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
        for (CaptureRuntime capture : BY_PLAYER.values().toArray(CaptureRuntime[]::new)) {
            if (!capture.levelKey.equals(level.dimension())) {
                continue;
            }
            ServerPlayer player = level.getPlayerByUUID(capture.playerId) instanceof ServerPlayer found
                    ? found : null;
            Entity bossEntity = level.getEntity(capture.bossId);
            if (!isUsable(player, bossEntity, capture) || gameTime >= capture.endsAt) {
                release(level, capture);
                continue;
            }
            if (capture.mode == BossPhaseData.CAPTURE_MODE_LIFT) {
                double safeHeight = safeLiftHeight(level, player,
                        player.getBoundingBox().move(capture.anchor.subtract(player.position())),
                        Math.max(0.0D, capture.targetY - capture.anchor.y));
                if (safeHeight < 0.0D) {
                    release(level, capture);
                    continue;
                }
                double safeTargetY = capture.anchor.y + safeHeight;
                if (safeTargetY + 1.0E-4D < capture.targetY) {
                    capture.targetY = safeTargetY;
                    syncState(player, capture, true);
                }
            }
            if (!hold(player, capture, gameTime)) {
                release(level, capture);
            }
        }
    }

    private static boolean isUsable(ServerPlayer player, Entity bossEntity, CaptureRuntime capture) {
        if (player == null || player.isRemoved() || !player.isAlive()
                || player.isCreative() || player.isSpectator()
                || !player.level().dimension().equals(capture.levelKey)
                || !(bossEntity instanceof EntityNPCInterface boss) || boss.isRemoved() || !boss.isAlive()) {
            return false;
        }
        if (!(boss instanceof IBossController holder)) {
            return false;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        return controller != null && controller.isCaptureEnabledForPhase(capture.originPhaseIndex);
    }

    private static boolean hold(ServerPlayer player, CaptureRuntime capture, long gameTime) {
        double desiredY = desiredY(capture, gameTime);
        AABB desiredBox = player.getBoundingBox().move(
                capture.anchor.x - player.getX(), desiredY - player.getY(), capture.anchor.z - player.getZ());
        if (!player.level().noCollision(player, desiredBox)) {
            return false;
        }
        Vec3 desired = new Vec3(capture.anchor.x, desiredY, capture.anchor.z);
        boolean moved = player.position().distanceToSqr(desired) > POSITION_EPSILON_SQUARED;
        boolean hadMotion = player.getDeltaMovement().lengthSqr() > POSITION_EPSILON_SQUARED;
        if (moved) {
            player.setPos(desired);
        }
        if (!capture.allowLook) {
            player.setYRot(capture.lockedYaw);
            player.setYHeadRot(capture.lockedYaw);
            player.setXRot(capture.lockedPitch);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setKnownMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.hurtMarked |= moved || hadMotion;
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

    private static double safeLiftHeight(ServerLevel level, ServerPlayer player,
                                         AABB anchorBox, double requestedHeight) {
        if (!level.noCollision(player, anchorBox)) {
            return -1.0D;
        }
        double safe = 0.0D;
        for (double height = Math.min(COLLISION_STEP, requestedHeight);
             height <= requestedHeight + 1.0E-7D; height += COLLISION_STEP) {
            double candidate = Math.min(height, requestedHeight);
            if (!level.noCollision(player, anchorBox.move(0.0D, candidate, 0.0D))) {
                break;
            }
            safe = candidate;
            if (candidate >= requestedHeight) {
                break;
            }
        }
        return Math.max(0.0D, safe);
    }

    public static void releasePlayer(ServerPlayer player) {
        CaptureRuntime capture = BY_PLAYER.get(player.getUUID());
        if (capture == null) {
            return;
        }
        ServerLevel level = player.getServer().getLevel(capture.levelKey);
        release(level, capture, player);
    }

    public static void releaseByBoss(EntityNPCInterface boss) {
        CaptureRuntime capture = BY_BOSS.get(boss.getUUID());
        if (capture == null) {
            return;
        }
        release(boss.level() instanceof ServerLevel level ? level : null, capture);
    }

    public static void clearLevel(ServerLevel level) {
        for (CaptureRuntime capture : BY_PLAYER.values().toArray(CaptureRuntime[]::new)) {
            if (capture.levelKey.equals(level.dimension())) {
                release(level, capture);
            }
        }
    }

    private static void release(ServerLevel level, CaptureRuntime capture) {
        release(level, capture, null);
    }

    private static void release(ServerLevel level, CaptureRuntime capture, ServerPlayer knownPlayer) {
        if (!BY_PLAYER.remove(capture.playerId, capture)) {
            return;
        }
        BY_BOSS.remove(capture.bossId, capture);
        ServerPlayer player = knownPlayer;
        if (player == null && level != null
                && level.getPlayerByUUID(capture.playerId) instanceof ServerPlayer found) {
            player = found;
        }
        if (player != null) {
            player.setDeltaMovement(0.0D, -0.05D, 0.0D);
            player.setKnownMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            player.hurtMarked = true;
            syncState(player, capture, false);
        }
        if (level != null) {
            Entity boss = level.getEntity(capture.bossId);
            Entity victim = level.getEntity(capture.playerId);
            PacketSyncBossLink packet = linkPacket(capture, 0);
            if (boss != null) {
                NetworkWrapper.sendToTracking(boss, packet);
            }
            if (victim != null) {
                NetworkWrapper.sendToTracking(victim, packet);
            }
            if (player != null) {
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

    private static void syncLink(EntityNPCInterface boss, ServerPlayer player,
                                 CaptureRuntime capture, int durationTicks) {
        PacketSyncBossLink packet = linkPacket(capture, durationTicks);
        NetworkWrapper.sendToTracking(boss, packet);
        NetworkWrapper.sendToTracking(player, packet);
        NetworkWrapper.send(player, packet);
    }

    private static PacketSyncBossLink linkPacket(CaptureRuntime capture, int durationTicks) {
        return new PacketSyncBossLink(PacketSyncBossLink.KIND_CAPTURE, capture.bossEntityId,
                capture.playerEntityId, capture.beamChannel, capture.beamStyle, durationTicks,
                capture.beamWidthPercent, capture.beamSagPercent, false);
    }
}
