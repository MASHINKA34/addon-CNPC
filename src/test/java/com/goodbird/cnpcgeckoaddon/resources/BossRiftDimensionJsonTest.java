package com.goodbird.cnpcgeckoaddon.resources;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftDimension;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two datapack files the rift dimension is made of.
 *
 * <p>A dimension that fails to parse does not crash anything: the world simply loads without it,
 * and the rift refuses to open with a line in the log that nobody reads. So the files are read
 * here - as JSON, for the shape the prompt asked for, and the dimension type through vanilla's own
 * codec, which is exactly what the world load runs them through.</p>
 */
class BossRiftDimensionJsonTest {

    private static final String TYPE = "/data/cnpcgeckoaddon/dimension_type/rift.json";
    private static final String STEM = "/data/cnpcgeckoaddon/dimension/rift.json";

    /** Every field the 1.21.1 dimension type codec requires; a missing one fails the whole file. */
    private static final List<String> REQUIRED = List.of(
            "has_skylight", "has_ceiling", "ultrawarm", "natural", "coordinate_scale", "bed_works",
            "respawn_anchor_works", "min_y", "height", "logical_height", "infiniburn", "ambient_light",
            "piglin_safe", "has_raids", "monster_spawn_light_level", "monster_spawn_block_light_limit");

    @Test
    @DisplayName("the level stem is the rift type over a flat generator of pure void")
    void theStemIsAFlatVoid() {
        JsonObject stem = read(STEM);
        assertEquals(BossRiftDimension.ID.toString(), stem.get("type").getAsString(),
                "the dimension has to use the addon's own type");
        JsonObject generator = stem.getAsJsonObject("generator");
        assertEquals("minecraft:flat", generator.get("type").getAsString());
        JsonObject settings = generator.getAsJsonObject("settings");
        assertEquals("minecraft:the_void", settings.get("biome").getAsString());
        assertTrue(settings.getAsJsonArray("layers").isEmpty(), "no layers: nothing but the platform is ever there");
        assertFalse(settings.get("lakes").getAsBoolean());
        assertFalse(settings.get("features").getAsBoolean(), "not even the void biome's starting platform");
        assertTrue(settings.getAsJsonArray("structure_overrides").isEmpty(), "and no structures");
    }

    @Test
    @DisplayName("the dimension type carries every field the codec asks for, and the values the rift needs")
    void theTypeIsComplete() {
        JsonObject type = read(TYPE);
        for (String field : REQUIRED) {
            assertTrue(type.has(field), "the dimension type lacks " + field);
        }
        assertEquals(BossRiftDimension.ID.toString(), type.get("effects").getAsString(),
                "the sky is the one the client registers for the rift");
        assertFalse(type.get("has_skylight").getAsBoolean());
        assertFalse(type.get("bed_works").getAsBoolean(), "nobody sets a spawn point in the rift");
        assertFalse(type.get("respawn_anchor_works").getAsBoolean());
        assertFalse(type.get("natural").getAsBoolean());
        assertFalse(type.get("piglin_safe").getAsBoolean());
        assertFalse(type.get("has_raids").getAsBoolean());
        assertEquals(0, type.get("min_y").getAsInt());
        assertEquals(256, type.get("height").getAsInt());
        assertEquals(256, type.get("logical_height").getAsInt());
        assertEquals(0, type.get("monster_spawn_light_level").getAsInt());
        assertEquals(0, type.get("monster_spawn_block_light_limit").getAsInt());
        assertEquals("#minecraft:infiniburn_overworld", type.get("infiniburn").getAsString());
        assertTrue(type.has("fixed_time"));
    }

    @Test
    @DisplayName("vanilla's own codec reads the dimension type")
    void theCodecReadsTheType() {
        DataResult<DimensionType> result = DimensionType.DIRECT_CODEC.parse(JsonOps.INSTANCE, read(TYPE));
        DimensionType type = result.getOrThrow(message -> new AssertionError("the dimension type does not parse: " + message));
        assertEquals(0, type.minY());
        assertEquals(256, type.height());
        assertEquals(BossRiftDimension.ID, type.effectsLocation());
        assertFalse(type.hasSkyLight());
        assertFalse(type.bedWorks());
    }

    @Test
    @DisplayName("the files sit where the dimension key says: the stem's name is the key's path")
    void theFilesMatchTheKey() {
        assertEquals("cnpcgeckoaddon", BossRiftDimension.KEY.location().getNamespace());
        assertEquals("rift", BossRiftDimension.KEY.location().getPath());
        assertTrue(STEM.endsWith("/dimension/" + BossRiftDimension.KEY.location().getPath() + ".json"));
    }

    private static JsonObject read(String resource) {
        try (InputStream input = BossRiftDimensionJsonTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException(resource + " is missing from the jar");
            }
            JsonElement json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return json.getAsJsonObject();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
