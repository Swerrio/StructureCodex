package io.swerr.structurecodex.preview;

import com.mojang.datafixers.util.Pair;
import io.swerr.structurecodex.StructureCodex;
import io.swerr.structurecodex.config.CodexConfig;
import io.swerr.structurecodex.mixin.StructureManagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class StructureAssembler {

    private static final int BLOCK_LIMIT = 120000;
    private static final int BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_SEARCH_STEP = 32;
    private static final long GENERATION_BUDGET_MS = 4000L;

    private static final int RETRY_RINGS = 3;
    private static final int RETRY_STEP = 4;
    private static final long RETRY_BUDGET_MS = 2000L;

    private StructureAssembler() {
    }

    public static Optional<StructurePreviewData> assemble(Structure structure, MinecraftServer server, long seed) {
        PreviewWorldgen worldgen;
        ServerLevel host;

        if (server != null) {
            worldgen = worldgenFor(structure, server, seed);
            host = server.overworld();
        } else {
            ClientWorldgen.Loaded loaded = ClientWorldgen.getIfReady();
            if (loaded == null) {
                return Optional.empty();
            }
            worldgen = PreviewWorldgen.sandbox(loaded.registries(), loaded.templates(),
                    PreviewWorldgen.pickStem(loaded.registries(), structure), seed);
            host = null;
        }

        ChunkPos anchor = findAnchor(structure, worldgen, RandomSource.create(seed));
        long deadline = System.nanoTime() + RETRY_BUDGET_MS * 1_000_000L;

        for (ChunkPos origin : ringsAround(anchor)) {
            Optional<StructurePreviewData> data = assembleAt(structure, worldgen, host, origin, seed);
            if (data.isPresent()) {
                return data;
            }
            if (System.nanoTime() > deadline) {
                break;
            }
        }
        return Optional.empty();
    }

    private static PreviewWorldgen worldgenFor(Structure structure, MinecraftServer server, long seed) {
        if (CodexConfig.get().previewInNormalOverworld()) {
            try {
                return PreviewWorldgen.sandbox(server, structure, seed);
            } catch (Exception exception) {
                StructureCodex.LOGGER.warn("Falling back to the live generator for previews", exception);
            }
        }
        return PreviewWorldgen.fromLevel(pickLevel(structure, server));
    }

    private static List<ChunkPos> ringsAround(ChunkPos anchor) {
        List<ChunkPos> result = new ArrayList<>();
        result.add(anchor);
        for (int ring = 1; ring <= RETRY_RINGS; ring++) {
            int edge = ring * RETRY_STEP;
            for (int i = -ring; i <= ring; i++) {
                int along = i * RETRY_STEP;
                result.add(new ChunkPos(anchor.x() + along, anchor.z() - edge));
                result.add(new ChunkPos(anchor.x() + along, anchor.z() + edge));
                result.add(new ChunkPos(anchor.x() - edge, anchor.z() + along));
                result.add(new ChunkPos(anchor.x() + edge, anchor.z() + along));
            }
        }
        return result;
    }

    private static ServerLevel pickLevel(Structure structure, MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Set<Holder<Biome>> possible = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
            for (Holder<Biome> biome : structure.biomes()) {
                if (possible.contains(biome)) {
                    return level;
                }
            }
        }
        return server.overworld();
    }

    private static ChunkPos findAnchor(Structure structure, PreviewWorldgen worldgen, RandomSource random) {
        try {
            BiomeSource source = worldgen.biomeSource();
            Climate.Sampler sampler = worldgen.randomState().sampler();
            Pair<BlockPos, Holder<Biome>> found = source.findBiomeHorizontal(0, 64, 0,
                    BIOME_SEARCH_RADIUS, BIOME_SEARCH_STEP, structure.biomes()::contains, random, true, sampler);
            if (found != null) {
                return ChunkPos.containing(found.getFirst());
            }
        } catch (Exception exception) {
            StructureCodex.LOGGER.warn("Could not locate a biome for a structure preview", exception);
        }
        return new ChunkPos(0, 0);
    }

    private static Optional<StructurePreviewData> assembleAt(Structure structure, PreviewWorldgen worldgen,
                                                             ServerLevel host, ChunkPos origin, long seed) {
        try {
            StructureTemplateManager templates = worldgen.templates();

            Optional<Structure.GenerationStub> stub =
                    structure.findValidGenerationPoint(worldgen.context(origin, seed));
            if (stub.isEmpty()) {
                StructureCodex.LOGGER.debug("No generation point at {}", origin);
                return Optional.empty();
            }

            List<StructurePiece> pieces = stub.get().getPiecesBuilder().build().pieces();
            if (pieces.isEmpty()) {
                StructureCodex.LOGGER.debug("No pieces at {}", origin);
                return Optional.empty();
            }

            Map<BlockPos, BlockState> collected = new HashMap<>();
            List<StructurePiece> generated = new ArrayList<>();

            int budget = CodexConfig.get().previewBlockBudget();
            int variant = 0;
            for (StructurePiece piece : pieces) {
                if (!collectPiece(piece, templates, variant++, collected)) {
                    generated.add(piece);
                }
                if (collected.size() > budget) {
                    break;
                }
            }

            if (!generated.isEmpty() && collected.size() <= budget) {
                collectGenerated(generated, pieces, host, worldgen, seed, collected);
            }

            if (collected.isEmpty()) {
                StructureCodex.LOGGER.debug("No blocks from {} pieces at {}", pieces.size(), origin);
                return Optional.empty();
            }
            return Optional.of(PreviewBuilder.build(collected));
        } catch (Exception exception) {
            StructureCodex.LOGGER.warn("Could not assemble a structure preview", exception);
            return Optional.empty();
        }
    }

    private static boolean collectPiece(StructurePiece piece,
                                        StructureTemplateManager templates,
                                        int variant,
                                        Map<BlockPos, BlockState> out) {
        if (piece instanceof TemplateStructurePiece template) {
            place(template.template(), template.placeSettings(), template.templatePosition(), variant, out);
            return true;
        }

        if (piece instanceof PoolElementStructurePiece pool
                && pool.getElement() instanceof SinglePoolElement single) {
            Optional<StructureTemplate> found = templates.get(single.getTemplateLocation());
            if (found.isPresent()) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
                place(found.get(), settings, pool.getPosition(), variant, out);
                return true;
            }
        }

        return false;
    }

    private static StructureCheck structureCheck(PreviewWorldgen worldgen, ServerLevel host, long seed) {
        if (host != null) {
            return ((StructureManagerAccessor) host.structureManager()).structurecodex$structureCheck();
        }
        return new StructureCheck(
                (pos, visitor) -> CompletableFuture.completedFuture(null),
                worldgen.registries(),
                worldgen.templates(),
                Level.OVERWORLD,
                worldgen.generator(),
                worldgen.randomState(),
                worldgen.heightAccessor(),
                worldgen.biomeSource(),
                seed,
                DataFixers.getDataFixer());
    }

    private static void collectGenerated(List<StructurePiece> generated,
                                         List<StructurePiece> all,
                                         ServerLevel host,
                                         PreviewWorldgen worldgen,
                                         long seed,
                                         Map<BlockPos, BlockState> out) {
        ChunkGenerator generator = worldgen.generator();
        BoundingBox first = all.getFirst().getBoundingBox();
        BlockPos anchor = new BlockPos(first.getCenter().getX(), first.minY(), first.getCenter().getZ());

        BoundingBox span = generated.getFirst().getBoundingBox();
        for (StructurePiece piece : generated) {
            span = BoundingBox.encapsulatingBoxes(List.of(span, piece.getBoundingBox())).orElse(span);
        }

        LevelHeightAccessor heights = worldgen.heightAccessor();
        BoundingBox bounds = new BoundingBox(span.minX() - 64, heights.getMinY(), span.minZ() - 64,
                span.maxX() + 64, heights.getMaxY(), span.maxZ() + 64);

        CaptureLevel capture = new CaptureLevel(host, worldgen.registries());
        StructureManager structures = new StructureManager(capture,
                new WorldOptions(seed, true, false),
                structureCheck(worldgen, host, seed));
        RandomSource random = RandomSource.create(seed);
        ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(span.minX()),
                SectionPos.blockToSectionCoord(span.minZ()));

        BoundingBox writable = bounds;
        long deadline = System.nanoTime() + GENERATION_BUDGET_MS * 1_000_000L;

        CaptureLevel.whileCapturing(() -> {
            int placed = 0;
            for (StructurePiece piece : generated) {
                try {
                    piece.postProcess(capture, structures, generator, random, writable, chunkPos, anchor);
                } catch (Exception exception) {
                    StructureCodex.LOGGER.warn("Could not generate the piece {} for a preview",
                            piece.getType(), exception);
                }
                placed++;
                if (System.nanoTime() > deadline
                        || capture.captured().size() > CodexConfig.get().previewBlockBudget()) {
                    StructureCodex.LOGGER.info("Stopped a preview after {} of {} generated pieces",
                            placed, generated.size());
                    break;
                }
            }
        });

        for (Map.Entry<BlockPos, BlockState> entry : capture.captured().entrySet()) {
            if (!entry.getValue().isAir()) {
                out.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void place(StructureTemplate template,
                              StructurePlaceSettings settings,
                              BlockPos origin,
                              int variant,
                              Map<BlockPos, BlockState> out) {
        if (template == null) {
            return;
        }

        Rotation rotation = settings.getRotation();
        Mirror mirror = settings.getMirror();
        BlockPos pivot = settings.getRotationPivot();

        for (TemplateBlocks.Raw raw : TemplateBlocks.read(template, variant)) {
            BlockPos local = StructureTemplate.transform(raw.pos(), mirror, rotation, pivot);
            BlockState state = raw.state().mirror(mirror).rotate(rotation);
            out.put(origin.offset(local), state);
        }
    }

    public static final class PreviewBuilder {

        private PreviewBuilder() {
        }

        public static StructurePreviewData build(Map<BlockPos, BlockState> blocks) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (BlockPos pos : blocks.keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }

            Map<BlockPos, BlockState> normalised = new HashMap<>(blocks.size());
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                normalised.put(new BlockPos(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ), entry.getValue());
            }

            Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
            return StructurePreviewData.of(normalised, size);
        }
    }
}

