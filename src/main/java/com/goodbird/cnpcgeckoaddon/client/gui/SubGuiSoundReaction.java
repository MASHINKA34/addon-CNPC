package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.SoundReactionData;
import com.goodbird.cnpcgeckoaddon.mixin.ISoundReactionData;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public final class SubGuiSoundReaction extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int MODE_BUTTON = 2;
    private static final int RADIUS_FIELD = 3;
    private static final int MEMORY_FIELD = 4;
    private static final int COOLDOWN_FIELD = 5;

    private final SoundReactionData data;

    public SubGuiSoundReaction(DataAI ai) {
        data = ((ISoundReactionData) ai).cnpcgeckoaddon$getSoundReactionData();
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        int y = guiTop + 16;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.sound.enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 142, y, 100, 20, data.isEnabled()));
        y += 30;

        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.sound.mode", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                new String[]{"cnpcgeckoaddon.sound.investigate", "cnpcgeckoaddon.sound.attack",
                        "cnpcgeckoaddon.sound.attack_enemies"}, data.getMode()));
        y += 30;

        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.sound.radius", y, data.getRadius(), 1, 16, 16);
        y += 26;
        addNumberField(MEMORY_FIELD, "cnpcgeckoaddon.sound.memory", y, data.getMemoryTicks(), 20, 1200, 100);
        y += 26;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.sound.cooldown", y, data.getCooldownTicks(), 0, 200, 20);

        addLabel(new GuiLabel(20, "cnpcgeckoaddon.sound.ticks_hint", guiLeft + 8, guiTop + 169, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 190, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int defaultValue) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, defaultValue);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            data.setMode(button.getValue());
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyTextField(field);
    }

    @Override
    public void close() {
        applyTextFields();
        super.close();
    }

    private void applyTextFields() {
        applyTextField(getTextField(RADIUS_FIELD));
        applyTextField(getTextField(MEMORY_FIELD));
        applyTextField(getTextField(COOLDOWN_FIELD));
    }

    private void applyTextField(GuiTextFieldNop field) {
        if (field == null) {
            return;
        }
        if (field.id == RADIUS_FIELD) {
            data.setRadius(field.getInteger());
        } else if (field.id == MEMORY_FIELD) {
            data.setMemoryTicks(field.getInteger());
        } else if (field.id == COOLDOWN_FIELD) {
            data.setCooldownTicks(field.getInteger());
        }
    }
}
