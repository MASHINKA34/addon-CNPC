package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Marks: a circle carried on somebody that the party has to gather into or clear out of. */
public final class SubGuiBossMark extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int MODE_BUTTON = 3;
    private static final int TARGET_COUNT_FIELD = 4;
    private static final int TARGET_MODE_BUTTON = 5;
    private static final int FUSE_FIELD = 6;
    private static final int RADIUS_FIELD = 7;
    private static final int FOLLOW_BUTTON = 8;
    private static final int MIN_PLAYERS_FIELD = 9;
    private static final int SELF_DAMAGE_FIELD = 10;
    private static final int DAMAGE_FIELD = 11;
    private static final int FAIL_DAMAGE_FIELD = 12;
    private static final int ACTION_DELAY_FIELD = 13;
    private static final int COOLDOWN_FIELD = 14;
    private static final int VFX_STYLE_BUTTON = 15;
    private static final int EFFECTS_BUTTON = 67;
    private static final int FAIL_EFFECTS_BUTTON = 68;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossMark(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
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
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.mark_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isMarkEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 108, y, 86, 20,
                phase.getMarkAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        // The first thing to pick, because half the rows below it belong to one rule or the
        // other and change with it.
        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.mark_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.MARK_MODE_LABELS, phase.getMarkMode()));
        y += 21;

        // How many marks and who they go on, on one line: the two answer the same question.
        addLabel(new GuiLabel(TARGET_COUNT_FIELD, "cnpcgeckoaddon.boss.mark_targets", guiLeft + 6, y + 6));
        addPairedField(TARGET_COUNT_FIELD, guiLeft + 72, y, phase.getMarkTargetCount(), 1, 8, 1, 38);
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getMarkTargetMode()));
        y += 21;

        // The two numbers the whole decision is made of: how long there is, and how far the
        // circle reaches while it lasts.
        addPairRow(FUSE_FIELD, RADIUS_FIELD, "cnpcgeckoaddon.boss.mark_fuse", y,
                phase.getMarkFuseTicks(), 10, 400, 60,
                phase.getMarkRadius(), 1, 16, 4);
        y += 21;

        addLabel(new GuiLabel(FOLLOW_BUTTON, "cnpcgeckoaddon.boss.mark_follow", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, FOLLOW_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isMarkFollow()));
        y += 21;

        // One row, two settings: each rule has exactly one number of its own, and only the
        // rule in force is ever shown here.
        addNumberField(MIN_PLAYERS_FIELD, "cnpcgeckoaddon.boss.mark_min_players", y,
                phase.getMarkMinPlayers(), 1, 10, 2);
        addNumberField(SELF_DAMAGE_FIELD, "cnpcgeckoaddon.boss.mark_self_damage", y,
                phase.getMarkSelfDamage(), 0, 1000, 0);
        y += 21;

        addPairRow(DAMAGE_FIELD, FAIL_DAMAGE_FIELD, "cnpcgeckoaddon.boss.mark_damage", y,
                phase.getMarkDamage(), 0, 1000, 30,
                phase.getMarkFailDamage(), 0, 1000, 60);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getMarkActionDelayTicks(), 0, 1200, 12,
                phase.getMarkCooldownTicks(), 1, 12000, 240);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.mark_hint", y + 3);
        // Two effect lists, because the gather does two different things to the people
        // inside it: it shares the hit out when they came, and punishes them when they did not.
        int buttonsY = Math.max(hintY + 4, guiTop + 253);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 116, 20,
                "cnpcgeckoaddon.boss.mark_effects"));
        addButton(new GuiButtonNop(this, FAIL_EFFECTS_BUTTON, guiLeft + 126, buttonsY, 116, 20,
                "cnpcgeckoaddon.boss.mark_fail_effects"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, buttonsY + 24, 60, 20,
                "gui.done", button -> close()));
        applyModeRows();
    }

    /**
     * Takes the rows the rule in force does not own off the screen.
     *
     * <p>Hidden in place rather than laid out again: this GUI framework has no
     * widget-clearing rebuild, so a screen that re-flowed itself on every click of the rule
     * button would stack a second copy of every row on the first. The two rule-specific
     * numbers therefore share one line, and only one of them is ever standing on it.</p>
     */
    private void applyModeRows() {
        boolean gather = phase.getMarkMode() == BossPhaseData.MARK_MODE_SOAK;
        showRow(MIN_PLAYERS_FIELD, gather);
        showRow(SELF_DAMAGE_FIELD, !gather);
        GuiTextFieldNop failDamage = getTextField(FAIL_DAMAGE_FIELD);
        if (failDamage != null) {
            failDamage.enabled = gather;
        }
        // The damage label carries the failure half of its pair, so it says one thing while
        // that half is on the screen and another once it is gone.
        GuiLabel damage = getLabel(DAMAGE_FIELD);
        if (damage != null) {
            damage.setMessage(Component.translatable(gather
                    ? "cnpcgeckoaddon.boss.mark_damage" : "cnpcgeckoaddon.boss.mark_damage_spread"));
        }
        GuiButtonNop failEffects = getButton(FAIL_EFFECTS_BUTTON);
        if (failEffects != null) {
            failEffects.shown = gather;
            // Hidden is not enough on its own: an unshown button still takes the click.
            failEffects.setEnabled(gather);
        }
    }

    /** Shows or hides one labelled number field, label and all. */
    private void showRow(int id, boolean shown) {
        GuiLabel label = getLabel(id);
        if (label != null) {
            label.enabled = shown;
        }
        GuiTextFieldNop field = getTextField(id);
        if (field != null) {
            field.enabled = shown;
        }
    }

    private int vfxStyleIndex() {
        String id = phase.getMarkVfx();
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
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
            setSubGui(new SubGuiBossEffectList(phase.getMarkEffects(), "cnpcgeckoaddon.boss.effects_mark"));
        } else if (button.id == FAIL_EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.getMarkFailEffects(),
                    "cnpcgeckoaddon.boss.effects_mark_fail"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setMarkEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == FOLLOW_BUTTON) {
            phase.setMarkFollow(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            phase.setMarkMode(button.getValue());
            applyModeRows();
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setMarkTargetMode(button.getValue());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.setMarkVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting mark animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setMarkAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setMarkActionDelayTicks);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setMarkAnimation(value);
            else animation.setValue(phase.getMarkAnimation());
        }
        GuiTextFieldNop count = getTextField(TARGET_COUNT_FIELD);
        if (count != null) phase.setMarkTargetCount(count.getInteger());
        GuiTextFieldNop fuse = getTextField(FUSE_FIELD);
        if (fuse != null) phase.setMarkFuseTicks(fuse.getInteger());
        GuiTextFieldNop radius = getTextField(RADIUS_FIELD);
        if (radius != null) phase.setMarkRadius(radius.getInteger());
        // Read whether or not the rule in force shows them: a hidden row keeps the number a
        // builder typed into it under the other rule, rather than losing it on a stray click.
        GuiTextFieldNop minPlayers = getTextField(MIN_PLAYERS_FIELD);
        if (minPlayers != null) phase.setMarkMinPlayers(minPlayers.getInteger());
        GuiTextFieldNop selfDamage = getTextField(SELF_DAMAGE_FIELD);
        if (selfDamage != null) phase.setMarkSelfDamage(selfDamage.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setMarkDamage(damage.getInteger());
        GuiTextFieldNop failDamage = getTextField(FAIL_DAMAGE_FIELD);
        if (failDamage != null) phase.setMarkFailDamage(failDamage.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setMarkActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setMarkCooldownTicks(cooldown.getInteger());
    }
}
