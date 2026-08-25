package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Per-phase placement policy for ordinary summoned minions. */
public final class SubGuiBossMinionSpawnSettings extends GuiBasic implements ITextfieldListener {
    private static final int MODE_BUTTON = 1;
    private static final int ORDER_BUTTON = 2;
    private static final int SEARCH_FIELD = 3;
    private static final int REUSE_BUTTON = 4;
    private static final int EDIT_BUTTON = 5;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossMinionSpawnSettings(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 196;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.minion_spawn_title", phaseIndex),
                guiLeft + 8, guiTop + 7, 0xFFFFFF));
        int y = guiTop + 27;
        addChoice(MODE_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_mode", y,
                BossPhaseData.MINION_SPAWN_MODE_LABELS, phase.getMinionSpawnMode());
        y += 25;
        addChoice(ORDER_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_order", y,
                BossPhaseData.MINION_SPAWN_ORDER_LABELS, phase.getMinionSpawnOrder());
        y += 25;

        addLabel(new GuiLabel(SEARCH_FIELD, "cnpcgeckoaddon.boss.minion_spawn_search",
                guiLeft + 8, y + 6));
        GuiTextFieldNop search = new GuiTextFieldNop(SEARCH_FIELD, this,
                guiLeft + 182, y, 60, 20, Integer.toString(phase.getMinionPointSearchRadius()));
        search.setNumbersOnly();
        search.setMinMaxDefault(0, 4, 0);
        addTextField(search);
        y += 25;

        addLabel(new GuiLabel(REUSE_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_reuse",
                guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, REUSE_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isMinionReuseOccupiedPoints()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.minion_spawn_hint",
                guiLeft + 8, guiTop + 135, 0xA0A0A0));
        addButton(new GuiButtonNop(this, EDIT_BUTTON, guiLeft + 8, guiTop + 170, 168, 20,
                "cnpcgeckoaddon.boss.minion_spawn_edit"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 170, 60, 20,
                "gui.done", button -> close()));
    }

    private void addChoice(int id, String label, int y, String[] values, int selected) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, id, guiLeft + 112, y, 130, 20, values, selected));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == MODE_BUTTON) {
            phase.setMinionSpawnMode(button.getValue());
        } else if (button.id == ORDER_BUTTON) {
            phase.setMinionSpawnOrder(button.getValue());
        } else if (button.id == REUSE_BUTTON) {
            phase.setMinionReuseOccupiedPoints(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == EDIT_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossMinionSpawnList(npc, phase, phaseIndex));
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop search = getTextField(SEARCH_FIELD);
        if (search != null) phase.setMinionPointSearchRadius(search.getInteger());
    }
}
