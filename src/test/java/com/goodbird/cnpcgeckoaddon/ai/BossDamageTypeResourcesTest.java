package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files the two ability damage types are made of: the types themselves, the vanilla tags
 * that say what they get past, and the death messages that name the boss.
 *
 * <p>A damage type that fails to load does not crash anything - the hit falls back to a mob
 * attack with a line in the log - and a tag entry that names the wrong id quietly lets armour
 * back in. Neither would be seen before a tester stood in full diamond and wondered, so the
 * files are read here, from the source tree a clean clone has.</p>
 */
class BossDamageTypeResourcesTest {

    private static final Path RESOURCES = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "resources"));
    private static final Path TYPES = RESOURCES.resolve(Path.of("data", "cnpcgeckoaddon", "damage_type"));
    private static final Path TAGS = RESOURCES.resolve(Path.of("data", "minecraft", "tags", "damage_type"));
    private static final Path LANG = RESOURCES.resolve(Path.of("assets", "cnpcgeckoaddon", "lang"));

    private static final String PIERCING = "cnpcgeckoaddon:ability_piercing";
    private static final String TRUE = "cnpcgeckoaddon:ability_true";

    @Test
    @DisplayName("both types are where their keys point, and vanilla's own codec reads them")
    void theTypesLoad() {
        for (ResourceKey<DamageType> key : List.of(BossAbilityDamageTypes.PIERCING, BossAbilityDamageTypes.TRUE)) {
            assertEquals("cnpcgeckoaddon", key.location().getNamespace());
            JsonObject json = read(TYPES.resolve(key.location().getPath() + ".json"));
            assertEquals("cnpcgeckoaddon." + key.location().getPath(), json.get("message_id").getAsString());
            assertEquals(0.1F, json.get("exhaustion").getAsFloat(), 1.0E-6F);
            assertEquals("never", json.get("scaling").getAsString());
            DamageType type = DamageType.DIRECT_CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(message -> new AssertionError(key.location() + " does not parse: " + message));
            assertEquals("cnpcgeckoaddon." + key.location().getPath(), type.msgId());
            assertSame(DamageScaling.NEVER, type.scaling());
        }
    }

    @Test
    @DisplayName("armour lets both through; enchantments and effects only the true one")
    void theTagsSayWhatEachGetsPast() {
        assertEquals(Set.of(PIERCING, TRUE), ownValues("bypasses_armor"));
        assertEquals(Set.of(TRUE), ownValues("bypasses_enchantments"));
        assertEquals(Set.of(TRUE), ownValues("bypasses_effects"));
        for (String tag : List.of("bypasses_armor", "bypasses_enchantments", "bypasses_effects")) {
            JsonObject json = read(TAGS.resolve(tag + ".json"));
            assertTrue(json.has("replace") && !json.get("replace").getAsBoolean(),
                    tag + " has to add to vanilla's list, not replace it");
        }
    }

    @Test
    @DisplayName("neither type gets past creative, the invulnerability frames or a shield")
    void nothingBypassesTheGamesOwnRules() {
        for (String tag : List.of("bypasses_invulnerability", "bypasses_cooldown", "bypasses_shield",
                "bypasses_resistance", "no_knockback")) {
            Path file = TAGS.resolve(tag + ".json");
            if (Files.isRegularFile(file)) {
                Set<String> values = ownValues(tag);
                assertFalse(values.contains(PIERCING) || values.contains(TRUE),
                        tag + " must not list the ability types: " + values);
            }
        }
    }

    @Test
    @DisplayName("each type's death message is in both locales, naming the boss")
    void theDeathMessagesNameTheBoss() {
        for (String locale : List.of("en_us", "ru_ru")) {
            JsonObject lang = read(LANG.resolve(locale + ".json"));
            for (String id : List.of("ability_piercing", "ability_true")) {
                String key = "death.attack.cnpcgeckoaddon." + id;
                // A hit with an attacker reads the plain key, or the .item one when the boss holds
                // a named item; vanilla never reads .player for a source that has an attacker.
                for (String variant : List.of(key, key + ".item")) {
                    assertTrue(lang.has(variant), locale + " lacks " + variant);
                    String text = lang.get(variant).getAsString();
                    assertTrue(text.contains("%1$s") && text.contains("%2$s"),
                            locale + ": " + variant + " has to name the victim and the boss: " + text);
                }
                assertTrue(lang.get(key + ".item").getAsString().contains("%3$s"),
                        locale + ": " + key + ".item has to name the item");
            }
        }
    }

    @Test
    @DisplayName("each profile damage type maps to its key, and a plain hit to none")
    void theProfileValuesMapToTheKeys() {
        assertNull(BossAbilityDamageTypes.keyOf(BossAbilityProfile.DAMAGE_NORMAL));
        assertSame(BossAbilityDamageTypes.PIERCING, BossAbilityDamageTypes.keyOf(BossAbilityProfile.DAMAGE_PIERCING));
        assertSame(BossAbilityDamageTypes.TRUE, BossAbilityDamageTypes.keyOf(BossAbilityProfile.DAMAGE_TRUE));
        assertNull(BossAbilityDamageTypes.keyOf(BossAbilityProfile.DAMAGE_TRUE + 1));
        assertNull(BossAbilityDamageTypes.keyOf(-1));
    }

    /** The values one of the tag files lists, as written. */
    private static Set<String> ownValues(String tag) {
        Set<String> values = new TreeSet<>();
        JsonArray array = read(TAGS.resolve(tag + ".json")).getAsJsonArray("values");
        for (JsonElement value : array) {
            values.add(value.getAsString());
        }
        return values;
    }

    private static JsonObject read(Path file) {
        assertTrue(Files.isRegularFile(file), file + " is missing");
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
