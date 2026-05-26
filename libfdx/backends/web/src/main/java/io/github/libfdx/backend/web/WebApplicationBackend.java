package io.github.libfdx.backend.web;

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
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.AnimationFrameCallback;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLCanvasElement;

public final class WebApplicationBackend implements ApplicationBackend, Application, AnimationFrameCallback {
    public static final ProviderId ID = ProviderId.of("web");

    private final SystemLogger logger = new SystemLogger();
    private WebApplicationConfig config;
    private ApplicationListener listener;
    private Fdx fdx;
    private WebDisplay display;
    private HTMLCanvasElement canvas;
    private GraphicsAttachment graphics;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private boolean running;
    private boolean disposed = true;
    private boolean listenerCreated;
    private long lastFrameMillis;
    private float deltaTime;
    private long frameId;

    @Override
    public ProviderId providerId() {
        return ID;
    }

    @Override
    public void start(ApplicationConfig config, ApplicationListener listener) {
        if (listener == null) {
            throw new FdxException("ApplicationListener cannot be null");
        }
        WebApplicationConfig actualConfig = toWebConfig(config);
        GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No web graphics provider configured");
        }
        if (actualConfig.graphicsProvider() != null
                && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
            throw new FdxException("Configured graphics provider ID does not match attached GraphicsAttachmentProvider");
        }

        this.config = actualConfig;
        this.listener = listener;
        DisplayConfig displayConfig = actualConfig.displayConfig();
        setDocumentTitle(displayConfig.title());
        canvas = getOrCreateCanvas(actualConfig.canvasId(), displayConfig.width(), displayConfig.height());
        display = new WebDisplay(displayConfig.title());
        refreshDisplaySize();

        graphics = graphicsProvider.create(new WebGraphicsEnvironment(display, NativeWindow.web(canvas)));
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), null, logger);

        disposed = false;
        running = true;
        lifecycle = ApplicationLifecycle.CREATED;

        listener.create(fdx);
        listenerCreated = true;
        listener.resize(display.width(), display.height());
        lifecycle = ApplicationLifecycle.RUNNING;
        lastFrameMillis = System.currentTimeMillis();
        Window.requestAnimationFrame(this);
    }

    public void start(WebApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private WebApplicationConfig toWebConfig(ApplicationConfig config) {
        if (config == null) {
            return new WebApplicationConfig();
        }
        if (config instanceof WebApplicationConfig) {
            return (WebApplicationConfig) config;
        }
        throw new FdxException("WebApplicationBackend requires WebApplicationConfig");
    }

    @Override
    public void onAnimationFrame(double timestamp) {
        if (!running || disposed || listener == null) {
            return;
        }
        try {
            step();
        } catch (Throwable error) {
            logger.error("Web application frame failed", error);
            dispose();
            throw error instanceof RuntimeException ? (RuntimeException) error : new FdxException("Web frame failed", error);
        }
        if (running && !disposed) {
            Window.requestAnimationFrame(this);
        }
    }

    private void step() {
        boolean resized = refreshDisplaySize();
        if (resized && graphics != null) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
            if (listenerCreated) {
                listener.resize(display.width(), display.height());
            }
        }
        if (graphics != null) {
            graphics.processEvents();
        }

        long now = System.currentTimeMillis();
        deltaTime = (now - lastFrameMillis) / 1000.0f;
        lastFrameMillis = now;
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
    }

    private boolean refreshDisplaySize() {
        int cssWidth = Math.max(1, clientWidth(canvas));
        int cssHeight = Math.max(1, clientHeight(canvas));
        float scale = Math.max(1.0f, devicePixelRatio());
        int framebufferWidth = Math.max(1, Math.round(cssWidth * scale));
        int framebufferHeight = Math.max(1, Math.round(cssHeight * scale));
        if (canvasWidth(canvas) != framebufferWidth || canvasHeight(canvas) != framebufferHeight) {
            setCanvasFramebufferSize(canvas, framebufferWidth, framebufferHeight);
        }
        return display.size(cssWidth, cssHeight, framebufferWidth, framebufferHeight);
    }

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
        running = false;
        lifecycle = ApplicationLifecycle.DISPOSED;
        try {
            if (listenerCreated && listener != null) {
                listener.dispose();
            }
        } finally {
            listenerCreated = false;
            if (graphics != null) {
                graphics.dispose();
                graphics = null;
            }
            fdx = null;
            listener = null;
            display = null;
            canvas = null;
            disposed = true;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @JSBody(params = { "id", "width", "height" }, script =
            "var canvas = document.getElementById(id);\n" +
            "if (!canvas) {\n" +
            "  canvas = document.createElement('canvas');\n" +
            "  canvas.id = id;\n" +
            "  document.body.appendChild(canvas);\n" +
            "}\n" +
            "canvas.width = width;\n" +
            "canvas.height = height;\n" +
            "if (!canvas.style.width) canvas.style.width = width + 'px';\n" +
            "if (!canvas.style.height) canvas.style.height = height + 'px';\n" +
            "canvas.style.display = 'block';\n" +
            "return canvas;")
    private static native HTMLCanvasElement getOrCreateCanvas(String id, int width, int height);

    @JSBody(params = { "title" }, script = "document.title = title || '';")
    private static native void setDocumentTitle(String title);

    @JSBody(params = { "canvas" }, script = "return canvas.clientWidth || canvas.width || 1;")
    private static native int clientWidth(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script = "return canvas.clientHeight || canvas.height || 1;")
    private static native int clientHeight(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script = "return canvas.width || 1;")
    private static native int canvasWidth(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas" }, script = "return canvas.height || 1;")
    private static native int canvasHeight(HTMLCanvasElement canvas);

    @JSBody(params = { "canvas", "width", "height" }, script =
            "canvas.width = width;\n" +
            "canvas.height = height;")
    private static native void setCanvasFramebufferSize(HTMLCanvasElement canvas, int width, int height);

    @JSBody(script = "return window.devicePixelRatio || 1;")
    private static native float devicePixelRatio();

    private static final class WebGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        WebGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
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

    private static final class WebDisplay implements Display {
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private boolean closeRequested;

        WebDisplay(String title) {
            this.title = title != null ? title : "";
        }

        boolean size(int width, int height, int framebufferWidth, int framebufferHeight) {
            boolean changed = this.width != width || this.height != height
                    || this.framebufferWidth != framebufferWidth || this.framebufferHeight != framebufferHeight;
            this.width = width;
            this.height = height;
            this.framebufferWidth = framebufferWidth;
            this.framebufferHeight = framebufferHeight;
            return changed;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public void title(String title) {
            this.title = title != null ? title : "";
            setDocumentTitle(this.title);
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
            return framebufferWidth;
        }

        @Override
        public int framebufferHeight() {
            return framebufferHeight;
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
