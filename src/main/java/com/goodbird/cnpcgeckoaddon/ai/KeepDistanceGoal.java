package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

public class KeepDistanceGoal extends Goal {
    private final EntityNPCInterface npc;
    private LivingEntity target;

    public KeepDistanceGoal(EntityNPCInterface npc) {
        this.npc = npc;
    }

    private int getKeepDistance() {
        return ((IRangedData) npc.stats.ranged).getRangedExtraData().getKeepDistance();
    }

    @Override
    public boolean canUse() {
        int distance = getKeepDistance();
        if (distance <= 0 || npc.isKilled()) {
            return false;
        }
        LivingEntity entity = npc.getTarget();
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        this.target = entity;
        return npc.distanceToSqr(entity) < (double) (distance * distance);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        Vec3 away = npc.position().subtract(target.position());
        if (away.horizontalDistanceSqr() < 1.0E-4D) {
            away = new Vec3(npc.getRandom().nextDouble() - 0.5D, 0.0D, npc.getRandom().nextDouble() - 0.5D);
        }
        away = away.normalize().scale(3.0D);
        npc.getMoveControl().setWantedPosition(npc.getX() + away.x, npc.getY(), npc.getZ() + away.z, 1.2D);
    }
}
