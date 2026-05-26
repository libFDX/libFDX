package io.github.libfdx.tests.desktop;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.tests.TestSelector;

public final class DesktopTestLauncher {
    private DesktopTestLauncher() {
    }

    public static void main(String[] args) {
        String testName = System.getProperty("libfdx.test.name", TestSelector.DEFAULT_TEST_NAME);
        String graphics = graphicsName();
        String graphicsDisplayName = graphicsDisplayName(graphics);
        boolean vSync = Boolean.parseBoolean(System.getProperty("libfdx.test.vsync", "true"));
        boolean visible = Boolean.parseBoolean(System.getProperty("libfdx.test.visible", "true"));
        int foregroundFps = Integer.parseInt(System.getProperty("libfdx.test.foregroundFps", "60"));
        System.out.println("[info] DesktopTestLauncher starting " + testName
                + " with " + graphicsDisplayName
                + ", provider=" + graphics
                + ", java=" + System.getProperty("java.version", "")
                + ", multiRelease=" + System.getProperty("jdk.util.jar.enableMultiRelease", "true")
                + ", vSync=" + vSync
                + ", foregroundFps=" + foregroundFps
                + ", visible=" + visible);
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Test: " + testName + " - " + graphicsDisplayName)
                .size(640, 480)
                .visible(visible)
                .vSync(vSync)
                .foregroundFps(foregroundFps)
                .graphics(graphicsProvider(graphics, vSync));

        ApplicationListener test = TestSelector.create(testName, exitAfterFrames());
        new DesktopApplicationBackend().start(config, test);
    }

    private static GraphicsAttachmentProvider graphicsProvider(String graphics, boolean vSync) {
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return new DesktopOpenGLProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            DesktopVulkanProvider provider = new DesktopVulkanProvider().vSync(vSync);
            if (!vSync) {
                provider.configuration().preferMailboxPresentMode(false);
            }
            return provider;
        }
        return new WGPUProvider().vSync(vSync);
    }

    private static String graphicsName() {
        String graphics = System.getProperty("libfdx.test.graphics", "wgpu");
        if ("wgpu-jni".equalsIgnoreCase(graphics) || "wgpu-ffm".equalsIgnoreCase(graphics)) {
            return "wgpu";
        }
        return graphics;
    }

    private static String graphicsDisplayName(String graphics) {
        String configured = System.getProperty("libfdx.test.graphicsLabel");
        if (configured != null && configured.trim().length() > 0) {
            return configured.trim();
        }
        String selected = System.getProperty("libfdx.test.graphics", "wgpu");
        if ("wgpu-jni".equalsIgnoreCase(selected)) {
            return "WGPU JNI";
        }
        if ("wgpu-ffm".equalsIgnoreCase(selected)) {
            return "WGPU FFM";
        }
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return "OpenGL";
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            return "Vulkan";
        }
        return "WGPU";
    }

    private static long exitAfterFrames() {
        String value = System.getProperty("libfdx.test.frames", "0");
        return Long.parseLong(value);
    }
}
