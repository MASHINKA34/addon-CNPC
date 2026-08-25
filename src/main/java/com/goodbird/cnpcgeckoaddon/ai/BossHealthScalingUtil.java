package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Shared identity and read-only calculations for party health scaling. */
public final class BossHealthScalingUtil {
    public static final ResourceLocation PARTY_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "boss_party_health");

    private BossHealthScalingUtil() {
    }

    public static double getMaxHealthWithoutPartyScaling(LivingEntity entity) {
        double maxHealth = entity.getMaxHealth();
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier modifier = instance == null ? null
                : instance.getModifier(PARTY_HEALTH_MODIFIER_ID);
        if (modifier != null && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
            maxHealth -= modifier.amount();
        }
        return Double.isFinite(maxHealth) ? Math.max(1.0D, maxHealth) : 1.0D;
    }
}
