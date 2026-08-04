package io.swerr.structurecodex.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class CodexIconButton extends AbstractButton {

    public static final String[] EXPAND = {
            "###....###",
            "#........#",
            "#........#",
            "..........",
            "..........",
            "..........",
            "..........",
            "#........#",
            "#........#",
            "###....###"
    };

    public static final String[] COLLAPSE = {
            "..#....#..",
            "..#....#..",
            "###....###",
            "..........",
            "..........",
            "..........",
            "..........",
            "###....###",
            "..#....#..",
            "..#....#.."
    };

    public static final String[] REROLL = {
            "##########",
            "#........#",
            "#.#....#.#",
            "#........#",
            "#...##...#",
            "#...##...#",
            "#........#",
            "#.#....#.#",
            "#........#",
            "##########"
    };

    private static final int ICON_HOVERED = 0xFFFFFFFF;
    private static final int ICON_ACTIVE = 0xFFDBDBDB;
    private static final int ICON_INACTIVE = 0xFFA0A0A0;

    private final Runnable action;
    private String[] icon;

    public CodexIconButton(int size, String[] icon, Component label, Runnable action) {
        super(0, 0, size, size, label);
        this.icon = icon;
        this.action = action;
        setTooltip(Tooltip.create(label));
    }

    public void setIcon(String[] value, Component label) {
        icon = value;
        setMessage(label);
        setTooltip(Tooltip.create(label));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDefaultSprite(graphics);

        int originX = getX() + (getWidth() - icon[0].length()) / 2;
        int originY = getY() + (getHeight() - icon.length) / 2;
        int color = !isActive() ? ICON_INACTIVE : isHoveredOrFocused() ? ICON_HOVERED : ICON_ACTIVE;

        drawIcon(graphics, originX, originY, color);
    }

    private void drawIcon(GuiGraphics graphics, int originX, int originY, int color) {
        for (int row = 0; row < icon.length; row++) {
            String line = icon[row];
            int start = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean filled = col < line.length() && line.charAt(col) == '#';
                if (filled && start < 0) {
                    start = col;
                } else if (!filled && start >= 0) {
                    graphics.fill(originX + start, originY + row, originX + col, originY + row + 1, color);
                    start = -1;
                }
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
