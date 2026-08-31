package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

public final class BossMechanicUtil {
    private BossMechanicUtil() {}

    public static boolean replacesVanillaAttacks(EntityNPCInterface npc) {
        return npc != null
                && ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData().isEnabled();
    }

    public static boolean keepsStationary(EntityNPCInterface npc) {
        if (!replacesVanillaAttacks(npc)) {
            return false;
        }
        if (((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData().isStationary()) {
            return true;
        }
        // A boss held by its totems is pinned by the same tick-by-tick lock, and the pounce is
        // the one movement that would fight it: the leap commits before the lock can answer.
        TeleportPathController controller = npc instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller != null && controller.isTotemHeld();
    }

    /**
     * Whether this candidate is hidden from aimed abilities by its own totem formation.
     *
     * <p>Asked by every boss about every candidate it is about to aim at, so a player - the
     * common case by far - leaves on the first line, and the flag behind this is read before
     * the formation is counted. The question is about the victim alone: whose cast it is, and
     * whether that boss has totems of its own, never enters into it.</p>
     */
    public static boolean hiddenByTotems(LivingEntity candidate) {
        if (!(candidate instanceof IBossController holder)) {
            return false;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        return controller != null && controller.isTotemHidden();
    }
}
