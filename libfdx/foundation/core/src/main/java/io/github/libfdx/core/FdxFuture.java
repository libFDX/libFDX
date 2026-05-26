package io.github.libfdx.core;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class FdxFuture<T> {
    private final CompletableFuture<T> future;

    private FdxFuture(CompletableFuture<T> future) {
        this.future = future;
    }

    public static <T> FdxFuture<T> completed(T value) {
        return new FdxFuture<T>(CompletableFuture.completedFuture(value));
    }

    public static <T> FdxFuture<T> failed(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(error != null ? error : new FdxException("Future failed"));
        return new FdxFuture<T>(future);
    }

    public static <T> FdxFuture<T> wrap(CompletableFuture<T> future) {
        if (future == null) {
            throw new FdxException("CompletableFuture cannot be null");
        }
        return new FdxFuture<T>(future);
    }

    public static <T> FdxFuture<T> supplyAsync(final Callable<T> callable, Executor executor) {
        if (callable == null) {
            throw new FdxException("Future callable cannot be null");
        }
        if (executor == null) {
            throw new FdxException("Future executor cannot be null");
        }
        CompletableFuture<T> future = new CompletableFuture<T>();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    future.complete(callable.call());
                } catch (Throwable error) {
                    future.completeExceptionally(error);
                }
            }
        });
        return new FdxFuture<T>(future);
    }

    public FdxFuture<T> onSuccess(final Consumer<T> callback) {
        if (callback == null) {
            return this;
        }
        future.thenAccept(callback);
        return this;
    }

    public FdxFuture<T> onFailure(final Consumer<Throwable> callback) {
        if (callback == null) {
            return this;
        }
        future.whenComplete((value, error) -> {
            if (error != null) {
                callback.accept(unwrap(error));
            }
        });
        return this;
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean isFailed() {
        return future.isCompletedExceptionally();
    }

    public T join() {
        return future.join();
    }

    public T get() {
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new FdxException("Interrupted while waiting for future", error);
        } catch (ExecutionException error) {
            Throwable cause = unwrap(error);
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new FdxException("Future failed", cause);
        }
    }

    public CompletableFuture<T> completableFuture() {
        return future;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof ExecutionException && error.getCause() != null) {
            return error.getCause();
        }
        if (error instanceof java.util.concurrent.CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
