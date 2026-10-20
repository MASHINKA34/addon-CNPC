package com.goodbird.cnpcgeckoaddon.data;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.TagParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Boss presets on disk: one SNBT file per preset in a folder, holding a boss' whole
 * {@link TeleportPathData#writeToNBT} block - its phases, abilities, zones, totems, loot, tuning and
 * profiles - in the format the save itself uses, so nothing but the save decides what a preset holds.
 *
 * <p>The folder is handed in rather than looked up, so this is the same code in the game and under a
 * test. Reading never throws: a file that is missing, too big, not SNBT or not a boss comes back as a
 * refusal with the reason, and whoever asked leaves the npc as it was.</p>
 */
public final class BossPresetFiles {

    /**
     * The biggest file a preset is read from. A boss with every phase and list full is a few hundred
     * kilobytes of text; anything past this is not a preset, and is not read into memory to find out.
     */
    public static final long MAX_BYTES = 16L * 1024L * 1024L;

    /** The byte order mark an editor may put in front of the text, which SNBT does not expect. */
    private static final char BOM = '﻿';

    /** How a read went. */
    public enum Status {
        /** The file holds a boss, ready to lay over one. */
        OK,
        /** There is no preset of that name. */
        MISSING,
        /** The name is none a preset may have, so no file was looked for. */
        BAD_NAME,
        /** The file is there but cannot be a preset: too big, unreadable, not SNBT, or not a boss. */
        BAD_FILE
    }

    /**
     * What a read found: the boss block when it went well, and otherwise why not.
     *
     * @param tag     the boss block, only with {@link Status#OK}
     * @param file    the file read, or null when the name had none
     * @param bytes   how big the file is, 0 when there was none
     * @param problem why it was refused, empty with {@link Status#OK}
     */
    public record Read(Status status, CompoundTag tag, Path file, long bytes, String problem) {
    }

    /**
     * What a write did.
     *
     * @param overwritten whether a preset of that name was there before and has been replaced
     */
    public record Written(Path file, long bytes, boolean overwritten) {
    }

    private BossPresetFiles() {
    }

    /** The file a preset of this name lives in. */
    public static Path fileOf(Path folder, String name) {
        return folder.resolve(BossPresetNames.fileName(name));
    }

    /** The presets in the folder, by name in alphabetical order; none when there is no folder yet. */
    public static List<String> list(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = BossPresetNames.nameOfFile(file.getFileName().toString());
                if (name != null && Files.isRegularFile(file)) {
                    names.add(name);
                }
            }
        }
        names.sort(null);
        return names;
    }

    /** The text a preset file holds: the boss block as SNBT, one key a line, keys in order. */
    public static String toText(CompoundTag boss) {
        return new SnbtPrinterTagVisitor().visit(boss) + "\n";
    }

    /**
     * Writes a boss block as the preset {@code name}, over one of that name if there is one. The text
     * goes to a file of its own first and is moved over the old one in one step, so a write that fails
     * halfway leaves the old preset whole rather than half of the new one.
     *
     * @throws IllegalArgumentException for a name no preset may have
     * @throws IOException              when the folder or the file cannot be written
     */
    public static Written write(Path folder, String name, CompoundTag boss) throws IOException {
        if (!BossPresetNames.isValid(name)) {
            throw new IllegalArgumentException("'" + name + "' is not a preset name");
        }
        Files.createDirectories(folder);
        Path file = fileOf(folder, name);
        boolean overwritten = Files.exists(file);
        byte[] text = toText(boss).getBytes(StandardCharsets.UTF_8);
        Path partial = Files.createTempFile(folder, name + ".", ".tmp");
        try {
            Files.write(partial, text);
            try {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(partial);
        }
        return new Written(file, text.length, overwritten);
    }

    /**
     * Reads the preset {@code name} and proves it is a boss by reading it into a scratch one, before
     * anybody lays it over a real npc. Keys this build does not know are passed over and keys it misses
     * read as their defaults, the way any older or newer save does.
     */
    public static Read read(Path folder, String name) {
        if (!BossPresetNames.isValid(name)) {
            return new Read(Status.BAD_NAME, null, null, 0L, "'" + name + "' is not a preset name");
        }
        Path file = fileOf(folder, name);
        if (!Files.isRegularFile(file)) {
            return new Read(Status.MISSING, null, file, 0L, "no file " + file);
        }
        long bytes = 0L;
        try {
            bytes = Files.size(file);
            if (bytes > MAX_BYTES) {
                return new Read(Status.BAD_FILE, null, file, bytes,
                        "the file is " + bytes + " bytes, more than the " + MAX_BYTES + " a preset may be");
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == BOM) {
                text = text.substring(1);
            }
            CompoundTag boss = TagParser.parseTag(text);
            new TeleportPathData().readFromNBT(boss);
            return new Read(Status.OK, boss, file, bytes, "");
        } catch (CommandSyntaxException notSnbt) {
            return new Read(Status.BAD_FILE, null, file, bytes, "not SNBT: " + notSnbt.getMessage());
        } catch (IOException unreadable) {
            return new Read(Status.BAD_FILE, null, file, bytes,
                    "could not be read: " + unreadable.getClass().getSimpleName() + ": " + unreadable.getMessage());
        } catch (RuntimeException | StackOverflowError notABoss) {
            // A tag nested past any boss' depth overflows the parser's recursion; anything a boss
            // cannot be read from throws out of the scratch read. Neither may reach the server tick.
            return new Read(Status.BAD_FILE, null, file, bytes,
                    "not a boss: " + notABoss.getClass().getSimpleName() + ": " + notABoss.getMessage());
        }
    }
}
