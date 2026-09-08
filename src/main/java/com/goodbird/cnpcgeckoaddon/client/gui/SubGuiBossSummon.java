package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

public final class SubGuiBossSummon extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int CLONE_NAME_FIELD = 3;
    private static final int CLONE_TAB_FIELD = 4;
    private static final int COUNT_FIELD = 5;
    private static final int RADIUS_FIELD = 6;
    private static final int MAX_ALIVE_FIELD = 7;
    private static final int ACTION_DELAY_FIELD = 8;
    private static final int COOLDOWN_FIELD = 9;
    private static final int SPAWN_POINTS_BUTTON = 10;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossSummon(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.summon_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;
        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.summon().isEnabled()));
        y += 23;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.summon().getAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 23;
        addTextFieldRow(CLONE_NAME_FIELD, "cnpcgeckoaddon.boss.clone_name", y, phase.summon().getCloneName());
        y += 23;
        addNumberField(CLONE_TAB_FIELD, "cnpcgeckoaddon.boss.clone_tab", y, phase.summon().getCloneTab(), 1, 9, 1);
        y += 23;
        addNumberField(COUNT_FIELD, "cnpcgeckoaddon.boss.minion_count", y, phase.summon().getCount(), 1, 32, 3);
        y += 23;
        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.boss.minion_radius", y, phase.summon().getRadius(), 1, 32, 4);
        y += 23;
        addNumberField(MAX_ALIVE_FIELD, "cnpcgeckoaddon.boss.max_minions", y, phase.summon().getMaxAlives(), 1, 128, 6);
        y += 23;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.summon().getActionDelayTicks(), 0, 1200, 20);
        y += 23;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.summon().getCooldownTicks(), 20, 12000, 400);

        addButton(new GuiButtonNop(this, SPAWN_POINTS_BUTTON, guiLeft + 8, guiTop + 230, 168, 20,
                "cnpcgeckoaddon.boss.minion_spawn_settings"));
        addDoneButton(guiLeft + 182, guiTop + 230, 60, 20);
    }

    private void addTextFieldRow(int id, String label, int y, String value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + 155, y, 87, 20, value));
    }

    @Override
    protected int numberLabelX() {
        return 6;
    }

    @Override
    protected int numberFieldX() {
        return 175;
    }

    @Override
    protected int numberFieldWidth() {
        return 67;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            phase.summon().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.summon_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.summon().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase.summon()::setActionDelayTicks);
            }));
        } else if (button.id == SPAWN_POINTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossMinionSpawnSettings(npc, phase, phaseIndex));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.summon().setAnimation(value);
            else animation.setValue(phase.summon().getAnimation());
        }
        GuiTextFieldNop clone = getTextField(CLONE_NAME_FIELD);
        if (clone != null) phase.summon().setCloneName(clone.getValue());
        applyNumberField(CLONE_TAB_FIELD, phase.summon()::setCloneTab);
        applyNumberField(COUNT_FIELD, phase.summon()::setCount);
        applyNumberField(RADIUS_FIELD, phase.summon()::setRadius);
        applyNumberField(MAX_ALIVE_FIELD, phase.summon()::setMaxAlives);
        applyNumberField(ACTION_DELAY_FIELD, phase.summon()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.summon()::setCooldownTicks);
    }
}
