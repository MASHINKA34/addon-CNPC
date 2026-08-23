package com.goodbird.cnpcgeckoaddon.utils;

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
            "wroughtnights"
    );

    private static final Map<String, ResourceLocation> BUNDLED_TEXTURES = loadBundledTextures();

    private static final Map<String, ResourceLocation> OVERRIDES = Map.ofEntries(
            entry("born_in_chaos_v1", "geo/felsteed.geo.json", "textures/entity/pumpkinheadrider.png"),
            entry("born_in_chaos_v1", "geo/felsteedlord.geo.json", "textures/entity/lordofpumpkins.png"),
            entry("born_in_chaos_v1", "geo/felsteedt.geo.json", "textures/entity/pumpkinheadrider.png"),
            entry("born_in_chaos_v1", "geo/sertheheadless.geo.json", "textures/entity/pumpkinheadrider.png"),
            entry("born_in_chaos_v1", "geo/zombiesinabarrel.geo.json", "textures/entity/barrel_zombie.png"),
            entry("born_in_chaos_v1", "geo/rottenzombie.geo.json", "textures/entity/decaying_zombie.png"),
            entry("born_in_chaos_v1", "geo/shyspirit.geo.json", "textures/entity/restlessspirit.png"),
            entry("born_in_chaos_v1", "geo/pumpkinheadmil.geo.json", "textures/entity/pumpkinheadrider.png"),
            entry("born_in_chaos_v1", "geo/lifestealerrevealedtrueform.geo.json", "textures/entity/lifestealer_trueform.png"),

            entry("cataclysm", "geo/clawdian_model.geo.json", "textures/entity/sea/shrimp.png"),
            entry("cataclysm", "geo/elemental_spear_model.geo.json", "textures/entity/sea/spear/lightning_spear_0.png"),
            entry("cataclysm", "geo/endermaptera_model.geo.json", "textures/entity/ender_ssap_bug.png"),
            entry("cataclysm", "geo/ignited_revenant_model.geo.json", "textures/entity/revenant_body.png"),

            entry("srparasites", "geo/modelbanofocused.geo.json", "textures/entity/monster/test.png"),
            entry("srparasites", "geo/modeltenn.geo.json", "textures/entity/monster/test.png"),
            entry("srparasites", "geo/modelgimadapted.geo.json", "textures/entity/monster/gima.png"),
            entry("srparasites", "geo/modelikiadapted.geo.json", "textures/entity/monster/ikia.png"),
            entry("srparasites", "geo/modelzaaadapted.geo.json", "textures/entity/monster/zaaa.png"),
            entry("srparasites", "geo/modelinfcowhead.geo.json", "textures/entity/monster/cowh.png"),
            entry("srparasites", "geo/modelinfpighead.geo.json", "textures/entity/monster/pigh.png"),
            entry("srparasites", "geo/modeldroppod.geo.json", "textures/entity/monster/ancientpod.png"),
            entry("srparasites", "geo/modellumadapted.geo.json", "textures/entity/monster/luma.png"),
            entry("srparasites", "geo/modelhulladapted.geo.json", "textures/entity/monster/hulla_old.png"),
            entry("srparasites", "geo/modelinfcow.geo.json", "textures/entity/monster/cow.png"),
            entry("srparasites", "geo/modelinfpig.geo.json", "textures/entity/monster/pig.png"),
            entry("srparasites", "geo/modelmor.geo.json", "textures/entity/monster/test.png"),
            entry("srparasites", "geo/modelviin.geo.json", "textures/entity/monster/lice.png"),
            entry("srparasites", "geo/modelinfwolf.geo.json", "textures/entity/monster/wolf.png"),
            entry("srparasites", "geo/modelinfplayerhead.geo.json", "textures/entity/monster/playerh.png"),
            entry("srparasites", "geo/modeliki.geo.json", "textures/entity/monster/vermin.png"),
            entry("srparasites", "geo/modelinfplayer.geo.json", "textures/entity/monster/infplayer.png"),

            entry("arphex", "geo/spidertarantula.geo.json", "textures/entities/tarantula1.png"),
            entry("arphex", "geo/mothlarvae.geo.json", "textures/entities/mothlarvae2.png"),
            entry("arphex", "geo/entropy_conduit.geo.json", "textures/entities/diabolos_decimator.png"),
            entry("arphex", "geo/mantis.geo.json", "textures/entities/mantismutilator.png"),
            entry("arphex", "geo/rhinobeetle.geo.json", "textures/entities/scarab2.png"),
            entry("arphex", "geo/spider_recluse.geo.json", "textures/entities/spider_recluse_2.png"),
            entry("arphex", "geo/spidersea.geo.json", "textures/entities/seaspider.png"),
            entry("arphex", "geo/riverroach.geo.json", "textures/entities/waterroach.png"),
            entry("arphex", "geo/sphere.geo.json", "textures/entities/tormentsphere.png"),
            entry("arphex", "geo/spidermoth.geo.json", "textures/entities/horrormothfixed.png"),
            entry("arphex", "geo/maggot.geo.json", "textures/entities/maggotlayerstransparent.png"),
            entry("arphex", "geo/centipedeevictor.geo.json", "textures/entities/centipedeevictorboss.png"),
            entry("arphex", "geo/spider_lunger.geo.json", "textures/entities/gianthuntsman.png"),
            entry("arphex", "geo/spidermothdweller.geo.json", "textures/entities/horrormothfixed.png"),
            entry("arphex", "geo/spider_wolf.geo.json", "textures/entities/wolfspider3egg.png"),
            entry("arphex", "geo/antalate.geo.json", "textures/entities/antsoldier.png"),
            entry("arphex", "geo/diabolos.geo.json", "textures/entities/diabolos_decimator.png"),
            entry("arphex", "geo/tormentor_forceanim.geo.json", "textures/entities/tormentor2.png"),
            entry("arphex", "geo/spider_infestor.geo.json", "textures/entities/sandspider.png"),
            entry("arphex", "geo/tormentor_flash_anim.geo.json", "textures/entities/tormentor2.png"),
            entry("arphex", "geo/longlegsfly.geo.json", "textures/entities/cranefly.png"),
            entry("arphex", "geo/butterfly.geo.json", "textures/entities/butterfly10.png"),
            entry("arphex", "geo/scorpionstriker.geo.json", "textures/entities/sunscorpion.png"),

            entry("block_factorys_bosses", "geo/entity/cannonball_stack.geo.json", "textures/entity/cannonball.png"),
            entry("block_factorys_bosses", "geo/entity/dragon_guard_shield.geo.json", "textures/entity/dragon_guard_sword_shield.png"),
            entry("block_factorys_bosses", "geo/entity/ice_spike_big.geo.json", "textures/entity/ice_spike.png"),
            entry("block_factorys_bosses", "geo/entity/ice_spike_medium.geo.json", "textures/entity/ice_spike.png"),
            entry("block_factorys_bosses", "geo/entity/ice_spike_projectile.geo.json", "textures/entity/ice_spike.png"),
            entry("block_factorys_bosses", "geo/entity/ice_spike_small.geo.json", "textures/entity/ice_spike.png"),
            entry("block_factorys_bosses", "geo/entity/ice_spike_tiny.geo.json", "textures/entity/ice_spike.png"),
            entry("block_factorys_bosses", "geo/entity/kraken_cinematic.geo.json", "textures/entity/kraken.png"),
            entry("block_factorys_bosses", "geo/entity/kraken_tentacle.geo.json", "textures/entity/kraken.png"),
            entry("block_factorys_bosses", "geo/entity/kraken_tentacle_slim.geo.json", "textures/entity/kraken.png"),
            entry("block_factorys_bosses", "geo/entity/yeti_boss_ice.geo.json", "textures/entity/yeti_boss.png"),

            entry("mowziesmobs", "geo/sol_visage.geo.json", "textures/item/sol_visage.png")
    );

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
        if (model == null || (!BUNDLED_NAMESPACES.contains(model.getNamespace())
                && !isEmbeddedArphex(model))) {
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
                && isAuxiliaryTexture(fileStem(npcTexture.getPath(), ".png"))) {
            return resolved;
        }

        return npcTexture;
    }

    public static ResourceLocation getDefaultTexture(ResourceLocation model) {
        if (model == null || (!BUNDLED_NAMESPACES.contains(model.getNamespace())
                && !isEmbeddedArphex(model))) {
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
        String modelName = fileStem(model.getPath(), ".geo.json");
        boolean embeddedArphex = isEmbeddedArphex(model);
        String overrideNamespace = embeddedArphex ? "arphex" : model.getNamespace();
        ResourceLocation override = OVERRIDES.get(overrideNamespace + ":" + normalize(modelName));
        if (embeddedArphex && override != null) {
            override = ResourceLocation.fromNamespaceAndPath(
                    model.getNamespace(),
                    "textures/entities/arphex/" + fileName(override.getPath()));
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
                .max(Comparator.comparingInt(texture -> score(modelName, texture)))
                .filter(texture -> score(modelName, texture) >= 3200)
                .orElse(null);
    }

    private static Map<String, ResourceLocation> loadBundledTextures() {
        Map<String, ResourceLocation> textures = new HashMap<>();
        try (InputStream input = MobModelTextureResolver.class.getResourceAsStream(
                "/META-INF/MOBMODEL_TEXTURES.tsv")) {
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
                    textures.put(
                            line.substring(0, separator),
                            ResourceLocation.parse(line.substring(separator + 1)));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
        return Map.copyOf(textures);
    }

    private static boolean isEmbeddedArphex(ResourceLocation model) {
        return model != null
                && model.getNamespace().equals("cnpcgeckoaddon")
                && model.getPath().startsWith("geo/arphex/");
    }

    private static int score(String modelName, ResourceLocation texture) {
        String textureName = fileStem(texture.getPath(), ".png");
        String modelNormalized = normalize(modelName);
        String textureNormalized = normalize(textureName);
        String modelCanonical = canonicalModel(modelName);
        String textureCanonical = stripTrailingDigits(textureNormalized);

        int score;
        if (modelNormalized.equals(textureNormalized)) {
            score = 10000;
        } else if (modelCanonical.equals(textureCanonical)) {
            score = 9600;
        } else if (modelCanonical.length() >= 4 && textureCanonical.length() >= 4
                && (modelCanonical.contains(textureCanonical) || textureCanonical.contains(modelCanonical))) {
            int shortest = Math.min(modelCanonical.length(), textureCanonical.length());
            int longest = Math.max(modelCanonical.length(), textureCanonical.length());
            score = 8200 + (shortest * 1200 / longest);
        } else {
            int longest = Math.max(modelCanonical.length(), textureCanonical.length());
            score = longest == 0
                    ? 0
                    : (longest - levenshtein(modelCanonical, textureCanonical)) * 7000 / longest;
        }

        if (isAuxiliaryTexture(textureName)) {
            score -= 5000;
        }
        return score;
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

    private static boolean isAuxiliaryTexture(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith("_e")
                || lower.endsWith("_eyes")
                || lower.contains("_eyes_")
                || lower.endsWith("_layer")
                || lower.contains("_layer_")
                || lower.endsWith("_overlay")
                || lower.contains("_overlay_")
                || lower.endsWith("_mask")
                || lower.contains("_glow")
                || lower.contains("glowmask")
                || lower.contains("emissive")
                || lower.contains("specular")
                || lower.contains("_normal");
    }

    private static String canonicalModel(String value) {
        String normalized = normalize(value);
        if (normalized.startsWith("model")) {
            normalized = normalized.substring(5);
        }

        boolean changed;
        do {
            changed = false;
            for (String suffix : new String[]{"model", "rework", "entity", "adapted", "focused"}) {
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                    normalized = normalized.substring(0, normalized.length() - suffix.length());
                    changed = true;
                }
            }
        } while (changed);

        return stripTrailingDigits(normalized);
    }

    private static String normalize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String stripTrailingDigits(String value) {
        int end = value.length();
        while (end > 0 && Character.isDigit(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String fileStem(String path, String suffix) {
        String name = fileName(path);
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static Map.Entry<String, ResourceLocation> entry(
            String namespace,
            String modelPath,
            String texturePath) {
        return Map.entry(
                namespace + ":" + normalize(fileStem(modelPath, ".geo.json")),
                ResourceLocation.fromNamespaceAndPath(namespace, texturePath));
    }
}
