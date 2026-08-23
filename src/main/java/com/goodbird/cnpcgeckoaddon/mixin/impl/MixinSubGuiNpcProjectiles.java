package com.goodbird.cnpcgeckoaddon.mixin.impl;

import net.minecraft.util.Mth;
import noppes.npcs.client.gui.SubGuiNpcProjectiles;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents invalid legacy/NBT values from becoming negative GUI array indexes. */
@Mixin(SubGuiNpcProjectiles.class)
public abstract class MixinSubGuiNpcProjectiles {

    @Shadow(remap = false)
    private DataRanged stats;

    @Inject(method = "init", at = @At("HEAD"))
    private void cnpcgeckoaddon$sanitizeProjectileGuiValues(CallbackInfo ci) {
        int explodeSize = Mth.clamp(stats.getExplodeSize(), 0, 3);
        if (explodeSize != stats.getExplodeSize()) {
            stats.setExplodeSize(explodeSize);
        }

        int particle = Mth.clamp(stats.getParticle(), 0, 7);
        if (particle != stats.getParticle()) {
            stats.setParticle(particle);
        }

        int effectType = stats.getEffectType();
        if ((effectType < 0 || effectType > 32) && effectType != 666) {
            effectType = 0;
        }
        int effectStrength = Mth.clamp(stats.getEffectStrength(), 0, 1);
        int effectTime = Mth.clamp(stats.getEffectTime(), 1, 99999);
        if (effectType != stats.getEffectType()
                || effectStrength != stats.getEffectStrength()
                || effectTime != stats.getEffectTime()) {
            stats.setEffect(effectType, effectStrength, effectTime);
        }
    }
}
