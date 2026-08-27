package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Which boss abilities pass this npc by entirely, and how much of each family of damage
 * sources it lets through.
 *
 * <p>Lives on the npc rather than in the boss settings for the reason carrying does: the npc
 * a dungeon builds a mechanic around - a bomb the players walk into the arena - is a plain
 * npc, and it has to survive the fight without the boss knowing anything about it.</p>
 */
public class NpcImmunityData {
    public static final int RESIST_SLOTS = 8;

    private static final String IMMUNE_ABILITIES_KEY = "GeckoNpcImmuneAbilities";
    private static final String DAMAGE_RESISTS_KEY = "GeckoNpcDamageResists";

    private static final int ALL_ABILITIES = (1 << BossAbilityKind.COUNT) - 1;

    /** Immune to nothing, so every npc built before this existed fights exactly as it did. */
    private int immuneAbilities;

    private final List<NpcDamageResistEntry> damageResists = new ArrayList<>();

    public NpcImmunityData() {
        for (int i = 0; i < RESIST_SLOTS; i++) {
            damageResists.add(new NpcDamageResistEntry());
        }
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt(IMMUNE_ABILITIES_KEY, immuneAbilities);
        ListTag resists = new ListTag();
        for (NpcDamageResistEntry entry : damageResists) {
            if (entry.isSet()) {
                resists.add(entry.writeToNBT());
            }
        }
        // Only set rules are saved: every npc in the world carries this data, and the
        // common case - no resistances - should not cost eight empty compounds each.
        if (!resists.isEmpty()) {
            tag.put(DAMAGE_RESISTS_KEY, resists);
        }
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        setImmuneAbilities(tag.getInt(IMMUNE_ABILITIES_KEY));
        ListTag resists = tag.getList(DAMAGE_RESISTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < RESIST_SLOTS; i++) {
            damageResists.get(i).readFromNBT(i < resists.size() ? resists.getCompound(i) : new CompoundTag());
        }
    }

    public NpcDamageResistEntry getResist(int index) {
        return damageResists.get(Math.max(0, Math.min(index, RESIST_SLOTS - 1)));
    }

    /**
     * The first rule that matches this hit, or null when none does.
     *
     * <p>Top to bottom, first match wins: that is what lets a narrow rule sit above a broad
     * catch-all instead of being drowned out by it.</p>
     */
    public NpcDamageResistEntry findResist(DamageSource source) {
        for (NpcDamageResistEntry entry : damageResists) {
            if (entry.isSet() && entry.matches(source)) {
                return entry;
            }
        }
        return null;
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
