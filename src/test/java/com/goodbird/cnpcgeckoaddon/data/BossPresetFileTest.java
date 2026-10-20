package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boss presets as files: a boss written out as SNBT reads back as the same boss, a file from another
 * build reads the way an older or newer save does, and a file that is not a boss is refused with a
 * reason instead of an exception - the npc it was meant for is never touched by it.
 */
class BossPresetFileTest {

    @TempDir
    Path folder;

    @Test
    @DisplayName("a boss written as a preset reads back as the same boss")
    void roundTrip() throws IOException {
        TeleportPathData boss = configuredBoss();
        CompoundTag saved = boss.writeToNBT(new CompoundTag());

        BossPresetFiles.Written written = BossPresetFiles.write(folder, "demo", saved);
        BossPresetFiles.Read read = BossPresetFiles.read(folder, "demo");

        assertFalse(written.overwritten());
        assertEquals(folder.resolve("demo.snbt"), written.file());
        assertEquals(Files.size(written.file()), written.bytes());
        assertEquals(BossPresetFiles.Status.OK, read.status(), read.problem());
        assertEquals(saved, read.tag(), "the file holds the boss block exactly");
        TeleportPathData loaded = new TeleportPathData();
        loaded.readFromNBT(read.tag());
        assertEquals(saved, loaded.writeToNBT(new CompoundTag()), "a boss read from it is the boss saved");
        assertEquals(3, loaded.getPhaseCount());
        assertEquals(33, loaded.getPhase(1).areaAttack().getDamage());
        assertEquals(5, loaded.getPhase(2).profile(BossAbilityKind.MELEE).getPriority());
    }

    @Test
    @DisplayName("the file is readable text, one key a line")
    void theFileIsText() throws IOException {
        BossPresetFiles.write(folder, "demo", configuredBoss().writeToNBT(new CompoundTag()));
        List<String> lines = Files.readAllLines(folder.resolve("demo.snbt"), StandardCharsets.UTF_8);

        assertEquals("{", lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.trim().startsWith("GeckoBossRageMultiplier: 350")), lines.toString());
    }

    @Test
    @DisplayName("writing over a preset says so, and lists name each preset once")
    void overwriteAndList() throws IOException {
        CompoundTag boss = configuredBoss().writeToNBT(new CompoundTag());
        assertEquals(List.of(), BossPresetFiles.list(folder.resolve("not yet")));

        BossPresetFiles.write(folder, "demo_2", boss);
        BossPresetFiles.write(folder, "demo", boss);
        BossPresetFiles.Written again = BossPresetFiles.write(folder, "demo", boss);
        Files.writeString(folder.resolve("notes.txt"), "not a preset");
        Files.writeString(folder.resolve("Bad Name.snbt"), "{}");

        assertTrue(again.overwritten());
        assertEquals(List.of("demo", "demo_2"), BossPresetFiles.list(folder));
        try (var files = Files.list(folder)) {
            assertFalse(files.anyMatch(file -> file.toString().endsWith(".tmp")), "no half-written file is left behind");
        }
    }

    @Test
    @DisplayName("a folder made for presets gets a note on them, once, which no list takes for a preset")
    void aNewFolderGetsItsNote() throws IOException {
        Path presets = folder.resolve("config").resolve("presets");
        CompoundTag boss = configuredBoss().writeToNBT(new CompoundTag());

        BossPresetFiles.write(presets, "demo", boss);

        Path readme = presets.resolve(BossPresetFiles.README);
        assertTrue(Files.isRegularFile(readme));
        String note = Files.readString(readme, StandardCharsets.UTF_8);
        assertTrue(note.contains("offsets from the arena") && note.contains("смещением от арены"), note);
        assertEquals(List.of("demo"), BossPresetFiles.list(presets));
        Files.delete(readme);
        BossPresetFiles.write(presets, "demo_2", boss);
        assertFalse(Files.exists(readme), "a note the builder deleted stays deleted");
    }

    @Test
    @DisplayName("a key this build does not know is passed over")
    void unknownKeysAreIgnored() throws IOException {
        TeleportPathData boss = configuredBoss();
        CompoundTag saved = boss.writeToNBT(new CompoundTag());
        BossPresetFiles.write(folder, "future", saved);
        Path file = folder.resolve("future.snbt");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, text.replaceFirst("\\{\n", "{\n    GeckoBossFromTheFuture: 5,\n"), StandardCharsets.UTF_8);

        BossPresetFiles.Read read = BossPresetFiles.read(folder, "future");

        assertEquals(BossPresetFiles.Status.OK, read.status(), read.problem());
        assertTrue(read.tag().contains("GeckoBossFromTheFuture"), "the edit really landed in the file");
        TeleportPathData loaded = new TeleportPathData();
        loaded.readFromNBT(read.tag());
        assertEquals(saved, loaded.writeToNBT(new CompoundTag()));
    }

    @Test
    @DisplayName("a key the file lacks - a preset older than the field - reads as that field's default")
    void missingKeysReadAsDefaults() throws IOException {
        BossPresetFiles.write(folder, "old", configuredBoss().writeToNBT(new CompoundTag()));
        Path file = folder.resolve("old.snbt");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String without = text.lines().filter(line -> !line.trim().startsWith("GeckoBossRageMultiplier:"))
                .collect(Collectors.joining("\n"));
        assertNotEquals(text.lines().count(), without.lines().count(), "the key was there to take out");
        Files.writeString(file, without, StandardCharsets.UTF_8);

        BossPresetFiles.Read read = BossPresetFiles.read(folder, "old");

        assertEquals(BossPresetFiles.Status.OK, read.status(), read.problem());
        TeleportPathData loaded = new TeleportPathData();
        loaded.readFromNBT(read.tag());
        assertEquals(new TeleportPathData().getRageMultiplierPercent(), loaded.getRageMultiplierPercent());
        assertEquals(3, loaded.getPhaseCount(), "the rest of the boss is still the preset's");
    }

    @Test
    @DisplayName("a broken file is refused with a reason, and nothing is thrown")
    void brokenFilesAreRefused() throws IOException {
        Files.writeString(folder.resolve("cut.snbt"), "{GeckoTeleportPathEnabled: 1b, GeckoBossPhases: [", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("prose.snbt"), "this is not a boss", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("deep.snbt"), "{a: " + "[".repeat(200_000), StandardCharsets.UTF_8);
        Files.write(folder.resolve("bytes.snbt"), new byte[]{(byte) 0xC3, (byte) 0x28, '{', '}'});

        for (String name : List.of("cut", "prose", "deep", "bytes")) {
            BossPresetFiles.Read read = BossPresetFiles.read(folder, name);
            assertEquals(BossPresetFiles.Status.BAD_FILE, read.status(), name);
            assertNull(read.tag(), name);
            assertFalse(read.problem().isEmpty(), name);
        }
    }

    @Test
    @DisplayName("a missing preset, a bad name and a byte order mark")
    void otherReads() throws IOException {
        assertEquals(BossPresetFiles.Status.MISSING, BossPresetFiles.read(folder, "nothing").status());
        assertEquals(BossPresetFiles.Status.MISSING, BossPresetFiles.read(folder.resolve("absent"), "demo").status());
        assertEquals(BossPresetFiles.Status.BAD_NAME, BossPresetFiles.read(folder, "Bad Name").status());
        assertThrows(IllegalArgumentException.class, () -> BossPresetFiles.write(folder, "../evil", new CompoundTag()));

        CompoundTag saved = configuredBoss().writeToNBT(new CompoundTag());
        Files.writeString(folder.resolve("marked.snbt"), "\uFEFF" + BossPresetFiles.toText(saved), StandardCharsets.UTF_8);
        BossPresetFiles.Read read = BossPresetFiles.read(folder, "marked");
        assertEquals(BossPresetFiles.Status.OK, read.status(), read.problem());
        assertEquals(saved, read.tag());
    }

    /** A boss with something set in most corners of it: three phases, an ability, a profile, a list, the rage. */
    private static TeleportPathData configuredBoss() {
        TeleportPathData boss = new TeleportPathData();
        boss.setEnabled(true);
        boss.markConfigured();
        boss.setPhaseCount(3);
        boss.setPhaseThreshold(1, 70);
        boss.setPhaseThreshold(2, 30);
        boss.getPhase(1).areaAttack().setEnabled(true);
        boss.getPhase(1).areaAttack().setDamage(33);
        boss.getPhase(2).profile(BossAbilityKind.MELEE).setPriority(5);
        boss.getPhase(2).profile(BossAbilityKind.MELEE).setDisplayName("Crusher");
        boss.getPhase(0).summon().getSpawnPoints().add();
        boss.getPhase(0).setComboFollowUp(BossAbilityKind.AREA, BossAbilityKind.MELEE);
        boss.setRageEnabled(true);
        boss.setRageMultiplierPercent(350);
        boss.setChestName("Spoils of \"the\" boss");
        return boss;
    }
}
