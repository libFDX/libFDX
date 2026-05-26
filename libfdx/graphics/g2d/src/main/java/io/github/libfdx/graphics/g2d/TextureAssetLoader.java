package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;

final class TextureAssetLoader implements AssetLoader<Texture> {
    private final GraphicsContext graphics;

    TextureAssetLoader(GraphicsContext graphics) {
        this.graphics = graphics;
    }

    @Override
    public Class<Texture> type() {
        return Texture.class;
    }

    @Override
    public FdxFuture<Texture> load(AssetLoadContext context, AssetDescriptor<Texture> descriptor) {
        final CompletableFuture<Texture> future = new CompletableFuture<Texture>();
        context.dependency(AssetDescriptor.of(descriptor.path(), ImageData.class))
                .onSuccess(image -> context.completeOnUpdate(new Callable<Texture>() {
                    @Override
                    public Texture call() {
                        Texture texture = graphics.device().createTexture(TextureDescriptor
                                .rgba8(descriptor.path(), image.width(), image.height()));
                        graphics.device().writeTexture(texture, image.rgba());
                        return texture;
                    }
                }).onSuccess(future::complete).onFailure(future::completeExceptionally))
                .onFailure(future::completeExceptionally);
        return FdxFuture.wrap(future);
    }
}
