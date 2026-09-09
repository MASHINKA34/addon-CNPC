package com.goodbird.cnpcgeckoaddon.mixin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that no mixin class calls a method on an array.
 *
 * <p>An array's only own method is {@code clone()}, and it compiles to an invocation whose
 * owner is the array descriptor - {@code [F} for a float array. While Mixin copies a mixin
 * method into its target it looks every invocation's owner up as a class, and an array
 * descriptor is not one: the lookup returns null and Mixin dies on it, failing the whole
 * apply. What that costs is not the mixin but the target - CustomNPCs could not be
 * transformed, its registration threw, and the client fell over during mod loading two mods
 * further down the log, so the crash report named somebody else entirely.</p>
 *
 * <p>One {@code float[] clone()} in the renderer mixin did exactly that. It is invisible in
 * review, compiles cleanly, passes every other test here and only ever shows up as a client
 * that will not start, which is why the rule is checked in the bytecode rather than trusted
 * to whoever writes the next mixin.</p>
 */
class MixinArrayMethodCallTest {

    private static final Path CLASSES = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("build", "classes", "java", "main",
                    "com", "goodbird", "cnpcgeckoaddon"));

    private static final Path MIXINS = CLASSES.resolve("mixin");

    /** What one class file invokes: the array descriptors, and how many calls there were at all. */
    private record Scan(List<String> arrayOwners, int methodRefs) {
    }

    @Test
    @DisplayName("no mixin calls a method on an array, which Mixin cannot apply")
    void noMixinCallsAMethodOnAnArray() throws IOException {
        assertTrue(Files.isDirectory(MIXINS),
                "the compiled mixins should be at " + MIXINS + "; this test runs after compileJava");
        Set<String> offenders = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(MIXINS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                scanned++;
                for (String owner : scan(file).arrayOwners()) {
                    offenders.add(MIXINS.relativize(file) + " calls a method on " + owner);
                }
            }
        }
        assertTrue(scanned > 0, "no compiled mixin classes were found under " + MIXINS);
        assertTrue(offenders.isEmpty(),
                "Mixin cannot apply a method that invokes anything on an array - read the values"
                        + " out by hand instead of calling clone(): " + offenders);
    }

    /**
     * The sweep above is only worth its green tick while the constant pool walk still reads
     * calls at all: a parser that quietly found nothing would pass it on every build.
     */
    @Test
    @DisplayName("the constant pool walk really does find calls")
    void theWalkFindsCallsAtAll() throws IOException {
        Path known = CLASSES.resolve(Path.of("data", "NpcCarryData.class"));
        assertTrue(Files.exists(known), "expected a compiled class to check the parser against: " + known);
        Scan scan = scan(known);
        assertTrue(scan.methodRefs() > 0,
                "the walk found no method references in " + known + ", so it is not reading the pool");
        assertTrue(scan.arrayOwners().isEmpty(), "no array calls were expected in " + known);
    }

    /** Walks one class file's constant pool for every method reference it holds. */
    private static Scan scan(Path file) throws IOException {
        Map<Integer, String> utf8 = new HashMap<>();
        Map<Integer, Integer> classNames = new HashMap<>();
        List<Integer> methodOwners = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            in.readInt();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8.put(index, in.readUTF());
                    case 7 -> classNames.put(index, in.readUnsignedShort());
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    // Methodref and InterfaceMethodref: the class index is the half worth keeping.
                    case 10, 11 -> {
                        methodOwners.add(in.readUnsignedShort());
                        in.readUnsignedShort();
                    }
                    case 3, 4, 9, 12, 17, 18 -> in.readInt();
                    // Long and Double take two constant pool slots, and the second is unused.
                    case 5, 6 -> {
                        in.readLong();
                        index++;
                    }
                    case 15 -> skip(in, 3);
                    default -> throw new IOException("unknown constant pool tag " + tag + " in " + file);
                }
            }
        }
        List<String> arrays = new ArrayList<>();
        for (int owner : methodOwners) {
            String name = utf8.get(classNames.getOrDefault(owner, -1));
            if (name != null && name.startsWith("[")) {
                arrays.add(name);
            }
        }
        return new Scan(arrays, methodOwners.size());
    }

    private static void skip(InputStream in, int bytes) throws IOException {
        if (in.readNBytes(bytes).length != bytes) {
            throw new IOException("truncated class file");
        }
    }
}
