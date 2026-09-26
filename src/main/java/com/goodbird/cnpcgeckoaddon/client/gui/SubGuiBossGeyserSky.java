package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossGeyserSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/** The geyser's second strike: the column that falls back onto the circle after the eruption. */
public final class SubGuiBossGeyserSky extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int DELAY_FIELD = 2;
    private static final int HEIGHT_FIELD = 3;
    private static final int FALL_FIELD = 4;
    private static final int RADIUS_FIELD = 5;
    private static final int DAMAGE_FIELD = 6;
    private static final int PRESS_FIELD = 7;
    private static final int VFX_BUTTON = 8;
    private static final int EFFECTS_BUTTON = 9;
    private static final int FALL_PARTICLES_BUTTON = 10;
    private static final int FALL_SOUND_BUTTON = 11;
    private static final int HIT_SOUND_BUTTON = 12;
    private static final int HIT_PARTICLES_BUTTON = 13;
    private static final int TITLE_LABEL = 30;
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
    private static final int CHOICE_X = 112;
    private static final int CHOICE_WIDTH = 130;
    private static final int BUTTONS_GAP = 4;
    private static final int BOTTOM_MARGIN = 8;
    /** The switch, six numbers on five rows, the wave, the potions and the four cues. */
    private static final int ROWS = 12;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey).toArray(String[]::new);

    private final BossGeyserSettings geyser;
    private int wrappedLabel;

    public SubGuiBossGeyserSky(BossGeyserSettings geyser) {
        this.geyser = geyser;
        imageWidth = 256;
        // Settled here since the panel is centred on it: the rows, then the Done under them.
        imageHeight = FIRST_ROW + ROWS * ROW + BUTTONS_GAP + CONTROL_HEIGHT + BOTTOM_MARGIN;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        wrappedLabel = WRAPPED_LABEL;
        addLabel(new ThemeLabel(TITLE_LABEL, "cnpcgeckoaddon.boss.geyser_sky_title", guiLeft + LABEL_X, guiTop + 5,
                0xFFFFFF));
        int y = guiTop + FIRST_ROW;
        toggle(ENABLED_BUTTON, "cnpcgeckoaddon.boss.geyser_sky_enabled", y, geyser.isSkyEnabled());
        y += ROW;
        single(DELAY_FIELD, "cnpcgeckoaddon.boss.geyser_sky_delay", y, geyser.getSkyDelayTicks(),
                BossGeyserSettings.MIN_SKY_DELAY_TICKS, BossGeyserSettings.MAX_SKY_DELAY_TICKS, 30);
        y += ROW;
        pair(HEIGHT_FIELD, FALL_FIELD, "cnpcgeckoaddon.boss.geyser_sky_fall", y,
                geyser.getSkyHeight(), BossGeyserSettings.MIN_SKY_HEIGHT, BossGeyserSettings.MAX_SKY_HEIGHT, 12,
                geyser.getSkyFallTicks(), BossGeyserSettings.MIN_SKY_FALL_TICKS,
                BossGeyserSettings.MAX_SKY_FALL_TICKS, 10);
        y += ROW;
        single(RADIUS_FIELD, "cnpcgeckoaddon.boss.geyser_sky_radius", y, geyser.getSkyRadius(),
                0, BossGeyserSettings.MAX_SKY_RADIUS, 0);
        y += ROW;
        single(DAMAGE_FIELD, "cnpcgeckoaddon.boss.geyser_sky_damage", y, geyser.getSkyDamage(),
                0, BossGeyserSettings.MAX_DAMAGE, 6);
        y += ROW;
        single(PRESS_FIELD, "cnpcgeckoaddon.boss.geyser_sky_press", y, geyser.getSkyPressTenths(),
                0, BossGeyserSettings.MAX_SKY_PRESS, 12);
        y += ROW;
        choice(VFX_BUTTON, "cnpcgeckoaddon.boss.geyser_sky_vfx", y, VFX_STYLE_LABELS, vfxStyleIndex(geyser.getSkyVfx()));
        y += ROW;
        addButton(new ThemeButton(this, EFFECTS_BUTTON, guiLeft + LABEL_X, y, RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT,
                "cnpcgeckoaddon.boss.effects_settings"));
        y += ROW;
        addCueButton(FALL_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_sky_fall", y, geyser.getSkyParticles());
        y += ROW;
        addCueButton(FALL_SOUND_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_sky_sound", y, geyser.getSkySound());
        y += ROW;
        addCueButton(HIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_sky_hit", y, geyser.getSkyHitSound());
        y += ROW;
        addCueButton(HIT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.geyser_cue_sky_hit_particles", y,
                geyser.getSkyHitParticles());
        y += ROW + BUTTONS_GAP;
        addDoneButton(guiLeft + 182, y, 60, CONTROL_HEIGHT);
    }

    private void toggle(int id, String key, int y, boolean value) {
        rowLabel(id, key, y, TOGGLE_X);
        addButton(new ThemeYesNo(this, id, guiLeft + TOGGLE_X, y, TOGGLE_WIDTH, CONTROL_HEIGHT, value));
    }

    private void choice(int id, String key, int y, String[] values, int selected) {
        rowLabel(id, key, y, CHOICE_X);
        addButton(new ThemeButton(this, id, guiLeft + CHOICE_X, y, CHOICE_WIDTH, CONTROL_HEIGHT, values, selected));
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
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(geyser.getSkyEffects(), "cnpcgeckoaddon.boss.effects_geyser_sky"));
        } else if (button.id == ENABLED_BUTTON) {
            geyser.setSkyEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == VFX_BUTTON) {
            geyser.setSkyVfx(AreaVfxStyles.values().get(button.getValue()).id());
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(DELAY_FIELD, geyser::setSkyDelayTicks);
        applyNumberField(HEIGHT_FIELD, geyser::setSkyHeight);
        applyNumberField(FALL_FIELD, geyser::setSkyFallTicks);
        applyNumberField(RADIUS_FIELD, geyser::setSkyRadius);
        applyNumberField(DAMAGE_FIELD, geyser::setSkyDamage);
        applyNumberField(PRESS_FIELD, geyser::setSkyPressTenths);
    }
}
