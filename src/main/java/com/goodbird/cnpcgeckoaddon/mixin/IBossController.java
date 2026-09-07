package com.goodbird.cnpcgeckoaddon.mixin;

import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;

/** Lets event handlers reach the boss controller of an NPC. */
public interface IBossController {
    /** @return the controller, or null if this NPC has never ticked as a boss */
    TeleportPathController cnpcgeckoaddon$getTeleportPathController();

    /**
     * Forgets the controller this npc was ticking, so a later tick builds a fresh one.
     *
     * <p>Called by the controller as it shuts itself down. Without it the npc would go on
     * holding a controller that has already taken itself off the live list, and every static
     * sweep that walks that list - a totem death, a level going away, a player leaving the
     * fight - would quietly pass this boss by for as long as it kept ticking.</p>
     */
    void cnpcgeckoaddon$clearTeleportPathController();
}
