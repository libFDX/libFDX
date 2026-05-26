package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

public final class PerspectiveCamera3D implements Camera3D {
    private float fieldOfViewDegrees;
    private float viewportWidth;
    private float viewportHeight;
    private float near = 0.1f;
    private float far = 100.0f;
    private Vector3 position = new Vector3(0.0f, 0.0f, 1.0f);
    private Vector3 target = Vector3.ZERO;
    private Vector3 up = Vector3.Y;
    private Matrix4 projection = Matrix4.IDENTITY;
    private Matrix4 view = Matrix4.IDENTITY;
    private Matrix4 combined = Matrix4.IDENTITY;

    public PerspectiveCamera3D(float fieldOfViewDegrees, float viewportWidth, float viewportHeight) {
        if (fieldOfViewDegrees <= 0.0f) {
            throw new FdxException("Camera field of view must be greater than zero");
        }
        if (viewportWidth <= 0.0f || viewportHeight <= 0.0f) {
            throw new FdxException("Camera viewport dimensions must be greater than zero");
        }
        this.fieldOfViewDegrees = fieldOfViewDegrees;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        update();
    }

    public PerspectiveCamera3D viewport(float width, float height) {
        if (width <= 0.0f || height <= 0.0f) {
            throw new FdxException("Camera viewport dimensions must be greater than zero");
        }
        this.viewportWidth = width;
        this.viewportHeight = height;
        update();
        return this;
    }

    public PerspectiveCamera3D position(float x, float y, float z) {
        this.position = new Vector3(x, y, z);
        update();
        return this;
    }

    public PerspectiveCamera3D lookAt(float x, float y, float z) {
        this.target = new Vector3(x, y, z);
        update();
        return this;
    }

    public PerspectiveCamera3D up(float x, float y, float z) {
        this.up = new Vector3(x, y, z);
        update();
        return this;
    }

    public PerspectiveCamera3D clip(float near, float far) {
        this.near = near;
        this.far = far;
        update();
        return this;
    }

    public void update() {
        projection = Matrix4.perspective(fieldOfViewDegrees, viewportWidth / viewportHeight, near, far);
        view = Matrix4.lookAt(position, target, up);
        combined = projection.multiply(view);
    }

    @Override
    public Vector3 position() {
        return position;
    }

    @Override
    public Matrix4 projection() {
        return projection;
    }

    @Override
    public Matrix4 view() {
        return view;
    }

    @Override
    public Matrix4 combined() {
        return combined;
    }

    @Override
    public float near() {
        return near;
    }

    @Override
    public float far() {
        return far;
    }
}
