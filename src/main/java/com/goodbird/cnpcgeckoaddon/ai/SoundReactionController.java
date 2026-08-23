package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.SoundReactionData;
import com.goodbird.cnpcgeckoaddon.mixin.ISoundReactionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

/**
 * Warden-style vibration listener and short-term sound memory owned by one NPC.
 */
public final class SoundReactionController implements VibrationSystem {
    private final EntityNPCInterface npc;
    private final VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.User vibrationUser;
    private final DynamicGameEventListener<VibrationSystem.Listener> dynamicListener;

    private BlockPos heardPosition;
    private UUID heardSource;
    private long memoryExpiresAt;
    private long nextReceptionAt;

    public SoundReactionController(EntityNPCInterface npc) {
        this.npc = npc;
        this.vibrationUser = new SoundVibrationUser();
        this.dynamicListener = new DynamicGameEventListener<>(new VibrationSystem.Listener(this));
    }

    public SoundReactionData settings() {
        return ((ISoundReactionData) npc.ais).cnpcgeckoaddon$getSoundReactionData();
    }

    public DynamicGameEventListener<VibrationSystem.Listener> dynamicListener() {
        return dynamicListener;
    }

    public void tick() {
        if (!(npc.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        VibrationSystem.Ticker.tick(serverLevel, vibrationData, vibrationUser);
        if (!settings().isEnabled()) {
            clearMemory();
            return;
        }
        if (heardPosition != null && serverLevel.getGameTime() >= memoryExpiresAt) {
            clearMemory();
            return;
        }
        if (heardSource != null && settings().getMode() != SoundReactionData.MODE_INVESTIGATE
                && (npc.getTarget() == null || !npc.getTarget().isAlive())) {
            Entity entity = serverLevel.getEntity(heardSource);
            if (entity instanceof LivingEntity living && isAttackableSource(living, settings().getMode())) {
                npc.setTarget(living);
            }
        }
    }

    public boolean hasActiveMemory() {
        return settings().isEnabled()
                && heardPosition != null
                && npc.level().getGameTime() < memoryExpiresAt;
    }

    public BlockPos getHeardPosition() {
        return heardPosition;
    }

    public void clearMemory() {
        heardPosition = null;
        heardSource = null;
        memoryExpiresAt = 0L;
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return vibrationUser;
    }

    private boolean isAttackableSource(LivingEntity living, int mode) {
        if (living == npc || !living.isAlive() || !npc.canAttack(living)) {
            return false;
        }
        if (mode == SoundReactionData.MODE_ATTACK_ENEMIES && npc.isAlliedTo(living)) {
            return false;
        }
        return !(living instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    private final class SoundVibrationUser implements VibrationSystem.User {
        private final PositionSource positionSource = new EntityPositionSource(npc, npc.getEyeHeight() * 0.5F);

        @Override
        public int getListenerRadius() {
            // The listener is registered for every NPC, so a disabled one must report 0:
            // otherwise every game event in the world keeps range-testing NPCs that can
            // never react to it.
            return settings().isEnabled() ? settings().getRadius() : 0;
        }

        @Override
        public PositionSource getPositionSource() {
            return positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.WARDEN_CAN_LISTEN;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel level, BlockPos position, Holder<GameEvent> event,
                                           GameEvent.Context context) {
            if (!settings().isEnabled() || !npc.isAlive() || npc.isRemoved()
                    || level.getGameTime() < nextReceptionAt) {
                return false;
            }
            Entity source = context.sourceEntity();
            return source != npc && (source == null || !source.isSpectator());
        }

        @Override
        public void onReceiveVibration(ServerLevel level, BlockPos position, Holder<GameEvent> event,
                                       Entity source, Entity projectileOwner, float distance) {
            if (!settings().isEnabled()) {
                return;
            }

            SoundReactionData data = settings();
            heardPosition = position.immutable();
            memoryExpiresAt = level.getGameTime() + data.getMemoryTicks();
            nextReceptionAt = level.getGameTime() + data.getCooldownTicks();

            Entity responsible = projectileOwner != null ? projectileOwner : source;
            heardSource = responsible == null ? null : responsible.getUUID();
            if (data.getMode() != SoundReactionData.MODE_INVESTIGATE
                    && responsible instanceof LivingEntity living && isAttackableSource(living, data.getMode())) {
                npc.setTarget(living);
            }
        }
    }
}
