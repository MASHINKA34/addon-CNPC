package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The damage a thrown npc deals: its own type, so it can be told apart from everything else.
 *
 * <p>The type is defined in the mod's own data pack under {@code damage_type/thrown_npc.json};
 * this is the key that reaches it. A type of its own is the whole point of the mechanic: a
 * resistance rule can name {@code cnpcgeckoaddon:thrown_npc} and a boss can be made to take
 * nothing but it, while the hit still walks the ordinary path - totem shield, resistances,
 * barrier - like any other.</p>
 */
public final class NpcThrowDamage {
    public static final ResourceKey<DamageType> THROWN_NPC = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "thrown_npc"));

    private NpcThrowDamage() {
    }

    /**
     * A hit dealt by a thrown npc.
     *
     * <p>The npc is the direct entity and the thrower the attacker, so the breakdown and the
     * resistance list see both: who was thrown, and who threw. Built on the constructor rather
     * than the vanilla factory because the factory's parameter names are the wrong way round -
     * what it calls the causing entity goes into the direct slot.</p>
     *
     * @param thrower who threw the npc, or null for the npc's own crash damage - which then
     *                credits nobody, angers nobody and switches no target
     */
    public static DamageSource source(Level level, Entity npc, @Nullable Entity thrower) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(THROWN_NPC);
        return new DamageSource(type, npc, thrower);
    }
}
