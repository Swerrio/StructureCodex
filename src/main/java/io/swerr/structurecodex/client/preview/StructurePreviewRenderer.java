package io.swerr.structurecodex.client.preview;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import io.swerr.structurecodex.mixin.GameRendererAccessor;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public class StructurePreviewRenderer extends PictureInPictureRenderer<StructurePreviewRenderState> {

    private static final float DEPTH_LIMIT = 900.0F;
    private static final float BOX_FILL = 0.94F;

    private GpuBuffer sectionUniform;
    private GpuBuffer globalsUniform;

    @Override
    public Class<StructurePreviewRenderState> getRenderStateClass() {
        return StructurePreviewRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "structure codex preview";
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected void renderToTexture(StructurePreviewRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        PreviewGpuMesh mesh = state.mesh();
        if (mesh == null || mesh.isEmpty()) {
            return;
        }

        Vec3i size = mesh.size();
        float boxWidth = state.x1() - state.x0();
        float boxHeight = state.y1() - state.y0();

        Matrix4f orientation = new Matrix4f()
                .rotateX((float) Math.toRadians(state.pitch()))
                .rotateY((float) Math.toRadians(state.yaw()));

        float pivotX = size.getX() / 2.0F;
        float pivotY = size.getY() / 2.0F;
        float pivotZ = size.getZ() / 2.0F;

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        Vector3f probe = new Vector3f();
        for (net.minecraft.core.BlockPos point : mesh.extremes()) {
            for (int corner = 0; corner < 8; corner++) {
                probe.set(point.getX() + ((corner & 1) == 0 ? 0.0F : 1.0F) - pivotX,
                        point.getY() + ((corner & 2) == 0 ? 0.0F : 1.0F) - pivotY,
                        point.getZ() + ((corner & 4) == 0 ? 0.0F : 1.0F) - pivotZ);
                orientation.transformPosition(probe);
                minX = Math.min(minX, probe.x);
                maxX = Math.max(maxX, probe.x);
                minY = Math.min(minY, probe.y);
                maxY = Math.max(maxY, probe.y);
            }
        }

        if (minX > maxX) {
            minX = -pivotX;
            maxX = pivotX;
            minY = -pivotY;
            maxY = pivotY;
        }

        float spanX = Math.max(1.0F, maxX - minX) + 1.0F;
        float spanY = Math.max(1.0F, maxY - minY) + 1.0F;

        float fit = Math.min(boxWidth / spanX, boxHeight / spanY) * BOX_FILL * state.zoom();

        Minecraft minecraft = Minecraft.getInstance();
        GpuTextureView atlas = minecraft.getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS)
                .getTextureView();

        GpuTextureView colour = RenderSystem.outputColorTextureOverride;
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride;
        if (colour == null || depth == null) {
            return;
        }

        Matrix4f trial = new Matrix4f(pose.last().pose())
                .rotateX((float) Math.toRadians(state.pitch()))
                .rotateY((float) Math.toRadians(state.yaw()))
                .scale(fit, -fit, -fit)
                .translate(-pivotX, -pivotY, -pivotZ);

        float lowX = Float.MAX_VALUE;
        float highX = -Float.MAX_VALUE;
        float lowY = Float.MAX_VALUE;
        float highY = -Float.MAX_VALUE;
        float reachZ = 0.0F;

        for (net.minecraft.core.BlockPos point : mesh.extremes()) {
            for (int corner = 0; corner < 8; corner++) {
                probe.set(point.getX() + ((corner & 1) == 0 ? 0.0F : 1.0F),
                        point.getY() + ((corner & 2) == 0 ? 0.0F : 1.0F),
                        point.getZ() + ((corner & 4) == 0 ? 0.0F : 1.0F));
                trial.transformPosition(probe);
                lowX = Math.min(lowX, probe.x);
                highX = Math.max(highX, probe.x);
                lowY = Math.min(lowY, probe.y);
                highY = Math.max(highY, probe.y);
                reachZ = Math.max(reachZ, Math.abs(probe.z));
            }
        }

        float depthCompress = Math.min(1.0F, DEPTH_LIMIT / Math.max(1.0F, reachZ));

        if (lowX <= highX) {
            float unit = Math.max(1.0F, colour.getHeight(0) / Math.max(1.0F, boxHeight));
            pose.translate((colour.getWidth(0) / 2.0F - (lowX + highX) / 2.0F) / unit,
                    (colour.getHeight(0) / 2.0F - (lowY + highY) / 2.0F) / unit,
                    0.0F);
        }

        pose.scale(1.0F, 1.0F, depthCompress);
        pose.mulPose(Axis.XP.rotationDegrees(state.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw()));
        pose.scale(fit, -fit, -fit);
        pose.translate(-pivotX, -pivotY, -pivotZ);

        if (sectionUniform == null) {
            sectionUniform = RenderSystem.getDevice().createBuffer(() -> "StructureCodex preview section",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, DynamicUniforms.CHUNK_SECTION_UBO_SIZE);
        }
        if (globalsUniform == null) {
            globalsUniform = RenderSystem.getDevice().createBuffer(() -> "StructureCodex preview globals",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, GlobalSettingsUniform.UBO_SIZE);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, GlobalSettingsUniform.UBO_SIZE)
                    .putIVec3(0, 0, 0)
                    .putVec3(0.0F, 0.0F, 0.0F)
                    .putVec2(colour.getWidth(0), colour.getHeight(0))
                    .putFloat(1.0F)
                    .putFloat(0.0F)
                    .putInt(0)
                    .putInt(0)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(globalsUniform.slice(), data);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, DynamicUniforms.CHUNK_SECTION_UBO_SIZE)
                    .putMat4f(pose.last().pose())
                    .putFloat(1.0F)
                    .putIVec2(atlas.getWidth(0), atlas.getHeight(0))
                    .putIVec3(0, 0, 0)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(sectionUniform.slice(), data);
        }

        GpuBufferSlice[] uniforms = {sectionUniform.slice()};

        GpuSampler sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "StructureCodex preview", colour, Optional.empty(), depth, OptionalDouble.empty(),
                new RenderPass.RenderArea(0, 0, colour.getWidth(0), colour.getHeight(0)))) {

            pass.disableScissor();
            GameRendererAccessor renderer = (GameRendererAccessor) minecraft.gameRenderer;
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Fog", renderer.structurecodex$fogRenderer().getBuffer(FogRenderer.FogMode.NONE));
            pass.setUniform("Globals", globalsUniform);
            pass.bindTexture("Sampler2", renderer.structurecodex$uiLightmap().getTextureView(), sampler);

            for (PreviewGpuMesh.Layer layer : mesh.layers()) {
                pass.setPipeline(layer.layer().pipeline());
                pass.bindTexture("Sampler0", atlas, sampler);

                RenderPass.Draw<GpuBufferSlice[]> draw = new RenderPass.Draw<>(
                        0,
                        layer.vertexBuffer(),
                        layer.indexBuffer(),
                        layer.indexType(),
                        0,
                        layer.indexCount(),
                        0,
                        (slices, uploader) -> uploader.upload("ChunkSection", slices[0]));

                pass.drawMultipleIndexed(List.of(draw), null, null, List.of("ChunkSection"), uniforms);
            }
        }
    }
}
