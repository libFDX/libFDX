package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.jParser.api.NativeObject;
import com.github.xpenatan.webgpu.WGPUAndroidWindow;
import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPUSurface;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.NativeWindow;

final class WGPUNativeSurface {
    private WGPUNativeSurface() {
    }

    static SurfaceHandle create(WGPUInstance instance, NativeWindow nativeWindow) {
        WGPUSurface surface;
        Object owner = null;
        switch (nativeWindow.platform()) {
            case WINDOWS:
                surface = instance.createWindowsSurface(NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()));
                break;
            case X11:
                surface = instance.createLinuxSurface(false,
                        NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()),
                        NativeObject.native_new().native_setAddress(nativeWindow.displayHandle()));
                break;
            case WAYLAND:
                surface = instance.createLinuxSurface(true,
                        NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()),
                        NativeObject.native_new().native_setAddress(nativeWindow.displayHandle()));
                break;
            case MACOS:
                surface = instance.createMacSurface(NativeObject.native_new().native_setAddress(nativeWindow.windowHandle()));
                break;
            case ANDROID:
                WGPUAndroidWindow androidWindow = new WGPUAndroidWindow();
                androidWindow.initLogcat();
                androidWindow.createAndroidSurface(nativeWindow.objectHandle());
                surface = instance.createAndroidSurface(androidWindow);
                owner = androidWindow;
                break;
            default:
                throw new FdxException("Unsupported native window platform for WGPU surface: " + nativeWindow.platform());
        }

        if (surface == null) {
            throw new FdxException("Could not create a valid WGPU surface");
        }
        return new SurfaceHandle(surface, owner);
    }

    static final class SurfaceHandle {
        private final WGPUSurface surface;
        private final Object owner;

        SurfaceHandle(WGPUSurface surface, Object owner) {
            this.surface = surface;
            this.owner = owner;
        }

        WGPUSurface surface() {
            return surface;
        }

        Object owner() {
            return owner;
        }
    }
}
