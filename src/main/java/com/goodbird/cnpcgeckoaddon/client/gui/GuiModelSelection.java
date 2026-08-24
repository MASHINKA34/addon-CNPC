package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.ModelSelectionHelper;
import com.goodbird.cnpcgeckoaddon.client.model.GeckoModelBounds;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Model-only picker with namespace filtering and deferred application. */
public class GuiModelSelection extends GuiNPCInterface {
    private static final int BUTTON_SELECT = 1;
    private static final int BUTTON_CANCEL = 2;
    private static final int BUTTON_NAMESPACE = 3;
    private static final int SEARCH_FIELD = 4;
    private static final int BUTTON_ZOOM_OUT = 5;
    private static final int BUTTON_ZOOM_IN = 6;
    private static final int BUTTON_AUTO_FIT = 7;
    private static final int MARGIN = 8;
    private static final int COLUMN_GAP = 8;
    private static final float MIN_SCALE_PERCENT = 5.0F;
    private static final float MAX_SCALE_PERCENT = 800.0F;
    private static final float START_YAW = 30.0F;
    private static final ResourceLocation NO_OP_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            "cnpcgeckoaddon", "animations/none.animation.json");
    private static final ResourceLocation DEFAULT_NPC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "customnpcs", "textures/entity/humanmale/steve.png");

    private static final Comparator<ResourceLocation> MODEL_ORDER = Comparator
            .comparing(ResourceLocation::getNamespace, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ResourceLocation::getPath, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ResourceLocation::toString);

    private final EntityCustomNpc targetNpc;
    private final Consumer<String> selectionAction;
    private final List<ResourceLocation> allModels;
    private final List<String> namespaces;
    private final List<String> visibleModels = new ArrayList<>();
    private final Map<ResourceLocation, Optional<GeckoModelBounds.Bounds>> boundsCache = new HashMap<>();

    private ModelList modelList;
    private EntityCustomModel previewEntity;
    private ResourceLocation previewModel;
    private GeckoModelBounds.Bounds previewBounds;
    private String selectedModel;
    private String searchText = "";
    private int namespaceIndex;
    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int previewTop;
    private int previewBottom;
    private float scalePercent = 100.0F;
    private float previewYaw = START_YAW;
    private boolean draggingPreview;
    private boolean previewRenderFailed;

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

        int previewControlsY = previewBottom + 4;
        int controlsWidth = Math.min(rightWidth, 126);
        int controlsX = rightX + (rightWidth - controlsWidth) / 2;
        addButton(new GuiButtonNop(this, BUTTON_ZOOM_OUT, controlsX, previewControlsY,
                24, 20, "−"));
        addButton(new GuiButtonNop(this, BUTTON_ZOOM_IN, controlsX + 27, previewControlsY,
                24, 20, "+"));
        addButton(new GuiButtonNop(this, BUTTON_AUTO_FIT, controlsX + 54, previewControlsY,
                controlsWidth - 54, 20, "cnpcgeckoaddon.model_picker.auto_fit"));

        int bottomWidth = Math.min(100, (availableWidth - 8) / 2);
        int bottomY = height - 28;
        int bottomCenter = width / 2;
        addButton(new GuiButtonNop(this, BUTTON_SELECT, bottomCenter - bottomWidth - 2,
                bottomY, bottomWidth, 20, "cnpcgeckoaddon.model_picker.select"));
        addButton(new GuiButtonNop(this, BUTTON_CANCEL, bottomCenter + 2,
                bottomY, bottomWidth, 20, "cnpcgeckoaddon.model_picker.cancel"));

        ensurePreviewEntity();
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
        ResourceLocation location = ResourceLocation.tryParse(model);
        if (location != null && !location.equals(previewModel)) {
            updatePreview(location);
        }
        GuiButtonNop selectButton = getButton(BUTTON_SELECT);
        if (selectButton != null) {
            selectButton.setEnabled(model != null);
        }
    }

    private void selectModel() {
        String model = modelList == null ? null : modelList.getSelectedModel();
        if (model == null) {
            return;
        }
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
        } else if (button.id == BUTTON_ZOOM_OUT) {
            adjustScale(-25.0F);
        } else if (button.id == BUTTON_ZOOM_IN) {
            adjustScale(25.0F);
        } else if (button.id == BUTTON_AUTO_FIT) {
            scalePercent = 100.0F;
            previewYaw = START_YAW;
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
        graphics.renderOutline(rightX, previewTop, rightWidth, previewBottom - previewTop, 0xFF808080);
        renderPreview(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.count",
                        visibleModels.size(), allModels.size()),
                leftX + 3, 66, 0xB0B0B0, false);
        graphics.drawCenteredString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.preview"),
                rightX + rightWidth / 2, 31, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.scale", Math.round(scalePercent)),
                rightX + rightWidth / 2, previewBottom - font.lineHeight - 3, 0xD0D0D0);

        if (visibleModels.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("cnpcgeckoaddon.model_picker.no_results"),
                    leftX + leftWidth / 2, 92, 0xA0A0A0);
        }
        renderSelectedModel(graphics, mouseX, mouseY);
        renderListTooltip(graphics, mouseX, mouseY);
    }

    private void ensurePreviewEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (previewEntity == null && minecraft.level != null && EntityRegistry.entityCustomModel != null) {
            previewEntity = new EntityCustomModel(EntityRegistry.entityCustomModel, minecraft.level);
            previewEntity.size = 5;
        }
    }

    private void updatePreview(ResourceLocation model) {
        ensurePreviewEntity();
        if (previewEntity == null) {
            return;
        }

        ModelSelectionHelper.ModelResources resources = ModelSelectionHelper.resolve(model);
        ResourceLocation npcTexture = AnimationFileUtil.parse(targetNpc.display.getSkinTexture());
        if (npcTexture == null && targetNpc.modelData.getEntity(targetNpc) instanceof EntityCustomModel currentModel) {
            npcTexture = currentModel.textureResLoc;
        }

        previewEntity.modelResLoc = model;
        previewEntity.textureResLoc = resources.defaultTexture() != null
                ? resources.defaultTexture()
                : npcTexture == null ? DEFAULT_NPC_TEXTURE : npcTexture;
        previewEntity.animResLoc = resources.animation() == null ? NO_OP_ANIMATION : resources.animation();
        previewEntity.idleAnim = compatibleIdleAnimation(resources.animation());

        previewModel = model;
        previewBounds = boundsCache.computeIfAbsent(model, location -> {
            BakedGeoModel bakedModel = GeckoLibCache.getBakedModels().get(location);
            return GeckoModelBounds.calculateModelBounds(bakedModel);
        }).orElse(null);
        scalePercent = 100.0F;
        previewRenderFailed = false;
    }

    private String compatibleIdleAnimation(ResourceLocation animationFile) {
        if (animationFile == null) {
            return "";
        }
        var animations = GeckoLibCache.getBakedAnimations().get(animationFile);
        String idle = ((IDataDisplay) targetNpc.display).getCustomModelData().getIdleAnim();
        return animations != null && animations.animations().containsKey(idle) ? idle : "";
    }

    private void renderPreview(GuiGraphics graphics) {
        if (previewEntity == null || previewModel == null || previewRenderFailed) {
            return;
        }

        int contentLeft = rightX + 2;
        int contentRight = rightX + rightWidth - 2;
        int contentTop = previewTop + font.lineHeight + 9;
        int contentBottom = previewBottom - font.lineHeight - 5;
        if (contentRight <= contentLeft || contentBottom <= contentTop) {
            return;
        }

        float fitScale = calculateFitScale(contentRight - contentLeft, contentBottom - contentTop);
        float renderScale = fitScale * scalePercent / 100.0F;
        Vector3f translation = calculateCenterTranslation();
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);

        previewEntity.yBodyRot = previewYaw;
        previewEntity.yBodyRotO = previewYaw;
        previewEntity.yHeadRot = previewYaw;
        previewEntity.yHeadRotO = previewYaw;
        PoseStack poseStack = new PoseStack();
        poseStack.translate(
                (contentLeft + contentRight) * 0.5F,
                (contentTop + contentBottom) * 0.5F,
                50.0F);
        poseStack.scale(renderScale, renderScale, -renderScale);
        poseStack.translate(translation.x, translation.y, translation.z);
        poseStack.mulPose(pose);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        graphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom);
        Lighting.setupForEntityInInventory();
        dispatcher.setRenderShadow(false);
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(
                    previewEntity,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0F,
                    1.0F,
                    poseStack,
                    graphics.bufferSource(),
                    15728880));
        } catch (RuntimeException ignored) {
            // Broken third-party geometry should not make the editor unusable.
            previewRenderFailed = true;
        } finally {
            try {
                graphics.flush();
            } catch (RuntimeException ignored) {
                previewRenderFailed = true;
            }
            dispatcher.setRenderShadow(true);
            Lighting.setupFor3DItems();
            graphics.disableScissor();
        }
    }

    private float calculateFitScale(int contentWidth, int contentHeight) {
        if (previewBounds == null) {
            return Math.max(8.0F, Math.min(contentWidth, contentHeight) * 0.35F);
        }

        double horizontalExtent = Math.max(0.05D,
                Math.hypot(previewBounds.width(), previewBounds.depth()));
        double verticalExtent = Math.max(0.05D, previewBounds.height());
        double widthScale = contentWidth * 0.8D / horizontalExtent;
        double heightScale = contentHeight * 0.8D / verticalExtent;
        return (float) Math.min(4096.0D, Math.max(0.01D, Math.min(widthScale, heightScale)));
    }

    private Vector3f calculateCenterTranslation() {
        if (previewBounds == null) {
            return new Vector3f(0.0F, previewEntity.getBbHeight() * 0.5F, 0.0F);
        }

        double angle = Math.toRadians(180.0F - previewYaw);
        double rotatedX = Math.cos(angle) * previewBounds.centerX()
                + Math.sin(angle) * previewBounds.centerZ();
        double rotatedZ = -Math.sin(angle) * previewBounds.centerX()
                + Math.cos(angle) * previewBounds.centerZ();
        return new Vector3f(
                (float) rotatedX,
                (float) previewBounds.centerY(),
                (float) -rotatedZ);
    }

    private void adjustScale(float amount) {
        scalePercent = Math.max(MIN_SCALE_PERCENT,
                Math.min(MAX_SCALE_PERCENT, scalePercent + amount));
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        return mouseX >= rightX && mouseX < rightX + rightWidth
                && mouseY >= previewTop && mouseY < previewBottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsidePreview(mouseX, mouseY) && scrollY != 0.0D) {
            adjustScale((float) Math.copySign(10.0D, scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsidePreview(mouseX, mouseY)) {
            draggingPreview = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && draggingPreview) {
            previewYaw = (previewYaw + (float) dragX * 0.8F) % 360.0F;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingPreview) {
            draggingPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (previewEntity != null) {
            previewEntity.tickCount++;
        }
    }

    @Override
    public void close() {
        releasePreviewEntity();
        super.close();
    }

    @Override
    public void removed() {
        releasePreviewEntity();
        super.removed();
    }

    private void releasePreviewEntity() {
        if (previewEntity != null) {
            previewEntity.discard();
            previewEntity = null;
        }
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

    private void renderListTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (modelList == null) {
            return;
        }
        String hoveredModel = modelList.getHoveredModel();
        if (hoveredModel != null && font.width(hoveredModel) > modelList.getRowWidth() - 4) {
            graphics.renderTooltip(font, Component.literal(hoveredModel), mouseX, mouseY);
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

        private String getHoveredModel() {
            return getHovered() == null ? null : getHovered().model;
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
