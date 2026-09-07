package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.PersistentDataUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class NpcProjectileDamage {
    private static final String DAMAGE_KEY = "cnpcgeckoaddon:ranged_damage";

    private NpcProjectileDamage() {
    }

    public static void configure(Projectile projectile, int damage) {
        projectile.getPersistentData().putInt(DAMAGE_KEY, Math.max(0, damage));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }
        CompoundTag data = PersistentDataUtil.read(projectile);
        if (data.contains(DAMAGE_KEY, Tag.TAG_INT)) {
            event.setAmount(Math.max(0, data.getInt(DAMAGE_KEY)));
        }
    }
}
