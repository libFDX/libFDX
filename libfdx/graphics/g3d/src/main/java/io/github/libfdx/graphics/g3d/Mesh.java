package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.VertexLayout;

public interface Mesh extends Disposable {
    String id();

    Buffer vertexBuffer();

    Buffer indexBuffer();

    VertexLayout vertexLayout();

    int vertexCount();

    int indexCount();

    BoundingBox bounds();
}
