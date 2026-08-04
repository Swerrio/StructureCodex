package io.swerr.structurecodex.client;

import io.swerr.structurecodex.StructureCodex;
import io.swerr.structurecodex.catalog.StructureCatalog;
import io.swerr.structurecodex.catalog.StructureCategory;
import io.swerr.structurecodex.catalog.StructureEntry;
import io.swerr.structurecodex.client.preview.PreviewLevel;
import io.swerr.structurecodex.client.preview.PreviewGpuMesh;
import io.swerr.structurecodex.client.preview.PreviewMesh;
import io.swerr.structurecodex.client.preview.StructurePreviewRenderState;
import io.swerr.structurecodex.config.CodexConfig;
import io.swerr.structurecodex.preview.ClientWorldgen;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.concurrent.CompletableFuture;
import io.swerr.structurecodex.mixin.GuiGraphicsAccessor;
import io.swerr.structurecodex.network.PlaceStructurePayload;
import io.swerr.structurecodex.preview.StructureAssembler;
import io.swerr.structurecodex.preview.StructurePreviewData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StructureCodexScreen extends Screen {

    private static final int TAB_BAR_HEIGHT = 24;
    private static final int SEARCH_HEIGHT = 24;
    private static final int INFO_HEIGHT = 58;
    private static final int TOOL_BUTTON_SIZE = 16;
    private static final int TOOL_BUTTON_GAP = 3;
    private static final int PREVIEW_MAX_SIZE = 4096;
    private static final int FOOTER_HEIGHT = 36;
    private static final int MIN_TAB_WIDTH = 44;
    private static final int TAB_PADDING = 16;

    private static final int COLOR_LABEL = 0xFFA0A0A0;
    private static final int COLOR_VALUE = 0xFFFFFFFF;
    private static final int COLOR_TRACK = 0x60000000;
    private static final int COLOR_BACKDROP = 0xF00E0E12;
    private static final int COLOR_THUMB = 0xFF8B8B8B;

    private final Screen parent;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final List<CategoryTab> categoryTabs = new ArrayList<>();

    private StructureCatalog catalog;
    private final List<CodexTabButton> tabButtons = new ArrayList<>();
    private StructureEntry selected;
    private Button placeButton;

    private EditBox searchBox;
    private String searchQuery = "";

    private StructurePreviewData preview;
    private PreviewGpuMesh quads;
    private float previewYaw = 35.0F;
    private float previewPitch = 20.0F;
    private float previewZoom = 1.0F;
    private boolean autoRotate = true;
    private boolean draggingPreview;
    private double lastDragX;
    private double lastDragY;
    private long previewSeed = 1234L;
    private int previewTicks;
    private int previewToken;
    private boolean previewLoading;
    private boolean fullscreen;
    private Button doneButton;
    private CodexIconButton expandButton;
    private CodexIconButton refreshButton;

    private int previewLeft() {
        return fullscreen ? 8 : width / 2 + 8;
    }

    private int previewRight() {
        return width - 8;
    }

    private int previewTop() {
        return fullscreen ? 8 : contentArea().top();
    }

    private int previewBottom() {
        if (fullscreen) {
            return height - 8;
        }
        ScreenRectangle area = contentArea();
        int available = area.top() + area.height() - INFO_HEIGHT;
        return Math.max(previewTop() + 40, available);
    }

    private void layoutToolButtons() {
        boolean available = hasPreviewArea();
        if (expandButton != null) {
            expandButton.setPosition(previewLeft() + TOOL_BUTTON_GAP, previewTop() + TOOL_BUTTON_GAP);
            expandButton.visible = available;
            expandButton.setIcon(
                    fullscreen ? CodexIconButton.COLLAPSE : CodexIconButton.EXPAND,
                    Component.translatable(fullscreen
                            ? "structurecodex.button.collapse"
                            : "structurecodex.button.expand"));
        }
        if (refreshButton != null) {
            refreshButton.setPosition(previewLeft() + TOOL_BUTTON_GAP * 2 + TOOL_BUTTON_SIZE,
                    previewTop() + TOOL_BUTTON_GAP);
            refreshButton.visible = available;
        }
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        boolean show = !fullscreen;
        setTabBarVisible(show);
        if (searchBox != null) {
            searchBox.visible = show;
        }
        if (placeButton != null) {
            placeButton.visible = show;
        }
        if (doneButton != null) {
            doneButton.visible = show;
        }
        for (CategoryTab tab : categoryTabs) {
            tab.list.visible = show;
        }
        layoutToolButtons();
    }

    private boolean hasPreviewArea() {
        return selected != null && selected.canGenerate();
    }

    private boolean inPreview(double x, double y) {
        return x >= previewLeft() && x < previewRight() && y >= previewTop() && y < previewBottom();
    }

    private void loadPreview() {
        preview = null;
        if (quads != null) {
            quads.close();
            quads = null;
        }
        previewTicks = 0;
        previewLoading = false;
        if (selected == null || !selected.canGenerate() || minecraft == null) {
            return;
        }
        previewLoading = true;
        MinecraftServer server = minecraft.getSingleplayerServer();
        Structure structure = selected.structure();
        net.minecraft.resources.Identifier id = selected.id();
        long seed = previewSeed;
        int token = ++previewToken;

        CompletableFuture<?> gate = server != null
                ? CompletableFuture.completedFuture(null)
                : ClientWorldgen.loadAsync();

        gate.thenApplyAsync(ignored -> StructureAssembler.assemble(structure, server, seed).orElse(null),
                        Util.backgroundExecutor())
                .whenCompleteAsync((data, error) -> {
                    if (token != previewToken) {
                        return;
                    }
                    previewLoading = false;
                    if (error != null) {
                        StructureCodex.LOGGER.warn("Could not build a preview for {}", id, error);
                        return;
                    }
                    if (data == null || minecraft.level == null) {
                        return;
                    }
                    preview = data;
                    PreviewLevel previewLevel = new PreviewLevel(minecraft, data.all(), data.size());
                    try (PreviewMesh mesh = PreviewMesh.build(previewLevel, data)) {
                        quads = mesh.isEmpty() ? null : PreviewGpuMesh.upload(mesh, data);
                    }
                }, minecraft)
                .exceptionally(failure -> {
                    StructureCodex.LOGGER.warn("Preview delivery failed for {}", id, failure);
                    return null;
                });
    }

    private boolean tabBarAttached;

    private void setTabBarVisible(boolean show) {
        if (show == tabBarAttached) {
            return;
        }
        for (CodexTabButton button : tabButtons) {
            button.visible = show;
        }
        tabBarAttached = show;
    }

    private int listWidth() {
        return width / 2 - 12;
    }

    public StructureCodexScreen() {
        this(null);
    }

    public StructureCodexScreen(Screen parent) {
        super(Component.translatable("structurecodex.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (catalog == null) {
            catalog = CatalogSource.load(minecraft).orElse(null);
        }

        if (minecraft != null && !minecraft.hasSingleplayerServer() && ClientWorldgen.getIfReady() == null) {
            ClientWorldgen.loadAsync().thenRunAsync(() -> {
                catalog = null;
                rebuildWidgets();
            }, minecraft);
        }

        categoryTabs.clear();
        tabButtons.clear();
        tabBarAttached = false;

        if (catalog != null) {
            for (StructureCategory category : catalog.populatedTabs()) {
                categoryTabs.add(new CategoryTab(category));
            }
        }

        if (!categoryTabs.isEmpty()) {
            int x = 0;
            for (CategoryTab tab : categoryTabs) {
                int tabWidth = Math.max(MIN_TAB_WIDTH, font.width(tab.getTabTitle()) + TAB_PADDING);
                CodexTabButton button = new CodexTabButton(tabManager, tab, tabWidth, TAB_BAR_HEIGHT);
                button.setPosition(x, 0);
                x += tabWidth;
                tabButtons.add(addRenderableWidget(button));
            }
            tabBarAttached = true;
            tabManager.setCurrentTab(categoryTabs.getFirst(), false);
            tabManager.setTabArea(contentArea());
        }

        if (catalog != null) {
            searchBox = new EditBox(font, 4, contentArea().top() + 2, listWidth(), 18,
                    Component.translatable("structurecodex.screen.search"));
            searchBox.setHint(Component.translatable("structurecodex.screen.search"));
            searchBox.setMaxLength(64);
            searchBox.setValue(searchQuery);
            searchBox.setResponder(this::onSearchChanged);
            addRenderableWidget(searchBox);
        }

        placeButton = addRenderableWidget(Button.builder(
                        Component.translatable("structurecodex.button.place"), button -> placeSelected())
                .bounds(width / 2 - 154, height - 28, 150, 20)
                .build());
        placeButton.active = selected != null && selected.canGenerate();

        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 + 4, height - 28, 150, 20)
                .build());

        expandButton = addRenderableWidget(new CodexIconButton(TOOL_BUTTON_SIZE, CodexIconButton.EXPAND,
                Component.translatable("structurecodex.button.expand"), this::toggleFullscreen));

        refreshButton = addRenderableWidget(new CodexIconButton(TOOL_BUTTON_SIZE, CodexIconButton.REROLL,
                Component.translatable("structurecodex.button.refresh"), this::rerollPreview));

        layoutToolButtons();
    }

    private ScreenRectangle contentArea() {
        int top = TAB_BAR_HEIGHT + 4;
        return new ScreenRectangle(0, top, width, height - top - FOOTER_HEIGHT);
    }

    private void onSearchChanged(String query) {
        searchQuery = query;
        for (CategoryTab tab : categoryTabs) {
            tab.applyFilter(query);
        }
    }

    private void onEntrySelected(StructureEntry entry) {
        selected = entry;
        if (placeButton != null) {
            placeButton.active = entry != null && entry.canGenerate();
        }
        autoRotate = true;
        previewYaw = 35.0F;
        previewPitch = 20.0F;
        previewZoom = 1.0F;
        loadPreview();
        layoutToolButtons();
    }

    @Override
    public void tick() {
        super.tick();
    }

    private void rerollPreview() {
        if (selected == null || !selected.canGenerate()) {
            return;
        }
        previewSeed = previewSeed * 6364136223846793005L + 1442695040888963407L;
        loadPreview();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (catalog == null) {
            boolean preparing = minecraft != null && !minecraft.hasSingleplayerServer()
                    && ClientWorldgen.getIfReady() == null;
            String message = preparing ? "structurecodex.screen.preparing" : "structurecodex.screen.unavailable";
            graphics.drawWordWrap(font, Component.translatable(message),
                    width / 2 - 150, height / 2 - 10, 300, COLOR_LABEL);
            return;
        }

        int detailLeft = previewLeft();
        int detailTop = contentArea().top() + 4;

        if (selected == null) {
            graphics.drawString(font, Component.translatable("structurecodex.screen.no_selection"),
                    detailLeft, detailTop, COLOR_LABEL);
            return;
        }

        int y = detailTop;

        if (hasPreviewArea()) {
            if (fullscreen) {
                graphics.fill(0, 0, width, height, COLOR_BACKDROP);
            }
            graphics.fill(previewLeft(), previewTop(), previewRight(), previewBottom(), COLOR_TRACK);
            graphics.renderOutline(previewLeft(), previewTop(),
                    previewRight() - previewLeft(), previewBottom() - previewTop(), COLOR_THUMB);

            if (quads != null) {
                GuiRenderState renderState = ((GuiGraphicsAccessor) graphics).structurecodex$guiRenderState();
                renderState.submitPicturesInPictureState(new StructurePreviewRenderState(
                        quads, previewYaw, previewPitch, previewZoom,
                        previewLeft() + 1, previewTop() + 1, previewRight() - 1, previewBottom() - 1,
                        1.0F, null));
            } else {
                graphics.drawCenteredString(font, Component.translatable(previewLoading
                                ? "structurecodex.screen.loading"
                                : "structurecodex.screen.no_preview"),
                        (previewLeft() + previewRight()) / 2,
                        (previewTop() + previewBottom()) / 2 - 4, COLOR_LABEL);
            }

            y = previewBottom() + 6;
        }

        if (fullscreen) {
            return;
        }

        graphics.drawString(font, Component.literal(selected.displayName()), detailLeft, y, COLOR_VALUE);
        y += font.lineHeight + 3;

        y = infoLine(graphics, detailLeft, y, "structurecodex.info.id", selected.id().toString());
        y = infoLine(graphics, detailLeft, y, "structurecodex.info.type", shortName(selected.info().type()));
        y = infoLine(graphics, detailLeft, y, "structurecodex.info.biomes",
                selected.info().biomeCount() + "   " + selected.info().step());

        if (preview != null) {
            infoLine(graphics, detailLeft, y, "structurecodex.info.size",
                    preview.size().getX() + "x" + preview.size().getY() + "x" + preview.size().getZ()
                            + "  (" + preview.totalBlocks() + ")");
        } else {
            infoLine(graphics, detailLeft, y, "structurecodex.info.terrain_adaptation",
                    selected.info().terrainAdaptation());
        }
    }

    private static String shortName(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private int infoLine(GuiGraphics graphics, int x, int y, String key, String value) {
        Component label = Component.translatable(key);
        graphics.drawString(font, label, x, y, COLOR_LABEL);
        graphics.drawString(font, value, x + font.width(label) + 6, y, COLOR_VALUE);
        return y + font.lineHeight + 2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (quads != null && inPreview(event.x(), event.y())) {
            if (doubleClick) {
                toggleFullscreen();
                return true;
            }
            draggingPreview = true;
            lastDragX = event.x();
            lastDragY = event.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingPreview) {
            previewYaw = (float) ((previewYaw + (event.x() - lastDragX) * 0.8) % 360.0);
            previewPitch = (float) Math.max(-89.0, Math.min(89.0, previewPitch + (event.y() - lastDragY) * 0.5));
            lastDragX = event.x();
            lastDragY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPreview = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (quads != null && inPreview(mouseX, mouseY)) {
            previewZoom = (float) Math.max(0.25, Math.min(5.0, previewZoom + scrollY * 0.15));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void placeSelected() {
        if (selected == null || !selected.canGenerate()) {
            return;
        }
        if (ClientPlayNetworking.canSend(PlaceStructurePayload.TYPE)) {
            ClientPlayNetworking.send(new PlaceStructurePayload(selected.id(),
                    CodexConfig.get().blendPlacement(), CodexConfig.get().vanillaTerrain(),
                    CodexConfig.get().placeDistance()));
            onClose();
            return;
        }
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("structurecodex.place.no_server"), false);
        }
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (fullscreen && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            toggleFullscreen();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        previewToken++;
        if (quads != null) {
            quads.close();
            quads = null;
        }
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreenAndShow(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class CategoryTab implements Tab {

        private final StructureCategory category;
        private final StructureList list;

        CategoryTab(StructureCategory category) {
            this.category = category;
            ScreenRectangle area = contentArea();
            this.list = new StructureList(minecraft, listWidth(), area.height() - SEARCH_HEIGHT,
                    area.top() + SEARCH_HEIGHT,
                    catalog.in(category, searchQuery), StructureCodexScreen.this::onEntrySelected);
        }

        @Override
        public Component getTabTitle() {
            return Component.translatable(category.translationKey());
        }

        @Override
        public Component getTabExtraNarration() {
            return getTabTitle();
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(list);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
            list.updateSizeAndPosition(listWidth(), area.height() - SEARCH_HEIGHT, 4, area.top() + SEARCH_HEIGHT);
        }

        void applyFilter(String query) {
            list.setEntries(catalog.in(category, query));
        }
    }
}
