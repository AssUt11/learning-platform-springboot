package com.learning.platform.ai.domain;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AiCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Closeable resource;

    public void register(Closeable resource) {
        this.resource = resource;
        if (cancelled.get()) close(resource);
    }

    public void cancel() {
        cancelled.set(true);
        close(resource);
    }

    public boolean isCancelled() { return cancelled.get(); }

    public void throwIfCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("AI request cancelled");
        }
    }

    private void close(Closeable target) {
        if (target == null) return;
        try {
            target.close();
        } catch (IOException ignored) {
            // Cancellation is best effort; the interrupted worker is the second stop signal.
        }
    }
}
