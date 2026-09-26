package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/** Platforms: which of the builder's boxes the boss sets alight, the fuse, and what they do to whoever stays on. */
public final class SubGuiBossPlatform extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int PICK_BUTTON = 5;
    private static final int FUSE_FIELD = 6;
    private static final int DAMAGE_FIELD = 7;
    private static final int KNOCKBACK_FIELD = 8;
    private static final int LAUNCH_FIELD = 9;
    private static final int LINGER_FIELD = 10;
    private static final int LINGER_INTERVAL_FIELD = 11;
    private static final int VFX_BUTTON = 12;
    private static final int ZONES_BUTTON = 13;
    private static final int TUNING_BUTTON = 14;
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
    /** Two numbers to a row, the second flush with the right edge; a lone number sits where the second would. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String HINT = "cnpcgeckoaddon.boss.platform_hint";
    private static final String[] VFX_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossPlatform(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init() reads
        // it: the locale decides how many lines the wrapped labels and the hint take. The platforms
        // have more settings than a panel holds, so the screen scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to - the cone screen's way of keeping the panel and its contents from drifting apart.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossPlatformSettings platform = phase.platform();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new ThemeLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.platform_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, platform.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, platform.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                platform.getActionDelayTicks(), 0, BossPlatformSettings.MAX_ACTION_DELAY_TICKS, 10,
                platform.getCooldownTicks(), BossPlatformSettings.MIN_COOLDOWN_TICKS,
                BossPlatformSettings.MAX_COOLDOWN_TICKS, 300);
        y = choice(place, PICK_BUTTON, "cnpcgeckoaddon.boss.platform_pick", y,
                BossPhaseData.PLATFORM_PICK_LABELS, platform.getPickMode());
        y = single(place, FUSE_FIELD, "cnpcgeckoaddon.boss.platform_fuse", y, platform.getFuseTicks(),
                BossPlatformSettings.MIN_FUSE_TICKS, BossPlatformSettings.MAX_FUSE_TICKS, 60);
        y = pair(place, DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.platform_hit", y,
                platform.getDamage(), 0, BossPlatformSettings.MAX_DAMAGE, 12,
                platform.getKnockback(), 0, BossPlatformSettings.MAX_KNOCKBACK, 2);
        y = single(place, LAUNCH_FIELD, "cnpcgeckoaddon.boss.platform_launch", y, platform.getLaunch(),
                0, BossPlatformSettings.MAX_LAUNCH, 0);
        y = pair(place, LINGER_FIELD, LINGER_INTERVAL_FIELD, "cnpcgeckoaddon.boss.platform_linger", y,
                platform.getLingerTicks(), 0, BossPlatformSettings.MAX_LINGER_TICKS, 0,
                platform.getLingerIntervalTicks(), BossPlatformSettings.MIN_LINGER_INTERVAL_TICKS,
                BossPlatformSettings.MAX_LINGER_INTERVAL_TICKS, 20);
        y = choice(place, VFX_BUTTON, "cnpcgeckoaddon.boss.area_vfx", y, VFX_LABELS, vfxIndex(platform.getVfx()));
        if (place) {
            addButton(new ThemeButton(this, ZONES_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.platform_zones"));
        }
        y += ROW;

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
        }
        y += wrappedHintHeight(HINT) + BUTTONS_GAP;
        if (place) {
            addButton(new ThemeButton(this, TUNING_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.platform_tuning"));
        }
        y += ROW;
        if (place) {
            addButton(new ThemeButton(this, EFFECTS_BUTTON, guiLeft + LABEL_X, guiTop + y, 120, CONTROL_HEIGHT,
                    "cnpcgeckoaddon.boss.effects_settings"));
            addDoneButton(guiLeft + 182, guiTop + y, 60, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    private int toggle(boolean place, int id, String key, int y, boolean value) {
        if (place) {
            rowLabel(id, key, y, TOGGLE_X);
            addButton(new ThemeYesNo(this, id, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH, CONTROL_HEIGHT, value));
        }
        return y + ROW;
    }

    private int choice(boolean place, int id, String key, int y, String[] values, int selected) {
        if (place) {
            rowLabel(id, key, y, CHOICE_X);
            addButton(new ThemeButton(this, id, guiLeft + CHOICE_X, guiTop + y, CHOICE_WIDTH, CONTROL_HEIGHT,
                    values, selected));
        }
        return y + ROW;
    }

    /** An animation: typed in, or picked from the model's own list. */
    private int select(boolean place, int id, String key, int y, String value) {
        if (place) {
            rowLabel(id, key, y, SELECT_FIELD_X);
            addTextField(new ThemeTextField(id, this, guiLeft + SELECT_FIELD_X, guiTop + y, SELECT_FIELD_WIDTH,
                    CONTROL_HEIGHT, value));
            addButton(new ThemeButton(this, id, guiLeft + SELECT_BUTTON_X, guiTop + y, SELECT_BUTTON_WIDTH,
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
            number(leftId, guiLeft + PAIR_X, guiTop + y, leftValue, leftMin, leftMax, leftFallback);
            number(rightId, guiLeft + PAIR_SECOND_X, guiTop + y, rightValue, rightMin, rightMax, rightFallback);
        }
        return y + ROW;
    }

    /** One small number, flush with the right edge where a pair's second number would be. */
    private int single(boolean place, int id, String key, int y, int value, int min, int max, int fallback) {
        if (place) {
            rowLabel(id, key, y, PAIR_SECOND_X);
            number(id, guiLeft + PAIR_SECOND_X, guiTop + y, value, min, max, fallback);
        }
        return y + ROW;
    }

    private void number(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new ThemeTextField(id, this, x, y, PAIR_WIDTH, CONTROL_HEIGHT, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /**
     * A row's label, wrapped onto two lines rather than run under the control beside it: a
     * GuiLabel neither wraps nor clips, and the Russian names are wider than the English ones.
     */
    private void rowLabel(int id, String key, int y, int controlX) {
        int width = controlX - LABEL_X - 4;
        // Not I18n.get: it runs the text through String.format, which would turn a bare % in a
        // label into "Format error: ..."; a translatable component hands it back as is.
        List<String> lines = wrapLines(Component.translatable(key).getString(), width);
        if (lines.size() == 1) {
            addLabel(new ThemeLabel(id, key, guiLeft + LABEL_X, guiTop + y + LABEL_DROP));
            return;
        }
        int top = guiTop + y + (CONTROL_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new ThemeLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    /** Where this wave sits on the cycle button; an id the list no longer has reads as its first style. */
    private static int vfxIndex(String id) {
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossPlatformSettings platform = phase.platform();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(platform.getEffects(), "cnpcgeckoaddon.boss.effects_platform"));
        } else if (button.id == ZONES_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossPlatformZoneList(npc, phase, phaseIndex));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossPlatformTuning(platform));
        } else if (button.id == ENABLED_BUTTON) {
            platform.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == PICK_BUTTON) {
            platform.setPickMode(button.getValue());
        } else if (button.id == VFX_BUTTON) {
            platform.setVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.platform_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                platform.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        platform::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossPlatformSettings platform = phase.platform();
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            // Kept only when the model has it; otherwise the field snaps back.
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) {
                platform.setAnimation(value);
            } else {
                animation.setValue(platform.getAnimation());
            }
        }
        applyNumberField(ACTION_DELAY_FIELD, platform::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, platform::setCooldownTicks);
        applyNumberField(FUSE_FIELD, platform::setFuseTicks);
        applyNumberField(DAMAGE_FIELD, platform::setDamage);
        applyNumberField(KNOCKBACK_FIELD, platform::setKnockback);
        applyNumberField(LAUNCH_FIELD, platform::setLaunch);
        applyNumberField(LINGER_FIELD, platform::setLingerTicks);
        applyNumberField(LINGER_INTERVAL_FIELD, platform::setLingerIntervalTicks);
    }
}
