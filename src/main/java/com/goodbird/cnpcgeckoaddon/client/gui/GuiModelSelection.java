package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.MobModelTextureResolver;
import com.goodbird.cnpcgeckoaddon.client.ModelSelectionHelper;
import com.goodbird.cnpcgeckoaddon.client.model.GeckoModelBounds;
import com.goodbird.cnpcgeckoaddon.data.CustomModelData;
import com.goodbird.cnpcgeckoaddon.data.GeckoGeometryBounds;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.registry.EntityRegistry;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.FormattedCharSequence;
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
import org.lwjgl.opengl.GL11;

/** Model-only picker with namespace filtering and deferred application. */
public class GuiModelSelection extends GuiNPCInterface {
    private static final int BUTTON_SELECT = 1;
    private static final int BUTTON_CANCEL = 2;
    private static final int BUTTON_NAMESPACE = 3;
    private static final int SEARCH_FIELD = 4;
    private static final int BUTTON_ZOOM_OUT = 5;
    private static final int BUTTON_ZOOM_IN = 6;
    private static final int BUTTON_AUTO_FIT = 7;
    private static final int BUTTON_TEXTURE = 8;
    private static final int MARGIN = 8;
    /** The widest the row of zoom, fit and texture buttons under the preview grows. */
    private static final int MAX_CONTROLS_WIDTH = 240;
    private static final int COLUMN_GAP = 8;
    private static final float MIN_SCALE_PERCENT = 5.0F;
    private static final float MAX_SCALE_PERCENT = 800.0F;
    private static final float START_YAW = 30.0F;
    /** The share of the preview box left free on every side of a fitted model. */
    private static final double FIT_MARGIN = 0.08D;
    /** Pixels per block past which a fitted model is not blown up further. */
    private static final double MAX_FIT_SCALE = 4096.0D;
    /** How far out of the screen the model's middle is drawn, as vanilla's inventory entity is. */
    private static final float PREVIEW_DEPTH = 50.0F;
    /** EntityCustomModel draws at size / 5 of the model; the preview keeps 5, so one to one. */
    private static final int PREVIEW_SIZE = 5;
    private static final ResourceLocation NO_OP_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            "cnpcgeckoaddon", "animations/none.animation.json");
    private static final ResourceLocation FALLBACK_MODEL = ResourceLocation.fromNamespaceAndPath(
            "cnpcgeckoaddon", "geo/modelnotfound.geo.json");

    private static final Comparator<ResourceLocation> MODEL_ORDER = Comparator
            .comparing(ResourceLocation::getNamespace, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ResourceLocation::getPath, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ResourceLocation::toString);

    private final EntityCustomNpc targetNpc;
    private final Consumer<String> selectionAction;
    private final List<ResourceLocation> allModels;
    /** The model the npc wears when nothing is baked under its id, shown until another is picked. */
    private final ResourceLocation missingCurrentModel;
    private final List<String> namespaces;
    private final List<String> visibleModels = new ArrayList<>();
    private final Map<ResourceLocation, Optional<GeckoGeometryBounds.Bounds>> boundsCache = new HashMap<>();
    /** Textures picked by hand, per model, until the picker closes; the selected model's goes on the npc. */
    private final Map<ResourceLocation, ResourceLocation> chosenTextures = new HashMap<>();

    private ModelList modelList;
    private EntityCustomModel previewEntity;
    private ResourceLocation previewRequestedModel;
    private ResourceLocation previewRenderedModel;
    private GeckoGeometryBounds.Bounds previewBounds;
    /** What the previewed model is dressed in and why, for the status line under the preview. */
    private MobModelTextureResolver.Resolution previewTexture;
    private int textureStatusY;
    private String selectedModel;
    private String searchText = "";
    private int namespaceIndex;
    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int countY;
    private int listTop;
    private int previewTop;
    private int previewBottom;
    private float scalePercent = 100.0F;
    private float previewYaw = START_YAW;
    private boolean draggingPreview;
    private boolean previewFallbackActive;
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
        this.missingCurrentModel = currentModel != null && !allModels.contains(currentModel) ? currentModel : null;
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

        int namespaceTop = searchTop + 23;
        addButton(new GuiButtonNop(this, BUTTON_NAMESPACE, leftX, namespaceTop,
                leftWidth, 20, namespaceButtonText()));

        this.countY = namespaceTop + 24;
        this.listTop = countY + font.lineHeight + 3;
        int listBottom = Math.max(listTop + 36, height - 36);
        this.modelList = new ModelList(Minecraft.getInstance(), leftX, listTop,
                leftWidth, listBottom - listTop);
        addWidget(modelList);

        this.previewTop = searchTop + 23;
        // One line under the preview says where its texture came from.
        int statusRow = font.lineHeight + 4;
        this.previewBottom = Math.max(previewTop + 48, height - 58 - statusRow);
        this.textureStatusY = previewBottom + 3;

        int previewControlsY = previewBottom + statusRow + 4;
        int controlsWidth = Math.min(rightWidth, MAX_CONTROLS_WIDTH);
        int controlsX = rightX + (rightWidth - controlsWidth) / 2;
        int fitWidth = (controlsWidth - 57) / 2;
        addButton(new GuiButtonNop(this, BUTTON_ZOOM_OUT, controlsX, previewControlsY,
                24, 20, "−"));
        addButton(new GuiButtonNop(this, BUTTON_ZOOM_IN, controlsX + 27, previewControlsY,
                24, 20, "+"));
        addButton(new GuiButtonNop(this, BUTTON_AUTO_FIT, controlsX + 54, previewControlsY,
                fitWidth, 20, "cnpcgeckoaddon.model_picker.auto_fit"));
        addButton(new GuiButtonNop(this, BUTTON_TEXTURE, controlsX + 57 + fitWidth, previewControlsY,
                controlsWidth - 57 - fitWidth, 20, "cnpcgeckoaddon.model_picker.texture_pick"));

        int bottomWidth = Math.min(100, (availableWidth - 8) / 2);
        int bottomY = height - 28;
        int bottomCenter = width / 2;
        addButton(new GuiButtonNop(this, BUTTON_SELECT, bottomCenter - bottomWidth - 2,
                bottomY, bottomWidth, 20, "cnpcgeckoaddon.model_picker.select"));
        addButton(new GuiButtonNop(this, BUTTON_CANCEL, bottomCenter + 2,
                bottomY, bottomWidth, 20, "cnpcgeckoaddon.model_picker.cancel"));

        ensurePreviewEntity();
        applyFilters();
        updateTextureButton();
    }

    private void updateTextureButton() {
        GuiButtonNop textureButton = getButton(BUTTON_TEXTURE);
        if (textureButton != null) {
            // A model that could not be read has nothing to dress.
            textureButton.setEnabled(previewRequestedModel != null && previewEntity != null
                    && !previewFallbackActive && !previewRenderFailed);
        }
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
            modelHighlighted(previousSelection);
        } else if (missingCurrentModel != null && missingCurrentModel.toString().equals(previousSelection)) {
            // The npc wears an id nothing is baked under: preview it as the fallback it is drawn
            // as, with its status, instead of quietly offering the list's first model in its place.
            if (!missingCurrentModel.equals(previewRequestedModel)) {
                updatePreview(missingCurrentModel);
            }
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
        if (location != null && !location.equals(previewRequestedModel)) {
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
        ModelSelectionHelper.applyToNpc(targetNpc, location, chosenTextures.get(location));
        selectionAction.accept(location.toString());
        close();
    }

    /**
     * Lists every png of the previewed model's namespace; the one picked dresses the preview at
     * once and goes on the npc with the model, the way CustomNPCs' own texture picker sets it.
     */
    private void openTexturePicker() {
        ResourceLocation model = previewRequestedModel;
        if (model == null) {
            return;
        }
        Map<String, ResourceLocation> byLabel = new HashMap<>();
        for (ResourceLocation texture : MobModelTextureResolver.texturesOf(model.getNamespace())) {
            String path = texture.getPath();
            byLabel.put(path.startsWith("textures/") ? path.substring("textures/".length()) : path, texture);
        }
        String title = Component.translatable("cnpcgeckoaddon.model_picker.texture_pick_title",
                model.getNamespace()).getString();
        setSubGui(new GuiStringSelection(this, title, new ArrayList<>(byLabel.keySet()), label -> {
            ResourceLocation picked = byLabel.get(label);
            if (picked != null) {
                chosenTextures.put(model, picked);
                if (model.equals(previewRequestedModel)) {
                    refreshPreviewTexture();
                }
            }
        }));
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
        } else if (button.id == BUTTON_TEXTURE) {
            openTexturePicker();
        }
    }

    @Override
    public void doubleClicked() {
        selectModel();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Under the texture list nothing answers the mouse, and the model is not drawn: the
        // list covers the whole screen, and super draws it last, over everything drawn so far.
        boolean covered = hasSubGui();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftX - 2, 23, leftX + leftWidth + 2, height - 32, 0x99000000);
        graphics.fill(rightX - 2, 23, rightX + rightWidth + 2, height - 32, 0x99000000);
        if (modelList != null) {
            modelList.render(graphics, covered ? -1 : mouseX, covered ? -1 : mouseY, partialTick);
        }
        graphics.renderOutline(rightX, previewTop, rightWidth, previewBottom - previewTop, 0xFF808080);
        if (!covered) {
            renderPreview(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (covered) {
            return;
        }

        graphics.drawString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.count",
                        visibleModels.size(), allModels.size()),
                leftX + 3, countY, 0xB0B0B0, false);
        graphics.drawCenteredString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.preview"),
                rightX + rightWidth / 2, 31, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("cnpcgeckoaddon.model_picker.scale", Math.round(scalePercent)),
                rightX + rightWidth / 2, previewBottom - font.lineHeight - 3, 0xD0D0D0);
        renderPreviewStatus(graphics);
        renderTextureStatus(graphics, mouseX, mouseY);

        if (visibleModels.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("cnpcgeckoaddon.model_picker.no_results"),
                    leftX + leftWidth / 2, listTop + 18, 0xA0A0A0);
        }
        renderSelectedModel(graphics, mouseX, mouseY);
        renderListTooltip(graphics, mouseX, mouseY);
    }

    /** Says under the preview where the model's texture came from; hovering it names the texture. */
    private void renderTextureStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        if (previewTexture == null || previewFallbackActive || previewRenderFailed) {
            return;
        }
        Component status = Component.translatable(switch (previewTexture.source()) {
            case MAP -> "cnpcgeckoaddon.model_picker.texture_status.map";
            case NAME -> "cnpcgeckoaddon.model_picker.texture_status.name";
            case NPC -> "cnpcgeckoaddon.model_picker.texture_status.npc";
            case NONE -> "cnpcgeckoaddon.model_picker.texture_status.none";
        });
        String text = status.getString();
        int available = rightWidth - 8;
        String visible = font.width(text) <= available
                ? text
                : font.plainSubstrByWidth(text, Math.max(0, available - font.width("..."))) + "...";
        int textWidth = font.width(visible);
        int textX = rightX + (rightWidth - textWidth) / 2;
        int color = previewTexture.source() == MobModelTextureResolver.Source.NONE ? 0xFFE0A0 : 0xB0B0B0;
        graphics.drawString(font, visible, textX, textureStatusY, color, false);
        if (mouseX >= textX && mouseX < textX + textWidth
                && mouseY >= textureStatusY && mouseY < textureStatusY + font.lineHeight) {
            graphics.renderTooltip(font, Component.literal(previewTexture.texture().toString()), mouseX, mouseY);
        }
    }

    private void ensurePreviewEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (previewEntity == null && minecraft.level != null && EntityRegistry.entityCustomModel != null) {
            previewEntity = new EntityCustomModel(EntityRegistry.entityCustomModel, minecraft.level);
            previewEntity.size = PREVIEW_SIZE;
        }
    }

    private void updatePreview(ResourceLocation model) {
        previewRequestedModel = model;
        previewFallbackActive = false;
        previewRenderFailed = false;
        previewTexture = null;
        ensurePreviewEntity();
        if (previewEntity == null) {
            previewRenderedModel = null;
            previewRenderFailed = true;
            updateTextureButton();
            return;
        }

        ModelSelectionHelper.ModelResources resources = ModelSelectionHelper.resolve(model);

        previewEntity.modelResLoc = model;
        previewEntity.animResLoc = resources.animation() == null ? NO_OP_ANIMATION : resources.animation();
        previewEntity.idleAnim = compatibleIdleAnimation(resources.animation());
        refreshPreviewTexture();

        previewRenderedModel = model;
        previewBounds = boundsFor(model);
        scalePercent = 100.0F;
        if (previewBounds == null) {
            // Nothing baked under this id, or nothing in it to draw: the renderer would show
            // its not-found model anyway, so show it framed and say why.
            activatePreviewFallback();
        }
        updateTextureButton();
    }

    /**
     * Dresses the preview in the skin the npc will carry once the previewed model is applied -
     * a texture picked for it here, or the one ModelSelectionHelper works out - and ModelCustom
     * resolves that skin through the same MobModelTextureResolver it resolves the npc's own
     * skin through in the world. The status line is read off the same resolution.
     */
    private void refreshPreviewTexture() {
        if (previewEntity == null || previewRequestedModel == null) {
            previewTexture = null;
            return;
        }
        ResourceLocation skin = ModelSelectionHelper.skinAfterApply(
                targetNpc, previewRequestedModel, chosenTextures.get(previewRequestedModel));
        previewTexture = MobModelTextureResolver.explain(previewRequestedModel, skin);
        if (!previewFallbackActive) {
            previewEntity.textureResLoc = skin;
        }
    }

    private GeckoGeometryBounds.Bounds boundsFor(ResourceLocation model) {
        return boundsCache.computeIfAbsent(model, location -> {
            BakedGeoModel bakedModel = GeckoLibCache.getBakedModels().get(location);
            return GeckoModelBounds.calculateModelBounds(bakedModel);
        }).orElse(null);
    }

    private void activatePreviewFallback() {
        previewFallbackActive = true;
        previewEntity.modelResLoc = FALLBACK_MODEL;
        // What ModelCustom draws on an npc whose model is missing: the not-found model in the
        // missing-texture sheet.
        previewEntity.textureResLoc = MobModelTextureResolver.MISSING_TEXTURE;
        previewEntity.animResLoc = NO_OP_ANIMATION;
        previewEntity.idleAnim = "";
        previewRenderedModel = FALLBACK_MODEL;
        previewBounds = boundsFor(FALLBACK_MODEL);
        updateTextureButton();
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
        if (previewEntity == null || previewRenderedModel == null || previewRenderFailed) {
            return;
        }

        int contentLeft = rightX + 2;
        int contentRight = rightX + rightWidth - 2;
        int contentTop = previewTop + font.lineHeight + 9;
        int contentBottom = previewBottom - font.lineHeight - 5;
        if (contentRight <= contentLeft || contentBottom <= contentTop) {
            return;
        }

        int contentWidth = contentRight - contentLeft;
        int contentHeight = contentBottom - contentTop;
        GeckoGeometryBounds.Bounds bounds = previewBounds == null
                ? null : previewBounds.scaled(previewModelScale());
        float fitScale;
        float anchorY;
        if (bounds == null) {
            fitScale = Math.max(8.0F, Math.min(contentWidth, contentHeight) * 0.35F);
            anchorY = (contentTop + contentBottom) * 0.5F;
        } else {
            GeckoGeometryBounds.Fit fit = GeckoGeometryBounds.fit(
                    bounds, contentWidth, contentHeight, FIT_MARGIN, MAX_FIT_SCALE);
            fitScale = (float) Math.max(0.01D, fit.scale());
            // The zoom grows the model about its own middle, which the fit puts just high enough
            // for the feet to rest on the bottom margin at 100 %.
            anchorY = contentBottom - (float) fit.centerAboveBottom();
        }
        float renderScale = fitScale * scalePercent / 100.0F;
        Vector3f translation = calculateCenterTranslation(bounds);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);

        previewEntity.yBodyRot = previewYaw;
        previewEntity.yBodyRotO = previewYaw;
        previewEntity.yHeadRot = previewYaw;
        previewEntity.yHeadRotO = previewYaw;
        // The screen's own pose, not a fresh one: CustomNPCs draws a sub-screen 60 out of the
        // screen, and from a fresh pose the model's middle sat ten pixels behind this screen's panel.
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean renderFailed = false;
        try {
            poseStack.translate((contentLeft + contentRight) * 0.5F, anchorY, PREVIEW_DEPTH);
            poseStack.scale(renderScale, renderScale, -renderScale);
            poseStack.translate(translation.x, translation.y, translation.z);
            poseStack.mulPose(pose);

            graphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom);
            try {
                clearPreviewDepth();
                Lighting.setupForEntityInInventory();
                dispatcher.setRenderShadow(false);
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
                renderFailed = true;
            } finally {
                try {
                    graphics.flush();
                } catch (RuntimeException ignored) {
                    renderFailed = true;
                }
                clearPreviewDepth();
                dispatcher.setRenderShadow(true);
                Lighting.setupFor3DItems();
                graphics.disableScissor();
            }
        } finally {
            poseStack.popPose();
        }
        if (renderFailed) {
            if (previewFallbackActive) {
                previewRenderFailed = true;
            } else {
                // Switch only after the failed frame has restored global render state.
                activatePreviewFallback();
            }
        }
    }

    private void renderPreviewStatus(GuiGraphics graphics) {
        Component status = previewRenderFailed
                ? Component.translatable("cnpcgeckoaddon.model_picker.preview_unavailable")
                : previewFallbackActive
                ? Component.translatable("cnpcgeckoaddon.model_picker.preview_fallback") : null;
        if (status == null) {
            return;
        }
        List<FormattedCharSequence> lines = font.split(status, Math.max(20, rightWidth - 12));
        int textY = previewRenderFailed
                ? previewTop + (previewBottom - previewTop - lines.size() * font.lineHeight) / 2
                : previewBottom - font.lineHeight - 7 - lines.size() * font.lineHeight;
        int backgroundTop = textY - 2;
        int backgroundBottom = textY + lines.size() * font.lineHeight + 2;
        graphics.fill(rightX + 3, backgroundTop, rightX + rightWidth - 3,
                backgroundBottom, 0xB0000000);
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, rightX + rightWidth / 2, textY, 0xFFE0A0);
            textY += font.lineHeight;
        }
    }

    /**
     * Forgets the depth of whatever was drawn in the preview box, before the model and after it.
     *
     * <p>The dark panel under the preview writes its depth, and a fitted model reaches as far
     * behind its own middle as it is deep: the far side of anything big lay behind the panel's
     * plane, failed the depth test and was never drawn, so the model showed cut in half. After
     * the model, its own depth would hide the status drawn over it. The scissor is on at both
     * calls, so nothing outside the preview box is touched.</p>
     */
    private static void clearPreviewDepth() {
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** The scale RenderCustomModel draws the preview entity at: its size over five, times its own. */
    private float previewModelScale() {
        return previewEntity.size / 5.0F * previewEntity.getScale();
    }

    /**
     * Moves the middle of the turned model onto the preview's anchor, so it spins about its own
     * vertical axis rather than the entity's origin and the diagonal fit holds at any angle.
     */
    private Vector3f calculateCenterTranslation(GeckoGeometryBounds.Bounds bounds) {
        if (bounds == null) {
            return new Vector3f(0.0F, previewEntity.getBbHeight() * 0.5F, 0.0F);
        }

        double angle = Math.toRadians(180.0F - previewYaw);
        double rotatedX = Math.cos(angle) * bounds.centerX()
                + Math.sin(angle) * bounds.centerZ();
        double rotatedZ = -Math.sin(angle) * bounds.centerX()
                + Math.cos(angle) * bounds.centerZ();
        return new Vector3f(
                (float) rotatedX,
                (float) bounds.centerY(),
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

    // While the texture list is open every mouse event is its own: CustomNPCs hands them on to
    // it from super, and the preview under it must not turn or zoom from clicks meant for the list.

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!hasSubGui() && isInsidePreview(mouseX, mouseY) && scrollY != 0.0D) {
            adjustScale((float) Math.copySign(10.0D, scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!hasSubGui() && button == 0 && isInsidePreview(mouseX, mouseY)) {
            draggingPreview = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!hasSubGui() && button == 0 && draggingPreview) {
            previewYaw = (previewYaw + (float) dragX * 0.8F) % 360.0F;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!hasSubGui() && button == 0 && draggingPreview) {
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
