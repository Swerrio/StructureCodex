package io.swerr.structurecodex.client.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.level.ColorResolver;
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
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PreviewLevel extends Level implements BlockAndTintGetter, LightChunkGetter {

    private static final int PREVIEW_HEIGHT = 512;

    private final LevelLightEngine lightEngine = new PreviewLightEngine(this);
    private final WorldBorder worldBorder = new WorldBorder();
    private final ChunkSource chunkSource = new PreviewChunkSource(this, lightEngine);
    private final Holder<Biome> biome;
    private final Map<BlockPos, BlockState> blocks;
    private final Vec3i size;

    public PreviewLevel(Minecraft minecraft, Map<BlockPos, BlockState> blocks, Vec3i size) {
        super(new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, true),
                Level.OVERWORLD,
                minecraft.level.registryAccess(),
                minecraft.level.registryAccess()
                        .lookupOrThrow(Registries.DIMENSION_TYPE)
                        .getOrThrow(BuiltinDimensionTypes.OVERWORLD),
                true,
                false,
                0L,
                0);
        this.blocks = blocks;
        this.size = size;
        this.biome = registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    }

    public Vec3i size() {
        return size;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = blocks.get(pos);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
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
    public LevelLightEngine getLightEngine() {
        return lightEngine;
    }

    @Override
    public float getShade(net.minecraft.core.Direction direction, boolean shade) {
        if (!shade) {
            return 1.0F;
        }
        return switch (direction) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
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
        return PREVIEW_HEIGHT;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public ChunkSource getChunkSource() {
        return chunkSource;
    }

    @Override
    public LightChunk getChunkForLighting(int x, int z) {
        return null;
    }

    @Override
    public net.minecraft.world.level.BlockGetter getLevel() {
        return this;
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
        return "StructureCodexPreview";
    }

    private static final class PreviewChunkSource extends ChunkSource {

        private final PreviewLevel level;
        private final LevelLightEngine lightEngine;

        private PreviewChunkSource(PreviewLevel level, LevelLightEngine lightEngine) {
            this.level = level;
            this.lightEngine = lightEngine;
        }

        @Override
        public net.minecraft.world.level.chunk.ChunkAccess getChunk(int x, int z,
                                                                    net.minecraft.world.level.chunk.status.ChunkStatus status,
                                                                    boolean create) {
            return null;
        }

        @Override
        public void tick(java.util.function.BooleanSupplier keepTicking, boolean tickChunks) {
        }

        @Override
        public String gatherStats() {
            return "StructureCodexPreviewChunks";
        }

        @Override
        public int getLoadedChunksCount() {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return lightEngine;
        }

        @Override
        public net.minecraft.world.level.BlockGetter getLevel() {
            return level;
        }
    }

    @SuppressWarnings("unused")
    private static ChunkPos unusedChunkPosReference() {
        return null;
    }
}
