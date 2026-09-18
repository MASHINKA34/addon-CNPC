package com.goodbird.cnpcgeckoaddon.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The addon's own compiled classes, read with ASM for the tests that hold the bytecode to a rule.
 *
 * <p>Read out of {@code build/classes} rather than loaded: a listener class names client-only or
 * CustomNPCs types a plain test cannot always load, and the rule is about what the method calls,
 * which the class file says without running anything - the way {@code MixinArrayMethodCallTest}
 * reads its own.</p>
 */
public final class CompiledClasses {

    public static final Path ROOT = Path
            .of(System.getProperty("cnpcgeckoaddon.projectDir", "."))
            .resolve(Path.of("build", "classes", "java", "main"));

    public static final String PACKAGE = "com/goodbird/cnpcgeckoaddon/";

    private CompiledClasses() {
    }

    /** Every class of the addon, nested and synthetic ones included. */
    public static List<ClassNode> all() {
        Path base = ROOT.resolve(PACKAGE);
        assertTrue(Files.isDirectory(base), "the compiled classes should be at " + base
                + "; this test runs after compileJava");
        List<ClassNode> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                classes.add(read(file));
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return classes;
    }

    /** One class by its internal name, {@code com/goodbird/cnpcgeckoaddon/ai/BossSchedulerEvents}. */
    public static ClassNode read(String internalName) {
        Path file = ROOT.resolve(internalName + ".class");
        assertTrue(Files.exists(file), "expected a compiled class at " + file);
        return read(file);
    }

    private static ClassNode read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Whether the method carries this annotation, as a descriptor: {@code Lnet/neoforged/bus/api/SubscribeEvent;}. */
    public static boolean annotated(MethodNode method, String descriptor) {
        return hasAnnotation(method.visibleAnnotations, descriptor)
                || hasAnnotation(method.invisibleAnnotations, descriptor);
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(descriptor)) {
                return true;
            }
        }
        return false;
    }

    /** Every method call the method makes, in order; invokedynamic is not one. */
    public static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call);
            }
        }
        return calls;
    }

    public static MethodNode method(ClassNode owner, String name) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        throw new AssertionError(owner.name + " has no method " + name);
    }
}
