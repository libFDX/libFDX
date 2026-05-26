package io.github.libfdx.graphics.g3d;

public interface Camera3D {
    Vector3 position();

    Matrix4 projection();

    Matrix4 view();

    Matrix4 combined();

    float near();

    float far();
}
