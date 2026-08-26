package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Keeps the boss the only thing that decides when a totem or a minion comes back.
 *
 * <p>Totems and minions are CustomNPCs clones, and every NPC carries its own resurrection
 * machinery. {@code EntityNPCInterface.remove(KILLED)} does not take the corpse out of the
 * world: it parks it with {@code health = -1} and arms {@code killedtime} with
 * {@code stats.respawnTime}, and {@code tickDeath} then calls {@code reset()} once that
 * stamp passes. The boss meanwhile sees an empty slot, starts its own respawn clock, and the
 * two end up fighting over the slot.</p>
 *
 * <p>Two things stop that here, and either one alone is enough:</p>
 * <ul>
 *   <li>the clone is switched to the "No" entry of the CustomNPCs respawn dropdown
 *       ({@code stats.spawnCycle}), which makes {@code remove(KILLED)} delete the entity
 *       outright instead of arming {@code killedtime};</li>
 *   <li>the corpse is discarded a tick after it dies. {@code discard()} is
 *       {@code RemovalReason.DISCARDED}, and the CustomNPCs override only intercepts
 *       {@code KILLED}, so nothing of the resurrection path runs at all.</li>
 * </ul>
 *
 * <p>Waiting a tick rather than removing inside the death event leaves the drops, the death
 * sound and the scripts their own frame; a single tick is still well ahead of the earliest
 * moment CustomNPCs could resurrect anything, because the first {@code tickDeath} is what
 * arms the stamp in the first place.</p>
 *
 * <p>Discarding an entity runs whatever that entity's mod hung on its removal, so it happens
 * outside the walk over the queue - see {@link TickQueue}.</p>
 */
public final class BossCloneRespawnGuard {
    /**
     * The "No" entry of the CustomNPCs respawn dropdown ({@code yes / day / night / no /
     * naturally}). Both {@code remove} and {@code tickDeath} check for it, and for
     * "naturally" right beside it, before they go anywhere near {@code killedtime}.
     */
    private static final int RESPAWN_TYPE_NEVER = 3;
    private static final int RESPAWN_TYPE_NATURALLY = 4;

    /** How many corpses may be taken away in one level tick; the rest go on the next. */
    private static final int MAX_PER_TICK = 64;

    private record Pending(ResourceKey<Level> dimension, Entity entity, long removeAt) {
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss clone removals", MAX_PER_TICK);

    private BossCloneRespawnGuard() {
    }

    /** Takes a clone's own resurrection away, on the spawn path and on the way back in from a save. */
    public static void suppressSelfRespawn(Entity clone) {
        if (!(clone instanceof EntityNPCInterface npc)) {
            return;
        }
        int respawnType = npc.stats.getRespawnType();
        if (respawnType == RESPAWN_TYPE_NEVER || respawnType == RESPAWN_TYPE_NATURALLY) {
            return;
        }
        // Written onto the entity rather than into the clone template, so the same NPC keeps
        // the respawn it was configured with everywhere it is used outside a boss fight.
        npc.stats.setRespawnType(RESPAWN_TYPE_NEVER);
    }

    /** Retires a dead boss clone: no self-respawn now, and no corpse left to resurrect. */
    public static void retire(Entity clone) {
        suppressSelfRespawn(clone);
        if (clone.level() instanceof ServerLevel level) {
            PENDING.add(new Pending(level.dimension(), clone, level.getGameTime() + 1L));
        }
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        PENDING.drain(
                pending -> pending.dimension().equals(level.dimension()) && gameTime >= pending.removeAt(),
                pending -> pending.entity().discard());
    }

    /**
     * Drops anything still waiting in a level that is going away.
     *
     * <p>The corpse can be saved by the unload, but it can no longer resurrect: the
     * suppressed respawn type is part of the NPC's own saved data, so it loads back as a body
     * that CustomNPCs deletes instead of resetting.</p>
     */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension().equals(level.dimension()));
    }
}
