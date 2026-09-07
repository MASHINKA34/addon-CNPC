package com.goodbird.cnpcgeckoaddon.registry;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossBoulder;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossTetherAnchor;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class EntityRegistry {

    public static EntityType<? extends EntityCustomModel> entityCustomModel;
    public static EntityType<EntityFluidSpit> entityFluidSpit;
    public static EntityType<EntityBossBoulder> entityBossBoulder;
    public static EntityType<EntityBossTetherAnchor> entityBossTetherAnchor;

    @SubscribeEvent
    public static void registerEntities(RegisterEvent event) {
        // The typed form of the event: it hands back a helper already bound to the entity
        // registry, so nothing here has to cast a wildcard registry into the shape it wants
        // and be trusted about it.
        event.register(Registries.ENTITY_TYPE, helper -> {
            // Never added to a level: CustomNPCs and the model picker build it client-side as
            // the thing a geckolib npc is drawn as, so its tracking is nominal.
            entityCustomModel = registerNewentity(helper, "custommodelentity", EntityCustomModel::new, 4, 10, false, 0.7F, 2F);
            entityFluidSpit = registerNewentity(helper, "fluidspit", EntityFluidSpit::new, 4, 2, true, 0.4F, 0.4F);
            // The registered size is only the spawn-time default: the real box follows the
            // per-cast scale through EntityBossBoulder#getDimensions. Tracked further than a
            // plain projectile because a boulder may be thrown the full 64 blocks of its
            // range, and whoever is standing where it left must still see it land.
            entityBossBoulder = registerNewentity(helper, "bossboulder", EntityBossBoulder::new, 8, 2, true, 1.5F, 1.5F);
            // The stake a spot tether is tied to never moves, so it is synced as rarely as the
            // tracker allows; its size only sets where the beam ends, low over the spot. The
            // beam is drawn from the stake, so it is tracked past the longest break distance.
            entityBossTetherAnchor = registerNewentity(helper, "bosstetheranchor", EntityBossTetherAnchor::new, 8, 20, false, 0.5F, 0.5F);
        });
    }

    /**
     * @param rangeChunks how far the entity is tracked, in chunks. NeoForge's
     *                    {@code setTrackingRange} replaces the supplier {@code clientTrackingRange()}
     *                    reads, so it is the only one of the two that has any effect and the
     *                    vanilla setter must not be called after it.
     */
    private static <T extends Entity> EntityType<T> registerNewentity(
            final RegisterEvent.RegisterHelper<EntityType<?>> helper, final String name, final EntityType.EntityFactory<T> factoryIn, final int rangeChunks, final int update, final boolean velocity, final float width, final float height) {
        final EntityType.Builder<T> builder = EntityType.Builder.of(factoryIn, MobCategory.MISC);
        builder.setTrackingRange(rangeChunks);
        builder.setUpdateInterval(update);
        builder.setShouldReceiveVelocityUpdates(velocity);
        builder.sized(width, height);
        final ResourceLocation registryName = ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, name);
        EntityType<T> entityType = builder.build(registryName.toString());
        helper.register(registryName, entityType);
        return entityType;
    }


    @SubscribeEvent
    public static void attribute(final EntityAttributeCreationEvent event) {
        event.put(entityCustomModel, LivingEntity.createLivingAttributes().add(Attributes.FOLLOW_RANGE).build());
    }
}
