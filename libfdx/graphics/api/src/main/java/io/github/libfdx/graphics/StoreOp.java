package io.github.libfdx.graphics;

public final class StoreOp {
    private final boolean store;

    private StoreOp(boolean store) {
        this.store = store;
    }

    public static StoreOp store() {
        return new StoreOp(true);
    }

    public static StoreOp discard() {
        return new StoreOp(false);
    }

    public boolean isStore() {
        return store;
    }
}
