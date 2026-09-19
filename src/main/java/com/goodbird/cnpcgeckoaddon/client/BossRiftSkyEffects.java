package com.goodbird.cnpcgeckoaddon.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The sky of the rift dimension: no clouds, no sun, no stars, and the fog colour dimmed to next to
 * black, so the void round a platform reads as nothing at all. While a rift is open the fog colour
 * is the rift's own; a builder looking a platform over sees the dark one.
 */
public final class BossRiftSkyEffects extends DimensionSpecialEffects {

    /** How much of the fog colour the sky keeps: the End's darkness. */
    private static final double DARKNESS = 0.15D;

    public BossRiftSkyEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor.scale(DARKNESS);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }
}
