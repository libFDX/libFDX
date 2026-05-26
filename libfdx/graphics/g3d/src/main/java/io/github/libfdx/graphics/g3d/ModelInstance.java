package io.github.libfdx.graphics.g3d;

public interface ModelInstance {
    Model model();

    Matrix4 transform();

    void collectRenderables(RenderQueue3D queue);
}
