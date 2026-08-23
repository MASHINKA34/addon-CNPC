package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

public final class SubGuiBossBarStyle extends GuiBasic {
    private static final int FIRST_STYLE_BUTTON = 100;

    private final TeleportPathData data;

    public SubGuiBossBarStyle(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 184;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.bar_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;
        for (int i = 0; i < BossBarStyles.values().size(); i++) {
            addButton(new GuiButtonNop(this, FIRST_STYLE_BUTTON + i, guiLeft + 8, y, 234, 20, styleLabel(i)));
            y += 22;
        }
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 158, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_STYLE_BUTTON;
        if (index < 0 || index >= BossBarStyles.values().size()) {
            return;
        }
        data.setBossBarStyle(BossBarStyles.values().get(index).id());
        for (int i = 0; i < BossBarStyles.values().size(); i++) {
            GuiButtonNop styleButton = getButton(FIRST_STYLE_BUTTON + i);
            if (styleButton != null) {
                styleButton.setDisplayText(styleLabel(i));
            }
        }
    }

    private String styleLabel(int index) {
        BossBarStyles.Style style = BossBarStyles.values().get(index);
        String label = I18n.get(style.translationKey());
        return style.id().equals(data.getBossBarStyle()) ? "> " + label : label;
    }
}
