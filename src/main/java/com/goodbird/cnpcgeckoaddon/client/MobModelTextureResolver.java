package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.utils.MobModelNameMatcher;
import com.goodbird.cnpcgeckoaddon.utils.ResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which texture a GeckoLib model is drawn with - in the world and in the model picker
 * alike, so what the picker shows is what the npc will look like.
 *
 * <p>CNPC Gecko Addon normally uses the NPC skin for every GeckoLib model. That is right for a
 * custom model painted for that skin, and wrong for a mob model with a sheet of its own: Steve
 * stretched over a dragon. So every model gets its own sheet looked up - the recorded table and
 * the hand-picked overrides for the bundled namespaces, then a png named after the model in its
 * own namespace, for the models of any mod - and a skin still left at a default is replaced by
 * it, while a skin somebody chose is kept. A texture that was put there automatically for a
 * different bundled model is treated as stale, so switching models cannot stretch the previous
 * mob's skin over the new geometry. A bundled model with no sheet at all is drawn with the
 * missing-texture sheet rather than with the default skin, so it shows as missing.</p>
 */
public final class MobModelTextureResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcgeckoaddon");
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
            "panascraftrpgmod",
            "eeeabsmobs",
            "dragonforged",
            "threateningly_mobs",
            "metus_oblita"
    );

    /** The sheet the addon draws for anything it has no texture for, as ModelCustom does for a missing model. */
    public static final ResourceLocation MISSING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cnpcgeckoaddon", "textures/model/alphabet.png");

    private static final String EMBEDDED_ARPHEX_TEXTURES = "textures/entities/arphex/";

    private static final Map<String, ResourceLocation> BUNDLED_TEXTURES = loadBundledTextures();

    private static final Map<String, ResourceLocation> OVERRIDES =
            loadTextureTable("/META-INF/MOBMODEL_TEXTURE_OVERRIDES.tsv", true);

    private static final Set<ResourceLocation> BUNDLED_DEFAULT_TEXTURES = Set.copyOf(BUNDLED_TEXTURES.values());

    /** Where the texture a model is drawn with came from; the model picker says it out loud. */
    public enum Source {
        /** The model's own sheet out of the recorded table or the overrides. */
        MAP,
        /** The model's own sheet, found as a png named after it. */
        NAME,
        /** The npc's skin: chosen by somebody, or all a model from outside the bundles has. */
        NPC,
        /** Nothing found for a bundled model: the missing-texture sheet. */
        NONE
    }

    /** The texture a model is drawn with and where it came from. */
    public record Resolution(ResourceLocation texture, Source source) {
    }

    private static final Resolution MISSING = new Resolution(MISSING_TEXTURE, Source.NONE);

    /**
     * Stored for a model with no sheet of its own. ConcurrentHashMap cannot hold null values,
     * so without it the lookup - which walks the namespace's textures - would re-run on every
     * single frame for every unmatched model.
     */
    private static final Resolution NO_OWN_TEXTURE = new Resolution(MISSING_TEXTURE, Source.NONE);

    private static final Map<ResourceLocation, Resolution> OWN_TEXTURES = new ConcurrentHashMap<>();

    private static final Map<String, List<ResourceLocation>> NAMESPACE_TEXTURES = new ConcurrentHashMap<>();

    private MobModelTextureResolver() {
    }

    /** The texture {@code model} is drawn with on an npc whose skin is {@code npcTexture}. */
    public static ResourceLocation resolve(ResourceLocation model, ResourceLocation npcTexture) {
        return explain(model, npcTexture).texture();
    }

    /** {@link #resolve}, and where the texture came from. */
    public static Resolution explain(ResourceLocation model, ResourceLocation npcTexture) {
        if (model == null) {
            return new Resolution(npcTexture == null ? MISSING_TEXTURE : npcTexture, Source.NPC);
        }
        boolean bundled = isBundledModel(model);
        if (!bundled && !isDefaultNpcTexture(npcTexture)) {
            // A skin set on another mod's model is somebody's choice; nothing here knows better,
            // and it is not worth a walk through that mod's textures on every frame.
            return new Resolution(npcTexture, Source.NPC);
        }
        return decide(model, npcTexture, ownTexture(model), bundled);
    }

    /**
     * The choice itself, given what the model's own sheet turned out to be - kept apart from the
     * lookups so it can be checked without a resource manager.
     *
     * @param own     the model's own sheet with {@link Source#MAP} or {@link Source#NAME}, or null
     * @param bundled whether the model belongs to one of the bundled namespaces
     */
    static Resolution decide(ResourceLocation model, ResourceLocation npcTexture, Resolution own, boolean bundled) {
        boolean defaultSkin = isDefaultNpcTexture(npcTexture);
        if (own == null) {
            // Another mod's model keeps the default skin it was made for; a bundled mob with no
            // sheet shows as missing rather than as a stretched Steve.
            return npcTexture == null || bundled && defaultSkin ? MISSING : new Resolution(npcTexture, Source.NPC);
        }
        if (defaultSkin || own.texture().equals(npcTexture)) {
            return own;
        }
        if (bundled) {
            // The GUI stores the chosen skin independently from the Gecko model.
            // When the user switches models, the former model's bundled texture
            // otherwise remains selected and produces a scrambled UV layout.
            if (BUNDLED_DEFAULT_TEXTURES.contains(npcTexture)) {
                return own;
            }
            // Emissive, eye, mask and overlay maps are render layers, not valid
            // standalone skins. Keep custom textures from other namespaces intact.
            if (npcTexture.getNamespace().equals(model.getNamespace())
                    && MobModelNameMatcher.isAuxiliaryTexture(MobModelNameMatcher.fileStem(npcTexture.getPath(), ".png"))) {
                return own;
            }
        }
        return new Resolution(npcTexture, Source.NPC);
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

    /** The model's own sheet - recorded, overridden or named after it - or null when it has none. */
    public static ResourceLocation getDefaultTexture(ResourceLocation model) {
        Resolution own = ownTexture(model);
        return own == null ? null : own.texture();
    }

    /** Whether the recorded table names this texture as some bundled model's own sheet. */
    public static boolean isRecordedDefault(ResourceLocation texture) {
        return texture != null && BUNDLED_DEFAULT_TEXTURES.contains(texture);
    }

    /**
     * Every png the live resource packs hold under {@code assets/<namespace>/textures}, in path
     * order - what a model of that namespace can be dressed in.
     */
    public static List<ResourceLocation> texturesOf(String namespace) {
        if (namespace == null) {
            return List.of();
        }
        return NAMESPACE_TEXTURES.computeIfAbsent(namespace, MobModelTextureResolver::listTextures);
    }

    /** Drops the memoized lookups so a resource reload can pick up newly added textures. */
    public static void invalidate() {
        OWN_TEXTURES.clear();
        NAMESPACE_TEXTURES.clear();
    }

    private static Resolution ownTexture(ResourceLocation model) {
        if (model == null) {
            return null;
        }
        Resolution cached = OWN_TEXTURES.computeIfAbsent(model, key -> {
            Resolution found = findOwnTexture(key);
            return found == null ? NO_OWN_TEXTURE : found;
        });
        return cached == NO_OWN_TEXTURE ? null : cached;
    }

    private static Resolution findOwnTexture(ResourceLocation model) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        boolean bundled = isBundledModel(model);
        boolean embeddedArphex = isEmbeddedArphex(model);
        String modelName = MobModelNameMatcher.fileStem(model.getPath(), ".geo.json");
        if (bundled) {
            ResourceLocation recorded = BUNDLED_TEXTURES.get(model.toString());
            if (recorded != null && resources.getResource(recorded).isPresent()) {
                return new Resolution(recorded, Source.MAP);
            }
            String overrideNamespace = embeddedArphex ? "arphex" : model.getNamespace();
            ResourceLocation override = OVERRIDES.get(overrideNamespace + ":" + MobModelNameMatcher.normalize(modelName));
            if (embeddedArphex && override != null) {
                override = ResourceIds.pathOrDefault(
                        model.getNamespace(),
                        EMBEDDED_ARPHEX_TEXTURES + MobModelNameMatcher.fileName(override.getPath()), null);
            }
            if (override != null && resources.getResource(override).isPresent()) {
                return new Resolution(override, Source.MAP);
            }
        }

        List<ResourceLocation> candidates = texturesOf(model.getNamespace());
        if (embeddedArphex) {
            candidates = candidates.stream()
                    .filter(texture -> texture.getPath().startsWith(EMBEDDED_ARPHEX_TEXTURES))
                    .toList();
        }
        ResourceLocation named = MobModelNameMatcher.findNamedTexture(model, candidates);
        if (named != null) {
            return new Resolution(named, Source.NAME);
        }
        if (!bundled) {
            // Close names are only trusted inside the bundles, where every sheet was checked to
            // belong to one of the bundled mobs; another mod's textures are anybody's.
            return null;
        }
        ResourceLocation closest = null;
        int closestScore = Integer.MIN_VALUE;
        for (ResourceLocation texture : candidates) {
            String path = texture.getPath();
            boolean creatureSheet = embeddedArphex
                    || path.startsWith("textures/entity/") || path.startsWith("textures/entities/");
            if (!creatureSheet) {
                continue;
            }
            int score = MobModelNameMatcher.score(modelName, texture);
            // Strictly better only: of equal scores the first in path order stays, as it always has.
            if (score > closestScore) {
                closest = texture;
                closestScore = score;
            }
        }
        return closest != null && closestScore >= MobModelNameMatcher.MATCH_THRESHOLD
                ? new Resolution(closest, Source.NAME) : null;
    }

    private static List<ResourceLocation> listTextures(String namespace) {
        TreeSet<ResourceLocation> found = new TreeSet<>();
        try {
            // Asked of each pack for this one namespace: the resource manager's own listing walks
            // the textures of every namespace of every mod to answer for one.
            Minecraft.getInstance().getResourceManager().listPacks().forEach(pack ->
                    pack.listResources(PackType.CLIENT_RESOURCES, namespace, "textures", (location, supplier) -> {
                        if (location.getPath().endsWith(".png")) {
                            found.add(location);
                        }
                    }));
        } catch (RuntimeException error) {
            LOGGER.warn("Could not list the textures of {}; its models are drawn without a matched sheet",
                    namespace, error);
        }
        return List.copyOf(found);
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
        int malformed = 0;
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
                    // One malformed line costs that line: the table is read on the render thread,
                    // and a parse that threw used to cost every other model its texture too.
                    ResourceLocation texture = ResourceLocation.tryParse(line.substring(separator + 1));
                    String key = normalizeKeys ? overrideKey(model) : model;
                    if (texture == null || key == null) {
                        malformed++;
                        continue;
                    }
                    textures.put(key, texture);
                }
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not read the bundled model texture table {}; bundled models use the npc skin", resource,
                    error);
            return Map.of();
        }
        if (malformed > 0) {
            LOGGER.warn("Skipped {} malformed lines of the bundled model texture table {}", malformed, resource);
        }
        return Map.copyOf(textures);
    }

    /** @return the key an override is looked up by, or null for a model that is not a valid id */
    private static String overrideKey(String model) {
        ResourceLocation location = ResourceLocation.tryParse(model);
        if (location == null) {
            return null;
        }
        return location.getNamespace() + ":" + MobModelNameMatcher.normalize(MobModelNameMatcher.fileStem(location.getPath(), ".geo.json"));
    }

    private static boolean isEmbeddedArphex(ResourceLocation model) {
        return model != null
                && model.getNamespace().equals("cnpcgeckoaddon")
                && model.getPath().startsWith("geo/arphex/");
    }

    /** Whether a skin is one CustomNPCs or vanilla hands out before anybody chose one. */
    public static boolean isDefaultNpcTexture(ResourceLocation texture) {
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
