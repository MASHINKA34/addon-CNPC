package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Tether: a leash to the boss, to a spot or to a partner, broken by running and punished if not. */
public final class SubGuiBossTether extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ANCHOR_BUTTON = 3;
    private static final int TARGET_COUNT_FIELD = 4;
    private static final int TARGET_MODE_BUTTON = 5;
    private static final int BREAK_DISTANCE_FIELD = 6;
    private static final int DURATION_FIELD = 7;
    private static final int PULL_FIELD = 8;
    private static final int FAIL_DAMAGE_FIELD = 9;
    private static final int ACTION_DELAY_FIELD = 10;
    private static final int COOLDOWN_FIELD = 11;
    private static final int STYLE_BUTTON = 12;
    private static final int WIDTH_FIELD = 13;
    private static final int EFFECTS_BUTTON = 67;
    private static final int FAIL_EFFECTS_BUTTON = 68;

    private static final String[] STYLE_LABELS = HookCordStyles.values().stream()
            .map(HookCordStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossTether(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 302;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.tether_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isTetherEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getTetherAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        addLabel(new GuiLabel(ANCHOR_BUTTON, "cnpcgeckoaddon.boss.tether_anchor", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, ANCHOR_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.TETHER_ANCHOR_LABELS, phase.getTetherAnchor()));
        y += 21;

        // How many leashes and who they land on, on one line: the two answer the same question.
        addLabel(new GuiLabel(TARGET_COUNT_FIELD, "cnpcgeckoaddon.boss.tether_targets", guiLeft + 6, y + 6));
        addPairedField(TARGET_COUNT_FIELD, guiLeft + 72, y, phase.getTetherTargetCount(), 1, 8, 2, 38);
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getTetherTargetMode()));
        y += 21;

        // The two numbers the mechanic is made of share a line, behind a narrower pair of
        // fields than the other screens use, so their long label still has room to be read.
        addLabel(new GuiLabel(BREAK_DISTANCE_FIELD, "cnpcgeckoaddon.boss.tether_break", guiLeft + 6, y + 6));
        addPairedField(BREAK_DISTANCE_FIELD, guiLeft + 156, y, phase.getTetherBreakDistance(), 3, 48, 10, 40);
        addPairedField(DURATION_FIELD, guiLeft + 202, y, phase.getTetherDurationTicks(), 20, 1200, 120, 40);
        y += 21;

        addNumberField(PULL_FIELD, "cnpcgeckoaddon.boss.tether_pull", y, phase.getTetherPull(), 0, 10, 0);
        y += 21;
        addNumberField(FAIL_DAMAGE_FIELD, "cnpcgeckoaddon.boss.tether_fail", y,
                phase.getTetherFailDamage(), 0, 1000, 12);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getTetherActionDelayTicks(), 0, 1200, 16,
                phase.getTetherCooldownTicks(), 1, 12000, 300);
        y += 21;

        addLabel(new GuiLabel(STYLE_BUTTON, "cnpcgeckoaddon.boss.tether_style", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                STYLE_LABELS, styleIndex()));
        y += 21;
        addNumberField(WIDTH_FIELD, "cnpcgeckoaddon.boss.tether_width", y,
                phase.getTetherWidthPercent(), 25, 400, 100);
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.tether_hint", y + 3);
        // Two effect lists, because the leash does two different things to somebody: it wears
        // on them for as long as it holds, and hits them once when it wins.
        int buttonsY = Math.max(hintY + 4, guiTop + 252);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 116, 20,
                "cnpcgeckoaddon.boss.tether_effects"));
        addButton(new GuiButtonNop(this, FAIL_EFFECTS_BUTTON, guiLeft + 126, buttonsY, 116, 20,
                "cnpcgeckoaddon.boss.tether_fail_effects"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, buttonsY + 24, 60, 20,
                "gui.done", button -> close()));
    }

    private int styleIndex() {
        String id = phase.getTetherStyle();
        for (int i = 0; i < HookCordStyles.values().size(); i++) {
            if (HookCordStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addLabel(new GuiLabel(leftId, label, guiLeft + 6, y + 6));
        addPairedField(leftId, guiLeft + 130, y, leftValue, leftMin, leftMax, leftFallback, 52);
        addPairedField(rightId, guiLeft + 190, y, rightValue, rightMin, rightMax, rightFallback, 52);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback,
                                int width) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
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
            setSubGui(new SubGuiBossEffectList(phase.getTetherEffects(), "cnpcgeckoaddon.boss.effects_tether"));
        } else if (button.id == FAIL_EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.getTetherFailEffects(),
                    "cnpcgeckoaddon.boss.effects_tether_fail"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setTetherEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANCHOR_BUTTON) {
            phase.setTetherAnchor(button.getValue());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setTetherTargetMode(button.getValue());
        } else if (button.id == STYLE_BUTTON) {
            phase.setTetherStyle(HookCordStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting tether animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setTetherAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setTetherActionDelayTicks);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setTetherAnimation(value);
            else animation.setValue(phase.getTetherAnimation());
        }
        GuiTextFieldNop count = getTextField(TARGET_COUNT_FIELD);
        if (count != null) phase.setTetherTargetCount(count.getInteger());
        GuiTextFieldNop distance = getTextField(BREAK_DISTANCE_FIELD);
        if (distance != null) phase.setTetherBreakDistance(distance.getInteger());
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setTetherDurationTicks(duration.getInteger());
        GuiTextFieldNop pull = getTextField(PULL_FIELD);
        if (pull != null) phase.setTetherPull(pull.getInteger());
        GuiTextFieldNop failDamage = getTextField(FAIL_DAMAGE_FIELD);
        if (failDamage != null) phase.setTetherFailDamage(failDamage.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setTetherActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setTetherCooldownTicks(cooldown.getInteger());
        GuiTextFieldNop width = getTextField(WIDTH_FIELD);
        if (width != null) phase.setTetherWidthPercent(width.getInteger());
    }
}
