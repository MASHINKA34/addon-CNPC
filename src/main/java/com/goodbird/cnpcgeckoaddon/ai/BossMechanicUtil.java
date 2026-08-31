package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
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
}
