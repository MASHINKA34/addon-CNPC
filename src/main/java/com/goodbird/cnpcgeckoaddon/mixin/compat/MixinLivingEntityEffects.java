package com.goodbird.cnpcgeckoaddon.mixin.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntityEffects {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$rewrapUnregisteredHolder(MobEffectInstance instance, Entity source, CallbackInfoReturnable<Boolean> cir) {
        Holder<MobEffect> holder = instance.getEffect();
        if (holder.unwrapKey().isPresent()) {
            return;
        }
        Holder<MobEffect> registered = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(holder.value());
        if (registered.unwrapKey().isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        MobEffectInstance fixed = new MobEffectInstance(registered, instance.getDuration(), instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
        cir.setReturnValue(((LivingEntity) (Object) this).addEffect(fixed, source));
    }
}
