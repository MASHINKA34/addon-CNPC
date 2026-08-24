package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** What happens to the clones a boss summoned once it is no longer fighting. */
public final class SubGuiBossMinions extends GuiBasic {
    private static final int ON_DEATH_BUTTON = 1;
    private static final int ON_RESET_BUTTON = 2;
    private static final int REMOVAL_BUTTON = 3;
    private static final int TOTEMS_BUTTON = 4;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossMinions(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.minions", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 30;

        addLabel(new GuiLabel(ON_DEATH_BUTTON, "cnpcgeckoaddon.boss.minions_on_death", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ON_DEATH_BUTTON, guiLeft + 155, y, 87, 20,
                data.isClearMinionsOnDeath()));
        y += 28;

        addLabel(new GuiLabel(ON_RESET_BUTTON, "cnpcgeckoaddon.boss.minions_on_reset", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ON_RESET_BUTTON, guiLeft + 155, y, 87, 20,
                data.isClearMinionsOnReset()));
        y += 28;

        addLabel(new GuiLabel(REMOVAL_BUTTON, "cnpcgeckoaddon.boss.minions_removal", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, REMOVAL_BUTTON, guiLeft + 112, y, 130, 20,
                TeleportPathData.MINION_REMOVAL_LABELS, data.getMinionRemovalMode()));
        y += 28;

        addButton(new GuiButtonNop(this, TOTEMS_BUTTON, guiLeft + 8, y, 234, 20,
                "cnpcgeckoaddon.boss.totem_settings"));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.minions_hint", guiLeft + 8, guiTop + 150, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.minions_reset_hint", guiLeft + 8, guiTop + 162, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ON_DEATH_BUTTON) {
            data.setClearMinionsOnDeath(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ON_RESET_BUTTON) {
            data.setClearMinionsOnReset(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == REMOVAL_BUTTON) {
            data.setMinionRemovalMode(button.getValue());
        } else if (button.id == TOTEMS_BUTTON) {
            setSubGui(new SubGuiBossTotems(npc, data));
        }
    }
}
