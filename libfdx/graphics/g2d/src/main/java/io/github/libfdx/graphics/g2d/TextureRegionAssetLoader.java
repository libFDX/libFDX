package io.github.libfdx.graphics.g2d;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.Texture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;

final class TextureRegionAssetLoader implements AssetLoader<TextureRegion> {
    @Override
    public Class<TextureRegion> type() {
        return TextureRegion.class;
    }

    @Override
    public FdxFuture<TextureRegion> load(AssetLoadContext context, AssetDescriptor<TextureRegion> descriptor) {
        CompletableFuture<TextureRegion> future = new CompletableFuture<TextureRegion>();
        context.dependency(AssetDescriptor.of(descriptor.path(), Texture.class))
                .onSuccess(texture -> context.completeOnUpdate(new Callable<TextureRegion>() {
                    @Override
                    public TextureRegion call() {
                        return new TextureRegion(texture);
                    }
                }).onSuccess(future::complete).onFailure(future::completeExceptionally))
                .onFailure(future::completeExceptionally);
        return FdxFuture.wrap(future);
    }
}
