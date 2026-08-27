package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public final class SubGuiBossSummon extends SubGuiFieldScreen implements ITextfieldListener {
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
        setBackground("menubg.png");
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
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isSummonEnabled()));
        y += 23;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getSummonAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 23;
        addTextFieldRow(CLONE_NAME_FIELD, "cnpcgeckoaddon.boss.clone_name", y, phase.getMinionCloneName());
        y += 23;
        addNumberField(CLONE_TAB_FIELD, "cnpcgeckoaddon.boss.clone_tab", y, phase.getMinionCloneTab(), 1, 9, 1);
        y += 23;
        addNumberField(COUNT_FIELD, "cnpcgeckoaddon.boss.minion_count", y, phase.getMinionCount(), 1, 32, 3);
        y += 23;
        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.boss.minion_radius", y, phase.getMinionRadius(), 1, 32, 4);
        y += 23;
        addNumberField(MAX_ALIVE_FIELD, "cnpcgeckoaddon.boss.max_minions", y, phase.getMaxAliveMinions(), 1, 128, 6);
        y += 23;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.getSummonActionDelayTicks(), 0, 1200, 20);
        y += 23;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.getSummonCooldownTicks(), 20, 12000, 400);

        addButton(new GuiButtonNop(this, SPAWN_POINTS_BUTTON, guiLeft + 8, guiTop + 230, 168, 20,
                "cnpcgeckoaddon.boss.minion_spawn_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
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
            phase.setSummonEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting minion summon animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setSummonAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase::setSummonActionDelayTicks);
            }));
        } else if (button.id == SPAWN_POINTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossMinionSpawnSettings(npc, phase, phaseIndex));
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
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setSummonAnimation(value);
            else animation.setValue(phase.getSummonAnimation());
        }
        GuiTextFieldNop clone = getTextField(CLONE_NAME_FIELD);
        if (clone != null) phase.setMinionCloneName(clone.getValue());
        GuiTextFieldNop tab = getTextField(CLONE_TAB_FIELD);
        if (tab != null) phase.setMinionCloneTab(tab.getInteger());
        GuiTextFieldNop count = getTextField(COUNT_FIELD);
        if (count != null) phase.setMinionCount(count.getInteger());
        GuiTextFieldNop radius = getTextField(RADIUS_FIELD);
        if (radius != null) phase.setMinionRadius(radius.getInteger());
        GuiTextFieldNop maxAlive = getTextField(MAX_ALIVE_FIELD);
        if (maxAlive != null) phase.setMaxAliveMinions(maxAlive.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setSummonActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setSummonCooldownTicks(cooldown.getInteger());
    }
}
