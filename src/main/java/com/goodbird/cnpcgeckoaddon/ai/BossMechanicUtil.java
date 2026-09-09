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
        // A boss stunned by its broken barrier is held the same way.
        TeleportPathController controller = npc instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        // And one holding the cast spot it went to: the hold is the point of the spot.
        return controller != null && (controller.isTotemHeld() || controller.isBarrierStunned()
                || controller.isCastSpotHeld());
    }

    /**
     * Whether the boss is on its way to a cast spot.
     *
     * <p>The one time its own chase has to let go of the navigation: the walk to the spot
     * and the walk after the target cannot both own the path, and the spot is what the boss
     * set off for. The pounce stands aside for the same reason.</p>
     */
    public static boolean isBoundForCastSpot(EntityNPCInterface npc) {
        if (!replacesVanillaAttacks(npc)) {
            return false;
        }
        TeleportPathController controller = npc instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller != null && controller.isBoundForCastSpot();
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
