package io.swerr.structurecodex.client;

import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.input.MouseButtonEvent;

public class CodexTabButton extends TabButton {

    private final TabManager tabManager;
    private final Tab codexTab;

    public CodexTabButton(TabManager tabManager, Tab tab, int width, int height) {
        super(tabManager, tab, width, height);
        this.tabManager = tabManager;
        this.codexTab = tab;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        tabManager.setCurrentTab(codexTab, true);
    }
}
