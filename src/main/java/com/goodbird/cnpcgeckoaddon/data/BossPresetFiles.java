package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.TagParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /** The note a new presets folder is made with, for whoever opens the folder on the server. */
    public static final String README = "README.txt";

    private static final String README_TEXT = String.join("\n",
            "Boss presets - CNPC Geckolib addon",
            "",
            "Each <name>.snbt file here is one boss: every boss setting of an npc - its phases, abilities,",
            "zones, totems, loot, fine-tuning and profiles - as SNBT text, the way the npc's own save holds them.",
            "Save one with /cnpcgecko preset save <name> or Presets... in the boss menu; load it with",
            "/cnpcgecko preset load <name> or the same screen. Loading replaces every boss setting of the npc,",
            "while its name, model and stats stay its own. Names are a-z, 0-9, _ and -, up to 32 characters.",
            "Delete or rename presets here, by hand: the game never does.",
            "Zones and points set to fixed coordinates are copied as they are - for a preset that fits any",
            "arena, set them as offsets from the arena.",
            "Loading takes the boss out of its health link group, so two bosses from one preset do not share",
            "their health; turn on Keep health link on the Presets... screen, or use",
            "/cnpcgecko preset load keepLink <name>, to keep the group.",
            "A preset saved by an older or newer version of the addon still loads: a setting it lacks takes",
            "its default, and one this version does not know is passed over.",
            "",
            "Пресеты боссов - аддон CNPC Geckolib",
            "",
            "Каждый файл <имя>.snbt здесь - один босс: все настройки босса у НПС - фазы, способности, зоны,",
            "тотемы, лут, тонкая настройка и профили - текстом SNBT, как их хранит сохранение самого НПС.",
            "Сохранить: /cnpcgecko preset save <имя> или «Пресеты…» в меню босса; загрузить:",
            "/cnpcgecko preset load <имя> или тот же экран. Загрузка заменяет все настройки босса у НПС,",
            "а имя, модель и статы остаются его собственными. Имя - a-z, 0-9, _ и -, до 32 символов.",
            "Удалять и переименовывать пресеты - здесь, руками: игра этого не делает.",
            "Зоны и точки с фиксированными координатами копируются как есть - для пресета, который",
            "подходит к любой арене, задавайте их смещением от арены.",
            "Загрузка снимает босса с группы связки здоровья, чтобы два босса из одного пресета не делили",
            "здоровье; чтобы оставить группу - «Оставить связку здоровья» в окне «Пресеты…» или",
            "/cnpcgecko preset load keepLink <имя>.",
            "Пресет от старой или новой версии аддона загружается: чего в нём нет - берётся по умолчанию,",
            "незнакомое этой версии - пропускается.",
            "");

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** The byte order mark an editor may put in front of the text, which SNBT does not expect. */
    private static final char BOM = '\uFEFF';

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
        boolean newFolder = !Files.isDirectory(folder);
        Files.createDirectories(folder);
        if (newFolder) {
            writeReadme(folder);
        }
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
     * Puts the note on presets into a folder just made for them. Only then: a builder who deletes it
     * does not get it back with every save. A note that cannot be written costs the note, not the save.
     */
    private static void writeReadme(Path folder) {
        Path readme = folder.resolve(README);
        try {
            Files.writeString(readme, README_TEXT, StandardCharsets.UTF_8);
            LOGGER.info("Boss presets folder {} made, with a note on presets in {}", folder, readme.getFileName());
        } catch (IOException error) {
            LOGGER.warn("Boss presets folder {} made, but its note {} could not be written", folder, readme, error);
        }
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
