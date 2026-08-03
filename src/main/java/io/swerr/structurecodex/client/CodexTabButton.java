package io.swerr.structurecodex.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class CodexTabButton extends TabButton {

    private static final WidgetSprites SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/tab_selected"),
            Identifier.withDefaultNamespace("widget/tab"),
            Identifier.withDefaultNamespace("widget/tab_selected_highlighted"),
            Identifier.withDefaultNamespace("widget/tab_highlighted"));

    private static final int COLOR_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_INACTIVE = 0xFFA0A0A0;
    private static final int TEXT_HEIGHT = 8;

    public CodexTabButton(TabManager tabManager, Tab tab, int width, int height) {
        super(tabManager, tab, width, height);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                SPRITES.get(isSelected(), isHoveredOrFocused()),
                getX(), getY(), getWidth(), getHeight());

        int textY = getY() + (getHeight() - TEXT_HEIGHT) / 2 + (isSelected() ? 0 : 1);
        graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, textY, active ? COLOR_ACTIVE : COLOR_INACTIVE);
    }
}
