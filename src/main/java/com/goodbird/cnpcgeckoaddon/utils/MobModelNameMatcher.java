package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

/**
 * Decides which of a mod's texture files belongs to one of its models, by name alone.
 *
 * <p>The bundles carry hundreds of models and hundreds of textures with no mapping between
 * them beyond what their authors called the files, so a model with no recorded default is
 * matched against every texture in its namespace and the closest name wins. Everything here
 * is a pure function of two strings, which is what makes the scoring testable at all -
 * {@link MobModelTextureResolver} itself cannot be, because it has to walk the live resource
 * packs to know what exists.</p>
 */
public final class MobModelNameMatcher {

    /**
     * Below this the closest name is still not close enough, and the model counts as having no
     * sheet. A name that is not even contained in the other scores by edit distance alone, and
     * those need three letters in four in place: below that the bundles only ever paired a
     * model with a stranger's sheet (a blood eagle with the bloodworm, a flamethrower with the
     * fly festerer), which looks worse than the missing-texture sheet and hides that one is due.
     */
    public static final int MATCH_THRESHOLD = 5200;

    /** Words a sheet may carry before or after its model's name and still be that model's own. */
    private static final String[] SHEET_WORDS = {"texture", "skin"};

    /** A name shorter than this is too common a string to match on once its number is dropped. */
    private static final int MIN_STRIPPED_NAME = 3;

    private static final Comparator<Named> CLOSEST_FIRST = Comparator
            .comparingInt(Named::tier)
            .thenComparingLong(Named::number)
            .thenComparingInt(Named::placement)
            .thenComparing(Named::path);

    private MobModelNameMatcher() {
    }

    /**
     * The png among {@code candidates} named after {@code model}, or null.
     *
     * <p>Stricter than {@link #score}: the file has to carry the model's whole name - exactly;
     * with "texture" or "skin" before or after it; with a number after it, the lowest number
     * first; with the model's own trailing number dropped; or with the "model" and "entity"
     * words exporters wrap names in taken off. A render layer never counts, and neither does a
     * png of another namespace, whatever it is called. Of equally close names, the one in the
     * folder the geometry sits in wins (a gun's sheet under {@code textures/item} for
     * {@code geo/item/gun}), then one in an entity folder, then the first in path order.</p>
     */
    public static ResourceLocation findNamedTexture(ResourceLocation model, Collection<ResourceLocation> candidates) {
        if (model == null || candidates == null) {
            return null;
        }
        String modelName = fileStem(model.getPath(), ".geo.json");
        String modelFolder = modelFolder(model.getPath());
        Named best = null;
        for (ResourceLocation texture : candidates) {
            if (texture == null || !texture.getNamespace().equals(model.getNamespace())
                    || !texture.getPath().endsWith(".png")) {
                continue;
            }
            String textureName = fileStem(texture.getPath(), ".png");
            if (isAuxiliaryTexture(textureName)) {
                continue;
            }
            Named named = named(modelName, textureName, texture, modelFolder);
            if (named != null && (best == null || CLOSEST_FIRST.compare(named, best) < 0)) {
                best = named;
            }
        }
        return best == null ? null : best.texture();
    }

    /** How closely one png carries a model's name; null when it does not carry it at all. */
    private record Named(ResourceLocation texture, int tier, long number, int placement) {
        String path() {
            return texture.getPath();
        }
    }

    private static Named named(String modelName, String textureName, ResourceLocation texture, String modelFolder) {
        String name = normalize(modelName);
        String stem = normalize(textureName);
        if (name.isEmpty() || stem.isEmpty()) {
            return null;
        }
        int placement = placement(texture.getPath(), modelFolder);
        if (stem.equals(name)) {
            return new Named(texture, 0, 0L, placement);
        }
        if (carriesSheetWord(stem, name)) {
            return new Named(texture, 1, 0L, placement);
        }
        String stemBase = stripTrailingDigits(stem);
        long stemNumber = number(stem.substring(stemBase.length()));
        if (!stemBase.equals(stem) && (stemBase.equals(name) || carriesSheetWord(stemBase, name))) {
            return new Named(texture, 2, stemNumber, placement);
        }
        String nameBase = stripTrailingDigits(name);
        if (nameBase.length() >= MIN_STRIPPED_NAME && !nameBase.equals(name)
                && (stem.equals(nameBase) || stemBase.equals(nameBase))) {
            return new Named(texture, 3, stemNumber, placement);
        }
        String canonical = canonicalModel(modelName);
        if (canonical.length() >= MIN_STRIPPED_NAME && !canonical.equals(name) && !canonical.equals(nameBase)
                && (stem.equals(canonical) || stemBase.equals(canonical))) {
            return new Named(texture, 4, stemNumber, placement);
        }
        return null;
    }

    private static boolean carriesSheetWord(String stem, String name) {
        for (String word : SHEET_WORDS) {
            if (stem.equals(name + word) || stem.equals(word + name)) {
                return true;
            }
        }
        return false;
    }

    /** The trailing digits of a name as a number; none reads as zero, too many as the largest. */
    private static long number(String digits) {
        if (digits.isEmpty()) {
            return 0L;
        }
        if (digits.length() > 18) {
            return Long.MAX_VALUE;
        }
        return Long.parseLong(digits);
    }

    /** 0 in the geometry's own folder, 1 in an entity folder, 2 anywhere else under textures. */
    private static int placement(String texturePath, String modelFolder) {
        String relative = texturePath.startsWith("textures/")
                ? texturePath.substring("textures/".length()) : texturePath;
        int slash = relative.lastIndexOf('/');
        String folder = slash < 0 ? "" : relative.substring(0, slash);
        if (!modelFolder.isEmpty() && (folder.equals(modelFolder) || folder.startsWith(modelFolder + "/"))) {
            return 0;
        }
        if (folder.equals("entity") || folder.startsWith("entity/")
                || folder.equals("entities") || folder.startsWith("entities/")) {
            return 1;
        }
        return 2;
    }

    /** The folder a geometry sits in under {@code geo/}, empty for one directly in it. */
    private static String modelFolder(String modelPath) {
        String relative = modelPath.startsWith("geo/") ? modelPath.substring("geo/".length()) : modelPath;
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? "" : relative.substring(0, slash);
    }

    public static int score(String modelName, ResourceLocation texture) {
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

    public static boolean isAuxiliaryTexture(String name) {
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

    static String canonicalModel(String value) {
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

    public static String normalize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }

    static String stripTrailingDigits(String value) {
        int end = value.length();
        while (end > 0 && Character.isDigit(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    public static String fileStem(String path, String suffix) {
        String name = fileName(path);
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    public static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    static int levenshtein(String left, String right) {
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
    }}
