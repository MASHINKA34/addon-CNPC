package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * The three things a cocoon does to somebody, one list each: what wears on them while they
 * are inside, what lands when nobody came, and what they get for being let out.
 */
public final class SubGuiBossCocoonEffects extends GuiBasic {
    private static final int INSIDE_BUTTON = 1;
    private static final int FAIL_BUTTON = 2;
    private static final int FREE_BUTTON = 3;

    private final BossPhaseData phase;

    public SubGuiBossCocoonEffects(BossPhaseData phase) {
        this.phase = phase;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 140;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.effects_cocoon", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 28;
        addButton(new GuiButtonNop(this, INSIDE_BUTTON, guiLeft + 8, y, 234, 20,
                "cnpcgeckoaddon.boss.effects_cocoon_inside"));
        y += 24;
        addButton(new GuiButtonNop(this, FAIL_BUTTON, guiLeft + 8, y, 234, 20,
                "cnpcgeckoaddon.boss.effects_cocoon_fail"));
        y += 24;
        addButton(new GuiButtonNop(this, FREE_BUTTON, guiLeft + 8, y, 234, 20,
                "cnpcgeckoaddon.boss.effects_cocoon_free"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 114, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == INSIDE_BUTTON) {
            setSubGui(new SubGuiBossEffectList(phase.getCocoonVictimEffects(),
                    "cnpcgeckoaddon.boss.effects_cocoon_inside"));
        } else if (button.id == FAIL_BUTTON) {
            setSubGui(new SubGuiBossEffectList(phase.getCocoonFailEffects(),
                    "cnpcgeckoaddon.boss.effects_cocoon_fail"));
        } else if (button.id == FREE_BUTTON) {
            setSubGui(new SubGuiBossEffectList(phase.getCocoonFreeEffects(),
                    "cnpcgeckoaddon.boss.effects_cocoon_free"));
        }
    }
}
