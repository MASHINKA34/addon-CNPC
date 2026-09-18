package com.goodbird.cnpcgeckoaddon.mixin.impl;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps a CustomNPCs projectile that carries no item from taking the server down as it lands.
 *
 * <p>{@code EntityProjectile.onHit} bursts into particles of the item it was thrown as, and
 * {@code ItemParticleOption} refuses an empty stack with an exception - thrown from inside the
 * entity's tick, which is the level's tick and the server's. CustomNPCs never checks that it
 * has an item before it throws one: an npc whose projectile slot is empty shoots all the same,
 * and so does a script handed an empty stack. The addon no longer lets an npc's own attack get
 * that far, but a script or another mod still can, so the projectile itself is made safe.</p>
 *
 * <p>Only the stack the particles are made of is replaced, and only while it is empty: what
 * the projectile is, hits for and drops still comes from the item it really carries. The
 * arrow is nothing but a texture for eight particles that would otherwise be a crash.</p>
 */
@Mixin(value = EntityProjectile.class, remap = false)
public abstract class MixinEntityProjectile {

    /**
     * Both places {@code onHit} builds item particles read the stack through this call, and
     * the method makes no other use of it. {@code require} is that count on purpose: a
     * CustomNPCs build that lands differently must fail here, loudly, rather than go back to
     * crashing on a hit - {@code EntityProjectileHookTest} holds the jar in {@code lib/} to it.
     */
    @Redirect(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;getItemDisplay()Lnet/minecraft/world/item/ItemStack;"),
            require = 2)
    private ItemStack cnpcgeckoaddon$impactParticleStack(EntityProjectile projectile) {
        ItemStack stack = projectile.getItemDisplay();
        return stack == null || stack.isEmpty() ? new ItemStack(Items.ARROW) : stack;
    }
}
