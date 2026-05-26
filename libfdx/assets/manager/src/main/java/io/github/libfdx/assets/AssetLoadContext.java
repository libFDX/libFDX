package io.github.libfdx.assets;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileSystem;

import java.util.concurrent.Callable;

public interface AssetLoadContext {
    FileSystem files();

    <T> FdxFuture<T> dependency(AssetDescriptor<T> descriptor);

    <T> FdxFuture<T> completeOnUpdate(Callable<T> task);
}
