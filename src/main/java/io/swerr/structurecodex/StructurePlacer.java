package io.swerr.structurecodex;

import io.swerr.structurecodex.preview.CaptureLevel;
import io.swerr.structurecodex.preview.StructurePreviewData;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StructurePlacer {

    private static final int PLACE_LIMIT = 400000;
    private static final int WRITE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public enum Result {
        PLACED,
        NO_PERMISSION,
        UNKNOWN_STRUCTURE,
        GENERATION_FAILED,
        CHUNKS_NOT_LOADED
    }

    public record Placement(Result result, BlockPos where) {
    }

    private StructurePlacer() {
    }

    public static Placement place(ServerPlayer player, Identifier id, boolean blend, int distance) {
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
            return new Placement(Result.NO_PERMISSION, BlockPos.ZERO);
        }

        ServerLevel level = player.level();
        Optional<Registry<Structure>> registry = level.registryAccess().lookup(Registries.STRUCTURE);
        if (registry.isEmpty()) {
            return new Placement(Result.UNKNOWN_STRUCTURE, BlockPos.ZERO);
        }

        Optional<Holder.Reference<Structure>> holder = registry.get().get(id);
        if (holder.isEmpty()) {
            return new Placement(Result.UNKNOWN_STRUCTURE, BlockPos.ZERO);
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();

        StructureStart start = generate(holder.get(), level, generator,
                ChunkPos.containing(offsetTowards(player, distance)));
        if (!start.isValid()) {
            return new Placement(Result.GENERATION_FAILED, BlockPos.ZERO);
        }

        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()),
                SectionPos.blockToSectionCoord(box.maxZ()));

        if (ChunkPos.rangeClosed(min, max).anyMatch(chunk -> !level.hasChunk(chunk.x(), chunk.z()))) {
            return new Placement(Result.CHUNKS_NOT_LOADED, BlockPos.ZERO);
        }

        CaptureLevel capture = new CaptureLevel(level, level.registryAccess());
        capture.readTerrainFrom(true);
        StructureStart placing = start;

        CaptureLevel.whileCapturing(() -> ChunkPos.rangeClosed(min, max).forEach(chunk -> placing.placeInChunk(
                capture,
                level.structureManager(),
                generator,
                level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ()),
                chunk)));

        Map<BlockPos, BlockState> captured = capture.captured();
        if (captured.isEmpty()) {
            return new Placement(Result.GENERATION_FAILED, BlockPos.ZERO);
        }

        Map<BlockPos, BlockState> keep = blend
                ? terrainToKeep(level, captured)
                : Map.of();

        ChunkPos.rangeClosed(min, max).forEach(chunk -> placing.placeInChunk(
                level,
                level.structureManager(),
                generator,
                level.getRandom(),
                new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ()),
                chunk));

        for (Map.Entry<BlockPos, BlockState> entry : keep.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), WRITE_FLAGS);
        }

        return new Placement(Result.PLACED, box.getCenter());
    }

    private static Map<BlockPos, BlockState> terrainToKeep(ServerLevel level,
                                                           Map<BlockPos, BlockState> captured) {
        Map<BlockPos, BlockState> keep = new HashMap<>();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

        for (Map.Entry<BlockPos, BlockState> entry : captured.entrySet()) {
            if (!entry.getValue().isAir()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir()) {
                continue;
            }
            if (touchesStructure(captured, pos, neighbour)) {
                continue;
            }
            keep.put(pos.immutable(), existing);
        }
        return keep;
    }

    private static boolean touchesStructure(Map<BlockPos, BlockState> captured, BlockPos pos,
                                            BlockPos.MutableBlockPos neighbour) {
        for (Direction direction : Direction.values()) {
            neighbour.setWithOffset(pos, direction);
            BlockState state = captured.get(neighbour);
            if (state != null && !state.isAir()) {
                return true;
            }
        }
        return false;
    }

    private static StructureStart generate(Holder<Structure> holder, ServerLevel level,
                                           ChunkGenerator generator, ChunkPos where) {
        return holder.value().generate(
                holder,
                level.dimension(),
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed(),
                where,
                0,
                level,
                biome -> true);
    }

    private static BlockPos offsetTowards(ServerPlayer player, int distance) {
        BlockPos feet = player.blockPosition();
        if (distance <= 0) {
            return feet;
        }
        int facing = Math.round(player.getYRot() / 90.0F) & 3;
        return switch (facing) {
            case 0 -> feet.offset(0, 0, distance);
            case 1 -> feet.offset(-distance, 0, 0);
            case 2 -> feet.offset(0, 0, -distance);
            default -> feet.offset(distance, 0, 0);
        };
    }
}
