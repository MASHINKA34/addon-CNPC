package com.goodbird.cnpcgeckoaddon.world;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossPresetFiles;
import com.goodbird.cnpcgeckoaddon.data.BossPresetNames;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.FMLPaths;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Boss presets on the server: a boss' whole set of settings saved to a file under the server's
 * {@code config/cnpcgeckoaddon/presets}, and laid over any npc again - the command and the presets
 * screen both come through here, so both refuse, log and answer alike.
 *
 * <p>A preset is the npc's boss block and nothing else: its name, model, stats and the rest of
 * CustomNPCs' own settings stay the npc's. Loading replaces the whole block, the way the npc's own AI
 * page does when it is saved, and nothing is replaced until the file has been read through as a boss.</p>
 */
public final class BossPresetManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** What a request came to, with the line the presets screen shows for it. */
    public enum Outcome {
        LISTED(""),
        SAVED("cnpcgeckoaddon.boss.preset_saved"),
        OVERWRITTEN("cnpcgeckoaddon.boss.preset_overwritten"),
        LOADED("cnpcgeckoaddon.boss.preset_loaded"),
        MISSING("cnpcgeckoaddon.boss.preset_missing"),
        BAD_NAME("cnpcgeckoaddon.boss.preset_bad_name"),
        BAD_FILE("cnpcgeckoaddon.boss.preset_bad_file"),
        NO_BOSS("cnpcgeckoaddon.boss.preset_no_boss"),
        DENIED("cnpcgeckoaddon.boss.preset_denied"),
        SAVE_FAILED("cnpcgeckoaddon.boss.preset_save_failed"),
        LIST_FAILED("cnpcgeckoaddon.boss.preset_list_failed");

        private final String key;

        Outcome(String key) {
            this.key = key;
        }

        /** The translation key of the line about it; empty for a list, which has no line of its own. */
        public String key() {
            return key;
        }

        public boolean succeeded() {
            return this == LISTED || this == SAVED || this == OVERWRITTEN || this == LOADED;
        }
    }

    /**
     * What one request came to.
     *
     * @param name    the preset, as cleaned; what was typed when it could not be
     * @param npcName the npc it was saved from or loaded into, empty when there was none
     * @param file    the preset's file, or the folder for a list; null when it never got that far
     * @param bytes   how big the file written or read is
     * @param names   the presets there are, for a list
     */
    public record Result(Outcome outcome, String name, String npcName, Path file, long bytes, List<String> names) {

        /** What the outcome's line names, in the order its key takes them. */
        public List<String> args() {
            return switch (outcome) {
                case SAVED, OVERWRITTEN, MISSING, BAD_FILE, SAVE_FAILED -> List.of(name);
                case LOADED -> List.of(name, npcName);
                default -> List.of();
            };
        }
    }

    private BossPresetManager() {
    }

    /** The folder this server keeps its presets in. */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(CNPCGeckoAddon.MODID).resolve("presets");
    }

    /** A request refused before anything was read or written, for {@code who}. */
    public static Result denied(String who, String what) {
        LOGGER.warn("Boss preset {} refused for {}: no permission (operator level 2 needed)", what, who);
        return new Result(Outcome.DENIED, "", "", null, 0L, List.of());
    }

    /** The presets there are now, for a list on screen or in chat. */
    public static Result list(String who) {
        Path folder = folder();
        try {
            List<String> names = BossPresetFiles.list(folder);
            LOGGER.info("Boss presets listed for {}: {} in {}", who, names.size(), folder);
            return new Result(Outcome.LISTED, "", "", folder, 0L, names);
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Boss presets could not be listed for {} in {}", who, folder, error);
            return new Result(Outcome.LIST_FAILED, "", "", folder, 0L, List.of());
        }
    }

    /** The names alone, for the command's suggestions: quiet, as it runs on every key typed. */
    public static List<String> names() {
        try {
            return BossPresetFiles.list(folder());
        } catch (IOException | RuntimeException error) {
            LOGGER.debug("Boss preset names could not be listed for a suggestion in {}: {}", folder(), error.toString());
            return List.of();
        }
    }

    /**
     * Saves {@code npc}'s boss block as the preset {@code typed} names, over one of that name.
     *
     * @param who whoever asked, for the log
     */
    public static Result save(EntityNPCInterface npc, String typed, String who) {
        String name = BossPresetNames.clean(typed);
        if (name.isEmpty()) {
            LOGGER.warn("Boss preset save refused for {}: '{}' leaves no name once cleaned (a-z, 0-9, _ and -, up to {})",
                    who, typed, BossPresetNames.MAX_LENGTH);
            return new Result(Outcome.BAD_NAME, typed, npcName(npc), null, 0L, List.of());
        }
        if (npc == null) {
            LOGGER.warn("Boss preset save of '{}' refused for {}: no npc to save it from", name, who);
            return new Result(Outcome.NO_BOSS, name, "", null, 0L, List.of());
        }
        TeleportPathData boss = bossOf(npc);
        CompoundTag block = boss.writeToNBT(new CompoundTag());
        if (block.isEmpty()) {
            // An npc that was never made a boss writes no block at all: there is nothing to keep.
            LOGGER.warn("Boss preset save of '{}' refused for {}: {} has never been set up as a boss", name, who,
                    describe(npc));
            return new Result(Outcome.NO_BOSS, name, npcName(npc), null, 0L, List.of());
        }
        Path folder = folder();
        try {
            BossPresetFiles.Written written = BossPresetFiles.write(folder, name, block);
            LOGGER.info("Boss preset '{}' {} by {} from {}: {} ({} bytes, {} phases, boss {})", name,
                    written.overwritten() ? "overwritten" : "saved", who, describe(npc), written.file(), written.bytes(),
                    boss.getPhaseCount(), boss.isEnabled() ? "on" : "off");
            return new Result(written.overwritten() ? Outcome.OVERWRITTEN : Outcome.SAVED, name, npcName(npc),
                    written.file(), written.bytes(), List.of());
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Boss preset '{}' could not be saved by {} from {} to {}", name, who, describe(npc),
                    BossPresetFiles.fileOf(folder, name), error);
            return new Result(Outcome.SAVE_FAILED, name, npcName(npc), BossPresetFiles.fileOf(folder, name), 0L,
                    List.of());
        }
    }

    /**
     * Lays the preset {@code typed} names over {@code npc}'s whole boss block. Nothing of the npc is
     * touched unless the file reads through as a boss; then the npc rebuilds its goals and is sent out
     * again, as when its own AI page is saved.
     *
     * @param who whoever asked, for the log
     */
    public static Result load(EntityNPCInterface npc, String typed, String who) {
        String name = BossPresetNames.clean(typed);
        if (name.isEmpty()) {
            LOGGER.warn("Boss preset load refused for {}: '{}' leaves no name once cleaned (a-z, 0-9, _ and -, up to {})",
                    who, typed, BossPresetNames.MAX_LENGTH);
            return new Result(Outcome.BAD_NAME, typed, npcName(npc), null, 0L, List.of());
        }
        if (npc == null) {
            LOGGER.warn("Boss preset load of '{}' refused for {}: no npc to load it into", name, who);
            return new Result(Outcome.NO_BOSS, name, "", null, 0L, List.of());
        }
        BossPresetFiles.Read read = BossPresetFiles.read(folder(), name);
        switch (read.status()) {
            case MISSING -> {
                LOGGER.warn("Boss preset load of '{}' into {} refused for {}: {}", name, describe(npc), who,
                        read.problem());
                return new Result(Outcome.MISSING, name, npcName(npc), read.file(), 0L, List.of());
            }
            case BAD_NAME -> {
                LOGGER.warn("Boss preset load of '{}' refused for {}: {}", name, who, read.problem());
                return new Result(Outcome.BAD_NAME, name, npcName(npc), null, 0L, List.of());
            }
            case BAD_FILE -> {
                LOGGER.warn("Boss preset '{}' not loaded into {} for {}: {} ({} bytes) is no preset - {}", name,
                        describe(npc), who, read.file(), read.bytes(), read.problem());
                return new Result(Outcome.BAD_FILE, name, npcName(npc), read.file(), read.bytes(), List.of());
            }
            case OK -> {
            }
        }
        TeleportPathData boss = bossOf(npc);
        CompoundTag before = boss.writeToNBT(new CompoundTag());
        try {
            boss.readFromNBT(read.tag());
        } catch (RuntimeException error) {
            // The file read through as a boss once already; should the npc's own read still fail, it
            // gets back exactly what it had.
            boss.readFromNBT(before);
            LOGGER.warn("Boss preset '{}' could not be laid over {} for {} - its own settings were put back", name,
                    describe(npc), who, error);
            return new Result(Outcome.BAD_FILE, name, npcName(npc), read.file(), read.bytes(), List.of());
        }
        npc.updateAI = true;
        npc.updateClient = true;
        LOGGER.info("Boss preset '{}' loaded by {} into {}: {} ({} bytes, {} phases, boss {})", name, who,
                describe(npc), read.file(), read.bytes(), boss.getPhaseCount(), boss.isEnabled() ? "on" : "off");
        return new Result(Outcome.LOADED, name, npcName(npc), read.file(), read.bytes(), List.of());
    }

    private static TeleportPathData bossOf(EntityNPCInterface npc) {
        return ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
    }

    private static String npcName(EntityNPCInterface npc) {
        return npc == null ? "" : npc.getName().getString();
    }

    /** "Name (uuid) at dimension x y z", for the log. */
    private static String describe(EntityNPCInterface npc) {
        return npc.getName().getString() + " (" + npc.getUUID() + ") at " + npc.level().dimension().location() + " "
                + npc.blockPosition().getX() + " " + npc.blockPosition().getY() + " " + npc.blockPosition().getZ();
    }
}
