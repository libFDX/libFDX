package io.github.libfdx.application;

import io.github.libfdx.Fdx;

public interface ApplicationListener {
    void create(Fdx fdx);

    void resize(int width, int height);

    void render();

    void pause();

    void resume();

    void dispose();
}
