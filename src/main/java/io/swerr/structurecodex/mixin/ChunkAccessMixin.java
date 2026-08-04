package io.swerr.structurecodex.mixin;

import io.swerr.structurecodex.StructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {

    @Inject(method = "markPosForPostProcessing(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void structurecodex$capturePostProcessing(BlockPos pos, CallbackInfo ci) {
        if (StructurePlacer.recordPostProcessing(pos)) {
            ci.cancel();
        }
    }
}
