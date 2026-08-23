package com.goodbird.cnpcgeckoaddon.mixin.compat;

import com.hollingsworth.arsnouveau.api.particle.ParticleEmitter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell", remap = false)
public class MixinEntityProjectileSpell {

    @Redirect(method = "sendResolveParticles", at = @At(value = "INVOKE", target = "Lcom/hollingsworth/arsnouveau/api/particle/ParticleEmitter;tick(Lnet/minecraft/world/level/Level;)V"), remap = false, require = 0)
    private void cnpcgeckoaddon$tickEmitterIfPresent(ParticleEmitter emitter, Level level) {
        if (emitter != null) {
            emitter.tick(level);
        }
    }
}
