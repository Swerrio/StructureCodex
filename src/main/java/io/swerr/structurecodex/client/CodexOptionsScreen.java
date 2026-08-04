package io.swerr.structurecodex.client;

import io.swerr.structurecodex.config.CodexConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CodexOptionsScreen extends OptionsSubScreen {

    private static final Component TITLE = Component.translatable("structurecodex.options.title");
    private static final int BUDGET_STEP = 10000;

    private final OptionInstance<Boolean> normalOverworld = OptionInstance.createBoolean(
            "structurecodex.options.normal_overworld",
            OptionInstance.cachedConstantTooltip(
                    Component.translatable("structurecodex.options.normal_overworld.tooltip")),
            CodexConfig.get().previewInNormalOverworld());

    private final OptionInstance<Boolean> blendPlacement = OptionInstance.createBoolean(
            "structurecodex.options.blend",
            OptionInstance.cachedConstantTooltip(
                    Component.translatable("structurecodex.options.blend.tooltip")),
            CodexConfig.get().blendPlacement());

    private final OptionInstance<Boolean> vanillaTerrain = OptionInstance.createBoolean(
            "structurecodex.options.vanilla_terrain",
            OptionInstance.cachedConstantTooltip(
                    Component.translatable("structurecodex.options.vanilla_terrain.tooltip")),
            CodexConfig.get().vanillaTerrain());

    private final OptionInstance<Integer> placeDistance = new OptionInstance<>(
            "structurecodex.options.place_distance",
            OptionInstance.cachedConstantTooltip(
                    Component.translatable("structurecodex.options.place_distance.tooltip")),
            Options::genericValueOrOffLabel,
            new OptionInstance.IntRange(0, 64),
            CodexConfig.get().placeDistance(),
            value -> {
            });

    private final OptionInstance<Integer> blockBudget = new OptionInstance<>(
            "structurecodex.options.block_budget",
            OptionInstance.cachedConstantTooltip(
                    Component.translatable("structurecodex.options.block_budget.tooltip")),
            (caption, value) -> Options.genericValueLabel(caption, value * BUDGET_STEP),
            new OptionInstance.IntRange(2, 40),
            CodexConfig.get().previewBlockBudget() / BUDGET_STEP,
            value -> {
            });

    public CodexOptionsScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        this.list.addSmall(normalOverworld, blendPlacement);
        this.list.addBig(vanillaTerrain);
        this.list.addBig(placeDistance);
        this.list.addBig(blockBudget);
        this.list.addSmall(List.<net.minecraft.client.gui.components.AbstractWidget>of(
                Button.builder(Component.translatable("structurecodex.options.browse"),
                                button -> this.minecraft.setScreenAndShow(new StructureCodexScreen(this)))
                        .build()));
    }

    @Override
    public void removed() {
        super.removed();
        CodexConfig.set(new CodexConfig(
                normalOverworld.get(),
                blockBudget.get() * BUDGET_STEP,
                blendPlacement.get(),
                vanillaTerrain.get(),
                placeDistance.get()));
    }
}
