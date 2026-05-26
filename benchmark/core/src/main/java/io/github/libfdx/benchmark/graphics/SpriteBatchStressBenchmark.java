package io.github.libfdx.benchmark.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;

public final class SpriteBatchStressBenchmark extends ApplicationAdapter {
    public static final String NAME = "sprite_batch_stress";
    private static final String SPRITE_ASSET = "fdx.png";
    private static final int SPRITE_COUNT = 8191;
    private static final int DRAW_SIZE = 32;
    private static final float ROTATION_SPEED = 20.0f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 1.0f;
    private static final long RANDOM_SEED = 0x51f15e2dL;
    private static final long REPORT_INTERVAL_NANOS = 1000000000L;

    private final long exitAfterNanos;
    private final String resultPath;
    private final float[] spriteCenterX = new float[SPRITE_COUNT];
    private final float[] spriteCenterY = new float[SPRITE_COUNT];
    private Application application;
    private Display display;
    private AssetManager assets;
    private Logger logger;
    private Batch2D batch;
    private TextureRegion sprite;
    private String graphicsApi;
    private String graphicsProvider;
    private boolean created;
    private long renderedFrames;
    private long lastReportNanos;
    private long lastReportFrame;
    private long startedAtNanos;
    private long lastRenderNanos;
    private float rotationDegrees;
    private float scale = MAX_SCALE;
    private float scaleSpeed = -1.0f;
    private int layoutWidth;
    private int layoutHeight;

    public SpriteBatchStressBenchmark(long exitAfterNanos, String resultPath) {
        this.exitAfterNanos = exitAfterNanos;
        this.resultPath = resultPath;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        GraphicsContext graphics = fdx.graphics().main();
        graphicsApi = graphicsApiName(graphics);
        graphicsProvider = graphics.providerId().value();
        assets = new DefaultAssetManager(fdx.files());
        logger = fdx.logger();
        G2DAssetLoaders.register(assets, graphics);
        batch = new SpriteBatch(graphics, SPRITE_COUNT);

        assets.load(AssetDescriptor.of(SPRITE_ASSET, Texture.class));
        assets.finishLoading();
        Texture texture = assets.get(SPRITE_ASSET, Texture.class);
        sprite = new TextureRegion(texture);
        configureViewport(framebufferWidth(), framebufferHeight());

        created = true;
        logger.info(logPrefix() + " created with " + SPRITE_COUNT
                + " sprites, " + texture.width() + "x" + texture.height()
                + " " + SPRITE_ASSET + " texture drawn at " + DRAW_SIZE + "x" + DRAW_SIZE
                + runLimitDescription());
    }

    @Override
    public void resize(int width, int height) {
        if (created) {
            configureViewport(framebufferWidth(), framebufferHeight());
        }
    }

    @Override
    public void render() {
        assets.update();
        if (layoutWidth != framebufferWidth() || layoutHeight != framebufferHeight()) {
            configureViewport(framebufferWidth(), framebufferHeight());
        }

        long startNanos = System.nanoTime();
        if (startedAtNanos == 0L) {
            startedAtNanos = startNanos;
        }
        float deltaTime = application.deltaTime();
        rotationDegrees += ROTATION_SPEED * deltaTime;
        if (rotationDegrees >= 360.0f) {
            rotationDegrees -= 360.0f;
        }
        scale += scaleSpeed * deltaTime;
        if (scale < MIN_SCALE) {
            scale = MIN_SCALE;
            scaleSpeed = 1.0f;
        } else if (scale > MAX_SCALE) {
            scale = MAX_SCALE;
            scaleSpeed = -1.0f;
        }

        float width = spriteWidth() * scale;
        float height = spriteHeight() * scale;
        float originX = width * 0.5f;
        float originY = height * 0.5f;
        batch.begin(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f));
        if (usesInstancedBatchPath()) {
            batch.draw(sprite, spriteCenterX, spriteCenterY, SPRITE_COUNT, width, height, originX, originY,
                    rotationDegrees);
        } else {
            for (int i = 0; i < SPRITE_COUNT; i++) {
                batch.draw(sprite, spriteCenterX[i] - originX, spriteCenterY[i] - originY,
                        width, height, originX, originY, rotationDegrees);
            }
        }
        batch.end();

        long now = System.nanoTime();
        lastRenderNanos = now;
        renderedFrames++;
        reportIfNeeded(now);

        if (exitAfterNanos > 0L && now - startedAtNanos >= exitAfterNanos) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (!created) {
            throw new FdxException("SpriteBatchStressBenchmark did not create graphics resources");
        }
        long elapsedNanos = elapsedTestNanos();
        if (exitAfterNanos > 0L && elapsedNanos < exitAfterNanos) {
            throw new FdxException(logPrefix() + " ran for " + format(nanosToSeconds(elapsedNanos))
                    + " of " + format(nanosToSeconds(exitAfterNanos)) + " required seconds");
        }
        double averageFrameFps = averageFrameFps(elapsedNanos);
        double averageSpriteDrawsPerSecond = averageFrameFps * SPRITE_COUNT;
        logger.info(logPrefix() + " rendered " + renderedFrames + " frames in "
                + format(nanosToSeconds(elapsedNanos)) + " seconds with rotating/scaling "
                + SPRITE_COUNT + " " + DRAW_SIZE + "x" + DRAW_SIZE
                + " sprites, average fps=" + format(averageFrameFps)
                + ", average sprite draws/s=" + format(averageSpriteDrawsPerSecond));
        writeResult(elapsedNanos, averageFrameFps, averageSpriteDrawsPerSecond);
    }

    private void reportIfNeeded(long now) {
        if (lastReportNanos == 0L) {
            lastReportNanos = startedAtNanos;
            lastReportFrame = 0L;
        }
        long elapsedNanos = now - lastReportNanos;
        if (elapsedNanos < REPORT_INTERVAL_NANOS) {
            return;
        }
        long frameDelta = renderedFrames - lastReportFrame;
        double fps = frameDelta * 1000000000.0 / elapsedNanos;
        logger.info(logPrefix() + " elapsed=" + format(nanosToSeconds(now - startedAtNanos))
                + " seconds, fps=" + format(fps) + " at " + SPRITE_COUNT
                + " " + DRAW_SIZE + "x" + DRAW_SIZE + " sprites");
        lastReportNanos = now;
        lastReportFrame = renderedFrames;
    }

    private void writeResult(long elapsedNanos, double averageFrameFps, double averageSpriteDrawsPerSecond) {
        if (resultPath == null || resultPath.trim().length() == 0) {
            return;
        }
        File file = new File(resultPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new FdxException("Could not create benchmark result directory: " + parent);
        }
        Properties result = new Properties();
        result.setProperty("benchmark", NAME);
        result.setProperty("label", graphicsApi);
        result.setProperty("graphicsProvider", graphicsProvider);
        result.setProperty("sprites", Integer.toString(SPRITE_COUNT));
        result.setProperty("spriteSize", DRAW_SIZE + "x" + DRAW_SIZE);
        result.setProperty("texture", SPRITE_ASSET);
        result.setProperty("frames", Long.toString(renderedFrames));
        result.setProperty("elapsedSeconds", format(nanosToSeconds(elapsedNanos)));
        result.setProperty("targetSeconds", format(nanosToSeconds(exitAfterNanos)));
        result.setProperty("averageFrameFps", format(averageFrameFps));
        result.setProperty("averageSpriteDrawsPerSecond", format(averageSpriteDrawsPerSecond));
        result.setProperty("javaVersion", System.getProperty("java.version", ""));
        result.setProperty("javaVm", System.getProperty("java.vm.name", "") + " "
                + System.getProperty("java.vm.version", ""));
        result.setProperty("os", System.getProperty("os.name", "") + " "
                + System.getProperty("os.version", "") + " "
                + System.getProperty("os.arch", ""));
        result.setProperty("multiRelease", System.getProperty("jdk.util.jar.enableMultiRelease", "true"));
        result.setProperty("generatedAt", Instant.now().toString());
        try (FileOutputStream output = new FileOutputStream(file)) {
            result.store(output, "libfdx benchmark result");
        } catch (IOException error) {
            throw new FdxException("Could not write benchmark result: " + file, error);
        }
    }

    private void configureViewport(int framebufferWidth, int framebufferHeight) {
        layoutWidth = Math.max(1, framebufferWidth);
        layoutHeight = Math.max(1, framebufferHeight);
        if (batch != null) {
            batch.viewport(layoutWidth, layoutHeight);
        }
        generateSprites();
    }

    private void generateSprites() {
        Random random = new Random(RANDOM_SEED);
        for (int i = 0; i < SPRITE_COUNT; i++) {
            spriteCenterX[i] = toNormalizedX(random.nextInt(layoutWidth));
            spriteCenterY[i] = toNormalizedY(random.nextInt(layoutHeight));
        }
    }

    private float toNormalizedX(int pixelX) {
        return -1.0f + (2.0f * pixelX / layoutWidth);
    }

    private float toNormalizedY(int pixelY) {
        return -1.0f + (2.0f * pixelY / layoutHeight);
    }

    private float spriteWidth() {
        return 2.0f * DRAW_SIZE / layoutWidth;
    }

    private float spriteHeight() {
        return 2.0f * DRAW_SIZE / layoutHeight;
    }

    private int framebufferWidth() {
        if (display == null) {
            return 1;
        }
        return display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
    }

    private int framebufferHeight() {
        if (display == null) {
            return 1;
        }
        return display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
    }

    private double averageFrameFps(long elapsedNanos) {
        if (renderedFrames == 0L || elapsedNanos <= 0L) {
            return 0.0;
        }
        return renderedFrames * 1000000000.0 / elapsedNanos;
    }

    private String graphicsApiName(GraphicsContext graphics) {
        String label = System.getProperty("libfdx.benchmark.graphicsLabel");
        if (label != null && label.trim().length() > 0) {
            return label.trim();
        }
        return graphics.providerId().value();
    }

    private boolean usesInstancedBatchPath() {
        return "gl".equals(graphicsProvider) || "wgpu".equals(graphicsProvider) || "vulkan".equals(graphicsProvider);
    }

    private String logPrefix() {
        return "SpriteBatchStressBenchmark[" + graphicsApi + "]";
    }

    private String runLimitDescription() {
        if (exitAfterNanos <= 0L) {
            return "";
        }
        return ", duration=" + format(nanosToSeconds(exitAfterNanos)) + " seconds";
    }

    private long elapsedTestNanos() {
        if (startedAtNanos == 0L || lastRenderNanos == 0L) {
            return 0L;
        }
        return Math.max(0L, lastRenderNanos - startedAtNanos);
    }

    private double nanosToSeconds(long nanos) {
        return nanos / 1000000000.0;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
