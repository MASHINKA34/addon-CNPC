package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossShadowSettings;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/** Shadow copies: copies of the boss that fight beside it, trade places with it, and end by being taken back or set off. */
public final class SubGuiBossShadow extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int COUNT_FIELD = 5;
    private static final int HEALTH_MODE_BUTTON = 6;
    private static final int HEALTH_PERCENT_FIELD = 7;
    private static final int HEALTH_VALUE_FIELD = 8;
    private static final int LIFETIME_FIELD = 9;
    private static final int ABILITIES_BUTTON = 10;
    private static final int POINTS_BUTTON = 11;
    private static final int SPAWN_RADIUS_FIELD = 12;
    private static final int HIDE_BAR_BUTTON = 13;
    private static final int SWAP_BUTTON = 14;
    private static final int SWAP_INTERVAL_FIELD = 15;
    private static final int SWAP_IDLE_BUTTON = 16;
    private static final int FINALE_BUTTON = 17;
    private static final int FINALE_AT_BUTTON = 18;
    private static final int ABSORB_HEAL_FIELD = 19;
    private static final int ABSORB_DAMAGE_FIELD = 20;
    private static final int ABSORB_STACKS_FIELD = 21;
    private static final int ABSORB_BUFF_FIELD = 22;
    private static final int ABSORB_BEAM_BUTTON = 23;
    private static final int BLAST_RADIUS_FIELD = 24;
    private static final int BLAST_DAMAGE_FIELD = 25;
    private static final int BLAST_KNOCKBACK_FIELD = 26;
    private static final int BLAST_VFX_BUTTON = 27;
    private static final int TUNING_BUTTON = 28;
    private static final int EFFECTS_BUTTON = 67;

    private static final int TITLE_LABEL = 30;
    private static final int HINT_LABEL = 40;
    /** Where the second lines of wrapped row labels are counted from. */
    private static final int WRAPPED_LABEL = 80;

    /** The first row's offset from the top of the panel, clear of the title. */
    private static final int FIRST_ROW = 18;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW = CONTROL_HEIGHT + 1;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;
    private static final int RIGHT_EDGE = 242;
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final int CHOICE_X = 112;
    private static final int CHOICE_WIDTH = 130;
    /** An animation row: the name typed in, and the picker beside it. */
    private static final int SELECT_FIELD_X = 108;
    private static final int SELECT_FIELD_WIDTH = 86;
    private static final int SELECT_BUTTON_X = 198;
    private static final int SELECT_BUTTON_WIDTH = 44;
    /** Two numbers to a row, the second flush with the right edge; a lone number takes the second place. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    /** Three numbers to a row, the last flush with the right edge. */
    private static final int TRIPLE_X = 118;
    private static final int TRIPLE_SECOND_X = 160;
    private static final int TRIPLE_THIRD_X = 202;
    private static final int TRIPLE_WIDTH = 40;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String HINT = "cnpcgeckoaddon.boss.shadow_hint";
    private static final String[] BEAM_STYLE_LABELS = HookCordStyles.values().stream()
            .map(HookCordStyles.Style::translationKey).toArray(String[]::new);
    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey).toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossShadow(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: the locale decides how many lines the hint takes. The copies have more
        // settings than a panel, so it scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to - the hurricane screen's way of keeping the panel and its contents together.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossShadowSettings shadow = phase.shadow();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.shadow_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, shadow.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, shadow.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                shadow.getActionDelayTicks(), 0, 1200, 20, shadow.getCooldownTicks(), 1, 12000, 600);
        y = single(place, COUNT_FIELD, "cnpcgeckoaddon.boss.shadow_count", y,
                shadow.getCount(), 1, BossShadowSettings.MAX_COUNT, 2);
        y = choice(place, HEALTH_MODE_BUTTON, "cnpcgeckoaddon.boss.shadow_health", y,
                BossShadowSettings.HEALTH_LABELS, shadow.getHealthMode());
        y = pair(place, HEALTH_PERCENT_FIELD, HEALTH_VALUE_FIELD, "cnpcgeckoaddon.boss.shadow_health_amount", y,
                shadow.getHealthPercent(), 1, 100, 20,
                shadow.getHealthValue(), 1, BossShadowSettings.MAX_HEALTH_VALUE, 100);
        y = single(place, LIFETIME_FIELD, "cnpcgeckoaddon.boss.shadow_lifetime", y,
                shadow.getLifetimeTicks(), 0, BossShadowSettings.MAX_LIFETIME_TICKS, 600);
        y = wide(place, ABILITIES_BUTTON, "cnpcgeckoaddon.boss.shadow_abilities", y);
        y = wide(place, POINTS_BUTTON, "cnpcgeckoaddon.boss.shadow_points", y);
        y = single(place, SPAWN_RADIUS_FIELD, "cnpcgeckoaddon.boss.shadow_spawn_radius", y,
                shadow.getSpawnRadius(), 1, BossShadowSettings.MAX_SPAWN_RADIUS, 4);
        y = toggle(place, HIDE_BAR_BUTTON, "cnpcgeckoaddon.boss.shadow_hide_bar", y, shadow.isHideBossBar());
        y = toggle(place, SWAP_BUTTON, "cnpcgeckoaddon.boss.shadow_swap", y, shadow.isSwapEnabled());
        y = single(place, SWAP_INTERVAL_FIELD, "cnpcgeckoaddon.boss.shadow_swap_interval", y,
                shadow.getSwapIntervalTicks(), BossShadowSettings.MIN_SWAP_INTERVAL_TICKS,
                BossShadowSettings.MAX_SWAP_INTERVAL_TICKS, 100);
        y = toggle(place, SWAP_IDLE_BUTTON, "cnpcgeckoaddon.boss.shadow_swap_idle", y, shadow.isSwapOnlyIdle());
        y = choice(place, FINALE_BUTTON, "cnpcgeckoaddon.boss.shadow_finale", y,
                BossShadowSettings.FINALE_LABELS, shadow.getFinale());
        y = choice(place, FINALE_AT_BUTTON, "cnpcgeckoaddon.boss.shadow_finale_at", y,
                BossShadowSettings.FINALE_AT_LABELS, shadow.getFinaleAt());
        y = pair(place, ABSORB_HEAL_FIELD, ABSORB_DAMAGE_FIELD, "cnpcgeckoaddon.boss.shadow_absorb", y,
                shadow.getAbsorbHealPercent(), 0, BossShadowSettings.MAX_ABSORB_HEAL_PERCENT, 10,
                shadow.getAbsorbDamagePercent(), 0, BossShadowSettings.MAX_ABSORB_DAMAGE_PERCENT, 15);
        y = pair(place, ABSORB_STACKS_FIELD, ABSORB_BUFF_FIELD, "cnpcgeckoaddon.boss.shadow_absorb_stacks", y,
                shadow.getAbsorbMaxStacks(), 1, BossShadowSettings.MAX_ABSORB_STACKS, 4,
                shadow.getAbsorbBuffTicks(), BossShadowSettings.MIN_ABSORB_BUFF_TICKS,
                BossShadowSettings.MAX_ABSORB_BUFF_TICKS, 600);
        y = choice(place, ABSORB_BEAM_BUTTON, "cnpcgeckoaddon.boss.shadow_absorb_beam", y,
                BEAM_STYLE_LABELS, cordStyleIndex(shadow.getAbsorbBeam()));
        y = triple(place, BLAST_RADIUS_FIELD, BLAST_DAMAGE_FIELD, BLAST_KNOCKBACK_FIELD,
                "cnpcgeckoaddon.boss.shadow_blast", y,
                shadow.getBlastRadius(), 1, BossShadowSettings.MAX_BLAST_RADIUS, 4,
                shadow.getBlastDamage(), 0, BossShadowSettings.MAX_BLAST_DAMAGE, 12,
                shadow.getBlastKnockback(), 0, BossShadowSettings.MAX_BLAST_KNOCKBACK, 2);
        y = choice(place, BLAST_VFX_BUTTON, "cnpcgeckoaddon.boss.shadow_blast_vfx", y,
                VFX_STYLE_LABELS, vfxStyleIndex(shadow.getBlastVfx()));
        y = wide(place, TUNING_BUTTON, "cnpcgeckoaddon.boss.shadow_tuning", y);

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
        }
        y += wrappedHintHeight(HINT) + BUTTONS_GAP;
        if (place) {
            addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + LABEL_X, guiTop + y, 120, CONTROL_HEIGHT,
                    "cnpcgeckoaddon.boss.effects_shadow_blast"));
            addDoneButton(guiLeft + 182, guiTop + y, 60, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    private int toggle(boolean place, int id, String key, int y, boolean value) {
        if (place) {
            rowLabel(id, key, y, TOGGLE_X);
            addButton(new GuiButtonYesNo(this, id, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH, CONTROL_HEIGHT, value));
        }
        return y + ROW;
    }

    private int choice(boolean place, int id, String key, int y, String[] values, int selected) {
        if (place) {
            rowLabel(id, key, y, CHOICE_X);
            addButton(new GuiButtonNop(this, id, guiLeft + CHOICE_X, guiTop + y, CHOICE_WIDTH, CONTROL_HEIGHT,
                    values, selected));
        }
        return y + ROW;
    }

    /** A button the whole row wide, opening a screen of its own. */
    private int wide(boolean place, int id, String key, int y) {
        if (place) {
            addButton(new GuiButtonNop(this, id, guiLeft + LABEL_X, guiTop + y, RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT,
                    key));
        }
        return y + ROW;
    }

    /** An animation: typed in, or picked from the model's own list. */
    private int select(boolean place, int id, String key, int y, String value) {
        if (place) {
            rowLabel(id, key, y, SELECT_FIELD_X);
            addTextField(new GuiTextFieldNop(id, this, guiLeft + SELECT_FIELD_X, guiTop + y, SELECT_FIELD_WIDTH,
                    CONTROL_HEIGHT, value));
            addButton(new GuiButtonNop(this, id, guiLeft + SELECT_BUTTON_X, guiTop + y, SELECT_BUTTON_WIDTH,
                    CONTROL_HEIGHT, "mco.template.button.select"));
        }
        return y + ROW;
    }

    /** Two small numbers on one line: the pairs the labels name with a slash between them. */
    private int pair(boolean place, int leftId, int rightId, String key, int y,
                     int leftValue, int leftMin, int leftMax, int leftFallback,
                     int rightValue, int rightMin, int rightMax, int rightFallback) {
        if (place) {
            rowLabel(leftId, key, y, PAIR_X);
            number(leftId, guiLeft + PAIR_X, guiTop + y, PAIR_WIDTH, leftValue, leftMin, leftMax, leftFallback);
            number(rightId, guiLeft + PAIR_SECOND_X, guiTop + y, PAIR_WIDTH, rightValue, rightMin, rightMax,
                    rightFallback);
        }
        return y + ROW;
    }

    /** Three small numbers on one line, for the blast's radius, damage and knockback. */
    private int triple(boolean place, int firstId, int secondId, int thirdId, String key, int y,
                       int firstValue, int firstMin, int firstMax, int firstFallback,
                       int secondValue, int secondMin, int secondMax, int secondFallback,
                       int thirdValue, int thirdMin, int thirdMax, int thirdFallback) {
        if (place) {
            rowLabel(firstId, key, y, TRIPLE_X);
            number(firstId, guiLeft + TRIPLE_X, guiTop + y, TRIPLE_WIDTH, firstValue, firstMin, firstMax, firstFallback);
            number(secondId, guiLeft + TRIPLE_SECOND_X, guiTop + y, TRIPLE_WIDTH, secondValue, secondMin, secondMax,
                    secondFallback);
            number(thirdId, guiLeft + TRIPLE_THIRD_X, guiTop + y, TRIPLE_WIDTH, thirdValue, thirdMin, thirdMax,
                    thirdFallback);
        }
        return y + ROW;
    }

    /** One number on a line, flush with the right edge where a pair's second one sits. */
    private int single(boolean place, int id, String key, int y, int value, int min, int max, int fallback) {
        if (place) {
            rowLabel(id, key, y, PAIR_SECOND_X);
            number(id, guiLeft + PAIR_SECOND_X, guiTop + y, PAIR_WIDTH, value, min, max, fallback);
        }
        return y + ROW;
    }

    private void number(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, CONTROL_HEIGHT, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /**
     * A row's label, wrapped onto two lines rather than run under the control beside it: a
     * GuiLabel neither wraps nor clips, and the Russian names here are sentences.
     */
    private void rowLabel(int id, String key, int y, int controlX) {
        int width = controlX - LABEL_X - 4;
        // Not I18n.get: it runs the text through String.format; a translatable component
        // hands the text back as is, per cent signs and all.
        List<String> lines = wrapLines(Component.translatable(key).getString(), width);
        if (lines.size() == 1) {
            addLabel(new GuiLabel(id, key, guiLeft + LABEL_X, guiTop + y + LABEL_DROP));
            return;
        }
        int top = guiTop + y + (CONTROL_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new GuiLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    private static int cordStyleIndex(String id) {
        List<HookCordStyles.Style> styles = HookCordStyles.values();
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    private static int vfxStyleIndex(String id) {
        List<AreaVfxStyles.Style> styles = AreaVfxStyles.values();
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossShadowSettings shadow = phase.shadow();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(shadow.getBlastEffects(), "cnpcgeckoaddon.boss.effects_shadow_blast"));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossShadowTuning(shadow));
        } else if (button.id == ABILITIES_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossShadowAbilities(shadow));
        } else if (button.id == POINTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossMinionSpawnList(npc, phase, phaseIndex, shadow.getPoints(),
                    "cnpcgeckoaddon.boss.shadow_points_title", false));
        } else if (button.id == ENABLED_BUTTON) {
            shadow.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == HIDE_BAR_BUTTON) {
            shadow.setHideBossBar(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SWAP_BUTTON) {
            shadow.setSwapEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SWAP_IDLE_BUTTON) {
            shadow.setSwapOnlyIdle(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == HEALTH_MODE_BUTTON) {
            shadow.setHealthMode(button.getValue());
        } else if (button.id == FINALE_BUTTON) {
            shadow.setFinale(button.getValue());
        } else if (button.id == FINALE_AT_BUTTON) {
            shadow.setFinaleAt(button.getValue());
        } else if (button.id == ABSORB_BEAM_BUTTON) {
            shadow.setAbsorbBeam(HookCordStyles.values().get(button.getValue()).id());
        } else if (button.id == BLAST_VFX_BUTTON) {
            shadow.setBlastVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.shadow_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                shadow.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        shadow::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossShadowSettings shadow = phase.shadow();
        applyAnimation(shadow);
        applyNumberField(ACTION_DELAY_FIELD, shadow::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, shadow::setCooldownTicks);
        applyNumberField(COUNT_FIELD, shadow::setCount);
        applyNumberField(HEALTH_PERCENT_FIELD, shadow::setHealthPercent);
        applyNumberField(HEALTH_VALUE_FIELD, shadow::setHealthValue);
        applyNumberField(LIFETIME_FIELD, shadow::setLifetimeTicks);
        applyNumberField(SPAWN_RADIUS_FIELD, shadow::setSpawnRadius);
        applyNumberField(SWAP_INTERVAL_FIELD, shadow::setSwapIntervalTicks);
        applyNumberField(ABSORB_HEAL_FIELD, shadow::setAbsorbHealPercent);
        applyNumberField(ABSORB_DAMAGE_FIELD, shadow::setAbsorbDamagePercent);
        applyNumberField(ABSORB_STACKS_FIELD, shadow::setAbsorbMaxStacks);
        applyNumberField(ABSORB_BUFF_FIELD, shadow::setAbsorbBuffTicks);
        applyNumberField(BLAST_RADIUS_FIELD, shadow::setBlastRadius);
        applyNumberField(BLAST_DAMAGE_FIELD, shadow::setBlastDamage);
        applyNumberField(BLAST_KNOCKBACK_FIELD, shadow::setBlastKnockback);
    }

    /** Keeps a typed animation only when the model has it; otherwise the field snaps back. */
    private void applyAnimation(BossShadowSettings shadow) {
        GuiTextFieldNop field = getTextField(ANIMATION_FIELD);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            shadow.setAnimation(value);
        } else {
            field.setValue(shadow.getAnimation());
        }
    }
}
