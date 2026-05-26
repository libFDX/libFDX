package io.github.libfdx.backend.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import io.github.libfdx.DefaultFdx;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationBackend;
import io.github.libfdx.application.ApplicationConfig;
import io.github.libfdx.application.ApplicationLifecycle;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.core.SystemLogger;
import io.github.libfdx.display.DefaultDisplays;
import io.github.libfdx.display.Display;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.GraphicsProviderSupport;
import io.github.libfdx.graphics.NativeWindow;

public final class AndroidApplicationBackend implements ApplicationBackend, Application,
        SurfaceHolder.Callback, Choreographer.FrameCallback {
    public static final ProviderId ID = ProviderId.of("android");

    private final SystemLogger logger = new SystemLogger();
    private Activity activity;
    private AndroidApplicationConfig config;
    private ApplicationListener listener;
    private SurfaceView surfaceView;
    private Surface surface;
    private Fdx fdx;
    private AndroidDisplay display;
    private GraphicsAttachment graphics;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private boolean running;
    private boolean paused;
    private boolean disposed = true;
    private boolean listenerCreated;
    private boolean startupFailed;
    private boolean frameCallbackPosted;
    private long lastFrameTimeNanos;
    private float deltaTime;
    private long frameId;

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    public void start(ApplicationConfig config, ApplicationListener listener) {
        throw new FdxException("AndroidApplicationBackend must be attached to an Android Activity");
    }

    public void attach(Activity activity, AndroidApplicationConfig config, ApplicationListener listener) {
        if (activity == null) {
            throw new FdxException("Android Activity cannot be null");
        }
        if (listener == null) {
            throw new FdxException("ApplicationListener cannot be null");
        }
        this.activity = activity;
        this.config = config != null ? config : new AndroidApplicationConfig();
        this.listener = listener;
        this.display = new AndroidDisplay(activity, this.config.displayConfig().title());
        this.disposed = false;
        this.running = true;
        this.paused = false;
        this.lifecycle = ApplicationLifecycle.CREATED;

        setupActivityWindow(activity);
        surfaceView = new SurfaceView(activity);
        surfaceView.getHolder().setFixedSize(this.config.displayConfig().width(), this.config.displayConfig().height());
        surfaceView.getHolder().addCallback(this);
        activity.setContentView(surfaceView);
    }

    private void setupActivityWindow(Activity activity) {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = activity.getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        Rect frame = holder.getSurfaceFrame();
        int width = Math.max(1, frame.width());
        int height = Math.max(1, frame.height());
        display.size(width, height);
        createSessionIfNeeded();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder.getSurface();
        display.size(width, height);
        if (!listenerCreated) {
            createSessionIfNeeded();
            return;
        }
        if (graphics != null) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
        }
        listener.resize(display.width(), display.height());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        removeFrameCallbackIfNeeded();
        shutdown();
        surface = null;
    }

    private void createSessionIfNeeded() {
        if (startupFailed || listenerCreated || surface == null || !surface.isValid() || display.framebufferWidth() <= 0
                || display.framebufferHeight() <= 0) {
            return;
        }
        GraphicsEnvironment graphicsEnvironment = new AndroidGraphicsEnvironment(display, NativeWindow.android(surface));
        GraphicsAttachment createdGraphics;
        try {
            createdGraphics = createGraphicsAttachment(graphicsEnvironment);
        } catch (FdxException e) {
            handleStartupFailure(e);
            return;
        }

        FileSystem files = new AndroidFileSystem(activity);

        graphics = createdGraphics;
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), files, logger);

        listener.create(fdx);
        listenerCreated = true;
        listener.resize(display.width(), display.height());
        lifecycle = ApplicationLifecycle.RUNNING;
        lastFrameTimeNanos = System.nanoTime();
        postFrameCallbackIfNeeded();
    }

    private GraphicsAttachment createGraphicsAttachment(GraphicsEnvironment graphicsEnvironment) {
        GraphicsAttachmentProvider graphicsProvider = config.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No Android graphics provider configured");
        }

        GraphicsFailureCollector failures = new GraphicsFailureCollector();
        GraphicsAttachment graphicsAttachment = tryCreateGraphics(graphicsProvider, graphicsEnvironment, failures);
        if (graphicsAttachment != null) {
            return graphicsAttachment;
        }

        if (config.graphicsFallbackEnabled()) {
            GraphicsAttachmentProvider[] fallbackGraphics = config.fallbackGraphics();
            for (GraphicsAttachmentProvider fallbackProvider : fallbackGraphics) {
                if (fallbackProvider == null || fallbackProvider.providerId().equals(graphicsProvider.providerId())) {
                    continue;
                }
                graphicsAttachment = tryCreateGraphics(fallbackProvider, graphicsEnvironment, failures);
                if (graphicsAttachment != null) {
                    return graphicsAttachment;
                }
            }
        }

        throw new FdxException("Android graphics startup failed. " + failures.message());
    }

    private GraphicsAttachment tryCreateGraphics(GraphicsAttachmentProvider provider,
            GraphicsEnvironment graphicsEnvironment, GraphicsFailureCollector failures) {
        String supportFailure = supportFailureReason(provider);
        if (supportFailure != null) {
            failures.add(provider, supportFailure);
            logger.warn("Android graphics provider " + provider.providerId() + " is not supported: " + supportFailure);
            return null;
        }

        try {
            GraphicsAttachment graphicsAttachment = provider.create(graphicsEnvironment);
            logger.info("Android graphics provider selected: " + provider.providerId());
            return graphicsAttachment;
        } catch (RuntimeException e) {
            failures.add(provider, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            logger.warn("Android graphics provider " + provider.providerId() + " failed to start: "
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
            return null;
        }
    }

    private static String supportFailureReason(GraphicsAttachmentProvider provider) {
        if (provider instanceof GraphicsProviderSupport) {
            return ((GraphicsProviderSupport) provider).supportFailureReason();
        }
        return null;
    }

    private void handleStartupFailure(FdxException error) {
        if (config.graphicsFailureMode() == AndroidGraphicsFailureMode.THROW) {
            throw error;
        }
        showStartupError(error.getMessage(), error);
    }

    private void showStartupError(String message, Throwable error) {
        startupFailed = true;
        running = false;
        lifecycle = ApplicationLifecycle.DISPOSED;
        removeFrameCallbackIfNeeded();
        logger.error("Android graphics startup failed", error);

        TextView errorView = new TextView(activity);
        errorView.setText("Graphics startup failed\n\n" + (message != null ? message : "Unknown graphics error"));
        errorView.setTextColor(Color.WHITE);
        errorView.setBackgroundColor(Color.rgb(24, 24, 24));
        errorView.setGravity(Gravity.CENTER);
        errorView.setTextSize(16.0f);
        int padding = Math.round(24.0f * activity.getResources().getDisplayMetrics().density);
        errorView.setPadding(padding, padding, padding, padding);
        activity.setContentView(errorView);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameCallbackPosted = false;
        if (!running || paused || disposed || !listenerCreated || surface == null || !surface.isValid()) {
            return;
        }

        if (graphics != null) {
            graphics.processEvents();
        }

        long now = System.nanoTime();
        deltaTime = (now - lastFrameTimeNanos) / 1000000000.0f;
        lastFrameTimeNanos = now;
        frameId++;

        if (graphics == null || graphics.beginFrame()) {
            try {
                listener.render();
            } finally {
                if (graphics != null) {
                    graphics.endFrame();
                }
            }
        }
        postFrameCallbackIfNeeded();
    }

    public void pause() {
        if (paused || disposed) {
            return;
        }
        paused = true;
        removeFrameCallbackIfNeeded();
        if (listenerCreated) {
            listener.pause();
            lifecycle = ApplicationLifecycle.PAUSED;
        }
    }

    public void resume() {
        if (disposed) {
            return;
        }
        paused = false;
        if (listenerCreated) {
            listener.resume();
            lifecycle = ApplicationLifecycle.RUNNING;
            lastFrameTimeNanos = System.nanoTime();
            postFrameCallbackIfNeeded();
        }
    }

    private void postFrameCallbackIfNeeded() {
        if (frameCallbackPosted || paused || disposed || !running || surface == null) {
            return;
        }
        frameCallbackPosted = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void removeFrameCallbackIfNeeded() {
        if (!frameCallbackPosted) {
            return;
        }
        Choreographer.getInstance().removeFrameCallback(this);
        frameCallbackPosted = false;
    }

    private void shutdown() {
        if (listenerCreated) {
            lifecycle = ApplicationLifecycle.DISPOSED;
            try {
                listener.dispose();
            } finally {
                listenerCreated = false;
            }
        }
        if (graphics != null) {
            graphics.dispose();
            graphics = null;
        }
        fdx = null;
    }

    @Override
    public ApplicationLifecycle lifecycle() {
        return lifecycle;
    }

    @Override
    public float deltaTime() {
        return deltaTime;
    }

    @Override
    public long frameId() {
        return frameId;
    }

    @Override
    public void requestExit() {
        running = false;
        if (display != null) {
            display.requestClose();
        }
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        removeFrameCallbackIfNeeded();
        shutdown();
        if (surfaceView != null) {
            surfaceView.getHolder().removeCallback(this);
            surfaceView = null;
        }
        surface = null;
        running = false;
        disposed = true;
        lifecycle = ApplicationLifecycle.DISPOSED;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static final class AndroidGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        AndroidGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
            this.display = display;
            this.nativeWindow = nativeWindow;
        }

        @Override
        public Display display() {
            return display;
        }

        @Override
        public NativeWindow nativeWindow() {
            return nativeWindow;
        }
    }

    private static final class GraphicsFailureCollector {
        private final StringBuilder message = new StringBuilder();

        void add(GraphicsAttachmentProvider provider, String failure) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append(provider.providerId()).append(": ").append(failure);
        }

        String message() {
            return message.length() > 0 ? message.toString() : "No provider failure details were reported.";
        }
    }

    private static final class AndroidDisplay implements Display {
        private final Activity activity;
        private String title;
        private int width;
        private int height;
        private boolean closeRequested;

        AndroidDisplay(Activity activity, String title) {
            this.activity = activity;
            this.title = title != null ? title : "";
            activity.setTitle(this.title);
        }

        void size(int width, int height) {
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public void title(String title) {
            this.title = title != null ? title : "";
            activity.setTitle(this.title);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public int framebufferWidth() {
            return width;
        }

        @Override
        public int framebufferHeight() {
            return height;
        }

        @Override
        public boolean closeRequested() {
            return closeRequested;
        }

        @Override
        public void requestClose() {
            closeRequested = true;
        }

        @Override
        public ProviderId providerId() {
            return ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T) this;
        }
    }
}
