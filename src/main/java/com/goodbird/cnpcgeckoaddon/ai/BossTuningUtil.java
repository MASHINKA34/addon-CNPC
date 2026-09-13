package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * The boss-wide tuning behind an entity, for the code that only has the entity.
 *
 * <p>Most of the runtimes tick with the boss' settings already in hand and read
 * {@code data.tuning()} directly. The marks, the puffs and the schedulers do not always: a
 * frame is sent from a static sweep that knows only an owner id, and a minion's last puff is
 * thrown by a queue that knows only the minion. Those ask here.</p>
 */
public final class BossTuningUtil {

    /**
     * What something with no boss behind it is tuned like: exactly as everything was before
     * any of it was a setting. Never handed out for editing - the screens reach a boss' own.
     */
    private static final BossTuningSettings DEFAULTS = new BossTuningSettings();

    private BossTuningUtil() {
    }

    /** This boss' own tuning, or the old constants for anything that is not a configured boss. */
    public static BossTuningSettings of(Entity boss) {
        if (boss instanceof EntityNPCInterface npc && npc.ais instanceof ITeleportPathData holder) {
            return holder.cnpcgeckoaddon$getTeleportPathData().tuning();
        }
        return DEFAULTS;
    }

    /** The old constants, for a snapshot taken where there is nobody to ask. */
    public static BossTuningSettings defaults() {
        return DEFAULTS;
    }
}
