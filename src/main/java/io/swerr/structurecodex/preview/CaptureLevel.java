package io.swerr.structurecodex.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class CaptureLevel extends Level implements net.minecraft.world.level.WorldGenLevel {

    private static final int GROUND = 64;
    private static final int CAPTURE_LIMIT = 150000;

    private static final ThreadLocal<Boolean> CAPTURING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isCapturing() {
        return CAPTURING.get();
    }

    public static void whileCapturing(Runnable action) {
        CAPTURING.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            CAPTURING.set(Boolean.FALSE);
        }
    }

    private final Map<BlockPos, BlockState> captured = new HashMap<>();
    private final Map<ChunkPos, ChunkAccess> chunks = new HashMap<>();
    private final ChunkSource chunkSource = new CaptureChunkSource(this);
    private final WorldBorder worldBorder = new WorldBorder();
    private final Holder<Biome> biome;
    private final ServerLevel origin;

    private boolean readThrough;

    public void readTerrainFrom(boolean value) {
        readThrough = value;
    }

    public CaptureLevel(ServerLevel origin, RegistryAccess registries) {
        super(new CaptureLevelData(),
                Level.OVERWORLD,
                registries,
                registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD),
                true,
                false,
                0L,
                0);
        this.origin = origin;
        this.biome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    }

    public Map<BlockPos, BlockState> captured() {
        return captured;
    }

    @Override
    public ServerLevel getLevel() {
        return origin;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new DifficultyInstance(Difficulty.PEACEFUL, 0L, 0L, 0.0F);
    }

    @Override
    public long getSeed() {
        return 0L;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int limit) {
        if (captured.size() >= CAPTURE_LIMIT) {
            return false;
        }
        captured.put(pos.immutable(), state);
        return true;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return setBlock(pos, state, flags, 512);
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean moved) {
        captured.put(pos.immutable(), Blocks.AIR.defaultBlockState());
        return true;
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, Entity breaker, int limit) {
        return removeBlock(pos, false);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = captured.get(pos);
        if (state != null) {
            return state;
        }
        if (readThrough && isLoaded(pos.getX(), pos.getZ())) {
            return origin.getBlockState(pos);
        }
        return pos.getY() < GROUND ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        io.swerr.structurecodex.StructurePlacer.Heights heights =
                io.swerr.structurecodex.StructurePlacer.heights();
        if (heights != null) {
            return heights.get(type, x, z);
        }
        return readThrough && isLoaded(x, z) ? origin.getHeight(type, x, z) : GROUND;
    }

    private boolean isLoaded(int x, int z) {
        return origin.hasChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x),
                net.minecraft.core.SectionPos.blockToSectionCoord(z));
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
        return chunks.computeIfAbsent(new ChunkPos(x, z), pos -> new CaptureChunk(this, pos, biome));
    }

    @Override
    public ChunkSource getChunkSource() {
        return chunkSource;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return biome;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 512;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public WorldBorder getWorldBorder() {
        return worldBorder;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public float getShade(net.minecraft.core.Direction direction, boolean shade) {
        return 1.0F;
    }

    @Override
    public EnvironmentAttributeSystem environmentAttributes() {
        return EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();
    }

    @Override
    public List<? extends Player> players() {
        return List.of();
    }

    @Override
    public Collection<EnderDragonPart> dragonParts() {
        return List.of();
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return null;
    }

    @Override
    public Entity getEntity(int id) {
        return null;
    }

    @Override
    public TickRateManager tickRateManager() {
        return null;
    }

    @Override
    public MapItemSavedData getMapData(MapId id) {
        return null;
    }

    @Override
    public Scoreboard getScoreboard() {
        return null;
    }

    @Override
    public RecipeAccess recipeAccess() {
        return null;
    }

    @Override
    public PotionBrewing potionBrewing() {
        return null;
    }

    @Override
    public FuelValues fuelValues() {
        return null;
    }

    @Override
    public LevelData.RespawnData getRespawnData() {
        return null;
    }

    @Override
    public void setRespawnData(LevelData.RespawnData data) {
    }

    @Override
    public void destroyBlockProgress(int entityId, BlockPos pos, int progress) {
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
    }

    @Override
    public void levelEvent(Entity source, int eventId, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
    }

    @Override
    public void playSeededSound(Entity source, double x, double y, double z, Holder<SoundEvent> sound,
                                SoundSource category, float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(Entity source, Entity entity, Holder<SoundEvent> sound, SoundSource category,
                                float volume, float pitch, long seed) {
    }

    @Override
    public void explode(Entity entity, DamageSource damageSource, ExplosionDamageCalculator calculator,
                        double x, double y, double z, float power, boolean fire,
                        Level.ExplosionInteraction interaction, ParticleOptions smallParticle,
                        ParticleOptions largeParticle, WeightedList<ExplosionParticleInfo> blockParticles,
                        Holder<SoundEvent> sound) {
    }

    @Override
    public String gatherChunkSourceStats() {
        return "StructureCodexCapture";
    }

    private static final class CaptureLevelData implements WritableLevelData {

        @Override
        public void setSpawn(LevelData.RespawnData data) {
        }

        @Override
        public LevelData.RespawnData getRespawnData() {
            return LevelData.RespawnData.DEFAULT;
        }

        @Override
        public long getGameTime() {
            return 0L;
        }

        @Override
        public boolean isHardcore() {
            return false;
        }

        @Override
        public Difficulty getDifficulty() {
            return Difficulty.PEACEFUL;
        }

        @Override
        public boolean isDifficultyLocked() {
            return false;
        }

        @Override
        public long getDayTime() {
            return 6000L;
        }

        @Override
        public boolean isThundering() {
            return false;
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public void setRaining(boolean raining) {
        }
    }

    private static final class CaptureChunk extends EmptyLevelChunk {

        private CaptureChunk(CaptureLevel level, ChunkPos pos, Holder<Biome> biome) {
            super(level, pos, biome);
        }

        @Override
        public void markPosForPostprocessing(BlockPos pos) {
        }
    }

    private static final class CaptureChunkSource extends ChunkSource {

        private final CaptureLevel level;

        private CaptureChunkSource(CaptureLevel level) {
            this.level = level;
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
            return level.getChunk(x, z, status, create);
        }

        @Override
        public void tick(BooleanSupplier keepTicking, boolean tickChunks) {
        }

        @Override
        public String gatherStats() {
            return "StructureCodexCaptureChunks";
        }

        @Override
        public int getLoadedChunksCount() {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.origin == null ? null : level.origin.getChunkSource().getLightEngine();
        }

        @Override
        public net.minecraft.world.level.BlockGetter getLevel() {
            return level;
        }
    }
}
