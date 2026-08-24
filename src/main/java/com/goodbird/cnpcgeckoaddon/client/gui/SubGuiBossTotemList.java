package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTotemEntry;
import com.goodbird.cnpcgeckoaddon.data.BossTotemList;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** Eight-row paged editor for the stable protection-totem slots. */
public final class SubGuiBossTotemList extends GuiBasic {
    private static final int FIRST_ROW_BUTTON = 100;
    private static final int PREV_PAGE_BUTTON = 1;
    private static final int NEXT_PAGE_BUTTON = 2;
    private static final int ADD_BUTTON = 3;
    private static final int CLEAR_BUTTON = 4;
    private static final int PAGE_LABEL = 5;
    private static final int ROWS = 8;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;
    private final BossTotemList totems;
    private int page;

    public SubGuiBossTotemList(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        this.totems = data.getTotems();
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.totem_positions_title",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 24;
        for (int row = 0; row < ROWS; row++) {
            addButton(new GuiButtonNop(this, FIRST_ROW_BUTTON + row, guiLeft + 8, y, 234, 20, ""));
            y += 22;
        }
        addButton(new GuiButtonNop(this, PREV_PAGE_BUTTON, guiLeft + 8, guiTop + 204, 20, 20, "<"));
        addLabel(new GuiLabel(PAGE_LABEL, "", guiLeft + 36, guiTop + 210, 0xA0A0A0));
        addButton(new GuiButtonNop(this, NEXT_PAGE_BUTTON, guiLeft + 222, guiTop + 204, 20, 20, ">"));
        addButton(new GuiButtonNop(this, ADD_BUTTON, guiLeft + 8, guiTop + 230, 70, 20,
                "cnpcgeckoaddon.boss.totem_add"));
        addButton(new GuiButtonNop(this, CLEAR_BUTTON, guiLeft + 82, guiTop + 230, 94, 20,
                "cnpcgeckoaddon.boss.totem_clear"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
        refreshRows();
    }

    private int pages() {
        return Math.max(1, (totems.size() + ROWS - 1) / ROWS);
    }

    private void refreshRows() {
        page = Math.min(page, pages() - 1);
        for (int row = 0; row < ROWS; row++) {
            GuiButtonNop button = getButton(FIRST_ROW_BUTTON + row);
            if (button == null) continue;
            int index = page * ROWS + row;
            boolean exists = index < totems.size();
            button.shown = exists;
            button.setEnabled(exists);
            button.setDisplayText(exists ? rowLabel(index) : "");
        }
        GuiLabel label = getLabel(PAGE_LABEL);
        if (label != null) {
            label.setMessage(Component.literal((page + 1) + " / " + pages()));
        }
        GuiButtonNop add = getButton(ADD_BUTTON);
        if (add != null) add.setEnabled(totems.size() < BossTotemList.MAX_ENTRIES);
    }

    private String rowLabel(int index) {
        BossTotemEntry entry = totems.get(index);
        String clone = entry.getCloneName().isEmpty()
                ? I18n.get("cnpcgeckoaddon.boss.totem_empty")
                : entry.getCloneTab() + ":" + entry.getCloneName();
        String coordinates;
        if (entry.getCoordinateMode() == BossTotemEntry.COORDINATE_FIXED) {
            coordinates = I18n.get("cnpcgeckoaddon.boss.totem_fixed_short") + " "
                    + entry.getX() + "/" + entry.getY() + "/" + entry.getZ();
        } else {
            coordinates = I18n.get("cnpcgeckoaddon.boss.totem_arena_short") + " "
                    + signed(entry.getX()) + "/" + signed(entry.getY()) + "/" + signed(entry.getZ());
        }
        return (index + 1) + ". " + clone + " · " + coordinates;
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_ROW_BUTTON;
        if (row >= 0 && row < ROWS) {
            int index = page * ROWS + row;
            if (index < totems.size()) {
                setSubGui(new SubGuiBossTotemEntry(npc, data, index));
            }
            return;
        }
        if (button.id == PREV_PAGE_BUTTON) {
            page = (page + pages() - 1) % pages();
            refreshRows();
        } else if (button.id == NEXT_PAGE_BUTTON) {
            page = (page + 1) % pages();
            refreshRows();
        } else if (button.id == ADD_BUTTON) {
            BossTotemEntry entry = totems.add();
            if (entry != null) {
                page = (totems.size() - 1) / ROWS;
                setSubGui(new SubGuiBossTotemEntry(npc, data, totems.size() - 1));
            }
        } else if (button.id == CLEAR_BUTTON) {
            totems.clear();
            page = 0;
            refreshRows();
        }
    }

    @Override
    public void subGuiClosed(Screen subgui) {
        super.subGuiClosed(subgui);
        refreshRows();
    }
}
