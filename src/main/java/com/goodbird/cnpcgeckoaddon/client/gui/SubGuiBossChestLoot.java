package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossLootEntry;
import com.goodbird.cnpcgeckoaddon.data.BossLootList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** The hand-picked contents of a boss loot chest, one row per slot. */
public final class SubGuiBossChestLoot extends GuiBasic {
    private static final int FIRST_SLOT_BUTTON = 100;
    private static final int PREV_PAGE_BUTTON = 10;
    private static final int NEXT_PAGE_BUTTON = 11;
    private static final int FROM_HAND_BUTTON = 12;
    private static final int CLEAR_BUTTON = 13;
    private static final int PAGE_LABEL = 14;

    private static final int ROWS = 8;
    private static final int PAGES = (BossLootList.SLOTS + ROWS - 1) / ROWS;

    private final BossLootList loot;
    private int page;

    public SubGuiBossChestLoot(BossLootList loot) {
        this.loot = loot;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.chest_loot_list", guiLeft + 8, guiTop + 8, 0xFFFFFF));

        int y = guiTop + 24;
        for (int row = 0; row < ROWS; row++) {
            addButton(new GuiButtonNop(this, FIRST_SLOT_BUTTON + row, guiLeft + 8, y, 234, 20, ""));
            y += 22;
        }

        addButton(new GuiButtonNop(this, PREV_PAGE_BUTTON, guiLeft + 8, guiTop + 204, 20, 20, "<"));
        addLabel(new GuiLabel(PAGE_LABEL, "", guiLeft + 36, guiTop + 210, 0xA0A0A0));
        addButton(new GuiButtonNop(this, NEXT_PAGE_BUTTON, guiLeft + 222, guiTop + 204, 20, 20, ">"));

        addButton(new GuiButtonNop(this, FROM_HAND_BUTTON, guiLeft + 8, guiTop + 230, 104, 20,
                "cnpcgeckoaddon.boss.chest_from_hand"));
        addButton(new GuiButtonNop(this, CLEAR_BUTTON, guiLeft + 116, guiTop + 230, 60, 20,
                "cnpcgeckoaddon.boss.chest_clear"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));

        refreshRows();
    }

    /**
     * Relabels the visible rows for the current page.
     *
     * <p>This GUI framework has no widget-clearing rebuild, so calling init() again would
     * stack a second set of buttons on top of the first - turning a page has to change what
     * the buttons already there say.</p>
     */
    private void refreshRows() {
        for (int row = 0; row < ROWS; row++) {
            GuiButtonNop button = getButton(FIRST_SLOT_BUTTON + row);
            if (button == null) {
                continue;
            }
            int index = page * ROWS + row;
            boolean exists = index < BossLootList.SLOTS;
            // The last page is short: hide the leftovers instead of showing dead rows.
            button.shown = exists;
            button.setEnabled(exists);
            button.setDisplayText(exists ? slotLabel(index) : "");
        }
        GuiLabel pageLabel = getLabel(PAGE_LABEL);
        if (pageLabel != null) {
            pageLabel.setMessage(Component.literal((page + 1) + " / " + PAGES));
        }
    }

    /** "3. Diamond x2-5 - 50%", or "3. empty" for a slot nobody filled in. */
    private String slotLabel(int index) {
        BossLootEntry entry = loot.get(index);
        if (entry.isEmpty()) {
            return (index + 1) + ". " + I18n.get("cnpcgeckoaddon.boss.chest_empty");
        }
        String count = entry.getMinCount() == entry.getMaxCount()
                ? Integer.toString(entry.getMinCount())
                : entry.getMinCount() + "-" + entry.getMaxCount();
        return (index + 1) + ". " + entry.getStack().getHoverName().getString()
                + " x" + count + " - " + entry.getChancePercent() + "%";
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_SLOT_BUTTON;
        if (row >= 0 && row < ROWS) {
            int index = page * ROWS + row;
            if (index < BossLootList.SLOTS) {
                setSubGui(new SubGuiBossChestEntry(loot.get(index)));
            }
            return;
        }
        if (button.id == PREV_PAGE_BUTTON) {
            page = (page + PAGES - 1) % PAGES;
            refreshRows();
        } else if (button.id == NEXT_PAGE_BUTTON) {
            page = (page + 1) % PAGES;
            refreshRows();
        } else if (button.id == FROM_HAND_BUTTON) {
            addFromHand();
        } else if (button.id == CLEAR_BUTTON) {
            loot.clear();
            refreshRows();
        }
    }

    /** Puts whatever the editor is holding into the first free slot, components and all. */
    private void addFromHand() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        int slot = loot.firstEmptySlot();
        if (held.isEmpty() || slot < 0) {
            return;
        }
        loot.get(slot).setStack(held);
        // Jump to wherever it landed, so the new row is the one being looked at.
        page = slot / ROWS;
        refreshRows();
    }

    @Override
    public void subGuiClosed(Screen subgui) {
        super.subGuiClosed(subgui);
        refreshRows();
    }
}
