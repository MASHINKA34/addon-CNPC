package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.GeckoTheme;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiStringSlotNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class GuiStringSelection extends GuiNPCInterface {
    private static final int HEADING_LABEL = 0;

    public GuiStringSlotNop slot;
    public Consumer<String> action;
    public Consumer<List<String>> multiAction;
    public Screen parent;
    public String title;
    public List<String> options;
    public List<String> selected;
    public boolean multiSelect;

    public GuiStringSelection(Screen parent, String title, List<String> options, Consumer<String> action) {
        drawDefaultBackground = false;
        this.parent = parent;
        this.action = action;
        this.title = title;
        // Copied: init() sorts this list, and callers pass lists they keep using
        // (some of them immutable, which would throw).
        this.options = new ArrayList<>(options);
    }

    public GuiStringSelection(Screen parent, String title, List<String> options, Collection<String> selected, Consumer<List<String>> multiAction) {
        drawDefaultBackground = false;
        this.parent = parent;
        this.multiAction = multiAction;
        this.title = title;
        this.options = new ArrayList<>(options);
        this.selected = new ArrayList<>(selected);
        this.multiSelect = true;
    }

    @Override
    public void init() {
        super.init();
        // Resolved here rather than left to the label: the label would translate it anyway,
        // but the centring is measured off the string, and measuring the key instead of the
        // sentence puts every picker's heading off to one side.
        String heading = Component.translatable(title).getString();
        addLabel(new ThemeLabel(HEADING_LABEL, heading, width / 2 - (this.font.width(heading) / 2), 20, 0xffffff));
        options.sort(String.CASE_INSENSITIVE_ORDER);
        slot = new GuiStringSlotNop(options, this, multiSelect);
        if (multiSelect) {
            slot.selectedList.addAll(selected);
        }
        addWidget(this.slot);

        GuiTextFieldNop search = new ThemeTextField(1, this, width / 2 - 40, height - 44, 190, 20, "");
        search.setHint(Component.translatable("cnpcgeckoaddon.string_picker.search"));
        search.setResponder(this::applyFilter);
        addTextField(search);

        this.addButton(new ThemeButton(this, 2, width / 2 - 150, height - 44, 98, 20, multiSelect ? "gui.done" : "gui.back"));
    }

    public void applyFilter(String query) {
        if (slot == null) {
            return;
        }
        String search = query.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).contains(search)) {
                filtered.add(option);
            }
        }
        slot.setList(filtered);
    }

    @Override
    public void render(GuiGraphics matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.slot.render(matrixStack, mouseX, mouseY, partialTicks);
        GuiLabel heading = getLabel(HEADING_LABEL);
        if (heading != null && GeckoTheme.enabled()) {
            // The theme's title strip behind the heading, above the list; the heading draws over it.
            try {
                GeckoTheme.headerBehind(matrixStack, heading.getX(), heading.getY(), font.width(heading.getMessage()));
            } catch (Throwable error) {
                CrashGuard.caught("client.gui.theme.picker_header", error);
            }
        }
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void doubleClicked() {
        if (multiSelect || slot.getSelectedString() == null) {
            return;
        }
        action.accept(slot.getSelectedString());
        close();
    }

    @Override
    public void buttonEvent(GuiButtonNop guibutton) {
        int id = guibutton.id;
        if (id == 2) {
            if (multiSelect) {
                multiAction.accept(new ArrayList<>(slot.selectedList));
            }
            close();
        }
    }
}
