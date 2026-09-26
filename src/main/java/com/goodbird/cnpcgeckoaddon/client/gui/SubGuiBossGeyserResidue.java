package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/** The residue an eruption leaves on the floor: its doses, and the stacks that build on whoever stands in it. */
public final class SubGuiBossGeyserResidue extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int LIFETIME_FIELD = 2;
    private static final int RADIUS_FIELD = 3;
    private static final int DAMAGE_FIELD = 4;
    private static final int INTERVAL_FIELD = 5;
    private static final int STACK_TICKS_FIELD = 6;
    private static final int MAX_STACKS_FIELD = 7;
    private static final int DECAY_FIELD = 8;
    private static final int HEIGHT_FIELD = 9;
    private static final int EFFECTS_BUTTON = 10;
    private static final int PARTICLES_BUTTON = 11;
    private static final int SOUND_BUTTON = 12;
    private static final int SOUND_INTERVAL_FIELD = 13;
    private static final int HIT_PARTICLES_BUTTON = 14;
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
    private static final int FIELD_X = 172;
    private static final int FIELD_WIDTH = 70;
    private static final int PAIR_X = 130;
    private static final int PAIR_SECOND_X = 190;
    private static final int PAIR_WIDTH = 52;
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;
    /** The switch, eight numbers on six rows, the potions, the three cues and the sound's interval. */
    private static final int ROWS = 12;

    private static final String HINT = "cnpcgeckoaddon.boss.geyser_residue_hint";

    private final BossGeyserSettings geyser;
    private int wrappedLabel;

    public SubGuiBossGeyserResidue(BossGeyserSettings geyser) {
        this.geyser = geyser;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: the locale decides how many lines the hint takes.
        imageHeight = FIRST_ROW + ROWS * ROW + HINT_GAP + wrappedHintHeight(HINT) + BUTTONS_GAP
                + CONTROL_HEIGHT + BOTTOM_MARGIN;
        super.init();
        wrappedLabel = WRAPPED_LABEL;
        addLabel(new ThemeLabel(TITLE_LABEL, "cnpcgeckoaddon.boss.geyser_residue_title", guiLeft + LABEL_X,
                guiTop + 5, 0xFFFFFF));
        int y = guiTop + FIRST_ROW;
        toggle(ENABLED_BUTTON, "cnpcgeckoaddon.boss.geyser_residue_enabled", y, geyser.isResidueEnabled());
        y += ROW;
        single(LIFETIME_FIELD, "cnpcgeckoaddon.boss.geyser_residue_lifetime", y, geyser.getResidueLifetimeTicks(),
                BossGeyserSettings.MIN_RESIDUE_LIFETIME_TICKS, BossGeyserSettings.MAX_RESIDUE_LIFETIME_TICKS, 200);
        y += ROW;
        single(RADIUS_FIELD, "cnpcgeckoaddon.boss.geyser_residue_radius", y, geyser.getResidueRadius(),
                0, BossGeyserSettings.MAX_RESIDUE_RADIUS, 0);
        y += ROW;
        pair(DAMAGE_FIELD, INTERVAL_FIELD, "cnpcgeckoaddon.boss.geyser_residue_hit", y,
                geyser.getResidueDamage(), 0, BossGeyserSettings.MAX_DAMAGE, 2,
                geyser.getResidueIntervalTicks(), BossGeyserSettings.MIN_RESIDUE_INTERVAL_TICKS,
                BossGeyserSettings.MAX_RESIDUE_INTERVAL_TICKS, 20);
        y += ROW;
        pair(STACK_TICKS_FIELD, MAX_STACKS_FIELD, "cnpcgeckoaddon.boss.geyser_residue_stacks", y,
                geyser.getResidueStackTicks(), BossGeyserSettings.MIN_RESIDUE_STACK_TICKS,
                BossGeyserSettings.MAX_RESIDUE_STACK_TICKS, 40,
                geyser.getResidueMaxStacks(), BossGeyserSettings.MIN_RESIDUE_MAX_STACKS,
                BossGeyserSettings.MAX_RESIDUE_MAX_STACKS, 4);
        y += ROW;
        single(DECAY_FIELD, "cnpcgeckoaddon.boss.geyser_residue_decay", y, geyser.getResidueDecayTicks(),
                BossGeyserSettings.MIN_RESIDUE_DECAY_TICKS, BossGeyserSettings.MAX_RESIDUE_DECAY_TICKS, 60);
        y += ROW;
        single(HEIGHT_FIELD, "cnpcgeckoaddon.boss.geyser_residue_height", y, geyser.getResidueHeightTenths(),
                BossGeyserSettings.MIN_RESIDUE_HEIGHT, BossGeyserSettings.MAX_RESIDUE_HEIGHT, 10);
        y += ROW;
        addButton(new ThemeButton(this, EFFECTS_BUTTON, guiLeft + LABEL_X, y, RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT,
                "cnpcgeckoaddon.boss.effects_settings"));
        y += ROW;
        addCueButton(PARTICLES_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_residue", y, geyser.getResidueParticles());
        y += ROW;
        addCueButton(SOUND_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_residue_sound", y, geyser.getResidueSound());
        y += ROW;
        single(SOUND_INTERVAL_FIELD, "cnpcgeckoaddon.boss.geyser_residue_sound_interval", y,
                geyser.getResidueSoundIntervalTicks(), BossGeyserSettings.MIN_RESIDUE_SOUND_INTERVAL_TICKS,
                BossGeyserSettings.MAX_RESIDUE_SOUND_INTERVAL_TICKS, 40);
        y += ROW;
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_residue_hit", y,
                geyser.getResidueHitParticles());
        y += ROW + HINT_GAP;
        y = addWrappedHint(HINT_LABEL, HINT, y) + BUTTONS_GAP;
        addDoneButton(guiLeft + 182, y, 60, CONTROL_HEIGHT);
    }

    private void toggle(int id, String key, int y, boolean value) {
        rowLabel(id, key, y, TOGGLE_X);
        addButton(new ThemeYesNo(this, id, guiLeft + TOGGLE_X, y, TOGGLE_WIDTH, CONTROL_HEIGHT, value));
    }

    /** One number on a line, in the column the plain number fields use. */
    private void single(int id, String key, int y, int value, int min, int max, int fallback) {
        rowLabel(id, key, y, FIELD_X);
        number(id, guiLeft + FIELD_X, y, FIELD_WIDTH, value, min, max, fallback);
    }

    /** Two small numbers on one line: the pair the label names with a slash between them. */
    private void pair(int leftId, int rightId, String key, int y,
                      int leftValue, int leftMin, int leftMax, int leftFallback,
                      int rightValue, int rightMin, int rightMax, int rightFallback) {
        rowLabel(leftId, key, y, PAIR_X);
        number(leftId, guiLeft + PAIR_X, y, PAIR_WIDTH, leftValue, leftMin, leftMax, leftFallback);
        number(rightId, guiLeft + PAIR_SECOND_X, y, PAIR_WIDTH, rightValue, rightMin, rightMax, rightFallback);
    }

    private void number(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new ThemeTextField(id, this, x, y, width, CONTROL_HEIGHT, Integer.toString(value));
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
        // hands the text back as is.
        List<String> lines = wrapLines(Component.translatable(key).getString(), width);
        if (lines.size() == 1) {
            addLabel(new ThemeLabel(id, key, guiLeft + LABEL_X, y + LABEL_DROP));
            return;
        }
        int top = y + (CONTROL_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new ThemeLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(geyser.getResidueEffects(),
                    "cnpcgeckoaddon.boss.effects_geyser_residue"));
        } else if (button.id == ENABLED_BUTTON) {
            geyser.setResidueEnabled(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(LIFETIME_FIELD, geyser::setResidueLifetimeTicks);
        applyNumberField(RADIUS_FIELD, geyser::setResidueRadius);
        applyNumberField(DAMAGE_FIELD, geyser::setResidueDamage);
        applyNumberField(INTERVAL_FIELD, geyser::setResidueIntervalTicks);
        applyNumberField(STACK_TICKS_FIELD, geyser::setResidueStackTicks);
        applyNumberField(MAX_STACKS_FIELD, geyser::setResidueMaxStacks);
        applyNumberField(DECAY_FIELD, geyser::setResidueDecayTicks);
        applyNumberField(HEIGHT_FIELD, geyser::setResidueHeightTenths);
        applyNumberField(SOUND_INTERVAL_FIELD, geyser::setResidueSoundIntervalTicks);
    }
}
