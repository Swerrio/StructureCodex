package io.swerr.structurecodex.client.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;

public final class StructurePreviewRenderState implements PictureInPictureRenderState {

    private final PreviewGpuMesh mesh;
    private final float yaw;
    private final float pitch;
    private final float zoom;
    private final int x0;
    private final int y0;
    private final int x1;
    private final int y1;
    private final float scale;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    public StructurePreviewRenderState(PreviewGpuMesh mesh, float yaw, float pitch, float zoom,
                                       int x0, int y0, int x1, int y1, float scale,
                                       ScreenRectangle scissorArea) {
        this.mesh = mesh;
        this.yaw = yaw;
        this.pitch = pitch;
        this.zoom = zoom;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.scale = scale;
        this.scissorArea = scissorArea;
        this.bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
    }

    public PreviewGpuMesh mesh() {
        return mesh;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float zoom() {
        return zoom;
    }

    @Override
    public int x0() {
        return x0;
    }

    @Override
    public int y0() {
        return y0;
    }

    @Override
    public int x1() {
        return x1;
    }

    @Override
    public int y1() {
        return y1;
    }

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public Matrix3x2f pose() {
        return PictureInPictureRenderState.IDENTITY_POSE;
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
