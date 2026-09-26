package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.function.Consumer;

/** Which bosses this one shares its health with or has to die together with, and how. */
public final class SubGuiBossHealthLink extends SubGuiFieldScreen {
    private static final int GROUP_FIELD = 1;
    private static final int MODE_BUTTON = 2;
    private static final int RANGE_FIELD = 3;
    private static final int WINDOW_FIELD = 4;
    private static final int REVIVE_FIELD = 5;
    private static final int DOWNED_ANIMATION_FIELD = 6;
    private static final int REVIVE_ANIMATION_FIELD = 7;
    private static final int TITLE_LABEL = 30;
    private static final int TICKS_HINT_LABEL = 31;
    private static final int FIRST_HINT_LABEL = 40;

    private static final int FIRST_ROW_Y = 26;
    private static final int ROW_HEIGHT = 22;
    private static final int ROWS = 7;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    private static final int HINT_COLOR = 0xA0A0A0;
    /** The group and animation fields start here: right of "Анимация подъёма", the widest label beside them. */
    private static final int TEXT_FIELD_X = 108;
    private static final String HINT = "cnpcgeckoaddon.boss.health_link_hint";

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossHealthLink(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the hint wraps to
        // is up to the locale.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, "cnpcgeckoaddon.boss.health_link_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + FIRST_ROW_Y;

        addLabel(new ThemeLabel(GROUP_FIELD, "cnpcgeckoaddon.boss.health_link_group", guiLeft + 8, y + 6));
        addTextField(new ThemeTextField(GROUP_FIELD, this, guiLeft + TEXT_FIELD_X, y, 134, BUTTON_HEIGHT,
                data.getHealthLinkGroup()));
        y += ROW_HEIGHT;

        addCycle(MODE_BUTTON, "cnpcgeckoaddon.boss.health_link_mode", y, TeleportPathData.HEALTH_LINK_MODE_LABELS,
                data.getHealthLinkMode());
        y += ROW_HEIGHT;

        addNumberField(RANGE_FIELD, "cnpcgeckoaddon.boss.health_link_range", y, data.getHealthLinkRange(),
                TeleportPathData.MIN_HEALTH_LINK_RANGE, TeleportPathData.MAX_HEALTH_LINK_RANGE,
                TeleportPathData.DEFAULT_HEALTH_LINK_RANGE);
        y += ROW_HEIGHT;

        addNumberField(WINDOW_FIELD, "cnpcgeckoaddon.boss.health_link_window", y, data.getHealthLinkWindowTicks(),
                TeleportPathData.MIN_HEALTH_LINK_WINDOW_TICKS, TeleportPathData.MAX_HEALTH_LINK_WINDOW_TICKS,
                TeleportPathData.DEFAULT_HEALTH_LINK_WINDOW_TICKS);
        y += ROW_HEIGHT;

        addNumberField(REVIVE_FIELD, "cnpcgeckoaddon.boss.health_link_revive", y, data.getHealthLinkRevivePercent(),
                TeleportPathData.MIN_HEALTH_LINK_REVIVE_PERCENT, TeleportPathData.MAX_HEALTH_LINK_REVIVE_PERCENT,
                TeleportPathData.DEFAULT_HEALTH_LINK_REVIVE_PERCENT);
        y += ROW_HEIGHT;

        addSelectRow(DOWNED_ANIMATION_FIELD, "cnpcgeckoaddon.boss.health_link_downed_anim", y,
                data.getHealthLinkDownedAnimation());
        y += ROW_HEIGHT;

        addSelectRow(REVIVE_ANIMATION_FIELD, "cnpcgeckoaddon.boss.health_link_revive_anim", y,
                data.getHealthLinkReviveAnimation());

        int hintEnd = addWrappedText(FIRST_HINT_LABEL, hintText(), guiTop + hintY());
        addLabel(new ThemeLabel(TICKS_HINT_LABEL, "cnpcgeckoaddon.teleport.ticks_hint", guiLeft + 8, hintEnd, HINT_COLOR));
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    private void addSelectRow(int id, String label, int y, String value) {
        addLabel(new ThemeLabel(id, label, guiLeft + 8, y + 6));
        addTextField(new ThemeTextField(id, this, guiLeft + TEXT_FIELD_X, y, 86, BUTTON_HEIGHT, value));
        addButton(new ThemeButton(this, id, guiLeft + 198, y, 44, BUTTON_HEIGHT, "mco.template.button.select"));
    }

    /**
     * The hint as it reads in this locale, through a translatable rather than I18n: a percent sign
     * added to it later would turn an I18n read into a format error on the screen.
     */
    private static String hintText() {
        return Component.translatable(HINT).getString();
    }

    /** Where the hint starts, from the panel's top: just under the last row. */
    private int hintY() {
        return FIRST_ROW_Y + ROWS * ROW_HEIGHT + 4;
    }

    /** Where the done button goes, from the panel's top: under the hint and the ticks line after it. */
    private int doneButtonY() {
        return hintY() + wrapLines(hintText(), imageWidth - 16).size() * LINE_HEIGHT + LINE_HEIGHT + 4;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == MODE_BUTTON) {
            data.setHealthLinkMode(button.getValue());
        } else if (button.id == DOWNED_ANIMATION_FIELD) {
            pickAnimation(DOWNED_ANIMATION_FIELD, "cnpcgeckoaddon.string_picker.health_link_downed_animation",
                    data::setHealthLinkDownedAnimation);
        } else if (button.id == REVIVE_ANIMATION_FIELD) {
            pickAnimation(REVIVE_ANIMATION_FIELD, "cnpcgeckoaddon.string_picker.health_link_revive_animation",
                    data::setHealthLinkReviveAnimation);
        }
    }

    private void pickAnimation(int id, String title, Consumer<String> setter) {
        setSubGui(new GuiStringSelection(this, title, BossAnimationGuiUtil.getAnimations(npc), name -> {
            setter.accept(name);
            getTextField(id).setValue(name);
        }));
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop group = getTextField(GROUP_FIELD);
        if (group != null) {
            data.setHealthLinkGroup(group.getValue());
        }
        applyNumberField(RANGE_FIELD, data::setHealthLinkRange);
        applyNumberField(WINDOW_FIELD, data::setHealthLinkWindowTicks);
        applyNumberField(REVIVE_FIELD, data::setHealthLinkRevivePercent);
        applyAnimation(DOWNED_ANIMATION_FIELD, data.getHealthLinkDownedAnimation(), data::setHealthLinkDownedAnimation);
        applyAnimation(REVIVE_ANIMATION_FIELD, data.getHealthLinkReviveAnimation(), data::setHealthLinkReviveAnimation);
    }

    /** Keeps a typed animation only when the model has it; otherwise the field snaps back. */
    private void applyAnimation(int id, String current, Consumer<String> setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            setter.accept(value);
        } else {
            field.setValue(current);
        }
    }

    /** Wide enough for the longer choice, "Одно здоровье на всех", which the default button clips. */
    @Override
    protected int cycleButtonX() {
        return 106;
    }

    @Override
    protected int cycleButtonWidth() {
        return 136;
    }
}
