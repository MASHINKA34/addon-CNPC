package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.mixin.INpcImmunityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * The one door a boss ability goes through to land on somebody.
 *
 * <p>A dozen abilities each with their own hurt, potion and shove call is a dozen places to
 * forget the npc immunity mask in, so the three of them are asked for together here: check
 * once, then damage, apply and push - or do none of it.</p>
 */
public final class BossAbilityDamageUtil {
    private BossAbilityDamageUtil() {
    }

    /** Whether one ability passes this entity by entirely. Only npcs can ever say yes. */
    public static boolean isImmune(Entity target, int ability) {
        return target instanceof EntityNPCInterface npc
                && npc.ais instanceof INpcImmunityData holder
                && holder.cnpcgeckoaddon$getNpcImmunityData().isImmuneTo(ability);
    }

    /**
     * Lands one ability on one victim: the damage, then the potions, then the shove.
     *
     * @param knockback strength of the push, or 0 for an ability that does not move anyone
     * @param pushX     together with {@code pushZ}, the direction the victim is pushed away
     *                  from - vanilla shoves against the vector it is handed
     * @return whether the damage landed
     */
    public static boolean hit(LivingEntity target, int ability, EntityNPCInterface boss, int damage,
                              BossEffectSet effects, int knockback, double pushX, double pushZ) {
        if (target == null || isImmune(target, ability)) {
            return false;
        }
        // A zero is "this ability does not hurt, it only does its other half"; handing that to
        // vanilla anyway would still burn the victim's invulnerability frames on nothing.
        boolean damaged = damage > 0
                && target.hurt(boss.level().damageSources().mobAttack(boss), damage);
        // Applied even when the hit was absorbed by invulnerability frames or armour: a plague
        // aura that stops working because the victim was briefly immune would feel broken
        // rather than fair.
        if (effects != null) {
            effects.applyAll(target, boss);
        }
        if (damaged && knockback > 0) {
            target.knockback(knockback, pushX, pushZ);
        }
        return damaged;
    }

    /** The potion half on its own, for an ability that deals its damage somewhere else. */
    public static boolean applyEffects(LivingEntity target, int ability, EntityNPCInterface boss,
                                       BossEffectSet effects) {
        if (target == null || isImmune(target, ability)) {
            return false;
        }
        effects.applyAll(target, boss);
        return true;
    }
}
