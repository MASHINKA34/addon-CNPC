package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** Lists the configured boss phases; the count itself is set on the parent screen. */
public final class SubGuiBossPhaseList extends GuiBasic {
    private static final int FIRST_PHASE_BUTTON = 100;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossPhaseList(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.phases", guiLeft + 8, guiTop + 8, 0xFFFFFF));

        int y = guiTop + 26;
        for (int i = 0; i < data.getPhaseCount(); i++) {
            addButton(new GuiButtonNop(this, FIRST_PHASE_BUTTON + i, guiLeft + 8, y, 234, 20, phaseLabel(i)));
            y += 22;
        }

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.phase_count_hint", guiLeft + 8, guiTop + 214, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    /** "Phase 2  -  from 66%", so the whole ladder is readable without opening each entry. */
    private String phaseLabel(int index) {
        return BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.phase", index)
                + "  -  " + I18n.get("cnpcgeckoaddon.boss.phase_from")
                + " " + data.getPhase(index).getStartHealthPercent() + "%";
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_PHASE_BUTTON;
        if (index >= 0 && index < data.getPhaseCount()) {
            setSubGui(new SubGuiBossPhase(npc, data, index));
        }
    }

    @Override
    public void subGuiClosed(Screen subgui) {
        super.subGuiClosed(subgui);
        // A phase screen may have changed its threshold. Relabel in place rather than
        // rebuilding: this GUI framework has no widget-clearing rebuild, so calling init()
        // again would stack a second set of buttons on top of the first.
        for (int i = 0; i < data.getPhaseCount(); i++) {
            GuiButtonNop button = getButton(FIRST_PHASE_BUTTON + i);
            if (button != null) {
                button.setDisplayText(phaseLabel(i));
            }
        }
    }
}
