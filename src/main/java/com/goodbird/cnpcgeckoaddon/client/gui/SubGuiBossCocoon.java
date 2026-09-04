package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Cocoon: a victim locked inside a clone spawned on them, freed by the party or punished. */
public final class SubGuiBossCocoon extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int TARGET_COUNT_FIELD = 3;
    private static final int TARGET_MODE_BUTTON = 4;
    private static final int CLONE_TAB_FIELD = 5;
    private static final int CLONE_NAME_FIELD = 6;
    private static final int GUARD_TAB_FIELD = 7;
    private static final int GUARD_NAME_FIELD = 8;
    private static final int RESCUE_MODE_BUTTON = 9;
    private static final int RESCUE_RADIUS_FIELD = 10;
    private static final int RESCUE_TICKS_FIELD = 11;
    private static final int DURATION_FIELD = 12;
    private static final int FAIL_DAMAGE_FIELD = 13;
    private static final int ACTION_DELAY_FIELD = 14;
    private static final int COOLDOWN_FIELD = 15;
    private static final int EFFECTS_BUTTON = 67;
    /** Row labels take ids from here up, two per row, so a wrapped one keeps both its lines. */
    private static final int FIRST_ROW_LABEL = 100;

    private static final int LABEL_COLOR = 0xFFFFFF;
    private static final int LABEL_LINE_HEIGHT = 9;
    /** The pair fields start here, so a pair row's label may run up to this. */
    private static final int PAIR_FIELD_X = 130;
    /** A clone row: the tab's small field, then the name, which needs the room a name needs. */
    private static final int CLONE_TAB_X = 124;
    private static final int CLONE_TAB_WIDTH = 28;
    private static final int CLONE_NAME_X = 156;
    private static final int CLONE_NAME_WIDTH = 86;
    /** The yes/no buttons start here, so a toggle row's label may run up to this. */
    private static final int TOGGLE_BUTTON_X = 155;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int nextRowLabel = FIRST_ROW_LABEL;

    public SubGuiBossCocoon(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 266;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        nextRowLabel = FIRST_ROW_LABEL;
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.cocoon_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addToggleRow(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, phase.isCocoonEnabled());
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.getCocoonAnimation());
        y += 21;

        // How many cocoons and who they close round, on one line: the two answer the same question.
        addLabel(new GuiLabel(TARGET_COUNT_FIELD, "cnpcgeckoaddon.boss.cocoon_targets", guiLeft + 6, y + 6));
        addSmallField(TARGET_COUNT_FIELD, guiLeft + 72, y, 38, phase.getCocoonTargetCount(), 1, 4, 1);
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getCocoonTargetMode()));
        y += 21;

        // The two clones, each as the summon asks for its own: a tab and a name, on one line.
        addCloneRow(CLONE_TAB_FIELD, CLONE_NAME_FIELD, "cnpcgeckoaddon.boss.cocoon_clone", y,
                phase.getCocoonCloneTab(), phase.getCocoonCloneName());
        y += 21;
        addCloneRow(GUARD_TAB_FIELD, GUARD_NAME_FIELD, "cnpcgeckoaddon.boss.cocoon_guard", y,
                phase.getCocoonGuardTab(), phase.getCocoonGuardName());
        y += 21;

        addLabel(new GuiLabel(RESCUE_MODE_BUTTON, "cnpcgeckoaddon.boss.cocoon_rescue", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, RESCUE_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.COCOON_RESCUE_LABELS, phase.getCocoonRescueMode()));
        y += 21;

        addPairRow(RESCUE_RADIUS_FIELD, RESCUE_TICKS_FIELD, "cnpcgeckoaddon.boss.cocoon_rescue_time", y,
                phase.getCocoonRescueRadius(), 1, 8, 3,
                phase.getCocoonRescueTicks(), 10, 1200, 60);
        y += 21;
        addPairRow(DURATION_FIELD, FAIL_DAMAGE_FIELD, "cnpcgeckoaddon.boss.cocoon_duration", y,
                phase.getCocoonDurationTicks(), 20, 2400, 300,
                phase.getCocoonFailDamage(), 0, 1000, 40);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getCocoonActionDelayTicks(), 0, 1200, 16,
                phase.getCocoonCooldownTicks(), 1, 12000, 400);
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.cocoon_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 240);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, buttonsY, 60, 20,
                "gui.done", button -> close()));
        updateRescueFields();
    }

    private void addSelectRow(int id, String label, int y, String value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + 108, y, 86, 20, value));
        addButton(new GuiButtonNop(this, id, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
    }

    /** A yes/no on one line, with a label that may take two. */
    private void addToggleRow(int id, String label, int y, boolean value) {
        addRowLabel(label, y, TOGGLE_BUTTON_X - 6 - 2);
        addButton(new GuiButtonYesNo(this, id, guiLeft + TOGGLE_BUTTON_X, y, 87, 20, value));
    }

    /** A clone's tab and name on one line, with a label that may take two. */
    private void addCloneRow(int tabId, int nameId, String label, int y, int tab, String name) {
        addRowLabel(label, y, CLONE_TAB_X - 6 - 2);
        addSmallField(tabId, guiLeft + CLONE_TAB_X, y, CLONE_TAB_WIDTH, tab, 1, 9, 1);
        addTextField(new GuiTextFieldNop(nameId, this, guiLeft + CLONE_NAME_X, y, CLONE_NAME_WIDTH, 20, name));
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addRowLabel(label, y, PAIR_FIELD_X - 6 - 2);
        addSmallField(leftId, guiLeft + PAIR_FIELD_X, y, 52, leftValue, leftMin, leftMax, leftFallback);
        addSmallField(rightId, guiLeft + 190, y, 52, rightValue, rightMin, rightMax, rightFallback);
    }

    /**
     * A row's label, on one line when it fits beside the row's control and on two when it
     * does not, the way the beam and hunt screens lay their long names out.
     */
    private void addRowLabel(String key, int y, int width) {
        String text = I18n.get(key);
        int x = guiLeft + 6;
        if (font.width(text) <= width) {
            addLabel(new GuiLabel(nextRowLabel, Component.literal(text), LABEL_COLOR, x, y + 6,
                    width, LABEL_LINE_HEIGHT));
            nextRowLabel += 2;
            return;
        }
        // Greedy: the first line takes every word that fits, the second takes the rest.
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        for (String word : text.split(" ")) {
            if (second.isEmpty() && font.width(first + (first.isEmpty() ? "" : " ") + word) <= width) {
                if (!first.isEmpty()) {
                    first.append(' ');
                }
                first.append(word);
            } else {
                if (!second.isEmpty()) {
                    second.append(' ');
                }
                second.append(word);
            }
        }
        addLabel(new GuiLabel(nextRowLabel, Component.literal(first.toString()), LABEL_COLOR, x, y + 1,
                width, LABEL_LINE_HEIGHT));
        addLabel(new GuiLabel(nextRowLabel + 1, Component.literal(second.toString()), LABEL_COLOR, x,
                y + 1 + LABEL_LINE_HEIGHT, width, LABEL_LINE_HEIGHT));
        nextRowLabel += 2;
    }

    private void addSmallField(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /** The stand rule's two numbers only mean something under the stand rule. */
    private void updateRescueFields() {
        boolean stand = phase.getCocoonRescueMode() == BossPhaseData.COCOON_RESCUE_STAND;
        GuiTextFieldNop radius = getTextField(RESCUE_RADIUS_FIELD);
        GuiTextFieldNop ticks = getTextField(RESCUE_TICKS_FIELD);
        if (radius != null) radius.enabled = stand;
        if (ticks != null) ticks.enabled = stand;
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
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossCocoonEffects(phase));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setCocoonEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setCocoonTargetMode(button.getValue());
        } else if (button.id == RESCUE_MODE_BUTTON) {
            phase.setCocoonRescueMode(button.getValue());
            updateRescueFields();
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting cocoon animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setCocoonAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setCocoonActionDelayTicks);
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
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setCocoonAnimation(value);
            else animation.setValue(phase.getCocoonAnimation());
        }
        GuiTextFieldNop count = getTextField(TARGET_COUNT_FIELD);
        if (count != null) phase.setCocoonTargetCount(count.getInteger());
        GuiTextFieldNop cloneTab = getTextField(CLONE_TAB_FIELD);
        if (cloneTab != null) phase.setCocoonCloneTab(cloneTab.getInteger());
        GuiTextFieldNop cloneName = getTextField(CLONE_NAME_FIELD);
        if (cloneName != null) phase.setCocoonCloneName(cloneName.getValue());
        GuiTextFieldNop guardTab = getTextField(GUARD_TAB_FIELD);
        if (guardTab != null) phase.setCocoonGuardTab(guardTab.getInteger());
        GuiTextFieldNop guardName = getTextField(GUARD_NAME_FIELD);
        if (guardName != null) phase.setCocoonGuardName(guardName.getValue());
        GuiTextFieldNop radius = getTextField(RESCUE_RADIUS_FIELD);
        if (radius != null) phase.setCocoonRescueRadius(radius.getInteger());
        GuiTextFieldNop ticks = getTextField(RESCUE_TICKS_FIELD);
        if (ticks != null) phase.setCocoonRescueTicks(ticks.getInteger());
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setCocoonDurationTicks(duration.getInteger());
        GuiTextFieldNop failDamage = getTextField(FAIL_DAMAGE_FIELD);
        if (failDamage != null) phase.setCocoonFailDamage(failDamage.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setCocoonActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setCocoonCooldownTicks(cooldown.getInteger());
    }
}
