package com.goodbird.cnpcgeckoaddon.mixin;

import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;

/** Lets event handlers reach the boss controller of an NPC. */
public interface IBossController {
    /** @return the controller, or null if this NPC has never ticked as a boss */
    TeleportPathController cnpcgeckoaddon$getTeleportPathController();
}
