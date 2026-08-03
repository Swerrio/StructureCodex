package io.swerr.structurecodex.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class PreviewLightEngine extends LevelLightEngine {

    private static final int FULL = 15;

    private final LayerLightEventListener fullBright = new LayerLightEventListener() {

        @Override
        public DataLayer getDataLayerData(SectionPos pos) {
            return null;
        }

        @Override
        public int getLightValue(BlockPos pos) {
            return FULL;
        }

        @Override
        public void checkBlock(BlockPos pos) {
        }

        @Override
        public boolean hasLightWork() {
            return false;
        }

        @Override
        public int runLightUpdates() {
            return FULL;
        }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean notReady) {
        }

        @Override
        public void setLightEnabled(ChunkPos pos, boolean retainData) {
        }

        @Override
        public void propagateLightSources(ChunkPos pos) {
        }
    };

    public PreviewLightEngine(LightChunkGetter chunks) {
        super(chunks, false, false);
    }

    @Override
    public LayerLightEventListener getLayerListener(LightLayer layer) {
        return fullBright;
    }
}
