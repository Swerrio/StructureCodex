package io.swerr.structurecodex.mixin;

import io.swerr.structurecodex.StructurePlacer;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GravityProcessor.class)
public class GravityProcessorMixin {

    @Redirect(method = "processBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int structurecodex$worldgenHeight(LevelReader reader, Heightmap.Types type, int x, int z) {
        StructurePlacer.Heights heights = StructurePlacer.heights();
        if (heights == null) {
            return reader.getHeight(type, x, z);
        }
        return heights.get(type, x, z);
    }
}
