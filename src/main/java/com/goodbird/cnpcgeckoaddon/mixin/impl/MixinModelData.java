package com.goodbird.cnpcgeckoaddon.mixin.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ModelData;
import noppes.npcs.entity.EntityNPCInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Mixin(ModelData.class)
public abstract class MixinModelData {

    @Unique
    private static final Logger cnpcgeckoaddon$LOGGER = LogManager.getLogger("cnpcgeckoaddon");

    @Unique
    private static final Set<String> cnpcgeckoaddon$reported = Collections.synchronizedSet(new HashSet<>());

    @Unique
    private ResourceLocation cnpcgeckoaddon$brokenEntityName;

    @Inject(method = "getEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcgeckoaddon$skipBrokenEntity(EntityNPCInterface npc, CallbackInfoReturnable<LivingEntity> cir) {
        ModelData data = (ModelData) (Object) this;
        if (!data.hasEntity()) {
            return;
        }
        ResourceLocation name = data.getEntityName();
        if (name == null) {
            cir.setReturnValue(null);
            return;
        }
        if (name.equals(cnpcgeckoaddon$brokenEntityName) || !BuiltInRegistries.ENTITY_TYPE.containsKey(name)) {
            cnpcgeckoaddon$markBroken(name, npc);
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getEntity", at = @At("RETURN"), remap = false)
    private void cnpcgeckoaddon$rememberFailedEntity(EntityNPCInterface npc, CallbackInfoReturnable<LivingEntity> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        ModelData data = (ModelData) (Object) this;
        if (!data.hasEntity()) {
            return;
        }
        ResourceLocation name = data.getEntityName();
        if (name != null) {
            cnpcgeckoaddon$markBroken(name, npc);
        }
    }

    @Unique
    private void cnpcgeckoaddon$markBroken(ResourceLocation name, EntityNPCInterface npc) {
        if (name.equals(cnpcgeckoaddon$brokenEntityName)) {
            return;
        }
        cnpcgeckoaddon$brokenEntityName = name;
        if (cnpcgeckoaddon$reported.add(name.toString())) {
            String npcName = npc == null ? "unknown" : npc.getName().getString();
            cnpcgeckoaddon$LOGGER.warn("Npc model entity {} could not be created (npc {}), model rendering is skipped", name, npcName);
        }
    }
}
