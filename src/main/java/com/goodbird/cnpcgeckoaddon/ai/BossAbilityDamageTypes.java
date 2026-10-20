package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The two damage types an ability's profile may deal its hits as, besides the plain mob attack.
 *
 * <p>Both are defined in the mod's own data pack under {@code damage_type/}, and what they get
 * past is said by vanilla's tags rather than here: {@code bypasses_armor} lists both,
 * {@code bypasses_enchantments} and {@code bypasses_effects} only the true one. Neither is in
 * {@code bypasses_invulnerability}, {@code bypasses_cooldown} or {@code bypasses_shield}, so
 * creative players, spectators, invulnerability frames, a raised shield, a totem of undying and
 * every protection of the addon's own - the totem shield, the barrier, the aggro zone, the lethal
 * guard - meet these hits exactly as they meet a mob attack.</p>
 */
public final class BossAbilityDamageTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    public static final ResourceKey<DamageType> PIERCING = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "ability_piercing"));
    public static final ResourceKey<DamageType> TRUE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "ability_true"));

    /** The types already reported missing, so a broken data pack says so once and not on every hit. */
    private static final Set<ResourceKey<DamageType>> REPORTED_MISSING = ConcurrentHashMap.newKeySet();

    private BossAbilityDamageTypes() {
    }

    /**
     * A hit dealt by {@code boss} as the given profile type.
     *
     * <p>The boss is both the direct entity and the attacker, the way a mob attack has it, so the
     * death message names it and the kill, the anger and the knockback go to it as before. Built
     * on the constructor rather than the vanilla factory, whose parameter names are the wrong way
     * round.</p>
     *
     * @param damageType one of the profile's {@code DAMAGE_*} values; anything else is a mob attack
     */
    public static DamageSource source(Level level, LivingEntity boss, int damageType) {
        ResourceKey<DamageType> key = keyOf(damageType);
        if (key == null) {
            return level.damageSources().mobAttack(boss);
        }
        Optional<Holder.Reference<DamageType>> type = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
        if (type.isEmpty()) {
            // Only a data pack that lost the addon's files gets here; the hit still lands, the old way.
            if (REPORTED_MISSING.add(key)) {
                LOGGER.warn("Damage type {} is missing from the registry, so {}'s hits of that type land as a "
                        + "plain mob attack - is the addon's data pack disabled?", key.location(),
                        boss.getName().getString());
            }
            return level.damageSources().mobAttack(boss);
        }
        return new DamageSource(type.get(), boss, boss);
    }

    /** The registry key behind one of the profile's damage types, or null for the plain mob attack. */
    static ResourceKey<DamageType> keyOf(int damageType) {
        return switch (damageType) {
            case BossAbilityProfile.DAMAGE_PIERCING -> PIERCING;
            case BossAbilityProfile.DAMAGE_TRUE -> TRUE;
            default -> null;
        };
    }
}
