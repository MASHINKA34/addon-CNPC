package com.goodbird.cnpcgeckoaddon.mixin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the CustomNPCs jar the addon is built against to the shape {@code MixinEntityProjectile}
 * was written for.
 *
 * <p>That mixin is what stands between a projectile thrown without an item and a dead server:
 * it swaps the stack {@code EntityProjectile.onHit} makes its impact particles of, and it does
 * so by redirecting the call that reads that stack. Whether the redirect still covers every
 * particle is a fact about CustomNPCs' bytecode, and neither the compiler nor any other test
 * here looks at it - a CustomNPCs that built its particles somewhere else, or read the stack
 * another way, would compile, pass, ship, and crash on the first hit.</p>
 *
 * <p>Read out of the class files with ASM, the way {@code MixinArrayMethodCallTest} reads its
 * own: neither CustomNPCs' entity nor a mixin can be loaded in a plain JVM. The count the
 * mixin demands is read from the compiled mixin rather than repeated here, so the two cannot
 * drift apart.</p>
 */
class EntityProjectileHookTest {

    private static final Path PROJECT = Path.of(System.getProperty("cnpcgeckoaddon.projectDir", "."));
    private static final Path LIB = PROJECT.resolve("lib");
    private static final Path MIXIN_CLASS = PROJECT.resolve(Path.of("build", "classes", "java", "main",
            "com", "goodbird", "cnpcgeckoaddon", "mixin", "impl", "MixinEntityProjectile.class"));

    private static final String PROJECTILE_ENTRY = "noppes/npcs/entity/EntityProjectile.class";
    private static final String PROJECTILE = "noppes/npcs/entity/EntityProjectile";
    private static final String ON_HIT = "onHit";
    private static final String ON_HIT_DESC = "(Lnet/minecraft/world/phys/HitResult;)V";
    private static final String GET_ITEM_DISPLAY = "getItemDisplay";
    private static final String GET_ITEM_DISPLAY_DESC = "()Lnet/minecraft/world/item/ItemStack;";
    private static final String PARTICLE = "net/minecraft/core/particles/ItemParticleOption";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    /** What the mixin's {@code @Redirect} says about itself. */
    private record Hook(String method, String target, int require) {
    }

    @Test
    @DisplayName("the redirect is aimed at the call EntityProjectile.onHit really makes")
    void theRedirectNamesTheCallItIsWrittenFor() throws IOException {
        Hook hook = readHook();
        assertEquals(ON_HIT + ON_HIT_DESC, hook.method(), "the redirect is not in onHit(HitResult)");
        assertEquals("L" + PROJECTILE + ";" + GET_ITEM_DISPLAY + GET_ITEM_DISPLAY_DESC, hook.target(),
                "the redirect is not of getItemDisplay()");
        assertTrue(hook.require() > 0, "a redirect that requires nothing cannot fail when CustomNPCs changes");
    }

    @Test
    @DisplayName("every item particle CustomNPCs' projectile makes is covered by the redirect")
    void everyImpactParticleIsCovered() throws IOException {
        Hook hook = readHook();
        List<Path> jars = customNpcsJars();
        assertFalse(jars.isEmpty(), "no CustomNPCs jar to check under " + LIB);
        for (Path jar : jars) {
            ClassNode projectile = readProjectile(jar);
            MethodNode onHit = method(projectile, ON_HIT, ON_HIT_DESC);
            assertNotNull(onHit, jar.getFileName() + " has no EntityProjectile.onHit(HitResult)");
            assertNotNull(method(projectile, GET_ITEM_DISPLAY, GET_ITEM_DISPLAY_DESC),
                    jar.getFileName() + " has no EntityProjectile.getItemDisplay()");

            int built = 0;
            int builtFromTheRedirectedCall = 0;
            int reads = 0;
            for (AbstractInsnNode insn : onHit.instructions) {
                if (isParticleConstructor(insn)) {
                    built++;
                    if (isItemDisplayRead(previousReal(insn))) {
                        builtFromTheRedirectedCall++;
                    }
                }
                if (isItemDisplayRead(insn)) {
                    reads++;
                }
            }
            assertEquals(hook.require(), built, jar.getFileName() + " builds item particles in onHit "
                    + built + " times, and the mixin requires " + hook.require());
            assertEquals(built, builtFromTheRedirectedCall, jar.getFileName()
                    + " builds item particles in onHit out of something other than getItemDisplay()");
            // A read that feeds no particle would be swapped for an arrow as well.
            assertEquals(hook.require(), reads, jar.getFileName() + " reads getItemDisplay() in onHit "
                    + reads + " times, and the mixin requires " + hook.require());

            List<String> elsewhere = new ArrayList<>();
            for (MethodNode other : projectile.methods) {
                if (other == onHit) {
                    continue;
                }
                for (AbstractInsnNode insn : other.instructions) {
                    if (isParticleConstructor(insn)) {
                        elsewhere.add(other.name + other.desc);
                    }
                }
            }
            assertTrue(elsewhere.isEmpty(), jar.getFileName()
                    + " also builds item particles where the mixin does not reach: " + elsewhere);
        }
    }

    private static boolean isParticleConstructor(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                && PARTICLE.equals(call.owner) && "<init>".equals(call.name);
    }

    private static boolean isItemDisplayRead(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call && PROJECTILE.equals(call.owner)
                && GET_ITEM_DISPLAY.equals(call.name) && GET_ITEM_DISPLAY_DESC.equals(call.desc);
    }

    /** The instruction before this one, past the labels, line numbers and frames in between. */
    private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
        AbstractInsnNode previous = insn.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static List<Path> customNpcsJars() throws IOException {
        assertTrue(Files.isDirectory(LIB), "the jars the addon compiles against should be in " + LIB);
        try (Stream<Path> files = Files.list(LIB)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("CustomNPCs") && name.endsWith(".jar");
            }).sorted().toList();
        }
    }

    private static ClassNode readProjectile(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(PROJECTILE_ENTRY);
            assertNotNull(entry, jar.getFileName() + " has no " + PROJECTILE_ENTRY);
            try (InputStream in = zip.getInputStream(entry)) {
                return read(in);
            }
        }
    }

    private static Hook readHook() throws IOException {
        assertTrue(Files.exists(MIXIN_CLASS),
                "the compiled mixin should be at " + MIXIN_CLASS + "; this test runs after compileJava");
        ClassNode mixin;
        try (InputStream in = Files.newInputStream(MIXIN_CLASS)) {
            mixin = read(in);
        }
        for (MethodNode method : mixin.methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (method.visibleAnnotations != null) {
                annotations.addAll(method.visibleAnnotations);
            }
            if (method.invisibleAnnotations != null) {
                annotations.addAll(method.invisibleAnnotations);
            }
            for (AnnotationNode annotation : annotations) {
                if (REDIRECT.equals(annotation.desc)) {
                    return hookOf(annotation);
                }
            }
        }
        throw new AssertionError("MixinEntityProjectile has no @Redirect left");
    }

    private static Hook hookOf(AnnotationNode redirect) {
        Object methods = value(redirect, "method");
        Object at = value(redirect, "at");
        Object require = value(redirect, "require");
        assertTrue(methods instanceof List<?> list && list.size() == 1,
                "the redirect should name exactly one method, and names " + methods);
        assertTrue(at instanceof AnnotationNode, "the redirect has no @At");
        assertTrue(require instanceof Integer, "the redirect states no require, so it falls back to the config's 1");
        return new Hook((String) ((List<?>) methods).get(0),
                (String) value((AnnotationNode) at, "target"), (Integer) require);
    }

    /** One value of an annotation, which ASM keeps as a flat name, value, name, value list. */
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) {
                return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static ClassNode read(InputStream in) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
        return node;
    }
}
