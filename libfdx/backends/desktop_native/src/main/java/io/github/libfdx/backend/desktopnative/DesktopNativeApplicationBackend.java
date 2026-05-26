package io.github.libfdx.backend.desktopnative;

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
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsClientApi;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;

public final class DesktopNativeApplicationBackend implements ApplicationBackend, Application {
    public static final ProviderId ID = ProviderId.of("desktop_native");

    private final SystemLogger logger = new SystemLogger();
    private Fdx fdx;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private DesktopNativeDisplay display;
    private GraphicsAttachment graphics;
    private boolean running;
    private boolean disposed = true;
    private boolean listenerCreated;
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
        DesktopNativeApplicationConfig actualConfig = toDesktopNativeConfig(config);
        DisplayConfig displayConfig = actualConfig.displayConfig();
        GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No desktop_native graphics provider configured");
        }
        if (actualConfig.graphicsProvider() != null && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
            throw new FdxException("Configured graphics provider ID does not match attached GraphicsAttachmentProvider");
        }
        GraphicsAttachmentRequirements requirements = graphicsProvider.requirements();

        initializeGlfw();
        long windowHandle = createWindow(displayConfig, requirements);
        display = new DesktopNativeDisplay(windowHandle, displayConfig.title());
        display.refreshSizes();

        graphics = graphicsProvider.create(new DesktopNativeGraphicsEnvironment(display, NativeWindow.glfw(windowHandle)));
        if (requirements.clientApi() == GraphicsClientApi.OPENGL) {
            DesktopNativeGLFW.swapInterval(displayConfig.vSync() ? 1 : 0);
        }
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), null, logger);

        disposed = false;
        running = true;
        lifecycle = ApplicationLifecycle.CREATED;

        if (displayConfig.visible()) {
            DesktopNativeGLFW.showWindow(windowHandle);
        }

        try {
            listener.create(fdx);
            listenerCreated = true;
            listener.resize(display.width(), display.height());
            lifecycle = ApplicationLifecycle.RUNNING;
            loop(listener, displayConfig);
        } finally {
            shutdown(listener);
        }
    }

    public void start(DesktopNativeApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private DesktopNativeApplicationConfig toDesktopNativeConfig(ApplicationConfig config) {
        if (config == null) {
            return new DesktopNativeApplicationConfig();
        }
        if (config instanceof DesktopNativeApplicationConfig) {
            return (DesktopNativeApplicationConfig) config;
        }
        throw new FdxException("DesktopNativeApplicationBackend requires DesktopNativeApplicationConfig");
    }

    private void initializeGlfw() {
        if (!DesktopNativeGLFW.init()) {
            throw new FdxException("Unable to initialize GLFW");
        }
    }

    private long createWindow(DisplayConfig config, GraphicsAttachmentRequirements requirements) {
        DesktopNativeGLFW.defaultWindowHints();
        applyGraphicsWindowHints(requirements);
        DesktopNativeGLFW.windowHint(DesktopNativeGLFW.VISIBLE, DesktopNativeGLFW.FALSE);
        DesktopNativeGLFW.windowHint(DesktopNativeGLFW.RESIZABLE, config.resizable() ? DesktopNativeGLFW.TRUE : DesktopNativeGLFW.FALSE);
        long windowHandle = DesktopNativeGLFW.createWindow(config.width(), config.height(), config.title());
        if (windowHandle == 0L) {
            throw new FdxException("Could not create GLFW window");
        }
        return windowHandle;
    }

    private void applyGraphicsWindowHints(GraphicsAttachmentRequirements requirements) {
        if (requirements == null || requirements.clientApi() == GraphicsClientApi.NO_API) {
            DesktopNativeGLFW.windowHint(DesktopNativeGLFW.CLIENT_API, DesktopNativeGLFW.NO_API);
            return;
        }
        if (requirements.clientApi() != GraphicsClientApi.OPENGL) {
            throw new FdxException("Unsupported desktop_native graphics client API: " + requirements.clientApi());
        }
        DesktopNativeGLFW.windowHint(DesktopNativeGLFW.CLIENT_API, DesktopNativeGLFW.OPENGL_API);
        DesktopNativeGLFW.windowHint(DesktopNativeGLFW.CONTEXT_VERSION_MAJOR, requirements.majorVersion());
        DesktopNativeGLFW.windowHint(DesktopNativeGLFW.CONTEXT_VERSION_MINOR, requirements.minorVersion());
        DesktopNativeGLFW.windowHint(DesktopNativeGLFW.OPENGL_FORWARD_COMPAT,
                requirements.forwardCompatible() ? DesktopNativeGLFW.TRUE : DesktopNativeGLFW.FALSE);
        if (requirements.profile() == GraphicsContextProfile.CORE) {
            DesktopNativeGLFW.windowHint(DesktopNativeGLFW.OPENGL_PROFILE, DesktopNativeGLFW.OPENGL_CORE_PROFILE);
        } else if (requirements.profile() == GraphicsContextProfile.COMPATIBILITY) {
            DesktopNativeGLFW.windowHint(DesktopNativeGLFW.OPENGL_PROFILE, DesktopNativeGLFW.OPENGL_COMPAT_PROFILE);
        } else {
            DesktopNativeGLFW.windowHint(DesktopNativeGLFW.OPENGL_PROFILE, DesktopNativeGLFW.OPENGL_ANY_PROFILE);
        }
    }

    private void loop(ApplicationListener listener, DisplayConfig displayConfig) {
        long lastTime = System.nanoTime();
        while (running && !DesktopNativeGLFW.windowShouldClose(display.windowHandle())) {
            DesktopNativeGLFW.pollEvents();
            display.refreshSizes();
            if (graphics != null) {
                graphics.processEvents();
            }

            long now = System.nanoTime();
            deltaTime = (now - lastTime) / 1000000000.0f;
            lastTime = now;
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
            sync(displayConfig.foregroundFps());
        }
    }

    private void sync(int fps) {
        if (fps <= 0) {
            return;
        }
        long sleepMillis = 1000L / fps;
        if (sleepMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void shutdown(ApplicationListener listener) {
        if (disposed) {
            return;
        }
        lifecycle = ApplicationLifecycle.PAUSED;
        if (listenerCreated) {
            listener.pause();
        }
        lifecycle = ApplicationLifecycle.DISPOSED;
        try {
            if (listenerCreated) {
                listener.dispose();
            }
        } finally {
            listenerCreated = false;
            if (graphics != null) {
                graphics.dispose();
                graphics = null;
            }
            if (display != null) {
                DesktopNativeGLFW.destroyWindow(display.windowHandle());
                display = null;
            }
            DesktopNativeGLFW.terminate();
            running = false;
            disposed = true;
            fdx = null;
        }
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
            DesktopNativeGLFW.setWindowShouldClose(display.windowHandle(), true);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    @Override
    public void dispose() {
        requestExit();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static final class DesktopNativeGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        DesktopNativeGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
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

    private static final class DesktopNativeDisplay implements Display {
        private final long windowHandle;
        private final int[] widthBuffer = new int[1];
        private final int[] heightBuffer = new int[1];
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;

        DesktopNativeDisplay(long windowHandle, String title) {
            this.windowHandle = windowHandle;
            this.title = title != null ? title : "";
        }

        long windowHandle() {
            return windowHandle;
        }

        void refreshSizes() {
            DesktopNativeGLFW.getWindowSize(windowHandle, widthBuffer, heightBuffer);
            width = widthBuffer[0];
            height = heightBuffer[0];
            DesktopNativeGLFW.getFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            framebufferWidth = widthBuffer[0];
            framebufferHeight = heightBuffer[0];
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public void title(String title) {
            this.title = title != null ? title : "";
            DesktopNativeGLFW.setWindowTitle(windowHandle, this.title);
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
            return DesktopNativeGLFW.windowShouldClose(windowHandle);
        }

        @Override
        public void requestClose() {
            DesktopNativeGLFW.setWindowShouldClose(windowHandle, true);
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
