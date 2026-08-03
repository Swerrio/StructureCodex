package io.swerr.structurecodex.client;

import io.swerr.structurecodex.catalog.StructureEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class StructureList extends ObjectSelectionList<StructureList.StructureRow> {

    private static final int ROW_HEIGHT = 14;
    private static final int COLOR_VANILLA = 0xFFFFFFFF;
    private static final int COLOR_MODDED = 0xFFB9CBE4;
    private static final int TEXT_HEIGHT = 8;

    private final Consumer<StructureEntry> onSelected;

    public StructureList(Minecraft minecraft, int width, int height, int y,
                         List<StructureEntry> entries, Consumer<StructureEntry> onSelected) {
        super(minecraft, width, height, y, ROW_HEIGHT);
        this.onSelected = onSelected;
        for (StructureEntry entry : entries) {
            addEntry(new StructureRow(entry));
        }
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 30;
    }

    public void setEntries(List<StructureEntry> entries) {
        clearEntries();
        for (StructureEntry entry : entries) {
            addEntry(new StructureRow(entry));
        }
        setScrollAmount(0);
    }

    public class StructureRow extends ObjectSelectionList.Entry<StructureRow> {

        private final StructureEntry entry;

        StructureRow(StructureEntry entry) {
            this.entry = entry;
        }

        public StructureEntry entry() {
            return entry;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            graphics.centeredText(Minecraft.getInstance().font, entry.displayName(),
                    getContentX() + getContentWidth() / 2,
                    getContentY() + (getContentHeight() - TEXT_HEIGHT) / 2,
                    entry.isVanilla() ? COLOR_VANILLA : COLOR_MODDED);
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.displayName());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            StructureList.this.setSelected(this);
            onSelected.accept(entry);
            return true;
        }
    }
}
