package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftDimension;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The crystals a rift asks its players to gather: how many stand where, what they look like, and
 * what counts as collecting one.
 *
 * <p>Its own screen rather than rows on the rift's, the way the rift's minions have their own:
 * these are the task, they have a look of their own to set, and the rift's page is already as
 * long as a panel can scroll.</p>
 */
public final class SubGuiBossRiftCrystals extends SubGuiFieldScreen {
    private static final int COUNT_FIELD = 1;
    private static final int RING_FIELD = 2;
    private static final int HOVER_FIELD = 3;
    private static final int BLOCK_FIELD = 4;
    private static final int BLOCK_SELECT_BUTTON = 5;
    private static final int COLOR_FIELD = 6;
    private static final int GLOW_BUTTON = 7;
    private static final int SPIN_FIELD = 8;
    private static final int BOB_FIELD = 9;
    private static final int BOB_PERIOD_FIELD = 10;
    private static final int SCALE_FIELD = 11;
    private static final int COLLECT_BUTTON = 12;
    private static final int RADIUS_FIELD = 13;
    private static final int ZONE_RING_BUTTON = 14;
    private static final int INTERVAL_FIELD = 15;
    private static final int COLLECT_SOUND_BUTTON = 16;
    private static final int COLLECT_PARTICLES_BUTTON = 17;
    private static final int AMBIENT_PARTICLES_BUTTON = 18;
    private static final int POINTS_BUTTON = 19;

    private static final int TITLE_LABEL = 30;
    private static final int HINT_LABEL = 40;
    /** Where the second lines of wrapped row labels are counted from. */
    private static final int WRAPPED_LABEL = 80;

    private static final int FIRST_ROW = 22;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW = CONTROL_HEIGHT + 1;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;
    private static final int RIGHT_EDGE = 242;
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final int CHOICE_X = 112;
    private static final int CHOICE_WIDTH = 130;
    /** A block id typed in, with the picker beside it. */
    private static final int SELECT_FIELD_X = 108;
    private static final int SELECT_FIELD_WIDTH = 86;
    private static final int SELECT_BUTTON_X = 198;
    private static final int SELECT_BUTTON_WIDTH = 44;
    /** Two small numbers to a row; a lone one takes the second place, flush with the right edge. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String HINT = "cnpcgeckoaddon.boss.rift_crystal_hint";

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossRiftCrystals(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: how many lines the hint takes is up to the locale.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they come
     * to, the rift's own screen's way.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossRiftSettings rift = phase.rift();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                    "cnpcgeckoaddon.boss.rift_crystals_title", phaseIndex), guiLeft + LABEL_X, guiTop + 7, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = pair(place, COUNT_FIELD, RING_FIELD, "cnpcgeckoaddon.boss.rift_crystal_ring", y,
                rift.getCrystalCount(), 1, BossRiftSettings.MAX_CRYSTAL_COUNT, 4,
                rift.getCrystalRingRadius(), BossRiftSettings.MIN_CRYSTAL_RING_RADIUS,
                BossRiftSettings.MAX_CRYSTAL_RING_RADIUS, 8);
        y = single(place, HOVER_FIELD, "cnpcgeckoaddon.boss.rift_crystal_hover", y, rift.getCrystalHoverTenths(),
                0, BossRiftSettings.MAX_CRYSTAL_HOVER_TENTHS, 20);
        y = select(place, BLOCK_FIELD, BLOCK_SELECT_BUTTON, "cnpcgeckoaddon.boss.rift_crystal_block", y,
                rift.getCrystalBlock());
        y = hex(place, COLOR_FIELD, "cnpcgeckoaddon.boss.rift_crystal_color", y, rift.getCrystalColor());
        y = toggle(place, GLOW_BUTTON, "cnpcgeckoaddon.boss.rift_crystal_glow", y, rift.isCrystalGlow());
        y = single(place, SPIN_FIELD, "cnpcgeckoaddon.boss.rift_crystal_spin", y, rift.getCrystalSpinDegrees(),
                0, BossRiftSettings.MAX_CRYSTAL_SPIN_DEGREES, 3);
        y = pair(place, BOB_FIELD, BOB_PERIOD_FIELD, "cnpcgeckoaddon.boss.rift_crystal_bob", y,
                rift.getCrystalBobTenths(), 0, BossRiftSettings.MAX_CRYSTAL_BOB_TENTHS, 3,
                rift.getCrystalBobPeriodTicks(), BossRiftSettings.MIN_CRYSTAL_BOB_PERIOD_TICKS,
                BossRiftSettings.MAX_CRYSTAL_BOB_PERIOD_TICKS, 40);
        y = single(place, SCALE_FIELD, "cnpcgeckoaddon.boss.rift_crystal_scale", y, rift.getCrystalScaleTenths(),
                BossRiftSettings.MIN_CRYSTAL_SCALE_TENTHS, BossRiftSettings.MAX_CRYSTAL_SCALE_TENTHS, 10);
        y = choice(place, COLLECT_BUTTON, "cnpcgeckoaddon.boss.rift_crystal_collect", y,
                BossRiftSettings.COLLECT_LABELS, rift.getCrystalCollectMode());
        y = single(place, RADIUS_FIELD, "cnpcgeckoaddon.boss.rift_crystal_radius", y,
                rift.getCrystalCollectRadiusTenths(), BossRiftSettings.MIN_CRYSTAL_RADIUS_TENTHS,
                BossRiftSettings.MAX_CRYSTAL_RADIUS_TENTHS, 15);
        y = toggle(place, ZONE_RING_BUTTON, "cnpcgeckoaddon.boss.rift_crystal_zone_ring", y,
                rift.isCrystalZoneRing());
        y = single(place, INTERVAL_FIELD, "cnpcgeckoaddon.boss.rift_crystal_interval", y,
                rift.getCrystalAmbientIntervalTicks(), BossRiftSettings.MIN_CRYSTAL_INTERVAL_TICKS,
                BossRiftSettings.MAX_CRYSTAL_INTERVAL_TICKS, 10);
        if (place) {
            addCueButton(COLLECT_SOUND_BUTTON, "cnpcgeckoaddon.boss.rift_cue_crystal_collect", guiTop + y,
                    rift.getCrystalCollectSound());
        }
        y += ROW;
        if (place) {
            addCueButton(COLLECT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.rift_cue_crystal_collect_particles",
                    guiTop + y, rift.getCrystalCollectParticles());
        }
        y += ROW;
        if (place) {
            addCueButton(AMBIENT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.rift_cue_crystal_ambient", guiTop + y,
                    rift.getCrystalAmbientParticles());
        }
        y += ROW;
        y = wide(place, POINTS_BUTTON, "cnpcgeckoaddon.boss.rift_crystal_points", y);

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
        }
        y += wrappedHintHeight(HINT) + BUTTONS_GAP;
        if (place) {
            addDoneButton(guiLeft + 182, guiTop + y, 60, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    private int toggle(boolean place, int id, String key, int y, boolean value) {
        if (place) {
            rowLabel(id, key, y, TOGGLE_X);
            addButton(new GuiButtonYesNo(this, id, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH, CONTROL_HEIGHT,
                    value));
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

    /** An id typed in, with a picker beside it. */
    private int select(boolean place, int fieldId, int buttonId, String key, int y, String value) {
        if (place) {
            rowLabel(fieldId, key, y, SELECT_FIELD_X);
            addTextField(new GuiTextFieldNop(fieldId, this, guiLeft + SELECT_FIELD_X, guiTop + y, SELECT_FIELD_WIDTH,
                    CONTROL_HEIGHT, value));
            addButton(new GuiButtonNop(this, buttonId, guiLeft + SELECT_BUTTON_X, guiTop + y, SELECT_BUTTON_WIDTH,
                    CONTROL_HEIGHT, "mco.template.button.select"));
        }
        return y + ROW;
    }

    /** A colour typed as six hex digits, where a lone number would sit. */
    private int hex(boolean place, int id, String key, int y, int color) {
        if (place) {
            rowLabel(id, key, y, PAIR_SECOND_X);
            addTextField(new GuiTextFieldNop(id, this, guiLeft + PAIR_SECOND_X, guiTop + y, PAIR_WIDTH,
                    CONTROL_HEIGHT, BossRiftSettings.hex(color)));
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
        // Not I18n.get: it runs the text through String.format; a translatable component hands
        // the text back as is, per cent signs and all.
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

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossRiftSettings rift = phase.rift();
        if (button.id == GLOW_BUTTON) {
            rift.setCrystalGlow(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ZONE_RING_BUTTON) {
            rift.setCrystalZoneRing(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COLLECT_BUTTON) {
            rift.setCrystalCollectMode(button.getValue());
        } else if (button.id == BLOCK_SELECT_BUTTON) {
            applyFields();
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.rift_crystal_block",
                    blockIds(), id -> {
                rift.setCrystalBlock(id);
                getTextField(BLOCK_FIELD).setValue(rift.getCrystalBlock());
            }));
        } else if (button.id == POINTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRiftCrystalList(npc, phase, phaseIndex));
        }
    }

    /** Every block in the game, for the picker to search through. */
    static List<String> blockIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.BLOCK.keySet()) {
            ids.add(key.toString());
        }
        Collections.sort(ids);
        return ids;
    }

    @Override
    protected void applyFields() {
        BossRiftSettings rift = phase.rift();
        applyNumberField(COUNT_FIELD, rift::setCrystalCount);
        applyNumberField(RING_FIELD, rift::setCrystalRingRadius);
        applyNumberField(HOVER_FIELD, rift::setCrystalHoverTenths);
        applyBlock(rift);
        GuiTextFieldNop color = getTextField(COLOR_FIELD);
        if (color != null) {
            rift.setCrystalColor(BossRiftSettings.parseHex(color.getValue(), rift.getCrystalColor()));
            color.setValue(BossRiftSettings.hex(rift.getCrystalColor()));
        }
        applyNumberField(SPIN_FIELD, rift::setCrystalSpinDegrees);
        applyNumberField(BOB_FIELD, rift::setCrystalBobTenths);
        applyNumberField(BOB_PERIOD_FIELD, rift::setCrystalBobPeriodTicks);
        applyNumberField(SCALE_FIELD, rift::setCrystalScaleTenths);
        applyNumberField(RADIUS_FIELD, rift::setCrystalCollectRadiusTenths);
        applyNumberField(INTERVAL_FIELD, rift::setCrystalAmbientIntervalTicks);
    }

    /** Keeps a typed block only when the game has it; otherwise the field snaps back. */
    private void applyBlock(BossRiftSettings rift) {
        GuiTextFieldNop field = getTextField(BLOCK_FIELD);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossRiftDimension.resolveBlock(value) != null) {
            rift.setCrystalBlock(value);
        } else {
            field.setValue(rift.getCrystalBlock());
        }
    }
}
