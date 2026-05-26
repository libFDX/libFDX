package io.github.libfdx.assets.loaders;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public final class ImageAssetLoader implements AssetLoader<ImageData> {
    public static void register(AssetManager assets) {
        assets.registerLoader(ImageData.class, new ImageAssetLoader());
    }

    @Override
    public Class<ImageData> type() {
        return ImageData.class;
    }

    @Override
    public FdxFuture<ImageData> load(final AssetLoadContext context, final AssetDescriptor<ImageData> descriptor) {
        CompletableFuture<ImageData> future = context.files().internal(descriptor.path()).readBytes()
                .completableFuture()
                .thenApply(ImageAssetLoader::decode);
        return FdxFuture.wrap(future);
    }

    public static ImageData decode(byte[] bytes) {
        ImageData android = decodeWithAndroid(bytes);
        if (android != null) {
            return android;
        }
        ImageData imageIo = decodeWithImageIo(bytes);
        if (imageIo != null) {
            return imageIo;
        }
        throw new FdxException("Could not decode image as PNG or JPG");
    }

    private static ImageData decodeWithAndroid(byte[] bytes) {
        try {
            Class<?> bitmapFactoryClass = Class.forName("android.graphics.BitmapFactory");
            Method decode = bitmapFactoryClass.getMethod("decodeByteArray", byte[].class, int.class, int.class);
            Object bitmap = decode.invoke(null, bytes, 0, bytes.length);
            if (bitmap == null) {
                return null;
            }
            Class<?> bitmapClass = bitmap.getClass();
            int width = ((Integer) bitmapClass.getMethod("getWidth").invoke(bitmap)).intValue();
            int height = ((Integer) bitmapClass.getMethod("getHeight").invoke(bitmap)).intValue();
            int[] pixels = new int[width * height];
            bitmapClass.getMethod("getPixels", int[].class, int.class, int.class, int.class, int.class, int.class, int.class)
                    .invoke(bitmap, pixels, 0, width, 0, 0, width, height);
            recycle(bitmapClass, bitmap);
            return rgba(width, height, pixels);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable error) {
            throw new FdxException("Android image decode failed", error);
        }
    }

    private static ImageData decodeWithImageIo(byte[] bytes) {
        try {
            Class<?> imageIoClass = Class.forName("javax.imageio.ImageIO");
            Method read = imageIoClass.getMethod("read", InputStream.class);
            Object image = read.invoke(null, new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            Class<?> imageClass = image.getClass();
            int width = ((Integer) imageClass.getMethod("getWidth").invoke(image)).intValue();
            int height = ((Integer) imageClass.getMethod("getHeight").invoke(image)).intValue();
            int[] pixels = new int[width * height];
            Method getRgb = imageClass.getMethod("getRGB", int.class, int.class, int.class, int.class,
                    int[].class, int.class, int.class);
            Object result = getRgb.invoke(image, 0, 0, width, height, pixels, 0, width);
            if (result != null && result.getClass().isArray()) {
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] = ((Integer) Array.get(result, i)).intValue();
                }
            }
            return rgba(width, height, pixels);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable error) {
            throw new FdxException("ImageIO decode failed", error);
        }
    }

    private static void recycle(Class<?> bitmapClass, Object bitmap) {
        try {
            bitmapClass.getMethod("recycle").invoke(bitmap);
        } catch (Throwable ignored) {
        }
    }

    private static ImageData rgba(int width, int height, int[] argbPixels) {
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
        for (int i = 0; i < argbPixels.length; i++) {
            int argb = argbPixels[i];
            rgba.put((byte) ((argb >> 16) & 0xff));
            rgba.put((byte) ((argb >> 8) & 0xff));
            rgba.put((byte) (argb & 0xff));
            rgba.put((byte) ((argb >> 24) & 0xff));
        }
        rgba.flip();
        return new ImageData(width, height, rgba);
    }
}
