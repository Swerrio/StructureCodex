package io.swerr.structurecodex.client.preview;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.swerr.structurecodex.StructureCodex;
import io.swerr.structurecodex.preview.StructurePreviewData;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        ModelBlockRenderer models = dispatcher.getModelRenderer();

        Map<ChunkSectionLayer, ByteBufferBuilder> allocators = new EnumMap<>(ChunkSectionLayer.class);
        Map<ChunkSectionLayer, BufferBuilder> builders = new EnumMap<>(ChunkSectionLayer.class);

        RandomSource random = RandomSource.create();
        List<BlockModelPart> parts = new ArrayList<>();
        PoseStack poseStack = new PoseStack();

        ModelBlockRenderer.enableCaching();
        try {
            for (StructurePreviewData.PlacedBlock block : data.blocks()) {
                BlockPos pos = block.pos();
                BlockState state = block.state();

                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    try {
                        dispatcher.renderLiquid(pos, level,
                                builderFor(ItemBlockRenderTypes.getRenderLayer(fluid), allocators, builders),
                                state, fluid);
                    } catch (Exception exception) {
                        StructureCodex.LOGGER.debug("Skipped a fluid while meshing the preview", exception);
                    }
                }

                if (state.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }

                try {
                    parts.clear();
                    random.setSeed(state.getSeed(pos));
                    dispatcher.getBlockModel(state).collectParts(random, parts);
                    if (parts.isEmpty()) {
                        continue;
                    }

                    VertexConsumer consumer =
                            builderFor(ItemBlockRenderTypes.getChunkRenderType(state), allocators, builders);
                    poseStack.pushPose();
                    try {
                        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                        models.tesselateWithoutAO(level, parts, state, pos, poseStack, consumer,
                                true, OverlayTexture.NO_OVERLAY);
                    } finally {
                        poseStack.popPose();
                    }
                } catch (Exception exception) {
                    StructureCodex.LOGGER.debug("Skipped a block while meshing the preview", exception);
                }
            }
        } finally {
            ModelBlockRenderer.clearCache();
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
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK));
    }

    @Override
    public void close() {
        meshes.values().forEach(MeshData::close);
        meshes.clear();
        allocators.values().forEach(ByteBufferBuilder::close);
        allocators.clear();
    }
}
