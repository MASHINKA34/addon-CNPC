package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public final class SubGuiBossBarStyle extends GuiBasic implements ITextfieldListener {
    private static final int SCALE_FIELD = 1;
    private static final int FIRST_STYLE_BUTTON = 100;

    private final TeleportPathData data;

    public SubGuiBossBarStyle(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 208;
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

        addLabel(new GuiLabel(SCALE_FIELD, "cnpcgeckoaddon.boss.bar_scale", guiLeft + 8, y + 6));
        GuiTextFieldNop scale = new GuiTextFieldNop(SCALE_FIELD, this, guiLeft + 172, y, 70, 20,
                Integer.toString(data.getBossBarScalePercent()));
        scale.setNumbersOnly();
        scale.setMinMaxDefault(TeleportPathData.MIN_BOSS_BAR_SCALE_PERCENT,
                TeleportPathData.MAX_BOSS_BAR_SCALE_PERCENT,
                TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT);
        addTextField(scale);
        y += 24;

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.bar_scale_hint", guiLeft + 8, y, 0xA0A0A0));
        y += 14;

        addButton(new GuiButtonNop(this, 66, guiLeft + 182, y, 60, 20, "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        applyFields();
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

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop scale = getTextField(SCALE_FIELD);
        if (scale != null) data.setBossBarScalePercent(scale.getInteger());
    }

    private String styleLabel(int index) {
        BossBarStyles.Style style = BossBarStyles.values().get(index);
        String label = I18n.get(style.translationKey());
        return style.id().equals(data.getBossBarStyle()) ? "> " + label : label;
    }
}
