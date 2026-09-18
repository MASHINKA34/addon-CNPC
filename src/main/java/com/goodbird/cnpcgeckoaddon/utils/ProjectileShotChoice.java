package com.goodbird.cnpcgeckoaddon.utils;

/**
 * What one ranged attack of an npc is fired with.
 *
 * <p>CustomNPCs builds its own projectile out of the item in the npc's projectile slot and
 * never asks whether there is one: an empty slot still launches an {@code EntityProjectile},
 * and that projectile throws out of its own {@code onHit} the moment it lands, because item
 * particles cannot be made of an empty stack - which is a server tick, and the server with
 * it. So the one rule here is that CustomNPCs only gets to shoot when it has an item to
 * shoot. Everything else is one of the two entities the addon spawns itself, or no shot.</p>
 *
 * <p>Kept free of the game so that every branch can be checked by a plain test: the callers
 * answer the five questions, this only ranks the answers.</p>
 */
public enum ProjectileShotChoice {
    /** The entity named in the ranged extras, spawned by the addon. */
    CUSTOM,
    /** CustomNPCs' own projectile, thrown as the item in the npc's projectile slot. */
    CNPC,
    /** The fallback entity of the ranged extras, spawned the way the custom one is. */
    FALLBACK,
    /** Nothing is fired, and CustomNPCs is kept from firing either. */
    NONE;

    /**
     * @param customConfigured   the ranged extras name an entity of their own
     * @param customUsable       and that entity is one the server has seen make a projectile
     * @param cnpcItemEmpty      the npc's projectile slot holds no item - an absent wrapper and
     *                           a wrapper around an empty stack are the same answer
     * @param fallbackConfigured the ranged extras name a fallback; an empty one means "no shot"
     * @param fallbackUsable     and the fallback is a projectile too
     */
    public static ProjectileShotChoice decide(boolean customConfigured, boolean customUsable,
                                              boolean cnpcItemEmpty,
                                              boolean fallbackConfigured, boolean fallbackUsable) {
        if (customConfigured && customUsable) {
            return CUSTOM;
        }
        // An npc with an item keeps shooting it exactly as it did before there was a fallback.
        if (!cnpcItemEmpty) {
            return CNPC;
        }
        if (fallbackConfigured && fallbackUsable) {
            return FALLBACK;
        }
        return NONE;
    }
}
