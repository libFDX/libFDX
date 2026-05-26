package io.github.libfdx;

import io.github.libfdx.application.Application;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Displays;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.Graphics;

public interface Fdx {
    Application app();

    Displays displays();

    Graphics graphics();

    FileSystem files();

    Logger logger();
}
