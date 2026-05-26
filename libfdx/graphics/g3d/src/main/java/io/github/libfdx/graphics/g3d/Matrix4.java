package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

public final class Matrix4 {
    public static final int VALUE_COUNT = 16;
    public static final Matrix4 IDENTITY = identity();

    private final float[] values;

    private Matrix4(float[] values) {
        if (values == null || values.length != VALUE_COUNT) {
            throw new FdxException("Matrix4 requires 16 values");
        }
        this.values = values.clone();
    }

    public static Matrix4 of(float[] values) {
        return new Matrix4(values);
    }

    public static Matrix4 identity() {
        float[] values = new float[VALUE_COUNT];
        values[0] = 1.0f;
        values[5] = 1.0f;
        values[10] = 1.0f;
        values[15] = 1.0f;
        return new Matrix4(values);
    }

    public static Matrix4 translation(float x, float y, float z) {
        float[] values = identity().values;
        values[12] = x;
        values[13] = y;
        values[14] = z;
        return new Matrix4(values);
    }

    public static Matrix4 scale(float x, float y, float z) {
        float[] values = new float[VALUE_COUNT];
        values[0] = x;
        values[5] = y;
        values[10] = z;
        values[15] = 1.0f;
        return new Matrix4(values);
    }

    public static Matrix4 rotationX(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float[] values = identity().values;
        values[5] = cos;
        values[6] = sin;
        values[9] = -sin;
        values[10] = cos;
        return new Matrix4(values);
    }

    public static Matrix4 rotationY(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float[] values = identity().values;
        values[0] = cos;
        values[2] = -sin;
        values[8] = sin;
        values[10] = cos;
        return new Matrix4(values);
    }

    public static Matrix4 rotationZ(float radians) {
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float[] values = identity().values;
        values[0] = cos;
        values[1] = sin;
        values[4] = -sin;
        values[5] = cos;
        return new Matrix4(values);
    }

    public static Matrix4 rotationQuaternion(float x, float y, float z, float w) {
        float len = (float)Math.sqrt(x * x + y * y + z * z + w * w);
        if (len == 0.0f) {
            return IDENTITY;
        }
        float invLen = 1.0f / len;
        x *= invLen;
        y *= invLen;
        z *= invLen;
        w *= invLen;

        float xx = x * x;
        float yy = y * y;
        float zz = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float wx = w * x;
        float wy = w * y;
        float wz = w * z;

        float[] values = identity().values;
        values[0] = 1.0f - 2.0f * (yy + zz);
        values[1] = 2.0f * (xy + wz);
        values[2] = 2.0f * (xz - wy);
        values[4] = 2.0f * (xy - wz);
        values[5] = 1.0f - 2.0f * (xx + zz);
        values[6] = 2.0f * (yz + wx);
        values[8] = 2.0f * (xz + wy);
        values[9] = 2.0f * (yz - wx);
        values[10] = 1.0f - 2.0f * (xx + yy);
        return new Matrix4(values);
    }

    public static Matrix4 perspective(float fieldOfViewDegrees, float aspectRatio, float near, float far) {
        if (aspectRatio == 0.0f) {
            throw new FdxException("Perspective camera aspect ratio cannot be zero");
        }
        if (near <= 0.0f || far <= near) {
            throw new FdxException("Perspective camera near/far range is invalid");
        }
        float f = (float)(1.0 / Math.tan(Math.toRadians(fieldOfViewDegrees) * 0.5));
        float[] values = new float[VALUE_COUNT];
        values[0] = f / aspectRatio;
        values[5] = f;
        values[10] = (far + near) / (near - far);
        values[11] = -1.0f;
        values[14] = (2.0f * far * near) / (near - far);
        return new Matrix4(values);
    }

    public static Matrix4 lookAt(Vector3 eye, Vector3 center, Vector3 up) {
        Vector3 forward = center.subtract(eye).normalize();
        Vector3 side = forward.cross(up).normalize();
        Vector3 upVector = side.cross(forward);
        float[] values = new float[VALUE_COUNT];
        values[0] = side.x();
        values[4] = side.y();
        values[8] = side.z();
        values[1] = upVector.x();
        values[5] = upVector.y();
        values[9] = upVector.z();
        values[2] = -forward.x();
        values[6] = -forward.y();
        values[10] = -forward.z();
        values[12] = -side.dot(eye);
        values[13] = -upVector.dot(eye);
        values[14] = forward.dot(eye);
        values[15] = 1.0f;
        return new Matrix4(values);
    }

    public Matrix4 multiply(Matrix4 other) {
        float[] result = new float[VALUE_COUNT];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                result[column * 4 + row] =
                        values[row] * other.values[column * 4]
                                + values[4 + row] * other.values[column * 4 + 1]
                                + values[8 + row] * other.values[column * 4 + 2]
                                + values[12 + row] * other.values[column * 4 + 3];
            }
        }
        return new Matrix4(result);
    }

    public float[] values() {
        return values.clone();
    }

    public Vector3 transformPosition(Vector3 position) {
        float x = position.x();
        float y = position.y();
        float z = position.z();
        return new Vector3(
                values[0] * x + values[4] * y + values[8] * z + values[12],
                values[1] * x + values[5] * y + values[9] * z + values[13],
                values[2] * x + values[6] * y + values[10] * z + values[14]);
    }

    public Vector3 transformDirection(Vector3 direction) {
        float x = direction.x();
        float y = direction.y();
        float z = direction.z();
        return new Vector3(
                values[0] * x + values[4] * y + values[8] * z,
                values[1] * x + values[5] * y + values[9] * z,
                values[2] * x + values[6] * y + values[10] * z).normalize();
    }
}
