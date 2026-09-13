package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;
import net.minecraft.world.entity.Entity;

/**
 * What a wave was told about itself when it was cast.
 *
 * <p>A wave outlives the moment it started - a corridor runs for three seconds, a lifted
 * block hangs for two - and it is driven from the level tick, where the boss may already be
 * dead. So its numbers and its shout are taken here, once, the way the wave already takes
 * its style and its radius: a builder retuning the boss mid-fight changes the next wave, not
 * the one already running.</p>
 *
 * @param sound the shout for the style this wave was cast with, or null for a silent style
 */
public record BossWaveTuning(double corridorSpeed, int minDurationTicks, int maxDurationTicks,
                             int floorSearchDepth, int blockLifetimeTicks, double audienceRange,
                             BossSoundCue sound) {

    public static BossWaveTuning of(Entity boss, String style) {
        BossTuningSettings tuning = BossTuningUtil.of(boss);
        BossSoundCue cue = tuning.waveSound(style);
        return new BossWaveTuning(tuning.waveCorridorSpeed(), tuning.waveMinTicks(),
                tuning.waveMaxTicks(), tuning.waveFloorSearchDepth(), tuning.waveBlockLifetimeTicks(),
                tuning.telegraphAudienceRange(), cue == null ? null : cue.copy());
    }
}
