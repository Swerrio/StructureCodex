package io.swerr.structurecodex.preview;

import io.swerr.structurecodex.StructureCodex;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.validation.DirectoryValidator;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientWorldgen {

    public record Loaded(RegistryAccess.Frozen registries,
                         StructureTemplateManager templates,
                         CloseableResourceManager resources) {
    }

    private record Packs(CloseableResourceManager resources, WorldDataConfiguration configuration) {
    }

    private static final Object LOCK = new Object();

    private static CompletableFuture<Loaded> pending;
    private static volatile Loaded ready;

    private ClientWorldgen() {
    }

    public static CompletableFuture<Loaded> loadAsync() {
        DirectoryValidator validator = Minecraft.getInstance().directoryValidator();
        synchronized (LOCK) {
            if (pending == null) {
                pending = CompletableFuture
                        .supplyAsync(() -> openPacks(validator), Util.backgroundExecutor())
                        .thenCompose(ClientWorldgen::loadRegistries)
                        .whenComplete(ClientWorldgen::publish);
            }
            return pending;
        }
    }

    public static Loaded getIfReady() {
        return ready;
    }

    public static void invalidate() {
        Loaded stale;
        synchronized (LOCK) {
            stale = ready;
            ready = null;
            pending = null;
        }
        if (stale != null) {
            stale.resources().close();
        }
    }

    private static void publish(Loaded loaded, Throwable error) {
        if (error != null) {
            StructureCodex.LOGGER.error("Could not build client-side worldgen registries", error);
            synchronized (LOCK) {
                pending = null;
            }
            return;
        }
        ready = loaded;
    }

    private static Packs openPacks(DirectoryValidator validator) {
        Path directory = FabricLoader.getInstance().getGameDir()
                .resolve("structurecodex").resolve("datapacks");
        PackRepository repository = ServerPacksSource.createPackRepository(directory, validator);
        WorldDataConfiguration configuration = MinecraftServer.configurePackRepository(
                repository, WorldDataConfiguration.DEFAULT, true, false);
        CloseableResourceManager resources =
                new MultiPackResourceManager(PackType.SERVER_DATA, repository.openAllSelected());
        return new Packs(resources, configuration);
    }

    private static CompletableFuture<Loaded> loadRegistries(Packs packs) {
        CloseableResourceManager resources = packs.resources();
        try {
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            List<Registry.PendingTags<?>> staticTags =
                    TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
            RegistryAccess.Frozen loadContext = layers.getAccessForLoading(RegistryLayer.WORLDGEN);
            List<HolderLookup.RegistryLookup<?>> context =
                    TagLoader.buildUpdatedLookups(loadContext, staticTags);

            return RegistryDataLoader
                    .load(resources, context, RegistryDataLoader.WORLDGEN_REGISTRIES, Util.backgroundExecutor())
                    .thenApply(worldgen -> finish(layers, worldgen, packs))
                    .whenComplete((loaded, error) -> {
                        if (error != null) {
                            resources.close();
                        }
                    });
        } catch (RuntimeException exception) {
            resources.close();
            throw exception;
        }
    }

    private static Loaded finish(LayeredRegistryAccess<RegistryLayer> layers,
                                 RegistryAccess.Frozen worldgen,
                                 Packs packs) {
        RegistryAccess.Frozen composite =
                layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen).compositeAccess();
        HolderGetter<Block> blocks = composite.lookupOrThrow(Registries.BLOCK)
                .filterFeatures(packs.configuration().enabledFeatures());
        StructureTemplateManager templates = new StructureTemplateManager(
                packs.resources(), null, DataFixers.getDataFixer(), blocks);
        return new Loaded(composite, templates, packs.resources());
    }
}
