package io.swerr.structurecodex.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

@Mixin(StructureTemplateManager.class)
public class StructureTemplateManagerMixin {

    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
                            + "getLevelPath(Lnet/minecraft/world/level/storage/LevelResource;)Ljava/nio/file/Path;"))
    private Path structurecodex$allowNullStorage(LevelStorageSource.LevelStorageAccess storage,
                                                 LevelResource resource) {
        if (storage != null) {
            return storage.getLevelPath(resource);
        }
        return FabricLoader.getInstance().getGameDir()
                .resolve("structurecodex").resolve("no-generated");
    }
}
