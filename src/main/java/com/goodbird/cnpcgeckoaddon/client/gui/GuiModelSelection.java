package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.ModelSelectionHelper;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import software.bernie.geckolib.cache.GeckoLibCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Model-only picker with namespace filtering and deferred application. */
public class GuiModelSelection extends GuiNPCInterface {
    private static final int BUTTON_SELECT = 1;
    private static final int BUTTON_CANCEL = 2;
    private static final int BUTTON_NAMESPACE = 3;
    private static final int SEARCH_FIELD = 4;
    private static final int MARGIN = 8;
    private static final int COLUMN_GAP = 8;

    private static final Comparator<ResourceLocation> MODEL_ORDER = Comparator
            .comparing(ResourceLocation::getNamespace, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ResourceLocation::getPath, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ResourceLocation::toString);

    private final EntityCustomNpc targetNpc;
    private final Consumer<String> selectionAction;
    private final List<ResourceLocation> allModels;
    private final List<String> namespaces;
    private final List<String> visibleModels = new ArrayList<>();

    private ModelList modelList;
    private String selectedModel;
    private String searchText = "";
    private int namespaceIndex;
    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int previewTop;
    private int previewBottom;

    public GuiModelSelection(EntityCustomNpc npc, Consumer<String> selectionAction) {
        super(npc);
        this.targetNpc = npc;
        this.selectionAction = selectionAction;
        this.drawDefaultBackground = false;
        this.closeOnEsc = true;

        this.allModels = GeckoLibCache.getBakedModels().keySet().stream()
                .filter(model -> model.getPath().endsWith(".geo.json"))
                .sorted(MODEL_ORDER)
                .toList();

        TreeSet<String> discoveredNamespaces = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ResourceLocation model : allModels) {
            discoveredNamespaces.add(model.getNamespace());
        }
        this.namespaces = new ArrayList<>(discoveredNamespaces.size() + 1);
        this.namespaces.add("");
        this.namespaces.addAll(discoveredNamespaces);

        CustomModelData modelData = ((IDataDisplay) npc.display).getCustomModelData();
        ResourceLocation currentModel = ResourceLocation.tryParse(modelData.getModel());
        this.selectedModel = currentModel == null ? null : currentModel.toString();
    }

    @Override
    public void init() {
        super.init();
        this.title = Component.translatable("cnpcgeckoaddon.model_picker.title").getString();

        int availableWidth = Math.max(304, width - MARGIN * 2);
        this.leftWidth = Math.max(140, Math.min(availableWidth - 156 - COLUMN_GAP,
                Math.round(availableWidth * 0.47F)));
        this.leftX = Math.max(0, (width - availableWidth) / 2);
        this.rightX = leftX + leftWidth + COLUMN_GAP;
        this.rightWidth = Math.max(148, availableWidth - leftWidth - COLUMN_GAP);

        int searchTop = 27;
        GuiTextFieldNop search = new GuiTextFieldNop(
                SEARCH_FIELD, this, leftX, searchTop, leftWidth, 20, searchText);
        search.setHint(Component.translatable("cnpcgeckoaddon.model_picker.search"));
        search.setResponder(value -> {
            searchText = value;
            applyFilters();
        });
        addTextField(search);

        addButton(new GuiButtonNop(this, BUTTON_NAMESPACE, leftX, searchTop + 23,
                leftWidth, 20, namespaceButtonText()));

        int listTop = searchTop + 47;
        int listBottom = Math.max(listTop + 36, height - 36);
        this.modelList = new ModelList(Minecraft.getInstance(), leftX, listTop,
                leftWidth, listBottom - listTop);
        addWidget(modelList);

        this.previewTop = searchTop + 23;
        this.previewBottom = Math.max(previewTop + 48, height - 58);

        int bottomWidth = Math.min(100, (availableWidth - 8) / 2);
        int bottomY = height - 28;
        int bottomCenter = width / 2;
        addButton(new GuiButtonNop(this, BUTTON_SELECT, bottomCenter - bottomWidth - 2,
                bottomY, bottomWidth, 20, "cnpcgeckoaddon.model_picker.select"));
        addButton(new GuiButtonNop(this, BUTTON_CANCEL, bottomCenter + 2,
                bottomY, bottomWidth, 20, "cnpcgeckoaddon.model_picker.cancel"));

        applyFilters();
    }

    private String namespaceButtonText() {
        String namespace = namespaces.get(namespaceIndex);
        Component value = namespace.isEmpty()
                ? Component.translatable("cnpcgeckoaddon.model_picker.all_mods")
                : Component.literal(namespace);
        return Component.translatable("cnpcgeckoaddon.model_picker.mod").getString()
                + ": " + value.getString();
    }

    private void applyFilters() {
        if (modelList == null) {
            return;
        }

        String namespace = namespaces.get(namespaceIndex);
        String query = searchText.trim().toLowerCase(Locale.ROOT);
        visibleModels.clear();
        for (ResourceLocation model : allModels) {
            if (!namespace.isEmpty() && !namespace.equals(model.getNamespace())) {
                continue;
            }
            String fullId = model.toString();
            if (!query.isEmpty()
                    && !fullId.toLowerCase(Locale.ROOT).contains(query)
                    && !model.getPath().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            visibleModels.add(fullId);
        }

        String previousSelection = selectedModel;
        modelList.setModels(visibleModels);
        if (previousSelection != null && visibleModels.contains(previousSelection)) {
            modelList.select(previousSelection);
        } else if (!visibleModels.isEmpty()) {
            modelList.select(visibleModels.getFirst());
            modelHighlighted(visibleModels.getFirst());
        }

        GuiButtonNop selectButton = getButton(BUTTON_SELECT);
        if (selectButton != null) {
            selectButton.setEnabled(modelList.getSelectedModel() != null);
        }
    }

    private void modelHighlighted(String model) {
        selectedModel = model;
        GuiButtonNop selectButton = getButton(BUTTON_SELECT);
        if (selectButton != null) {
            selectButton.setEnabled(model != null);
        }
    }

    private void selectModel() {
        String model = modelList == null ? null : modelList.getSelectedModel();
        ResourceLocation location = ResourceLocation.tryParse(model);
        if (location == null) {
            return;
        }
        ModelSelectionHelper.applyToNpc(targetNpc, location);
        selectionAction.accept(location.toString());
        close();
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == BUTTON_NAMESPACE) {
            namespaceIndex = (namespaceIndex + 1) % namespaces.size();
            button.setMessage(Component.literal(namespaceButtonText()));
            applyFilters();
        } else if (button.id == BUTTON_SELECT) {
            selectModel();
        } else if (button.id == BUTTON_CANCEL) {
            close();
        }
    }

    @Override
    public void doubleClicked() {
        selectModel();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftX - 2, 23, leftX + leftWidth + 2, height - 32, 0x99000000);
        graphics.fill(rightX - 2, 23, rightX + rightWidth + 2, height - 32, 0x99000000);
        if (modelList != null) {
            modelList.render(graphics, mouseX, mouseY, partialTick);
        }
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.count",
                        visibleModels.size(), allModels.size()),
                leftX + 3, 66, 0xB0B0B0, false);
        graphics.drawCenteredString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.preview"),
                rightX + rightWidth / 2, 31, 0xFFFFFF);
        graphics.renderOutline(rightX, previewTop, rightWidth, previewBottom - previewTop, 0xFF808080);

        if (visibleModels.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("cnpcgeckoaddon.model_picker.no_results"),
                    leftX + leftWidth / 2, 92, 0xA0A0A0);
        }
        renderSelectedModel(graphics, mouseX, mouseY);
    }

    private void renderSelectedModel(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedModel == null) {
            return;
        }
        int textX = rightX + 4;
        int textY = previewTop + 4;
        int textWidth = rightWidth - 8;
        String visible = font.width(selectedModel) <= textWidth
                ? selectedModel
                : font.plainSubstrByWidth(selectedModel, Math.max(0, textWidth - font.width("..."))) + "...";
        graphics.drawString(font, visible, textX, textY, 0xD0D0D0, false);
        if (font.width(selectedModel) > textWidth
                && mouseX >= textX && mouseX < textX + textWidth
                && mouseY >= textY && mouseY < textY + font.lineHeight) {
            graphics.renderTooltip(font, Component.literal(selectedModel), mouseX, mouseY);
        }
    }

    private final class ModelList extends ObjectSelectionList<ModelEntry> {
        private ModelList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, width, height, y, 18);
            setX(x);
            setWidth(width);
            setHeight(height);
        }

        private void setModels(List<String> models) {
            clearEntries();
            for (String model : models) {
                addEntry(new ModelEntry(model));
            }
            setSelected(null);
        }

        private void select(String model) {
            for (ModelEntry entry : children()) {
                if (entry.model.equals(model)) {
                    setSelected(entry);
                    ensureVisible(entry);
                    return;
                }
            }
        }

        private String getSelectedModel() {
            return getSelected() == null ? null : getSelected().model;
        }

        @Override
        public int getRowWidth() {
            return Math.max(20, getWidth() - 12);
        }
    }

    private final class ModelEntry extends ObjectSelectionList.Entry<ModelEntry> {
        private final String model;
        private long previousClick;

        private ModelEntry(String model) {
            this.model = model;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            String visible = font.width(model) <= width - 4
                    ? model
                    : font.plainSubstrByWidth(model, Math.max(0, width - 4 - font.width("..."))) + "...";
            graphics.drawString(font, visible, left + 2, top + 2, 0xFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            long clickTime = System.currentTimeMillis();
            boolean doubleClick = modelList.getSelected() == this
                    && clickTime - previousClick < 400L;
            previousClick = clickTime;
            modelList.setSelected(this);
            modelHighlighted(model);
            if (doubleClick) {
                GuiModelSelection.this.doubleClicked();
            }
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(model);
        }
    }
}
