package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.EnumSet;

/** Walks an idle NPC toward the most recently received vibration. */
public final class SoundInvestigationGoal extends Goal {
    private final EntityNPCInterface npc;
    private final SoundReactionController controller;
    private int repathDelay;

    public SoundInvestigationGoal(EntityNPCInterface npc, SoundReactionController controller) {
        this.npc = npc;
        this.controller = controller;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return controller.hasActiveMemory() && hasNoCombatTarget() && !isAtSound();
    }

    @Override
    public boolean canContinueToUse() {
        return controller.hasActiveMemory() && hasNoCombatTarget() && !isAtSound();
    }

    @Override
    public void start() {
        repathDelay = 0;
        moveToSound();
    }

    @Override
    public void tick() {
        BlockPos position = controller.getHeardPosition();
        if (position == null) {
            return;
        }
        npc.getLookControl().setLookAt(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
        if (--repathDelay <= 0 || npc.getNavigation().isDone()) {
            repathDelay = 10;
            moveToSound();
        }
    }

    @Override
    public void stop() {
        if (isAtSound()) {
            controller.clearMemory();
        }
    }

    private boolean hasNoCombatTarget() {
        return npc.getTarget() == null || !npc.getTarget().isAlive();
    }

    private boolean isAtSound() {
        BlockPos position = controller.getHeardPosition();
        return position == null || npc.distanceToSqr(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D) <= 2.25D;
    }

    private void moveToSound() {
        BlockPos position = controller.getHeardPosition();
        if (position == null) {
            return;
        }
        double speed = Mth.clamp(npc.ais.getWalkingSpeed() / 5.0D, 0.5D, 2.0D);
        npc.getNavigation().moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, speed);
    }
}
