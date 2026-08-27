package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public final class SubGuiBossTeleport extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ORDER_BUTTON = 1;
    private static final int SOUND_BUTTON = 2;
    private static final int PRE_ANIMATION_FIELD = 3;
    private static final int PRE_DELAY_FIELD = 4;
    private static final int POST_ANIMATION_FIELD = 5;
    private static final int POST_LOCK_FIELD = 6;
    private static final int MIN_DELAY_FIELD = 7;
    private static final int MAX_DELAY_FIELD = 8;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossTeleport(EntityNPCInterface npc, TeleportPathData data,
                              BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.data = data;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.teleport_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;
        addLabel(new GuiLabel(ORDER_BUTTON, "cnpcgeckoaddon.teleport.order", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, ORDER_BUTTON, guiLeft + 112, y, 130, 20,
                new String[]{"cnpcgeckoaddon.teleport.sequential", "cnpcgeckoaddon.teleport.ping_pong",
                        "cnpcgeckoaddon.teleport.random"}, data.getOrder()));
        y += 23;
        addLabel(new GuiLabel(SOUND_BUTTON, "cnpcgeckoaddon.teleport.sound", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, SOUND_BUTTON, guiLeft + 155, y, 87, 20,
                data.shouldPlaySound()));
        y += 23;
        addAnimationRow(PRE_ANIMATION_FIELD, "cnpcgeckoaddon.boss.teleport_pre_anim", y,
                phase.getTeleportPreparationAnimation());
        y += 23;
        addNumberField(PRE_DELAY_FIELD, "cnpcgeckoaddon.boss.teleport_pre_delay", y,
                phase.getTeleportPreparationTicks(), 0, 1200, 20);
        y += 23;
        addAnimationRow(POST_ANIMATION_FIELD, "cnpcgeckoaddon.boss.teleport_post_anim", y,
                phase.getAppearanceAnimation());
        y += 23;
        addNumberField(POST_LOCK_FIELD, "cnpcgeckoaddon.boss.teleport_post_lock", y,
                phase.getAppearanceLockTicks(), 0, 1200, 20);
        y += 23;
        addNumberField(MIN_DELAY_FIELD, "cnpcgeckoaddon.teleport.min_delay", y,
                phase.getTeleportMinDelayTicks(), 10, 1200, 60);
        y += 23;
        addNumberField(MAX_DELAY_FIELD, "cnpcgeckoaddon.teleport.max_delay", y,
                phase.getTeleportMaxDelayTicks(), 10, 1200, 100);

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.teleport.ticks_hint",
                guiLeft + 8, guiTop + 202, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    private void addAnimationRow(int id, String label, int y, String value) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + 98, y, 96, 20, value));
        addButton(new GuiButtonNop(this, id, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ORDER_BUTTON) {
            data.setOrder(button.getValue());
        } else if (button.id == SOUND_BUTTON) {
            data.setPlaySound(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == PRE_ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting teleport preparation animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setTeleportPreparationAnimation(name);
                getTextField(PRE_ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, PRE_DELAY_FIELD, phase::setTeleportPreparationTicks);
            }));
        } else if (button.id == POST_ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting post-teleport animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setAppearanceAnimation(name);
                getTextField(POST_ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, POST_LOCK_FIELD, phase::setAppearanceLockTicks);
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
        applyAnimation(PRE_ANIMATION_FIELD, true);
        applyAnimation(POST_ANIMATION_FIELD, false);
        GuiTextFieldNop preDelay = getTextField(PRE_DELAY_FIELD);
        if (preDelay != null) phase.setTeleportPreparationTicks(preDelay.getInteger());
        GuiTextFieldNop postLock = getTextField(POST_LOCK_FIELD);
        if (postLock != null) phase.setAppearanceLockTicks(postLock.getInteger());
        GuiTextFieldNop min = getTextField(MIN_DELAY_FIELD);
        GuiTextFieldNop max = getTextField(MAX_DELAY_FIELD);
        if (min != null && max != null) phase.setTeleportDelayRange(min.getInteger(), max.getInteger());
    }

    private void applyAnimation(int fieldId, boolean preparation) {
        GuiTextFieldNop field = getTextField(fieldId);
        if (field == null) return;
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            if (preparation) phase.setTeleportPreparationAnimation(value);
            else phase.setAppearanceAnimation(value);
        } else {
            field.setValue(preparation ? phase.getTeleportPreparationAnimation() : phase.getAppearanceAnimation());
        }
    }
}
