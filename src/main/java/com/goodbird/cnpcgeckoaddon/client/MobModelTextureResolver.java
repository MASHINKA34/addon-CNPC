package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.utils.MobModelNameMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the default texture shipped with a bundled mob model.
 *
 * <p>CNPC Gecko Addon normally uses the NPC skin for every GeckoLib model.
 * That is useful for custom models, but it stretches Steve over imported mob
 * geometry when the user has not selected a skin manually. For the bundled
 * model namespaces we instead select the model's recorded base texture while
 * still respecting an explicitly selected custom texture. A texture that was
 * selected automatically for a different bundled model is treated as stale,
 * so switching models cannot stretch the previous mob's skin over the new
 * geometry.</p>
 */
public final class MobModelTextureResolver {
    private static final Set<String> BUNDLED_NAMESPACES = Set.of(
            "born_in_chaos_v1",
            "cataclysm",
            "srparasites",
            "sculkhorde",
            "arphex",
            "block_factorys_bosses",
            "mowziesmobs",
            "legend_of_the_dwerden",
            "sculks_of_arda",
            "luminous_nether",
            "callfromthedepth_",
            "fromtheshadows",
            "nethersentinel",
            "skarrier_mobs",
            "blighted_beasts",
            "deep_dark_regrowth",
            "deeperdarker_legacy",
            "deeperdarker",
            "sculk_worm",
            "minecraft_dungend_two_mobs",
            "echoes",
            "nue",
            "infernalexp",
            "betternether",
            "piglinproliferation",
            "nourished_nether",
            "creatures_expanded",
            "ecosystemmod",
            "myceliummire",
            "mosslings_muddlings",
            "undergarden",
            "critters_and_cryptids",
            "redev_edition_mobs",
            "wroughtnights",
            "dungeons_and_combat",
            "panascraftrpgmod"
    );

    private static final Map<String, ResourceLocation> BUNDLED_TEXTURES = loadBundledTextures();

    private static final Map<String, ResourceLocation> OVERRIDES =
            loadTextureTable("/META-INF/MOBMODEL_TEXTURE_OVERRIDES.tsv", true);

    private static final Set<ResourceLocation> BUNDLED_DEFAULT_TEXTURES = Set.copyOf(BUNDLED_TEXTURES.values());

    /**
     * Sentinel stored for models that have no bundled texture. ConcurrentHashMap cannot
     * hold null values, so without it {@link #findTexture} - which walks every resource
     * pack entry - would re-run on every single frame for every unmatched model.
     */
    private static final ResourceLocation NO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cnpcgeckoaddon", "no_bundled_texture");

    private static final Map<ResourceLocation, ResourceLocation> CACHE = new ConcurrentHashMap<>();

    private MobModelTextureResolver() {
    }

    public static ResourceLocation resolve(ResourceLocation model, ResourceLocation npcTexture) {
        if (!isBundledModel(model)) {
            return npcTexture;
        }

        ResourceLocation resolved = getDefaultTexture(model);
        if (resolved == null) {
            return npcTexture;
        }
        if (isDefaultNpcTexture(npcTexture)) {
            return resolved;
        }
        if (npcTexture == null || npcTexture.equals(resolved)) {
            return resolved;
        }

        // The GUI stores the chosen skin independently from the Gecko model.
        // When the user switches models, the former model's bundled texture
        // otherwise remains selected and produces a scrambled UV layout.
        if (BUNDLED_DEFAULT_TEXTURES.contains(npcTexture)) {
            return resolved;
        }

        // Emissive, eye, mask and overlay maps are render layers, not valid
        // standalone skins. Keep custom textures from other namespaces intact.
        if (npcTexture.getNamespace().equals(model.getNamespace())
                && MobModelNameMatcher.isAuxiliaryTexture(MobModelNameMatcher.fileStem(npcTexture.getPath(), ".png"))) {
            return resolved;
        }

        return npcTexture;
    }

    /**
     * Whether this model belongs to a bundle whose recorded default texture is used instead
     * of the npc skin. Every model in {@code MOBMODEL_TEXTURES.tsv} has to pass, or its
     * bundle renders with a stretched npc skin however complete its texture table is.
     */
    public static boolean isBundledModel(ResourceLocation model) {
        return model != null
                && (BUNDLED_NAMESPACES.contains(model.getNamespace()) || isEmbeddedArphex(model));
    }

    public static ResourceLocation getDefaultTexture(ResourceLocation model) {
        if (!isBundledModel(model)) {
            return null;
        }
        ResourceLocation cached = CACHE.computeIfAbsent(model, key -> {
            ResourceLocation found = findTexture(key);
            return found == null ? NO_TEXTURE : found;
        });
        return cached == NO_TEXTURE ? null : cached;
    }

    /** Drops the memoized lookups so a resource reload can pick up newly added textures. */
    public static void invalidate() {
        CACHE.clear();
    }

    private static ResourceLocation findTexture(ResourceLocation model) {
        var resourceManager = Minecraft.getInstance().getResourceManager();
        ResourceLocation bundled = BUNDLED_TEXTURES.get(model.toString());
        if (bundled != null && resourceManager.getResource(bundled).isPresent()) {
            return bundled;
        }
        String modelName = MobModelNameMatcher.fileStem(model.getPath(), ".geo.json");
        boolean embeddedArphex = isEmbeddedArphex(model);
        String overrideNamespace = embeddedArphex ? "arphex" : model.getNamespace();
        ResourceLocation override = OVERRIDES.get(overrideNamespace + ":" + MobModelNameMatcher.normalize(modelName));
        if (embeddedArphex && override != null) {
            override = ResourceLocation.fromNamespaceAndPath(
                    model.getNamespace(),
                    "textures/entities/arphex/" + MobModelNameMatcher.fileName(override.getPath()));
        }
        if (override != null && resourceManager.getResource(override).isPresent()) {
            return override;
        }

        return resourceManager.listResources(
                        "textures",
                        location -> location.getNamespace().equals(model.getNamespace())
                                && (embeddedArphex
                                ? location.getPath().startsWith("textures/entities/arphex/")
                                : (location.getPath().startsWith("textures/entity/")
                                || location.getPath().startsWith("textures/entities/")))
                                && location.getPath().endsWith(".png"))
                .keySet()
                .stream()
                .max(Comparator.comparingInt(texture -> MobModelNameMatcher.score(modelName, texture)))
                .filter(texture -> MobModelNameMatcher.score(modelName, texture) >= MobModelNameMatcher.MATCH_THRESHOLD)
                .orElse(null);
    }

    private static Map<String, ResourceLocation> loadBundledTextures() {
        return loadTextureTable("/META-INF/MOBMODEL_TEXTURES.tsv", false);
    }

    /**
     * Reads one {@code model resource<TAB>texture resource} table out of the jar.
     *
     * @param normalizeKeys true to key the table the way an override is looked up - the
     *                      namespace plus the normalized model file stem - rather than by
     *                      the model resource the file spells out
     */
    private static Map<String, ResourceLocation> loadTextureTable(String resource, boolean normalizeKeys) {
        Map<String, ResourceLocation> textures = new HashMap<>();
        try (InputStream input = MobModelTextureResolver.class.getResourceAsStream(resource)) {
            if (input == null) {
                return Map.of();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    int separator = line.indexOf('\t');
                    if (separator <= 0 || separator == line.length() - 1) {
                        continue;
                    }
                    String model = line.substring(0, separator);
                    ResourceLocation texture = ResourceLocation.parse(line.substring(separator + 1));
                    textures.put(normalizeKeys ? overrideKey(model) : model, texture);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
        return Map.copyOf(textures);
    }

    private static String overrideKey(String model) {
        ResourceLocation location = ResourceLocation.parse(model);
        return location.getNamespace() + ":" + MobModelNameMatcher.normalize(MobModelNameMatcher.fileStem(location.getPath(), ".geo.json"));
    }

    private static boolean isEmbeddedArphex(ResourceLocation model) {
        return model != null
                && model.getNamespace().equals("cnpcgeckoaddon")
                && model.getPath().startsWith("geo/arphex/");
    }

    private static boolean isDefaultNpcTexture(ResourceLocation texture) {
        if (texture == null) {
            return true;
        }
        String path = texture.getPath().toLowerCase(Locale.ROOT);
        boolean defaultName = path.contains("steve") || path.contains("alex")
                || path.contains("humanmale") || path.contains("humanfemale");
        return defaultName && (texture.getNamespace().equals("customnpcs")
                || texture.getNamespace().equals("minecraft"));
    }

}
