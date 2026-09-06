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
        if (instance != null && instance.hasModifier(PARTY_HEALTH_MODIFIER_ID)) {
            AttributeInstance unscaled = new AttributeInstance(instance.getAttribute(), ignored -> {});
            unscaled.replaceFrom(instance);
            unscaled.removeModifier(PARTY_HEALTH_MODIFIER_ID);
            maxHealth = unscaled.getValue();
        }
        return Double.isFinite(maxHealth) ? Math.max(1.0D, maxHealth) : 1.0D;
    }

    public static double calculateAdditiveBonus(AttributeInstance instance, double desiredMax) {
        double additiveBase = instance.getBaseValue();
        double baseMultiplier = 1.0D;
        double totalMultiplier = 1.0D;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.id().equals(PARTY_HEALTH_MODIFIER_ID)) {
                continue;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> additiveBase += modifier.amount();
                case ADD_MULTIPLIED_BASE -> baseMultiplier += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> totalMultiplier *= 1.0D + modifier.amount();
            }
        }
        double multiplier = baseMultiplier * totalMultiplier;
        if (!(multiplier > 0.0D) || !Double.isFinite(multiplier)) {
            return 0.0D;
        }
        double bonus = instance.getAttribute().value().sanitizeValue(desiredMax) / multiplier - additiveBase;
        return Double.isFinite(bonus) ? Math.max(0.0D, bonus) : 0.0D;
    }
}
