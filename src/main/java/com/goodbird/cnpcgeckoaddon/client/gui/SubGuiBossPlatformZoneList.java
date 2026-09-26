package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZone;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZoneList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** Eight-row paged editor for one phase's platforms, laid out like the summon's points. */
public final class SubGuiBossPlatformZoneList extends SubGuiFieldScreen {
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
    private final BossPlatformZoneList zones;
    private int page;

    public SubGuiBossPlatformZoneList(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.zones = phase.platform().getZones();
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.platform_zones_title", phaseIndex),
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        addLabel(new ThemeLabel(EMPTY_LABEL, "cnpcgeckoaddon.boss.platform_zone_empty",
                guiLeft + 8, guiTop + 30, 0xA0A0A0));
        int y = guiTop + 24;
        for (int row = 0; row < ROWS; row++) {
            addButton(new ThemeButton(this, FIRST_ROW_BUTTON + row, guiLeft + 8, y, 234, 20, ""));
            y += 22;
        }
        addButton(new ThemeButton(this, PREV_PAGE_BUTTON, guiLeft + 8, guiTop + 204, 20, 20, "<"));
        addLabel(new ThemeLabel(PAGE_LABEL, "", guiLeft + 36, guiTop + 210, 0xA0A0A0));
        addButton(new ThemeButton(this, NEXT_PAGE_BUTTON, guiLeft + 222, guiTop + 204, 20, 20, ">"));
        addButton(new ThemeButton(this, ADD_BUTTON, guiLeft + 8, guiTop + 230, 70, 20,
                "cnpcgeckoaddon.boss.minion_spawn_add"));
        addButton(new ThemeButton(this, CLEAR_BUTTON, guiLeft + 82, guiTop + 230, 94, 20,
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
            add.setEnabled(zones.size() < BossPlatformZoneList.MAX_ENTRIES);
        }
    }

    /**
     * "+ Arena offset -3/+0/+2 .. +3/+2/+6 · 1" while the platform can be set alight, "- ..." while
     * it is switched off - the way the ability lists mark what is on - with its random weight last,
     * and in list order, which is the order a phase taking its platforms in turn walks.
     */
    private String rowLabel(int index) {
        BossPlatformZone zone = zones.get(index);
        String corners;
        if (zone.getCoordinateMode() == BossPlatformZone.COORDINATE_FIXED) {
            corners = I18n.get("cnpcgeckoaddon.boss.minion_spawn_fixed") + " "
                    + zone.getX1() + "/" + zone.getY1() + "/" + zone.getZ1() + " .. "
                    + zone.getX2() + "/" + zone.getY2() + "/" + zone.getZ2();
        } else {
            corners = I18n.get("cnpcgeckoaddon.boss.minion_spawn_arena") + " "
                    + withSign(zone.getX1()) + "/" + withSign(zone.getY1()) + "/" + withSign(zone.getZ1()) + " .. "
                    + withSign(zone.getX2()) + "/" + withSign(zone.getY2()) + "/" + withSign(zone.getZ2());
        }
        return (zone.isEnabled() ? "+ " : "- ") + corners + " · " + zone.getWeight();
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_ROW_BUTTON;
        if (row >= 0 && row < ROWS) {
            int index = page * ROWS + row;
            if (index < zones.size()) {
                setSubGui(new SubGuiBossPlatformZone(npc, phase, phaseIndex, index));
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
            BossPlatformZone zone = zones.add();
            if (zone != null) {
                page = (zones.size() - 1) / ROWS;
                setSubGui(new SubGuiBossPlatformZone(npc, phase, phaseIndex, zones.size() - 1));
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
