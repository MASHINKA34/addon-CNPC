package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftCrystalList;
import com.goodbird.cnpcgeckoaddon.data.BossRiftCrystalPoint;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** Eight-row paged editor for one rift's crystal zones, laid out like the platforms' zones. */
public final class SubGuiBossRiftCrystalList extends SubGuiFieldScreen {
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
    private final BossRiftCrystalList points;
    private int page;

    public SubGuiBossRiftCrystalList(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.points = phase.rift().getCrystalPoints();
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.rift_crystal_points_title", phaseIndex),
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        addLabel(new GuiLabel(EMPTY_LABEL, "cnpcgeckoaddon.boss.rift_crystal_point_empty",
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
        return Math.max(1, (points.size() + ROWS - 1) / ROWS);
    }

    private void refreshRows() {
        page = Math.min(page, pages() - 1);
        for (int row = 0; row < ROWS; row++) {
            GuiButtonNop button = getButton(FIRST_ROW_BUTTON + row);
            if (button == null) {
                continue;
            }
            int index = page * ROWS + row;
            boolean exists = index < points.size();
            button.shown = exists;
            button.setEnabled(exists);
            button.setDisplayText(exists ? rowLabel(index) : "");
        }
        GuiLabel empty = getLabel(EMPTY_LABEL);
        if (empty != null) {
            empty.visible = points.size() == 0;
        }
        GuiLabel pageLabel = getLabel(PAGE_LABEL);
        if (pageLabel != null) {
            pageLabel.setMessage(Component.literal((page + 1) + " / " + pages()));
        }
        GuiButtonNop add = getButton(ADD_BUTTON);
        if (add != null) {
            add.setEnabled(points.size() < BossRiftCrystalList.MAX_ENTRIES);
        }
    }

    /**
     * "+ Arena offset +4/+0/-3 · 1.5 · B47AFF" while the zone gets a crystal, "- ..." while it is
     * switched off - the way the ability lists mark what is on - with the zone's radius and its own
     * colour after it, so a list of a dozen zones can be read for the odd one out.
     */
    private String rowLabel(int index) {
        BossRiftCrystalPoint point = points.get(index);
        String where;
        if (point.getCoordinateMode() == BossRiftCrystalPoint.COORDINATE_FIXED) {
            where = I18n.get("cnpcgeckoaddon.boss.minion_spawn_fixed") + " "
                    + point.getX() + "/" + point.getY() + "/" + point.getZ();
        } else {
            where = I18n.get("cnpcgeckoaddon.boss.minion_spawn_arena") + " "
                    + withSign(point.getX()) + "/" + withSign(point.getY()) + "/" + withSign(point.getZ());
        }
        String radius = point.getRadiusTenths() > 0
                ? String.valueOf(point.getRadiusTenths() / 10.0D)
                : String.valueOf(phase.rift().crystalCollectRadius());
        String colour = point.getColorOverride() < 0
                ? BossRiftSettings.hex(phase.rift().getCrystalColor())
                : BossRiftSettings.hex(point.getColorOverride());
        return (point.isEnabled() ? "+ " : "- ") + where + " · " + radius + " · " + colour;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_ROW_BUTTON;
        if (row >= 0 && row < ROWS) {
            int index = page * ROWS + row;
            if (index < points.size()) {
                setSubGui(new SubGuiBossRiftCrystalPoint(npc, phase, phaseIndex, index));
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
            BossRiftCrystalPoint point = points.add();
            if (point != null) {
                page = (points.size() - 1) / ROWS;
                setSubGui(new SubGuiBossRiftCrystalPoint(npc, phase, phaseIndex, points.size() - 1));
            }
        } else if (button.id == CLEAR_BUTTON) {
            points.clear();
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
