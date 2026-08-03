package io.swerr.structurecodex.preview;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.Set;

public record PreviewWorldgen(RegistryAccess registries,
                              ChunkGenerator generator,
                              BiomeSource biomeSource,
                              RandomState randomState,
                              LevelHeightAccessor heightAccessor,
                              StructureTemplateManager templates) {

    private static long cachedSeed;
    private static PreviewWorldgen cached;

    public static synchronized PreviewWorldgen sandbox(RegistryAccess registries,
                                                       StructureTemplateManager templates,
                                                       LevelStem stem,
                                                       long seed) {
        ChunkGenerator generator = stem.generator();
        if (cached != null
                && cachedSeed == seed
                && cached.registries == registries
                && cached.generator == generator) {
            return cached;
        }

        DimensionType dimension = stem.type().value();
        PreviewWorldgen worldgen = new PreviewWorldgen(
                registries,
                generator,
                generator.getBiomeSource(),
                randomState(registries, generator, seed),
                LevelHeightAccessor.create(dimension.minY(), dimension.height()),
                templates);

        cachedSeed = seed;
        cached = worldgen;
        return worldgen;
    }

    public static PreviewWorldgen sandbox(MinecraftServer server, Structure structure, long seed) {
        RegistryAccess registries = server.registryAccess();
        return sandbox(registries, server.getStructureManager(), pickStem(registries, structure), seed);
    }

    public static LevelStem pickStem(RegistryAccess registries, Structure structure) {
        WorldDimensions dimensions = WorldPresets.createNormalWorldDimensions(registries);
        for (LevelStem stem : dimensions.dimensions().values()) {
            Set<Holder<Biome>> possible = stem.generator().getBiomeSource().possibleBiomes();
            for (Holder<Biome> biome : structure.biomes()) {
                if (possible.contains(biome)) {
                    return stem;
                }
            }
        }
        return WorldPresets.getNormalOverworld(registries);
    }

    public static PreviewWorldgen fromLevel(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return new PreviewWorldgen(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level,
                level.getServer().getStructureManager());
    }

    private static RandomState randomState(RegistryAccess registries, ChunkGenerator generator, long seed) {
        HolderGetter<NormalNoise.NoiseParameters> noises = registries.lookupOrThrow(Registries.NOISE);
        if (generator instanceof NoiseBasedChunkGenerator noise) {
            return RandomState.create(noise.generatorSettings().value(), noises, seed);
        }
        return RandomState.create(registries, NoiseGeneratorSettings.OVERWORLD, seed);
    }

    public Structure.GenerationContext context(ChunkPos origin, long seed) {
        return new Structure.GenerationContext(registries, generator, biomeSource, randomState,
                templates, seed, origin, heightAccessor, biome -> true);
    }
}
