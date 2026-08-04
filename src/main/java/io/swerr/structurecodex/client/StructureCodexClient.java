package io.swerr.structurecodex.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.swerr.structurecodex.StructureCodex;
import io.swerr.structurecodex.client.preview.StructurePreviewRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class StructureCodexClient implements ClientModInitializer {

    private static KeyMapping openCodex;

    @Override
    public void onInitializeClient() {
        SpecialGuiElementRegistry.register(context -> new StructurePreviewRenderer(context.vertexConsumers()));

        KeyMapping.Category category = KeyMapping.Category.register(StructureCodex.id("main"));

        openCodex = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "structurecodex.key.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCodex.consumeClick()) {
                client.setScreenAndShow(new StructureCodexScreen());
            }
        });

    }
}
