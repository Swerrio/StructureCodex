package io.swerr.structurecodex.client;

import io.swerr.structurecodex.StructureCodex;
import io.swerr.structurecodex.catalog.StructureCatalog;
import io.swerr.structurecodex.preview.ClientWorldgen;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

public final class CatalogSource {

    private CatalogSource() {
    }

    public static Optional<StructureCatalog> load(Minecraft minecraft) {
        try {
            if (minecraft.hasSingleplayerServer()) {
                MinecraftServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    Optional<StructureCatalog> live = StructureCatalog.fromRegistry(server.registryAccess());
                    if (live.isPresent()) {
                        return live;
                    }
                }
            }
            ClientWorldgen.Loaded loaded = ClientWorldgen.getIfReady();
            if (loaded != null) {
                Optional<StructureCatalog> local = StructureCatalog.fromRegistry(loaded.registries());
                if (local.isPresent()) {
                    return local;
                }
            }
            if (minecraft.level != null) {
                Optional<StructureCatalog> synced = StructureCatalog.fromRegistry(minecraft.level.registryAccess());
                if (synced.isPresent()) {
                    return synced;
                }
            }
            return StructureCatalog.fromBuiltInData();
        } catch (RuntimeException exception) {
            StructureCodex.LOGGER.error("Failed to load the structure catalog", exception);
            return Optional.empty();
        }
    }
}
