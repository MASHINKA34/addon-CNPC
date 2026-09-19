package com.goodbird.cnpcgeckoaddon.mixin;

import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import noppes.npcs.entity.EntityNPCInterface;

public interface IRangedData {
    RangedExtraData getRangedExtraData();

    /**
     * The npc these ranged settings belong to, which CustomNPCs keeps to itself: the ranged
     * screen needs it for the reload animation picker, which lists the model's own animations.
     */
    EntityNPCInterface getRangedNpc();
}
