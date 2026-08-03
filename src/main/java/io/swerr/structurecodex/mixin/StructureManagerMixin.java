package io.swerr.structurecodex.mixin;

import io.swerr.structurecodex.preview.CaptureLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(StructureManager.class)
public class StructureManagerMixin {

    @Inject(method = "startsForStructure(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void structurecodex$skipChunkLookupWhileCapturing(
            ChunkPos pos, Predicate<Structure> matcher, CallbackInfoReturnable<List<StructureStart>> info) {
        if (CaptureLevel.isCapturing()) {
            info.setReturnValue(List.of());
        }
    }

    @Inject(method = "startsForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void structurecodex$skipSectionLookupWhileCapturing(
            SectionPos pos, Structure structure, CallbackInfoReturnable<List<StructureStart>> info) {
        if (CaptureLevel.isCapturing()) {
            info.setReturnValue(List.of());
        }
    }
}
