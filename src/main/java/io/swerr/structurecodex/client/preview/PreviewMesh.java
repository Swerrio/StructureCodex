package io.swerr.structurecodex.client.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.swerr.structurecodex.StructureCodex;
import io.swerr.structurecodex.preview.StructurePreviewData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.EnumMap;
import java.util.Map;

public final class PreviewMesh implements AutoCloseable {

    private final Map<ChunkSectionLayer, MeshData> meshes;
    private final Map<ChunkSectionLayer, ByteBufferBuilder> allocators;
    private final Vec3i size;

    private PreviewMesh(Map<ChunkSectionLayer, MeshData> meshes,
                        Map<ChunkSectionLayer, ByteBufferBuilder> allocators,
                        Vec3i size) {
        this.meshes = meshes;
        this.allocators = allocators;
        this.size = size;
    }

    public Map<ChunkSectionLayer, MeshData> meshes() {
        return meshes;
    }

    public Vec3i size() {
        return size;
    }

    public boolean isEmpty() {
        return meshes.isEmpty();
    }

    public static PreviewMesh build(PreviewLevel level, StructurePreviewData data) {
        Minecraft minecraft = Minecraft.getInstance();
        ModelManager modelManager = minecraft.getModelManager();
        BlockStateModelSet models = modelManager.getBlockStateModelSet();
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(false, true, minecraft.getBlockColors());
        FluidRenderer fluidRenderer = new FluidRenderer(modelManager.getFluidStateModelSet());

        Map<ChunkSectionLayer, ByteBufferBuilder> allocators = new EnumMap<>(ChunkSectionLayer.class);
        Map<ChunkSectionLayer, BufferBuilder> builders = new EnumMap<>(ChunkSectionLayer.class);

        FluidRenderer.Output fluidOutput = layer -> builderFor(layer, allocators, builders);
        BlockQuadOutput blockOutput = (x, y, z, quad, instance) ->
                builderFor(quad.materialInfo().layer(), allocators, builders)
                        .putBlockBakedQuad(x, y, z, quad, instance);

        for (StructurePreviewData.PlacedBlock block : data.blocks()) {
            BlockPos pos = block.pos();
            BlockState state = block.state();

            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                try {
                    fluidRenderer.tesselate(level, pos, fluidOutput, state, fluid);
                } catch (Exception exception) {
                    StructureCodex.LOGGER.debug("Skipped a fluid while meshing the preview", exception);
                }
            }

            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            BlockStateModel model = models.get(state);
            if (model == null) {
                continue;
            }
            try {
                blockRenderer.tesselateBlock(blockOutput, pos.getX(), pos.getY(), pos.getZ(),
                        level, pos, state, model, state.getSeed(pos));
            } catch (Exception exception) {
                StructureCodex.LOGGER.debug("Skipped a block while meshing the preview", exception);
            }
        }

        Map<ChunkSectionLayer, MeshData> meshes = new EnumMap<>(ChunkSectionLayer.class);
        for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : builders.entrySet()) {
            try {
                MeshData mesh = entry.getValue().build();
                if (mesh != null) {
                    meshes.put(entry.getKey(), mesh);
                }
            } catch (Exception exception) {
                StructureCodex.LOGGER.warn("Could not build the preview mesh for {}", entry.getKey(), exception);
            }
        }

        return new PreviewMesh(meshes, allocators, data.size());
    }

    private static VertexConsumer builderFor(ChunkSectionLayer layer,
                                             Map<ChunkSectionLayer, ByteBufferBuilder> allocators,
                                             Map<ChunkSectionLayer, BufferBuilder> builders) {
        return builders.computeIfAbsent(layer, key -> new BufferBuilder(
                allocators.computeIfAbsent(key, k -> new ByteBufferBuilder(k.bufferSize())),
                key.pipeline().getPrimitiveTopology(),
                key.vertexFormat()));
    }

    @Override
    public void close() {
        meshes.values().forEach(MeshData::close);
        meshes.clear();
        allocators.values().forEach(ByteBufferBuilder::close);
        allocators.clear();
    }
}
