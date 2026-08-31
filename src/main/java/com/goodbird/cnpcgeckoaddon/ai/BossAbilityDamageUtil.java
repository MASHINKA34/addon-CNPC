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
    /** No ability is landing right now, so whatever is being hurt is plain damage. */
    public static final int NO_ABILITY = -1;

    /**
     * The ability whose hit is being dealt at this instant, for whoever is downstream of it.
     *
     * <p>A damage listener is handed a {@link net.minecraft.world.damagesource.DamageSource}
     * and nothing else - by then "this was the geyser" is gone, and the totem filter needs it
     * back. A plain static holds it because the server tick is one thread; the save-and-restore
     * below is what keeps the answer honest when one ability's hit sets another one off.</p>
     */
    private static int currentAbility = NO_ABILITY;

    private BossAbilityDamageUtil() {
    }

    /** The ability landing right now, or {@link #NO_ABILITY} outside any ability's hit. */
    public static int currentAbility() {
        return currentAbility;
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
        if (target == null || passesBy(target, ability)) {
            return false;
        }
        int outerAbility = currentAbility;
        currentAbility = ability;
        try {
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
        } finally {
            // Restored on every road out. A mark left standing would sign the next plain sword
            // swing as this ability, and a totem that only the geyser may break would fall to it.
            currentAbility = outerAbility;
        }
    }

    /** The potion half on its own, for an ability that deals its damage somewhere else. */
    public static boolean applyEffects(LivingEntity target, int ability, EntityNPCInterface boss,
                                       BossEffectSet effects) {
        if (target == null || passesBy(target, ability)) {
            return false;
        }
        int outerAbility = currentAbility;
        currentAbility = ability;
        try {
            // Marked as well as hurting does: a potion of harming is damage arriving under this
            // ability's name, and the totem filter has to read it as such.
            effects.applyAll(target, boss);
        } finally {
            currentAbility = outerAbility;
        }
        return true;
    }

    /**
     * Everyone this ability simply does not reach: an immune npc, or a totem whose slot does
     * not list this ability among the ones that may break it.
     *
     * <p>The totem half is asked here rather than only on the incoming damage, because a hit
     * that bounces off must leave no potions and no shove behind either.</p>
     *
     * <p>Public because two abilities move their victim outside this door - the geyser throws
     * with its launch and the hook with its drag - and both have to throw around exactly the
     * set this lets through, not a wider one.</p>
     */
    public static boolean passesBy(Entity target, int ability) {
        return isImmune(target, ability) || BossTotemUtil.rejects(target, ability);
    }
}
