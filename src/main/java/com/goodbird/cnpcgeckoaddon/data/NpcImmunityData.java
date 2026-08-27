package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Which boss abilities pass this npc by entirely.
 *
 * <p>Lives on the npc rather than in the boss settings for the reason carrying does: the npc
 * a dungeon builds a mechanic around - a bomb the players walk into the arena - is a plain
 * npc, and it has to survive the fight without the boss knowing anything about it.</p>
 */
public class NpcImmunityData {
    private static final String IMMUNE_ABILITIES_KEY = "GeckoNpcImmuneAbilities";

    private static final int ALL_ABILITIES = (1 << BossAbilityKind.COUNT) - 1;

    /** Immune to nothing, so every npc built before this existed fights exactly as it did. */
    private int immuneAbilities;

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt(IMMUNE_ABILITIES_KEY, immuneAbilities);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        setImmuneAbilities(tag.getInt(IMMUNE_ABILITIES_KEY));
    }

    /** The whole mask, one bit per {@link BossAbilityKind}. */
    public int getImmuneAbilities() {
        return immuneAbilities;
    }

    public void setImmuneAbilities(int value) {
        immuneAbilities = value & ALL_ABILITIES;
    }

    public boolean isImmuneTo(int ability) {
        return ability >= 0 && ability < BossAbilityKind.COUNT
                && (immuneAbilities & 1 << ability) != 0;
    }

    public void setImmuneTo(int ability, boolean immune) {
        if (ability < 0 || ability >= BossAbilityKind.COUNT) {
            return;
        }
        if (immune) {
            immuneAbilities |= 1 << ability;
        } else {
            immuneAbilities &= ~(1 << ability);
        }
    }
}
