package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * One damage resistance rule on an npc: a matcher naming a family of damage sources, and
 * the percentage of that damage still let through.
 *
 * <p>Exists because the CustomNPCs resistances know only melee, arrow, explosion and
 * knockback - a modded gun is none of those, so a melee boss dies to bullets it was never
 * balanced against. Matching by id string keeps this mod out of the business of knowing
 * every gun mod: the builder types whatever id the damage actually arrives with.</p>
 *
 * <p>The matcher understands five shapes: a damage type id ("scorchedguns:bullet"), a
 * damage type tag ("#minecraft:is_projectile"), the entity that delivered the hit
 * ("entity:scorchedguns:bullet_projectile"), a whole mod ("scorchedguns:*") and everything
 * ("*"). The whole-mod shape is the load-bearing one: the exact id of a bullet is unknown
 * until fired, but the mod id is known the moment the mod is installed.</p>
 */
public final class NpcDamageResistEntry {
    public static final int PERCENT_NORMAL = 100;
    public static final int PERCENT_MAX = 500;

    private static final String ENTITY_PREFIX = "entity:";

    private String matcher = "";
    private int percent = PERCENT_NORMAL;

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Matcher", matcher);
        tag.putInt("Percent", percent);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        setMatcher(tag.getString("Matcher"));
        percent = tag.contains("Percent")
                ? Mth.clamp(tag.getInt("Percent"), 0, PERCENT_MAX) : PERCENT_NORMAL;
    }

    /**
     * Whether this rule covers the given hit.
     *
     * <p>A string that fits none of the shapes - or does not parse - simply never matches:
     * a typo in the GUI must cost that one rule, not the npc's ability to take damage.</p>
     */
    public boolean matches(DamageSource source) {
        if (matcher.equals("*")) {
            return true;
        }
        if (matcher.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(matcher.substring(1));
            return tag != null && source.is(TagKey.create(Registries.DAMAGE_TYPE, tag));
        }
        // Checked before the damage type id: "entity:arrow" parses as a valid damage type
        // location too, and would silently match nothing as one.
        if (matcher.startsWith(ENTITY_PREFIX)) {
            ResourceLocation id = ResourceLocation.tryParse(matcher.substring(ENTITY_PREFIX.length()));
            Entity direct = source.getDirectEntity();
            return id != null && direct != null
                    && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()));
        }
        if (matcher.endsWith(":*")) {
            return matchesNamespace(source, matcher.substring(0, matcher.length() - 2));
        }
        ResourceLocation id = ResourceLocation.tryParse(matcher);
        return id != null && id.equals(damageTypeId(source));
    }

    /** The whole-mod shape: the mod's own damage types, or whatever its guns shoot. */
    private static boolean matchesNamespace(DamageSource source, String namespace) {
        if (namespace.isEmpty()) {
            return false;
        }
        ResourceLocation type = damageTypeId(source);
        if (type != null && type.getNamespace().equals(namespace)) {
            return true;
        }
        // A projectile is anything standing between the attacker and the hit - decided by
        // identity rather than the Projectile class, because gun mods rarely extend vanilla's.
        // That also keeps the mod's melee mobs out of a rule written against its bullets.
        Entity direct = source.getDirectEntity();
        return direct != null && direct != source.getEntity()
                && BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace().equals(namespace);
    }

    private static ResourceLocation damageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(null);
    }

    /** Empty matcher means the slot is unused. */
    public boolean isSet() {
        return !matcher.isEmpty();
    }

    public String getMatcher() {
        return matcher;
    }

    public void setMatcher(String value) {
        matcher = value == null ? "" : value.trim();
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int value) {
        percent = Mth.clamp(value, 0, PERCENT_MAX);
    }

    public void clear() {
        matcher = "";
        percent = PERCENT_NORMAL;
    }
}
