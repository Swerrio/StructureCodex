package io.swerr.structurecodex.preview;

import io.swerr.structurecodex.StructureCodex;
import io.swerr.structurecodex.mixin.StructureTemplateAccessor;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TemplateBlocks {

    private static final Set<Block> HIDDEN = Set.of(
            Blocks.STRUCTURE_BLOCK,
            Blocks.STRUCTURE_VOID,
            Blocks.BARRIER,
            Blocks.LIGHT);

    private static final Map<String, BlockState> FINAL_STATES = new ConcurrentHashMap<>();

    public record Raw(BlockPos pos, BlockState state) {
    }

    private TemplateBlocks() {
    }

    public static List<Raw> read(StructureTemplate template, int variant) {
        List<Raw> result = new ArrayList<>();
        try {
            List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).structurecodex$palettes();
            if (palettes.isEmpty()) {
                return result;
            }

            StructureTemplate.Palette palette = palettes.get(Math.floorMod(variant, palettes.size()));
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                BlockState state = info.state();
                if (state.is(Blocks.JIGSAW)) {
                    state = finalState(info);
                }
                if (state.isAir() || HIDDEN.contains(state.getBlock())) {
                    continue;
                }
                result.add(new Raw(info.pos(), state));
            }
        } catch (Exception exception) {
            StructureCodex.LOGGER.warn("Could not read a structure template", exception);
        }
        return result;
    }

    private static BlockState finalState(StructureTemplate.StructureBlockInfo info) {
        CompoundTag nbt = info.nbt();
        if (nbt == null) {
            return Blocks.AIR.defaultBlockState();
        }
        String name = nbt.getStringOr("final_state", "minecraft:air");
        BlockState cached = FINAL_STATES.get(name);
        if (cached != null) {
            return cached;
        }
        BlockState parsed = Blocks.AIR.defaultBlockState();
        try {
            parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, name, false).blockState();
        } catch (Exception exception) {
            StructureCodex.LOGGER.warn("Could not parse jigsaw final_state {}", name, exception);
        }
        FINAL_STATES.put(name, parsed);
        return parsed;
    }
}
