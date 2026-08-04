package io.swerr.structurecodex.client.preview;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import io.swerr.structurecodex.preview.StructurePreviewData;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Vec3i;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PreviewGpuMesh implements AutoCloseable {

    public record Layer(ChunkSectionLayer layer,
                        GpuBuffer vertexBuffer,
                        GpuBuffer indexBuffer,
                        VertexFormat.IndexType indexType,
                        int indexCount) {
    }

    private final List<Layer> layers;
    private final Vec3i size;
    private final int totalIndices;
    private final float centreX;
    private final float centreY;
    private final float centreZ;
    private final List<net.minecraft.core.BlockPos> extremes;

    private PreviewGpuMesh(List<Layer> layers, Vec3i size, int totalIndices,
                           float centreX, float centreY, float centreZ,
                           List<net.minecraft.core.BlockPos> extremes) {
        this.layers = layers;
        this.size = size;
        this.totalIndices = totalIndices;
        this.centreX = centreX;
        this.centreY = centreY;
        this.centreZ = centreZ;
        this.extremes = extremes;
    }

    public float centreX() {
        return centreX;
    }

    public float centreY() {
        return centreY;
    }

    public float centreZ() {
        return centreZ;
    }

    public List<net.minecraft.core.BlockPos> extremes() {
        return extremes;
    }

    public List<Layer> layers() {
        return layers;
    }

    public Vec3i size() {
        return size;
    }

    public int quadCount() {
        return totalIndices / 6;
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public static PreviewGpuMesh upload(PreviewMesh mesh, StructurePreviewData source) {
        GpuDevice device = RenderSystem.getDevice();
        List<Layer> uploaded = new ArrayList<>();
        int indices = 0;

        for (Map.Entry<ChunkSectionLayer, MeshData> entry : mesh.meshes().entrySet()) {
            MeshData data = entry.getValue();
            MeshData.DrawState draw = data.drawState();
            if (draw.indexCount() == 0) {
                continue;
            }

            GpuBuffer vertices = device.createBuffer(
                    () -> "structurecodex preview vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    data.vertexBuffer());

            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;
            if (data.indexBuffer() != null) {
                indexBuffer = device.createBuffer(
                        () -> "structurecodex preview indices",
                        GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                        data.indexBuffer());
                indexType = draw.indexType();
            } else {
                ByteBuffer generated = quadIndices(draw.indexCount());
                try {
                    indexBuffer = device.createBuffer(
                            () -> "structurecodex preview indices",
                            GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                            generated);
                } finally {
                    MemoryUtil.memFree(generated);
                }
                indexType = VertexFormat.IndexType.INT;
            }

            uploaded.add(new Layer(entry.getKey(), vertices, indexBuffer, indexType, draw.indexCount()));
            indices += draw.indexCount();
        }

        return new PreviewGpuMesh(List.copyOf(uploaded), mesh.size(), indices,
                source.centreX(), source.centreY(), source.centreZ(), source.extremes());
    }

    private static ByteBuffer quadIndices(int indexCount) {
        ByteBuffer buffer = MemoryUtil.memAlloc(indexCount * 4);
        IntBuffer ints = buffer.asIntBuffer();
        for (int quad = 0; quad < indexCount / 6; quad++) {
            int base = quad * 4;
            ints.put(base);
            ints.put(base + 1);
            ints.put(base + 2);
            ints.put(base + 2);
            ints.put(base + 3);
            ints.put(base);
        }
        return buffer;
    }

    @Override
    public void close() {
        for (Layer layer : layers) {
            layer.vertexBuffer().close();
            if (layer.indexBuffer() != null) {
                layer.indexBuffer().close();
            }
        }
    }
}
