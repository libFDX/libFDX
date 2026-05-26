package io.github.libfdx.display;

import io.github.libfdx.core.ProviderHandle;

public interface Display extends ProviderHandle {
    String title();

    void title(String title);

    int width();

    int height();

    int framebufferWidth();

    int framebufferHeight();

    boolean closeRequested();

    void requestClose();
}
