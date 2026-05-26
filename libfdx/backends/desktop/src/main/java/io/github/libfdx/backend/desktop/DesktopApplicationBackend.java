package io.github.libfdx.backend.desktop;

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
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.DefaultGraphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsAttachmentRequirements;
import io.github.libfdx.graphics.GraphicsClientApi;
import io.github.libfdx.graphics.GraphicsContextProfile;
import io.github.libfdx.graphics.GraphicsEnvironment;
import io.github.libfdx.graphics.NativeWindow;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallback;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay;
import static org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandWindow;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window;

public final class DesktopApplicationBackend implements ApplicationBackend, Application {
    public static final ProviderId ID = ProviderId.of("desktop");

    private final SystemLogger logger = new SystemLogger();
    private final FrameSync sync = new FrameSync();
    private Fdx fdx;
    private ApplicationLifecycle lifecycle = ApplicationLifecycle.DISPOSED;
    private GLFWErrorCallback errorCallback;
    private GLFWFramebufferSizeCallback framebufferSizeCallback;
    private GLFWWindowSizeCallback windowSizeCallback;
    private GLFWWindowCloseCallback closeCallback;
    private DesktopDisplay display;
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
        DesktopApplicationConfig actualConfig = toDesktopConfig(config);
        DisplayConfig displayConfig = actualConfig.displayConfig();
        GraphicsAttachmentProvider graphicsProvider = actualConfig.graphics();
        if (graphicsProvider == null) {
            throw new FdxException("No graphics provider configured");
        }
        if (actualConfig.graphicsProvider() != null && !actualConfig.graphicsProvider().equals(graphicsProvider.providerId())) {
            throw new FdxException("Configured graphics provider ID does not match attached GraphicsAttachmentProvider");
        }
        GraphicsAttachmentRequirements graphicsRequirements = graphicsProvider.requirements();

        initializeGlfw();
        long windowHandle = createWindow(displayConfig, graphicsRequirements);
        display = new DesktopDisplay(windowHandle, displayConfig.title());
        display.refreshSizes();
        installCallbacks(listener);

        DefaultFileSystem files = new DefaultFileSystem();

        graphics = graphicsProvider.create(new DesktopGraphicsEnvironment(display, createNativeWindow(windowHandle)));
        if (graphicsRequirements.clientApi() == GraphicsClientApi.OPENGL) {
            GLFW.glfwSwapInterval(displayConfig.vSync() ? 1 : 0);
        }
        fdx = new DefaultFdx(this, new DefaultDisplays(display), new DefaultGraphics(graphics), files, logger);

        disposed = false;
        running = true;
        lifecycle = ApplicationLifecycle.CREATED;

        if (displayConfig.visible()) {
            GLFW.glfwShowWindow(windowHandle);
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

    public void start(DesktopApplicationConfig config, ApplicationListener listener) {
        start((ApplicationConfig) config, listener);
    }

    private DesktopApplicationConfig toDesktopConfig(ApplicationConfig config) {
        if (config == null) {
            return new DesktopApplicationConfig();
        }
        if (config instanceof DesktopApplicationConfig) {
            return (DesktopApplicationConfig) config;
        }
        throw new FdxException("DesktopApplicationBackend requires DesktopApplicationConfig");
    }

    private void initializeGlfw() {
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        GLFW.glfwSetErrorCallback(errorCallback);
        if (!GLFW.glfwInit()) {
            throw new FdxException("Unable to initialize GLFW");
        }
    }

    private long createWindow(DisplayConfig config, GraphicsAttachmentRequirements graphicsRequirements) {
        GLFW.glfwDefaultWindowHints();
        applyGraphicsWindowHints(graphicsRequirements);
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, config.resizable() ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);

        long windowHandle = GLFW.glfwCreateWindow(config.width(), config.height(), config.title(), 0L, 0L);
        if (windowHandle == 0L) {
            throw new FdxException("Could not create GLFW window");
        }
        centerWindow(windowHandle, config.width(), config.height());
        return windowHandle;
    }

    private void applyGraphicsWindowHints(GraphicsAttachmentRequirements graphicsRequirements) {
        if (graphicsRequirements == null || graphicsRequirements.clientApi() == GraphicsClientApi.NO_API) {
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            return;
        }
        if (graphicsRequirements.clientApi() == GraphicsClientApi.VULKAN) {
            if (!org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported()) {
                throw new FdxException("Vulkan is not supported by GLFW on this system");
            }
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
            return;
        }
        if (graphicsRequirements.clientApi() != GraphicsClientApi.OPENGL) {
            throw new FdxException("Unsupported desktop graphics client API: " + graphicsRequirements.clientApi());
        }
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, graphicsRequirements.majorVersion());
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, graphicsRequirements.minorVersion());
        GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,
                graphicsRequirements.forwardCompatible() ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        if (graphicsRequirements.profile() == GraphicsContextProfile.CORE) {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        } else if (graphicsRequirements.profile() == GraphicsContextProfile.COMPATIBILITY) {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        } else {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_ANY_PROFILE);
        }
    }

    private void centerWindow(long windowHandle, int width, int height) {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            return;
        }
        org.lwjgl.glfw.GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode == null) {
            return;
        }
        GLFW.glfwSetWindowPos(windowHandle, (mode.width() - width) / 2, (mode.height() - height) / 2);
    }

    private NativeWindow createNativeWindow(long windowHandle) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return NativeWindow.windows(windowHandle, GLFWNativeWin32.glfwGetWin32Window(windowHandle));
        }
        if (osName.contains("linux")) {
            if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) {
                return NativeWindow.wayland(windowHandle, glfwGetWaylandDisplay(), glfwGetWaylandWindow(windowHandle));
            }
            return NativeWindow.x11(windowHandle, glfwGetX11Display(), glfwGetX11Window(windowHandle));
        }
        if (osName.contains("mac")) {
            return NativeWindow.macos(windowHandle, windowHandle);
        }
        throw new FdxException("Unsupported native desktop window platform: " + osName);
    }

    private void installCallbacks(final ApplicationListener listener) {
        framebufferSizeCallback = new GLFWFramebufferSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                refreshDisplayAfterResize(listener);
            }
        };
        windowSizeCallback = new GLFWWindowSizeCallback() {
            @Override
            public void invoke(long window, int width, int height) {
                refreshDisplayAfterResize(listener);
            }
        };
        closeCallback = new GLFWWindowCloseCallback() {
            @Override
            public void invoke(long window) {
                running = false;
            }
        };
        GLFW.glfwSetFramebufferSizeCallback(display.windowHandle(), framebufferSizeCallback);
        GLFW.glfwSetWindowSizeCallback(display.windowHandle(), windowSizeCallback);
        GLFW.glfwSetWindowCloseCallback(display.windowHandle(), closeCallback);
    }

    private void refreshDisplayAfterResize(ApplicationListener listener) {
        int oldWidth = display.width();
        int oldHeight = display.height();
        int oldFramebufferWidth = display.framebufferWidth();
        int oldFramebufferHeight = display.framebufferHeight();
        display.refreshSizes();
        if (graphics != null
                && (oldFramebufferWidth != display.framebufferWidth()
                || oldFramebufferHeight != display.framebufferHeight())) {
            graphics.resize(display.framebufferWidth(), display.framebufferHeight());
        }
        if (listenerCreated && (oldWidth != display.width() || oldHeight != display.height())) {
            listener.resize(display.width(), display.height());
        }
    }

    private void loop(ApplicationListener listener, DisplayConfig displayConfig) {
        long lastTime = System.nanoTime();
        while (running && !GLFW.glfwWindowShouldClose(display.windowHandle())) {
            GLFW.glfwPollEvents();
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
            sync.sync(displayConfig.foregroundFps());
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
                GLFW.glfwSetFramebufferSizeCallback(display.windowHandle(), null);
                GLFW.glfwSetWindowSizeCallback(display.windowHandle(), null);
                GLFW.glfwSetWindowCloseCallback(display.windowHandle(), null);
                GLFW.glfwDestroyWindow(display.windowHandle());
                display = null;
            }
            if (framebufferSizeCallback != null) {
                framebufferSizeCallback.free();
                framebufferSizeCallback = null;
            }
            if (windowSizeCallback != null) {
                windowSizeCallback.free();
                windowSizeCallback = null;
            }
            if (closeCallback != null) {
                closeCallback.free();
                closeCallback = null;
            }
            GLFW.glfwTerminate();
            if (errorCallback != null) {
                errorCallback.free();
                errorCallback = null;
            }
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
            GLFW.glfwSetWindowShouldClose(display.windowHandle(), true);
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

    private static final class FrameSync {
        void sync(int fps) {
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
    }

    private static final class DesktopGraphicsEnvironment implements GraphicsEnvironment {
        private final Display display;
        private final NativeWindow nativeWindow;

        DesktopGraphicsEnvironment(Display display, NativeWindow nativeWindow) {
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

    private static final class DesktopDisplay implements Display {
        private final long windowHandle;
        private String title;
        private int width;
        private int height;
        private int framebufferWidth;
        private int framebufferHeight;
        private final IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
        private final IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);

        DesktopDisplay(long windowHandle, String title) {
            this.windowHandle = windowHandle;
            this.title = title;
        }

        long windowHandle() {
            return windowHandle;
        }

        void refreshSizes() {
            widthBuffer.clear();
            heightBuffer.clear();
            GLFW.glfwGetWindowSize(windowHandle, widthBuffer, heightBuffer);
            width = widthBuffer.get(0);
            height = heightBuffer.get(0);

            widthBuffer.clear();
            heightBuffer.clear();
            GLFW.glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            framebufferWidth = widthBuffer.get(0);
            framebufferHeight = heightBuffer.get(0);
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public void title(String title) {
            this.title = title != null ? title : "";
            GLFW.glfwSetWindowTitle(windowHandle, this.title);
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
            return GLFW.glfwWindowShouldClose(windowHandle);
        }

        @Override
        public void requestClose() {
            GLFW.glfwSetWindowShouldClose(windowHandle, true);
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
