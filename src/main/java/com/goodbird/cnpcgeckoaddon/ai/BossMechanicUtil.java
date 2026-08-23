package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;

public final class BossMechanicUtil {
    private BossMechanicUtil() {}

    public static boolean replacesVanillaAttacks(EntityNPCInterface npc) {
        return npc != null
                && ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData().isEnabled();
    }

    public static boolean keepsStationary(EntityNPCInterface npc) {
        return replacesVanillaAttacks(npc)
                && ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData().isStationary();
    }
}
