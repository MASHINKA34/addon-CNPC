package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Turns the boss dangerous once a fight has dragged on for too long. */
public final class SubGuiBossRage extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int DELAY_FIELD = 2;
    private static final int MULTIPLIER_FIELD = 3;
    private static final int ANIMATION_FIELD = 4;
    private static final int LOCK_FIELD = 5;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossRage(EntityNPCInterface npc, TeleportPathData data) {
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
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.rage_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.rage_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, data.isRageEnabled()));
        y += 24;

        addNumberField(DELAY_FIELD, "cnpcgeckoaddon.boss.rage_delay", y, data.getRageDelayTicks(),
                TeleportPathData.MIN_RAGE_DELAY_TICKS, TeleportPathData.MAX_RAGE_DELAY_TICKS, 3600);
        y += 24;

        addNumberField(MULTIPLIER_FIELD, "cnpcgeckoaddon.boss.rage_multiplier", y,
                data.getRageMultiplierPercent(), TeleportPathData.MIN_RAGE_MULTIPLIER_PERCENT,
                TeleportPathData.MAX_RAGE_MULTIPLIER_PERCENT, 200);
        y += 24;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.rage_anim", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 98, y, 96, 20,
                data.getRageAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 24;

        addNumberField(LOCK_FIELD, "cnpcgeckoaddon.boss.rage_lock", y, data.getRageLockTicks(),
                0, 1200, 40);

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.rage_hint", guiLeft + 8, guiTop + 150, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.teleport.ticks_hint", guiLeft + 8, guiTop + 162, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setRageEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting enrage animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                data.setRageAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
            }));
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
        GuiTextFieldNop delay = getTextField(DELAY_FIELD);
        if (delay != null) data.setRageDelayTicks(delay.getInteger());
        GuiTextFieldNop multiplier = getTextField(MULTIPLIER_FIELD);
        if (multiplier != null) data.setRageMultiplierPercent(multiplier.getInteger());
        GuiTextFieldNop lock = getTextField(LOCK_FIELD);
        if (lock != null) data.setRageLockTicks(lock.getInteger());
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation == null) return;
        String value = animation.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            data.setRageAnimation(value);
        } else {
            animation.setValue(data.getRageAnimation());
        }
    }
}
