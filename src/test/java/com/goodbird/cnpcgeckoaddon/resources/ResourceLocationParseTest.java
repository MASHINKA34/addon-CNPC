package com.goodbird.cnpcgeckoaddon.resources;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the two throwing ways of making a resource id to ids spelt out in the code.
 *
 * <p>{@code ResourceLocation.parse} and {@code ResourceLocation.fromNamespaceAndPath} throw on the
 * first character an id may not hold, and a throw on a tick, a frame or a packet is a crash. Text
 * from a script, an editor field or NBT goes through {@code ResourceIds} (or {@code tryParse})
 * instead, which hands back a fallback. What is left for the two is read here, in the sources:</p>
 * <ul>
 *     <li>{@code parse} only ever on a string literal;</li>
 *     <li>{@code fromNamespaceAndPath} with a literal - or a string constant of the same file -
 *         for its namespace and its path, or one of the {@link #COMPUTED_FROM_FIXED_NAMES} below,
 *         each with the reason its text can only ever be a legal id;</li>
 *     <li>every literal id so written is a legal one, so a typo fails here rather than the
 *         class that holds it.</li>
 * </ul>
 */
class ResourceLocationParseTest {

    private static final Path SOURCES = Path.of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("src", "main", "java"));

    /**
     * The calls whose path is worked out rather than written down, and why that is safe. Keyed by
     * file and a piece of the call's own arguments, so a new computed call is not covered by an
     * old reason.
     */
    private static final Map<String, String> COMPUTED_FROM_FIXED_NAMES = Map.of(
            "client/gui/BossTimerOverlay.java|\"textures/gui/boss_bar/\" + path",
            "the style id comes out of BossBarStyles, a fixed table, and the file names are literals",
            "client/gui/CustomBossBarOverlay.java|\"textures/gui/boss_bar/\" + path",
            "the style id comes out of BossBarStyles, a fixed table, and the file names are literals",
            "client/renderer/AnimatedLinkRenderUtil.java|\"textures/entity/hook/\" + styleId",
            "the style id comes out of HookCordStyles, a fixed table, and the file names are literals",
            "client/renderer/BossChestRenderer.java|key.getSerializedName()",
            "the name is a BossChestStyles.Skin constant's own",
            "client/renderer/RenderBossBoulder.java|\"textures/entity/boulder/\" + id",
            "the id is a BoulderStyles one, normalized against that fixed table before it gets here",
            "registry/EntityRegistry.java|CNPCGeckoAddon.MODID, name",
            "every name registerEntities passes is a literal",
            "network/NetworkWrapper.java|messageType.getSimpleName().toLowerCase(Locale.ROOT)",
            "a packet class' own name, lower-cased in the root locale");

    private static final Set<String> KNOWN_NAMESPACE_CONSTANTS = Set.of(
            "CNPCGeckoAddon.MODID", "GeckoLibConstants.MODID", "MODID");

    private static final Pattern STRING_LITERALS = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"(?:\\s*\\+\\s*\"(?:[^\"\\\\]|\\\\.)*\")*");
    private static final Pattern CONSTANT_NAME = Pattern.compile("(?:([A-Z][A-Za-z0-9]*)\\.)?([A-Z][A-Z0-9_]*)");
    private static final Pattern CONSTANT_DECLARATION = Pattern.compile(
            "static\\s+final\\s+String\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\")\\s*;");

    /** One call found in the sources. */
    private record Call(String file, int line, String method, List<String> arguments) {
        String where() {
            return file + ":" + line;
        }
    }

    @Test
    @DisplayName("ResourceLocation.parse is only ever handed a literal")
    void parseTakesOnlyLiterals() throws IOException {
        Set<String> offenders = new TreeSet<>();
        int found = 0;
        for (Call call : calls()) {
            if (!call.method().equals("parse")) {
                continue;
            }
            found++;
            String argument = call.arguments().isEmpty() ? "" : call.arguments().get(0);
            String literal = literal(argument, Map.of());
            if (literal == null) {
                offenders.add(call.where() + " parses " + argument);
            } else if (ResourceLocation.tryParse(literal) == null) {
                offenders.add(call.where() + " parses the invalid literal " + literal);
            }
        }
        assertTrue(offenders.isEmpty(), "text that is not spelt out in the code goes through ResourceIds or "
                + "ResourceLocation.tryParse, which cannot throw: " + offenders + " (" + found + " calls checked)");
    }

    @Test
    @DisplayName("fromNamespaceAndPath is written with literals, or its computed path is listed with a reason")
    void fromNamespaceAndPathIsLiteralOrListed() throws IOException {
        Set<String> offenders = new TreeSet<>();
        Set<String> usedExceptions = new TreeSet<>();
        int checked = 0;
        for (Call call : calls()) {
            if (!call.method().equals("fromNamespaceAndPath") && !call.method().equals("withDefaultNamespace")) {
                continue;
            }
            checked++;
            Map<String, String> constants = constants(call.file());
            List<String> arguments = call.arguments();
            String path = arguments.get(arguments.size() - 1);
            String namespace = arguments.size() > 1 ? arguments.get(0) : "\"minecraft\"";
            String literalPath = literal(path, constants);
            boolean namespaceKnown = literal(namespace, constants) != null
                    || KNOWN_NAMESPACE_CONSTANTS.contains(namespace.trim());
            if (literalPath != null && namespaceKnown) {
                String literalNamespace = literal(namespace, constants);
                if (!ResourceLocation.isValidPath(literalPath)
                        || literalNamespace != null && !ResourceLocation.isValidNamespace(literalNamespace)) {
                    offenders.add(call.where() + " spells out an invalid id " + namespace + ", " + path);
                }
                continue;
            }
            String exception = exceptionFor(call);
            if (exception == null) {
                offenders.add(call.where() + " builds an id from " + String.join(", ", arguments));
            } else {
                usedExceptions.add(exception);
            }
        }
        assertTrue(checked > 20, "only " + checked + " calls were found; the walk is not reading the sources");
        assertTrue(offenders.isEmpty(), "an id worked out of text that could be anything goes through "
                + "ResourceIds.pathOrDefault; one worked out of a fixed table is listed in this test with the "
                + "reason it is safe: " + offenders);
        Set<String> stale = new TreeSet<>(COMPUTED_FROM_FIXED_NAMES.keySet());
        stale.removeAll(usedExceptions);
        assertTrue(stale.isEmpty(), "these listed exceptions match no call any more; take them out: " + stale);
    }

    /** The walk itself, on a piece of code it has to get right, so a green run means something. */
    @Test
    @DisplayName("the walk finds calls across lines, skips comments and resolves constants")
    void theWalkReadsCallsTheWayTheCompilerDoes() {
        String source = """
                class Sample {
                    private static final String NAME = "boss_chest";
                    // ResourceLocation.parse(comment) is not a call
                    /* nor is ResourceLocation.fromNamespaceAndPath(a, b) */
                    ResourceLocation a = ResourceLocation.fromNamespaceAndPath(
                            CNPCGeckoAddon.MODID, "textures/" + "x.png");
                    ResourceLocation b = ResourceLocation.fromNamespaceAndPath(MODID, NAME);
                    ResourceLocation c = ResourceLocation.parse(text.trim());
                }
                """;
        List<Call> calls = callsIn("Sample.java", source);
        assertEquals(3, calls.size(), "found " + calls);
        assertEquals(List.of("CNPCGeckoAddon.MODID", "\"textures/\" + \"x.png\""), calls.get(0).arguments());
        assertEquals(5, calls.get(0).line());
        assertEquals("textures/x.png", literal(calls.get(0).arguments().get(1), Map.of()));
        assertEquals("boss_chest", literal(calls.get(1).arguments().get(1), constantsIn(source)));
        assertEquals(null, literal(calls.get(2).arguments().get(0), Map.of()));
    }

    private static String exceptionFor(Call call) {
        String joined = String.join(", ", call.arguments());
        for (String key : COMPUTED_FROM_FIXED_NAMES.keySet()) {
            int bar = key.indexOf('|');
            if (call.file().equals(key.substring(0, bar)) && joined.contains(key.substring(bar + 1))) {
                return key;
            }
        }
        return null;
    }

    /** The text a literal argument spells, joining literal concatenations; null for anything else. */
    private static String literal(String argument, Map<String, String> constants) {
        String trimmed = argument.trim();
        Matcher constant = CONSTANT_NAME.matcher(trimmed);
        if (constant.matches()) {
            // A constant of this file, or one of another class written with its class' name.
            Map<String, String> owner = constant.group(1) == null ? constants
                    : CLASS_CONSTANTS.getOrDefault(constant.group(1), Map.of());
            String value = owner.get(constant.group(2));
            if (value != null) {
                trimmed = value;
            }
        }
        if (!STRING_LITERALS.matcher(trimmed).matches()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        Matcher part = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(trimmed);
        while (part.find()) {
            text.append(part.group(1));
        }
        return text.toString();
    }

    private static final Map<String, Map<String, String>> CONSTANTS = new HashMap<>();
    /** Every class' string constants by the class' simple name, filled as the sources are walked. */
    private static final Map<String, Map<String, String>> CLASS_CONSTANTS = new HashMap<>();

    private static Map<String, String> constants(String file) throws IOException {
        Map<String, String> cached = CONSTANTS.get(file);
        if (cached == null) {
            cached = constantsIn(stripComments(Files.readString(SOURCES.resolve(
                    Path.of("com", "goodbird", "cnpcgeckoaddon")).resolve(file), StandardCharsets.UTF_8)));
            CONSTANTS.put(file, cached);
        }
        return cached;
    }

    private static Map<String, String> constantsIn(String code) {
        Map<String, String> constants = new HashMap<>();
        Matcher declaration = CONSTANT_DECLARATION.matcher(code);
        while (declaration.find()) {
            constants.put(declaration.group(1), declaration.group(2));
        }
        return constants;
    }

    private static List<Call> calls() throws IOException {
        Path base = SOURCES.resolve(Path.of("com", "goodbird", "cnpcgeckoaddon"));
        assertTrue(Files.isDirectory(base), "the sources should be at " + base);
        List<Call> calls = new ArrayList<>();
        try (Stream<Path> files = Files.walk(base)) {
            List<Path> sources = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            for (Path file : sources) {
                String name = file.getFileName().toString();
                CLASS_CONSTANTS.put(name.substring(0, name.length() - ".java".length()),
                        constantsIn(stripComments(Files.readString(file, StandardCharsets.UTF_8))));
            }
            for (Path file : sources) {
                String relative = base.relativize(file).toString().replace('\\', '/');
                // The gametests are left out of the jar, and build test ids out of test data on purpose.
                if (!relative.startsWith("gametest/")) {
                    calls.addAll(callsIn(relative, Files.readString(file, StandardCharsets.UTF_8)));
                }
            }
        }
        return calls;
    }

    private static final Pattern CALL = Pattern.compile(
            "ResourceLocation\\s*\\.\\s*(parse|fromNamespaceAndPath|withDefaultNamespace)\\s*\\(");

    static List<Call> callsIn(String file, String source) {
        String code = stripComments(source);
        List<Call> calls = new ArrayList<>();
        Matcher matcher = CALL.matcher(code);
        while (matcher.find()) {
            int open = matcher.end() - 1;
            List<String> arguments = arguments(code, open);
            int line = 1;
            for (int i = 0; i < matcher.start(); i++) {
                if (code.charAt(i) == '\n') {
                    line++;
                }
            }
            calls.add(new Call(file, line, matcher.group(1), arguments));
        }
        return calls;
    }

    /** The top-level arguments of the call whose opening parenthesis is at {@code open}, trimmed. */
    private static List<String> arguments(String code, int open) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '"') {
                i = endOfString(code, i);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    arguments.add(code.substring(start, i).replaceAll("\\s+", " ").trim());
                    return arguments;
                }
            } else if (c == ',' && depth == 1) {
                arguments.add(code.substring(start, i).replaceAll("\\s+", " ").trim());
                start = i + 1;
            }
        }
        throw new AssertionError("unbalanced call at " + open);
    }

    private static int endOfString(String code, int quote) {
        for (int i = quote + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return code.length();
    }

    /** Blanks out comments, keeping every newline so line numbers stay true, and every string as it is. */
    static String stripComments(String source) {
        StringBuilder code = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                end = end < 0 ? source.length() : end + 3;
                code.append(source, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < source.length() && source.charAt(end) != c) {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(end + 1, source.length());
                code.append(source, i, end);
                i = end;
            } else if (source.startsWith("//", i)) {
                while (i < source.length() && source.charAt(i) != '\n') {
                    code.append(' ');
                    i++;
                }
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? source.length() : end + 2;
                for (int j = i; j < end; j++) {
                    code.append(source.charAt(j) == '\n' ? '\n' : ' ');
                }
                i = end;
            } else {
                code.append(c);
                i++;
            }
        }
        return code.toString();
    }
}
