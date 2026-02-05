package io.sessioncast.core.event;

/**
 * Represents a subscription that can be disposed (unsubscribed).
 */
public interface Disposable {

    /**
     * Dispose this subscription.
     */
    void dispose();

    /**
     * Check if this subscription has been disposed.
     */
    boolean isDisposed();

    /**
     * Create a disposable from a runnable.
     */
    static Disposable fromRunnable(Runnable onDispose) {
        return new Disposable() {
            private volatile boolean disposed = false;

            @Override
            public void dispose() {
                if (!disposed) {
                    disposed = true;
                    onDispose.run();
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }
        };
    }

    /**
     * Empty disposable that does nothing.
     */
    static Disposable empty() {
        return new Disposable() {
            @Override
            public void dispose() {}

            @Override
            public boolean isDisposed() {
                return true;
            }
        };
    }
}
