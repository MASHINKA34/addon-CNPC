package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public final class SubGuiTeleportPath extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int COMBAT_ONLY_BUTTON = 2;
    private static final int STATIONARY_BUTTON = 3;
    private static final int PHASE_COUNT_FIELD = 4;
    private static final int TRANSITION_ANIMATION_FIELD = 5;
    private static final int TRANSITION_LOCK_FIELD = 6;
    private static final int BOSS_BAR_BUTTON = 25;
    private static final int RESET_BUTTON = 26;
    private static final int RAGE_BUTTON = 27;
    private static final int CHEST_BUTTON = 28;

    private final TeleportPathData data;
    private final EntityNPCInterface npc;

    public SubGuiTeleportPath(DataAI ai, EntityNPCInterface npc) {
        this.data = ((ITeleportPathData) ai).cnpcgeckoaddon$getTeleportPathData();
        this.npc = npc;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        int y = guiTop + 10;
        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.teleport.enabled", y, data.isEnabled());
        y += 26;
        addYesNo(COMBAT_ONLY_BUTTON, "cnpcgeckoaddon.teleport.combat_only", y, data.isCombatOnly());
        y += 26;
        addYesNo(STATIONARY_BUTTON, "cnpcgeckoaddon.boss.stationary", y, data.isStationary());
        y += 26;

        addNumberField(PHASE_COUNT_FIELD, "cnpcgeckoaddon.boss.phase_count", y,
                data.getPhaseCount(), TeleportPathData.MIN_PHASES, TeleportPathData.MAX_PHASES, 2);
        y += 26;

        addLabel(new GuiLabel(TRANSITION_ANIMATION_FIELD, "cnpcgeckoaddon.boss.transition_anim", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(TRANSITION_ANIMATION_FIELD, this, guiLeft + 98, y, 96, 20,
                data.getPhaseTransitionAnimation()));
        addButton(new GuiButtonNop(this, TRANSITION_ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 26;
        addNumberField(TRANSITION_LOCK_FIELD, "cnpcgeckoaddon.boss.transition_lock", y,
                data.getPhaseTransitionLockTicks(), 0, 1200, 40);

        addButton(new GuiButtonNop(this, 22, guiLeft + 8, guiTop + 164, 114, 20,
                "cnpcgeckoaddon.boss.targeting_settings"));
        addButton(new GuiButtonNop(this, 23, guiLeft + 128, guiTop + 164, 114, 20,
                "cnpcgeckoaddon.boss.minion_settings"));
        addButton(new GuiButtonNop(this, RESET_BUTTON, guiLeft + 8, guiTop + 186, 114, 20,
                "cnpcgeckoaddon.boss.reset_settings"));
        addButton(new GuiButtonNop(this, RAGE_BUTTON, guiLeft + 128, guiTop + 186, 114, 20,
                "cnpcgeckoaddon.boss.rage_settings"));
        addButton(new GuiButtonNop(this, 24, guiLeft + 8, guiTop + 208, 114, 20,
                "cnpcgeckoaddon.boss.explosion_settings"));
        addButton(new GuiButtonNop(this, CHEST_BUTTON, guiLeft + 128, guiTop + 208, 114, 20,
                "cnpcgeckoaddon.boss.chest_settings"));
        // Four rows is all that fits below the fields, so the bottom one takes three
        // buttons - the two shortest labels of the eight, plus Done.
        addButton(new GuiButtonNop(this, 20, guiLeft + 8, guiTop + 230, 84, 20,
                "cnpcgeckoaddon.boss.phase_settings"));
        addButton(new GuiButtonNop(this, BOSS_BAR_BUTTON, guiLeft + 98, guiTop + 230, 84, 20,
                "cnpcgeckoaddon.boss.bar_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 186, guiTop + 230, 56, 20,
                "gui.done", button -> close()));
    }

    private void addYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 142, y, 100, 20, value));
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COMBAT_ONLY_BUTTON) {
            data.setCombatOnly(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == STATIONARY_BUTTON) {
            data.setStationary(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TRANSITION_ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting phase transition animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                data.setPhaseTransitionAnimation(name);
                getTextField(TRANSITION_ANIMATION_FIELD).setValue(name);
            }));
        } else if (button.id == 20) {
            applyFields();
            setSubGui(new SubGuiBossPhaseList(npc, data));
        } else if (button.id == 22) {
            applyFields();
            setSubGui(new SubGuiBossTargeting(npc, data));
        } else if (button.id == 23) {
            applyFields();
            setSubGui(new SubGuiBossMinions(npc, data));
        } else if (button.id == 24) {
            applyFields();
            setSubGui(new SubGuiBossExplosion(npc, data));
        } else if (button.id == BOSS_BAR_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossBarStyle(data));
        } else if (button.id == RESET_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossReset(data));
        } else if (button.id == RAGE_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRage(npc, data));
        } else if (button.id == CHEST_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossChest(data));
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyField(field);
    }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        applyField(getTextField(PHASE_COUNT_FIELD));
        applyField(getTextField(TRANSITION_ANIMATION_FIELD));
        applyField(getTextField(TRANSITION_LOCK_FIELD));
    }

    private void applyField(GuiTextFieldNop field) {
        if (field == null) return;
        if (field.id == PHASE_COUNT_FIELD) {
            data.setPhaseCount(field.getInteger());
        } else if (field.id == TRANSITION_LOCK_FIELD) {
            data.setPhaseTransitionLockTicks(field.getInteger());
        } else if (field.id == TRANSITION_ANIMATION_FIELD) {
            String value = field.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) {
                data.setPhaseTransitionAnimation(value);
            } else {
                field.setValue(data.getPhaseTransitionAnimation());
            }
        }
    }
}
