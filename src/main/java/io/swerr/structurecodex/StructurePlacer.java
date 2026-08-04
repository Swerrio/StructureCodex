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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StructurePlacer {

    private static final int PLACE_LIMIT = 400000;
    private static final int ADAPT_MARGIN = 8;
    private static final int CLEAR_MARGIN = 1;
    private static final int SURFACE_CLEAR_HEIGHT = 48;
    private static final int SUPPORT_PROBE_DEPTH = 24;
    private static final double BEARD_THRESHOLD = 0.4D;
    private static final int SURFACE_GATE_MARGIN = 64;
    private static final int POST_PROCESS_LIMIT = 65536;
    private static final int WRITE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final int ADAPT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    private static final ThreadLocal<Heights> HEIGHTS = new ThreadLocal<>();
    private static final ThreadLocal<List<BlockPos>> POST_PROCESS = new ThreadLocal<>();

    public static boolean recordPostProcessing(BlockPos pos) {
        List<BlockPos> pending = POST_PROCESS.get();
        if (pending == null) {
            return false;
        }
        if (pending.size() < POST_PROCESS_LIMIT) {
            pending.add(pos.immutable());
        }
        return true;
    }

    public static Heights heights() {
        return HEIGHTS.get();
    }

    public static final class Heights {

        private final ServerLevel level;
        private final ChunkGenerator generator;
        private final RandomState randomState;
        private final Map<Long, Integer> cache = new HashMap<>();

        private Heights(ServerLevel level, ChunkGenerator generator, RandomState randomState) {
            this.level = level;
            this.generator = generator;
            this.randomState = randomState;
        }

        public int get(Heightmap.Types type, int x, int z) {
            Heightmap.Types worldgen = switch (type) {
                case WORLD_SURFACE -> Heightmap.Types.WORLD_SURFACE_WG;
                case OCEAN_FLOOR -> Heightmap.Types.OCEAN_FLOOR_WG;
                default -> type;
            };
            long key = BlockPos.asLong(x, worldgen.ordinal(), z);
            Integer cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            int height = generator.getBaseHeight(x, z, worldgen, level, randomState);
            cache.put(key, height);
            return height;
        }
    }

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

    public static Placement place(ServerPlayer player, Identifier id, boolean blend,
                                  boolean vanillaTerrain, int distance) {
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
                new ChunkPos(offsetTowards(player, distance)));
        if (!start.isValid()) {
            return new Placement(Result.GENERATION_FAILED, BlockPos.ZERO);
        }

        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()),
                SectionPos.blockToSectionCoord(box.maxZ()));

        if (ChunkPos.rangeClosed(min, max).anyMatch(chunk -> !level.hasChunk(chunk.x, chunk.z))) {
            return new Placement(Result.CHUNKS_NOT_LOADED, BlockPos.ZERO);
        }

        StructureStart placing = start;
        TerrainAdjustment adaptation = vanillaTerrain
                ? placing.getStructure().terrainAdaptation()
                : TerrainAdjustment.NONE;
        long seed = level.getRandom().nextLong();

        Heights heights = vanillaTerrain
                ? new Heights(level, generator, level.getChunkSource().randomState())
                : null;
        List<BlockPos> postProcess = new ArrayList<>();
        HEIGHTS.set(heights);
        POST_PROCESS.set(postProcess);
        try {
            CaptureLevel capture = new CaptureLevel(level, level.registryAccess());
            capture.readTerrainFrom(true);

            RandomSource dryRandom = RandomSource.create(seed);
            CaptureLevel.whileCapturing(() -> ChunkPos.rangeClosed(min, max).forEach(chunk -> placing.placeInChunk(
                    capture,
                    level.structureManager(),
                    generator,
                    dryRandom,
                    chunkBounds(level, chunk),
                    chunk)));

            Map<BlockPos, BlockState> captured = capture.captured();
            if (captured.isEmpty()) {
                return new Placement(Result.GENERATION_FAILED, BlockPos.ZERO);
            }

            Map<BlockPos, BlockState> keep = blend && adaptation == TerrainAdjustment.NONE
                    ? terrainToKeep(level, captured)
                    : Map.of();

            adaptTerrain(level, placing, adaptation, heights);

            RandomSource placeRandom = RandomSource.create(seed);
            ChunkPos.rangeClosed(min, max).forEach(chunk -> placing.placeInChunk(
                    level,
                    level.structureManager(),
                    generator,
                    placeRandom,
                    chunkBounds(level, chunk),
                    chunk));

            for (Map.Entry<BlockPos, BlockState> entry : keep.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), WRITE_FLAGS);
            }

            replayPostProcessing(level, postProcess);
        } finally {
            HEIGHTS.remove();
            POST_PROCESS.remove();
        }

        return new Placement(Result.PLACED, box.getCenter());
    }

    private static void replayPostProcessing(ServerLevel level, List<BlockPos> pending) {
        for (BlockPos pos : pending) {
            BlockState state = level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                fluid.tick(level, pos, state);
            }
            if (state.getBlock() instanceof LiquidBlock) {
                continue;
            }
            BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
            if (updated != state) {
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        pending.clear();
    }

    private static BoundingBox chunkBounds(ServerLevel level, ChunkPos chunk) {
        return new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
    }

    private static void adaptTerrain(ServerLevel level, StructureStart start,
                                     TerrainAdjustment adaptation, Heights heights) {
        if (adaptation == TerrainAdjustment.NONE || heights == null) {
            return;
        }

        List<Beardifier.Rigid> rigid = new ArrayList<>();
        List<JigsawJunction> junctions = new ArrayList<>();
        List<BoundingBox> footprint = new ArrayList<>();

        for (StructurePiece piece : start.getPieces()) {
            footprint.add(piece.getBoundingBox());
            if (piece instanceof PoolElementStructurePiece pool) {
                if (pool.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
                    rigid.add(new Beardifier.Rigid(pool.getBoundingBox(), adaptation, pool.getGroundLevelDelta()));
                }
                junctions.addAll(pool.getJunctions());
            } else {
                rigid.add(new Beardifier.Rigid(piece.getBoundingBox(), adaptation, 0));
            }
        }

        clearAboveSurface(level, footprint, heights);

        if (rigid.isEmpty()) {
            return;
        }

        int budget = PLACE_LIMIT;
        Map<Long, BlockState> support = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Beardifier.Rigid piece : rigid) {
            BoundingBox region = piece.box().inflatedBy(ADAPT_MARGIN);
            int minY = Math.max(level.getMinY(), region.minY());
            int maxY = Math.min(level.getMaxY(), region.maxY());
            if (minY > maxY) {
                continue;
            }

            List<Beardifier.Rigid> nearbyPieces = new ArrayList<>();
            for (Beardifier.Rigid other : rigid) {
                if (other.box().inflatedBy(Beardifier.BEARD_KERNEL_RADIUS + 1).intersects(region)) {
                    nearbyPieces.add(other);
                }
            }
            List<JigsawJunction> nearbyJunctions = new ArrayList<>();
            for (JigsawJunction junction : junctions) {
                BoundingBox point = new BoundingBox(new BlockPos(junction.getSourceX(),
                        junction.getSourceGroundY(), junction.getSourceZ()));
                if (point.inflatedBy(Beardifier.BEARD_KERNEL_RADIUS + 1).intersects(region)) {
                    nearbyJunctions.add(junction);
                }
            }

            Beardifier beardifier = new Beardifier(List.copyOf(nearbyPieces), List.copyOf(nearbyJunctions), region);

            for (int y = minY; y <= maxY; y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    for (int x = region.minX(); x <= region.maxX(); x++) {
                        if (budget <= 0) {
                            return;
                        }
                        double density = beardifier.compute(new DensityFunction.SinglePointContext(x, y, z));
                        if (density <= -BEARD_THRESHOLD) {
                            cursor.set(x, y, z);
                            BlockState current = level.getBlockState(cursor);
                            if (current.isAir() || current.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
                                continue;
                            }
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), ADAPT_FLAGS);
                            budget--;
                        } else if (density >= BEARD_THRESHOLD) {
                            cursor.set(x, y, z);
                            if (!level.getBlockState(cursor).isAir()) {
                                continue;
                            }
                            level.setBlock(cursor, supportState(level, support, x, y, z), ADAPT_FLAGS);
                            budget--;
                        }
                    }
                }
            }
        }
    }

    private static void clearAboveSurface(ServerLevel level, List<BoundingBox> footprint, Heights heights) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BoundingBox piece : footprint) {
            BoundingBox region = piece.inflatedBy(CLEAR_MARGIN);
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    int live = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    if (region.maxY() < live - SURFACE_GATE_MARGIN) {
                        continue;
                    }
                    int surface = heights.get(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                    if (region.maxY() < surface) {
                        continue;
                    }
                    int top = Math.min(Math.min(live, surface + SURFACE_CLEAR_HEIGHT), level.getMaxY());
                    for (int y = Math.max(surface, level.getMinY()); y <= top; y++) {
                        cursor.set(x, y, z);
                        BlockState state = level.getBlockState(cursor);
                        if (state.isAir() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
                            continue;
                        }
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), ADAPT_FLAGS);
                    }
                }
            }
        }
    }

    private static BlockState supportState(ServerLevel level, Map<Long, BlockState> cache, int x, int y, int z) {
        long key = BlockPos.asLong(x, 0, z);
        BlockState cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        BlockState found = y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int floor = Math.max(level.getMinY(), y - SUPPORT_PROBE_DEPTH);
        for (int probeY = y - 1; probeY >= floor; probeY--) {
            probe.set(x, probeY, z);
            BlockState state = level.getBlockState(probe);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                    || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.TERRACOTTA)) {
                found = state;
            }
            break;
        }

        if (found.is(Blocks.GRASS_BLOCK) || found.is(Blocks.PODZOL) || found.is(Blocks.MYCELIUM)) {
            found = Blocks.DIRT.defaultBlockState();
        }
        cache.put(key, found);
        return found;
    }

    private static Map<BlockPos, BlockState> terrainToKeep(ServerLevel level,
                                                           Map<BlockPos, BlockState> captured) {
        Map<BlockPos, BlockState> keep = new HashMap<>();
        if (captured.isEmpty()) {
            return keep;
        }

        BoundingBox bounds = boundsOf(captured);
        int sizeX = bounds.getXSpan() + 2;
        int sizeY = bounds.getYSpan() + 2;
        int sizeZ = bounds.getZSpan() + 2;

        boolean[] solid = new boolean[sizeX * sizeY * sizeZ];
        for (Map.Entry<BlockPos, BlockState> entry : captured.entrySet()) {
            if (entry.getValue().isAir()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            solid[cell(pos, bounds, sizeX, sizeY)] = true;
        }

        boolean[] reachable = floodFromShell(solid, sizeX, sizeY, sizeZ);

        for (Map.Entry<BlockPos, BlockState> entry : captured.entrySet()) {
            if (!entry.getValue().isAir()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir()) {
                continue;
            }
            if (!reachable[cell(pos, bounds, sizeX, sizeY)]) {
                continue;
            }
            keep.put(pos.immutable(), existing);
        }
        return keep;
    }

    private static BoundingBox boundsOf(Map<BlockPos, BlockState> captured) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : captured.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int cell(BlockPos pos, BoundingBox bounds, int sizeX, int sizeY) {
        int x = pos.getX() - bounds.minX() + 1;
        int y = pos.getY() - bounds.minY() + 1;
        int z = pos.getZ() - bounds.minZ() + 1;
        return x + sizeX * (y + sizeY * z);
    }

    private static boolean[] floodFromShell(boolean[] solid, int sizeX, int sizeY, int sizeZ) {
        boolean[] reachable = new boolean[solid.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int z = 0; z < sizeZ; z++) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    boolean shell = x == 0 || y == 0 || z == 0
                            || x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1;
                    if (!shell) {
                        continue;
                    }
                    int i = x + sizeX * (y + sizeY * z);
                    if (!solid[i] && !reachable[i]) {
                        reachable[i] = true;
                        queue.add(i);
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % sizeX;
            int y = (i / sizeX) % sizeY;
            int z = i / (sizeX * sizeY);

            for (Direction direction : Direction.values()) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                    continue;
                }
                int n = nx + sizeX * (ny + sizeY * nz);
                if (!solid[n] && !reachable[n]) {
                    reachable[n] = true;
                    queue.add(n);
                }
            }
        }
        return reachable;
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
