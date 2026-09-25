package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import com.goodbird.cnpcgeckoaddon.data.BossVentZoneList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** Eight-row paged editor for one phase's vents, laid out like the platforms' list. */
public final class SubGuiBossVentZoneList extends SubGuiFieldScreen {
    private static final int FIRST_ROW_BUTTON = 100;
    private static final int PREV_PAGE_BUTTON = 1;
    private static final int NEXT_PAGE_BUTTON = 2;
    private static final int ADD_BUTTON = 3;
    private static final int CLEAR_BUTTON = 4;
    private static final int PAGE_LABEL = 5;
    private static final int EMPTY_LABEL = 6;
    private static final int TITLE_LABEL = 30;
    private static final int ROWS = 8;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final BossVentZoneList zones;
    private int page;

    public SubGuiBossVentZoneList(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.zones = phase.vent().getZones();
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.vent_zones_title", phaseIndex),
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        addLabel(new GuiLabel(EMPTY_LABEL, "cnpcgeckoaddon.boss.vent_zone_empty",
                guiLeft + 8, guiTop + 30, 0xA0A0A0));
        int y = guiTop + 24;
        for (int row = 0; row < ROWS; row++) {
            addButton(new GuiButtonNop(this, FIRST_ROW_BUTTON + row, guiLeft + 8, y, 234, 20, ""));
            y += 22;
        }
        addButton(new GuiButtonNop(this, PREV_PAGE_BUTTON, guiLeft + 8, guiTop + 204, 20, 20, "<"));
        addLabel(new GuiLabel(PAGE_LABEL, "", guiLeft + 36, guiTop + 210, 0xA0A0A0));
        addButton(new GuiButtonNop(this, NEXT_PAGE_BUTTON, guiLeft + 222, guiTop + 204, 20, 20, ">"));
        addButton(new GuiButtonNop(this, ADD_BUTTON, guiLeft + 8, guiTop + 230, 70, 20,
                "cnpcgeckoaddon.boss.minion_spawn_add"));
        addButton(new GuiButtonNop(this, CLEAR_BUTTON, guiLeft + 82, guiTop + 230, 94, 20,
                "cnpcgeckoaddon.boss.minion_spawn_clear"));
        addDoneButton(guiLeft + 182, guiTop + 230, 60, 20);
        refreshRows();
    }

    private int pages() {
        return Math.max(1, (zones.size() + ROWS - 1) / ROWS);
    }

    private void refreshRows() {
        page = Math.min(page, pages() - 1);
        for (int row = 0; row < ROWS; row++) {
            GuiButtonNop button = getButton(FIRST_ROW_BUTTON + row);
            if (button == null) {
                continue;
            }
            int index = page * ROWS + row;
            boolean exists = index < zones.size();
            button.shown = exists;
            button.setEnabled(exists);
            button.setDisplayText(exists ? rowLabel(index) : "");
        }
        GuiLabel empty = getLabel(EMPTY_LABEL);
        if (empty != null) {
            empty.visible = zones.size() == 0;
        }
        GuiLabel pageLabel = getLabel(PAGE_LABEL);
        if (pageLabel != null) {
            pageLabel.setMessage(Component.literal((page + 1) + " / " + pages()));
        }
        GuiButtonNop add = getButton(ADD_BUTTON);
        if (add != null) {
            add.setEnabled(zones.size() < BossVentZoneList.MAX_ENTRIES);
        }
    }

    /**
     * "+ North side 6 · -3/+0/-1 .. +3/+2/-1" while the vent can fire, "- ..." while it is switched
     * off - the way the ability lists mark what is on - led by the face it fires out of and its
     * reach, which is what tells two vents of one wall apart, and in list order, which is the order
     * a volley taken one after another walks.
     */
    private String rowLabel(int index) {
        BossVentZone zone = zones.get(index);
        String corners;
        if (zone.getCoordinateMode() == BossVentZone.COORDINATE_FIXED) {
            corners = zone.getX1() + "/" + zone.getY1() + "/" + zone.getZ1() + " .. "
                    + zone.getX2() + "/" + zone.getY2() + "/" + zone.getZ2();
        } else {
            corners = withSign(zone.getX1()) + "/" + withSign(zone.getY1()) + "/" + withSign(zone.getZ1()) + " .. "
                    + withSign(zone.getX2()) + "/" + withSign(zone.getY2()) + "/" + withSign(zone.getZ2());
        }
        return (zone.isEnabled() ? "+ " : "- ") + I18n.get(BossVentZone.FACE_LABELS[zone.getFace()]) + " "
                + zone.getReach() + " · " + corners;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_ROW_BUTTON;
        if (row >= 0 && row < ROWS) {
            int index = page * ROWS + row;
            if (index < zones.size()) {
                setSubGui(new SubGuiBossVentZone(npc, phase, phaseIndex, index));
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
            BossVentZone zone = zones.add();
            if (zone != null) {
                page = (zones.size() - 1) / ROWS;
                setSubGui(new SubGuiBossVentZone(npc, phase, phaseIndex, zones.size() - 1));
            }
        } else if (button.id == CLEAR_BUTTON) {
            zones.clear();
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
