package io.github.libfdx.tests;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.tests.graphics.CircleTest;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import io.github.libfdx.tests.graphics.ReadbackTest;
import io.github.libfdx.tests.graphics.SquareTest;
import io.github.libfdx.tests.graphics.SpriteBatchTest;
import io.github.libfdx.tests.graphics.TextureTest;
import io.github.libfdx.tests.graphics.TriangleTest;

import java.util.Locale;

public final class TestSelector {
    private static final String TRIANGLE = "triangle";
    private static final String SQUARE = "square";
    private static final String CIRCLE = "circle";
    private static final String TEXTURE = "texture";
    private static final String SPRITE = "sprite";
    private static final String MODEL = "model";
    private static final String READBACK = "readback";
    public static final String DEFAULT_TEST_NAME = MODEL;
    private static final String[] TEST_NAMES = {
            TRIANGLE,
            SQUARE,
            CIRCLE,
            TEXTURE,
            SPRITE,
            MODEL,
            READBACK
    };

    private TestSelector() {
    }

    public static ApplicationListener create(String name, long exitAfterFrames) {
        String testName = normalize(name);
        if (TRIANGLE.equals(testName)) {
            return new TriangleTest(exitAfterFrames);
        }
        if (SQUARE.equals(testName)) {
            return new SquareTest(exitAfterFrames);
        }
        if (CIRCLE.equals(testName)) {
            return new CircleTest(exitAfterFrames);
        }
        if (TEXTURE.equals(testName)) {
            return new TextureTest(exitAfterFrames);
        }
        if (SPRITE.equals(testName)) {
            return new SpriteBatchTest(exitAfterFrames);
        }
        if (MODEL.equals(testName)) {
            return new ModelBatchTest(exitAfterFrames);
        }
        if (READBACK.equals(testName)) {
            return new ReadbackTest(exitAfterFrames);
        }
        throw new FdxException("Unknown test '" + name + "'. Available tests: " + availableTests());
    }

    public static String[] testNames() {
        return TEST_NAMES.clone();
    }

    public static String availableTests() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < TEST_NAMES.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(TEST_NAMES[i]);
        }
        return builder.toString();
    }

    private static String normalize(String name) {
        if (name == null || name.trim().length() == 0) {
            return DEFAULT_TEST_NAME;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
