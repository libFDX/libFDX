package io.github.libfdx.tests.android;

import android.content.Intent;
import android.net.Uri;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.android.AndroidApplicationActivity;
import io.github.libfdx.backend.android.AndroidApplicationConfig;
import io.github.libfdx.backend.android.AndroidGraphicsFailureMode;
import io.github.libfdx.backend.android.AndroidGlesProvider;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.tests.TestSelector;

public class AndroidTestActivity extends AndroidApplicationActivity {
    @Override
    protected AndroidApplicationConfig createApplicationConfig() {
        String testName = testName();
        return new AndroidApplicationConfig()
                .title("libfdx Test: " + testName + " - " + graphicsDisplayName())
                .size(640, 480)
                .vSync(true)
                .foregroundFps(60)
                .graphicsFailureMode(AndroidGraphicsFailureMode.THROW)
                .graphics(graphicsProvider());
    }

    @Override
    protected ApplicationListener createApplicationListener() {
        return TestSelector.create(testName(), 0L);
    }

    private GraphicsAttachmentProvider graphicsProvider() {
        if ("gles".equalsIgnoreCase(graphicsName())) {
            return new AndroidGlesProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return new AndroidVulkanProvider();
        }
        return new WGPUProvider();
    }

    private String testName() {
        Intent intent = getIntent();
        if (intent != null) {
            String extra = intent.getStringExtra("libfdx.test.name");
            if (extra != null && extra.trim().length() > 0) {
                return extra.trim();
            }
            Uri data = intent.getData();
            if (data != null) {
                String queryValue = data.getQueryParameter("test");
                if (queryValue != null && queryValue.trim().length() > 0) {
                    return queryValue.trim();
                }
            }
        }
        return TestSelector.DEFAULT_TEST_NAME;
    }

    protected String graphicsName() {
        return "wgpu";
    }

    protected String graphicsDisplayName() {
        if ("gles".equalsIgnoreCase(graphicsName())) {
            return "OpenGL ES";
        }
        if ("vulkan".equalsIgnoreCase(graphicsName()) || "vk".equalsIgnoreCase(graphicsName())) {
            return "Vulkan JNI";
        }
        return "WGPU JNI";
    }
}
