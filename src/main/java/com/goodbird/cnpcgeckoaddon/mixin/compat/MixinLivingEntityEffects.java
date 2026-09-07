package com.goodbird.cnpcgeckoaddon.mixin.compat;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Puts a {@link MobEffectInstance} built on an unregistered holder back on its registered one.
 *
 * <p>Vanilla resolves the effect's id off the holder when it writes the effect to the client,
 * so an instance another mod built with {@code Holder.direct(...)} takes the connection down
 * rather than the mod that made it. Rewrapping is enough whenever the effect itself is
 * registered, which it is in every case seen so far - the id was simply lost on the way.</p>
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityEffects {

    @Unique
    private static final Logger cnpcgeckoaddon$LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** One line per effect, not one per application: this sits on every addEffect there is. */
    @Unique
    private static final Set<String> cnpcgeckoaddon$reported = Collections.synchronizedSet(new HashSet<>());

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void cnpcgeckoaddon$rewrapUnregisteredHolder(MobEffectInstance instance, Entity source, CallbackInfoReturnable<Boolean> cir) {
        Holder<MobEffect> holder = instance.getEffect();
        if (holder.unwrapKey().isPresent()) {
            return;
        }
        Holder<MobEffect> registered = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(holder.value());
        if (registered.unwrapKey().isEmpty()) {
            // The effect is not in the registry at all, so there is no id to send and no way
            // to apply it without disconnecting whoever can see the victim. Dropped - but
            // said out loud once, or this is a potion that silently never works.
            cnpcgeckoaddon$reportUnregistered(holder);
            cir.setReturnValue(false);
            return;
        }
        MobEffectInstance fixed = new MobEffectInstance(registered, instance.getDuration(), instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
        cir.setReturnValue(((LivingEntity) (Object) this).addEffect(fixed, source));
    }

    @Unique
    private static void cnpcgeckoaddon$reportUnregistered(Holder<MobEffect> holder) {
        String name = String.valueOf(holder.value());
        if (cnpcgeckoaddon$reported.add(name)) {
            cnpcgeckoaddon$LOGGER.warn("Mob effect {} is not registered; the effect was dropped "
                    + "rather than applied, because sending it would disconnect the client", name);
        }
    }
}
