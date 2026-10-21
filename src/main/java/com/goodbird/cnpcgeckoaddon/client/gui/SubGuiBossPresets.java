package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossPresetNames;
import com.goodbird.cnpcgeckoaddon.network.BossPresetClientBridge;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketBossPresetReply;
import com.goodbird.cnpcgeckoaddon.network.PacketBossPresetRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The boss' presets, which live as files on the server: a name and Save to write this boss to one,
 * the presets there are to pick one from, and Load - after a question - to lay it over this boss.
 *
 * <p>Before either, the whole npc editor is sent to the server the way closing it sends it, so a save
 * holds what the screens show and a load starts from every other change made in the editor. A load
 * that went through closes the editor without that save - the server's copy is the preset now, and
 * the screens still hold the old boss - and says so in the chat; the menu opened again shows the
 * preset. Files are only ever made here, never deleted: the hint says where they are.</p>
 *
 * <p>A load takes the boss out of the health link group the preset carries unless Keep health link
 * says otherwise, and the chat says which it was. The switch is the screen's own, starting at No each
 * time the screen is opened.</p>
 */
public final class SubGuiBossPresets extends SubGuiFieldScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private static final int NAME_FIELD = 1;
    private static final int SAVE_BUTTON = 2;
    private static final int LOAD_BUTTON = 3;
    private static final int PREV_PAGE_BUTTON = 4;
    private static final int NEXT_PAGE_BUTTON = 5;
    private static final int KEEP_LINK_BUTTON = 6;
    private static final int FIRST_ROW_BUTTON = 100;

    private static final int TITLE_LABEL = 30;
    private static final int NAME_LABEL = 31;
    private static final int EMPTY_LABEL = 32;
    private static final int PAGE_LABEL = 33;
    private static final int FIRST_STATUS_LABEL = 34;
    private static final int KEEP_LINK_LABEL = 36;
    private static final int FIRST_HINT_LABEL = 40;

    private static final String HINT = "cnpcgeckoaddon.boss.preset_hint";

    private static final int ROWS = 6;
    private static final int ROW_PITCH = 22;
    private static final int STATUS_LINES = 2;
    private static final int ROWS_Y = 64;
    private static final int PAGER_Y = ROWS_Y + ROWS * ROW_PITCH;
    private static final int BUTTONS_Y = PAGER_Y + 24;
    private static final int KEEP_LINK_Y = BUTTONS_Y + 24;
    private static final int STATUS_Y = KEEP_LINK_Y + 26;
    private static final int HINT_Y = STATUS_Y + STATUS_LINES * LINE_HEIGHT + 4;

    private static final int STATUS_GOOD = 0x80E080;
    private static final int STATUS_BAD = 0xFF8080;

    /** The presets screen on view, which an answer's list and line go to; null once none is. */
    private static SubGuiBossPresets shown;

    static {
        BossPresetClientBridge.setHandler(SubGuiBossPresets::onReply);
    }

    private final EntityNPCInterface npc;
    /** What the name field holds, kept across the layouts a question or a resize puts the screen through. */
    private String typedName = "";
    private List<String> names = List.of();
    /** Whether the server has said what presets there are yet. */
    private boolean listed;
    private String selected = "";
    private int page;
    private String status = "";
    private boolean statusGood = true;
    /** Whether a load leaves the boss in the preset's health link group; kept across layouts, not across openings. */
    private boolean keepLink;

    public SubGuiBossPresets(EntityNPCInterface npc) {
        this.npc = npc;
        imageWidth = 256;
        closeOnEsc = true;
        shown = this;
        send(PacketBossPresetRequest.LIST, "", false);
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: the hint wraps to as many lines as the locale needs.
        imageHeight = HINT_Y + wrappedHintHeight(HINT) + 8;
        super.init();
        shown = this;
        addLabel(new ThemeLabel(TITLE_LABEL, "cnpcgeckoaddon.boss.presets_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        addLabel(new ThemeLabel(NAME_LABEL, "cnpcgeckoaddon.boss.preset_name", guiLeft + 8, guiTop + 26));
        GuiTextFieldNop field = new ThemeTextField(NAME_FIELD, this, guiLeft + 8, guiTop + 38, 160, 20, typedName);
        field.setMaxLength(PacketBossPresetRequest.MAX_NAME_LENGTH);
        addTextField(field);
        addButton(new ThemeButton(this, SAVE_BUTTON, guiLeft + 172, guiTop + 38, 70, 20,
                "cnpcgeckoaddon.boss.preset_save"));

        addLabel(new ThemeLabel(EMPTY_LABEL, "cnpcgeckoaddon.boss.preset_none", guiLeft + 8, guiTop + ROWS_Y + 6,
                0xA0A0A0));
        for (int row = 0; row < ROWS; row++) {
            addButton(new ThemeButton(this, FIRST_ROW_BUTTON + row, guiLeft + 8, guiTop + ROWS_Y + row * ROW_PITCH,
                    234, 20, ""));
        }
        addButton(new ThemeButton(this, PREV_PAGE_BUTTON, guiLeft + 8, guiTop + PAGER_Y, 20, 20, "<"));
        addLabel(new ThemeLabel(PAGE_LABEL, "", guiLeft + 36, guiTop + PAGER_Y + 6, 0xA0A0A0));
        addButton(new ThemeButton(this, NEXT_PAGE_BUTTON, guiLeft + 222, guiTop + PAGER_Y, 20, 20, ">"));

        addButton(new ThemeButton(this, LOAD_BUTTON, guiLeft + 8, guiTop + BUTTONS_Y, 114, 20,
                "cnpcgeckoaddon.boss.preset_load"));
        addDoneButton(guiLeft + 182, guiTop + BUTTONS_Y, 60, 20);
        addLabel(new ThemeLabel(KEEP_LINK_LABEL, "cnpcgeckoaddon.boss.preset_keep_link", guiLeft + 8,
                guiTop + KEEP_LINK_Y + 6));
        addButton(new ThemeYesNo(this, KEEP_LINK_BUTTON, guiLeft + 155, guiTop + KEEP_LINK_Y, 87, 20, keepLink));
        for (int line = 0; line < STATUS_LINES; line++) {
            addLabel(new ThemeLabel(FIRST_STATUS_LABEL + line, Component.empty(), STATUS_GOOD, guiLeft + 8,
                    guiTop + STATUS_Y + line * LINE_HEIGHT, imageWidth - 16, LINE_HEIGHT));
        }
        addWrappedHint(FIRST_HINT_LABEL, HINT, guiTop + HINT_Y);
        refresh();
    }

    /** Puts the list, the pages, the Load button and the status line in step with what is known. */
    private void refresh() {
        int pages = Math.max(1, (names.size() + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        for (int row = 0; row < ROWS; row++) {
            GuiButtonNop button = getButton(FIRST_ROW_BUTTON + row);
            if (button == null) {
                continue;
            }
            int index = page * ROWS + row;
            boolean exists = index < names.size();
            button.shown = exists;
            button.setEnabled(exists);
            button.setDisplayText(exists ? rowText(names.get(index)) : "");
        }
        GuiLabel empty = getLabel(EMPTY_LABEL);
        if (empty != null) {
            empty.visible = listed && names.isEmpty();
        }
        GuiLabel pageLabel = getLabel(PAGE_LABEL);
        if (pageLabel != null) {
            pageLabel.setMessage(Component.literal((page + 1) + " / " + pages));
        }
        setEnabled(PREV_PAGE_BUTTON, pages > 1);
        setEnabled(NEXT_PAGE_BUTTON, pages > 1);
        setEnabled(LOAD_BUTTON, !selected.isEmpty());
        List<String> lines = status.isEmpty() ? List.of() : wrapLines(status, imageWidth - 16);
        for (int line = 0; line < STATUS_LINES; line++) {
            if (getLabel(FIRST_STATUS_LABEL + line) instanceof ThemeLabel label) {
                label.setMessage(Component.literal(line < lines.size() ? lines.get(line) : ""));
                label.setColor(statusGood ? STATUS_GOOD : STATUS_BAD);
            }
        }
    }

    /** A preset's row: its name, marked while it is the one Load would lay over the boss. */
    private String rowText(String name) {
        return name.equals(selected) ? "» " + name + " «" : name;
    }

    private void setEnabled(int id, boolean enabled) {
        GuiButtonNop button = getButton(id);
        if (button != null) {
            button.setEnabled(enabled);
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_ROW_BUTTON;
        if (row >= 0 && row < ROWS) {
            int index = page * ROWS + row;
            if (index < names.size()) {
                selected = names.get(index);
                LOGGER.debug("presets: '{}' picked to load", selected);
                refresh();
            }
            return;
        }
        int pages = Math.max(1, (names.size() + ROWS - 1) / ROWS);
        if (button.id == PREV_PAGE_BUTTON) {
            page = (page + pages - 1) % pages;
            refresh();
        } else if (button.id == NEXT_PAGE_BUTTON) {
            page = (page + 1) % pages;
            refresh();
        } else if (button.id == SAVE_BUTTON) {
            savePreset();
        } else if (button.id == LOAD_BUTTON) {
            askToLoad();
        } else if (button.id == KEEP_LINK_BUTTON && button instanceof GuiButtonYesNo toggle) {
            keepLink = toggle.getBoolean();
            LOGGER.debug("presets: a load {} the preset's health link group", keepLink ? "keeps" : "drops");
        }
    }

    private void savePreset() {
        applyFields();
        if (BossPresetNames.clean(typedName).isEmpty()) {
            LOGGER.info("presets: '{}' leaves no name once cleaned, nothing asked of the server", typedName);
            showStatus(Component.translatable("cnpcgeckoaddon.boss.preset_bad_name").getString(), false);
            return;
        }
        saveEditor();
        send(PacketBossPresetRequest.SAVE, typedName, false);
    }

    private void askToLoad() {
        applyFields();
        if (selected.isEmpty()) {
            return;
        }
        String preset = selected;
        boolean keep = keepLink;
        String question = Component.translatable("cnpcgeckoaddon.boss.preset_load_confirm",
                npcName(), preset).getString();
        setSubGui(new SubGuiConfirm(question, () -> {
            saveEditor();
            send(PacketBossPresetRequest.LOAD, preset, keep);
        }));
    }

    /**
     * Sends the whole npc editor to the server the way closing it would - CustomNPCs' own save of the
     * page under these screens - so the server acts on what the screens show.
     */
    private void saveEditor() {
        Screen root = getParent();
        if (root instanceof GuiNPCInterface2 page && root != this) {
            page.save();
            LOGGER.info("presets: the editor of {} sent to the server as closing it would, before the request",
                    npcName());
        } else {
            LOGGER.warn("presets: no npc page under this screen ({}), the server acts on what it last had",
                    root == null ? "none" : root.getClass().getSimpleName());
        }
    }

    /** The npc's name as the question and the log name it; a screen opened without one says so. */
    private String npcName() {
        return npc == null ? "?" : npc.getName().getString();
    }

    private void showStatus(String line, boolean good) {
        status = line;
        statusGood = good;
        refresh();
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop field = getTextField(NAME_FIELD);
        if (field != null) {
            typedName = field.getValue();
        }
    }

    @Override
    public void close() {
        if (shown == this) {
            shown = null;
        }
        super.close();
    }

    private static void send(int action, String name, boolean keepLink) {
        LOGGER.info("presets: asking the server for action {} with '{}'{}", action, name,
                action == PacketBossPresetRequest.LOAD ? ", keep health link " + keepLink : "");
        NetworkWrapper.sendToServer(new PacketBossPresetRequest(action, name, keepLink));
    }

    /** The server's answer: a load that went closes the editor; anything else lands on the screen, or in the chat. */
    private static void onReply(PacketBossPresetReply reply) {
        Minecraft minecraft = Minecraft.getInstance();
        Component line = reply.key().isEmpty() ? null : Component.translatable(reply.key(), reply.args().toArray());
        Component linkLine = reply.linkKey().isEmpty() ? null
                : Component.translatable(reply.linkKey(), reply.linkArgs().toArray());
        LOGGER.info("presets: the server answered action {} with {}{}{}, {} presets listed", reply.action(),
                reply.success() ? "success" : "a refusal", line == null ? "" : " - " + line.getString(),
                linkLine == null ? "" : " / " + linkLine.getString(), reply.listed() ? reply.names().size() : "no");
        if (reply.action() == PacketBossPresetRequest.LOAD && reply.success()) {
            closeEditorOf(minecraft, reply.npcId());
            shown = null;
            tell(minecraft, line);
            // The group the preset carried, dropped or kept, right under the line about the load.
            tell(minecraft, linkLine);
            return;
        }
        SubGuiBossPresets screen = shown;
        if (screen != null && isOnScreen(minecraft, screen)) {
            screen.accept(reply, line);
        } else {
            tell(minecraft, line);
        }
    }

    private void accept(PacketBossPresetReply reply, Component line) {
        if (reply.listed()) {
            names = new ArrayList<>(reply.names());
            listed = true;
            if (!names.contains(selected)) {
                selected = "";
            }
        }
        if (line != null) {
            status = line.getString();
            statusGood = reply.success();
        }
        refresh();
    }

    /**
     * Closes the npc's editor without the save closing it sends: the server's copy is the preset now,
     * and the screens would send the old boss back over it.
     */
    private static void closeEditorOf(Minecraft minecraft, int npcId) {
        if (minecraft.screen instanceof GuiNPCInterface page && page.npc != null && page.npc.getId() == npcId) {
            GuiTextFieldNop.unfocus();
            minecraft.setScreen(null);
            LOGGER.info("presets: the editor of npc {} closed without saving, a preset was loaded into it", npcId);
        } else {
            LOGGER.info("presets: no editor of npc {} open to close after the load", npcId);
        }
    }

    private static void tell(Minecraft minecraft, Component line) {
        if (line != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(line, false);
        }
    }

    /** Whether the screen is somewhere in the stack on view, under whatever was opened over it. */
    private static boolean isOnScreen(Minecraft minecraft, Screen screen) {
        Screen at = minecraft.screen;
        for (int depth = 0; at != null && depth < 32; depth++) {
            if (at == screen) {
                return true;
            }
            at = at instanceof GuiBasic basic ? basic.getSubGui() : null;
        }
        return false;
    }
}
